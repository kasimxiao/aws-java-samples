package com.aws.sample.cloudwatch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.aws.sample.common.AwsConfig;

import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.DeleteLogGroupRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilteredLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream;
import software.amazon.awssdk.services.cloudwatchlogs.model.OrderBy;
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutRetentionPolicyRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.QueryStatus;
import software.amazon.awssdk.services.cloudwatchlogs.model.ResultField;
import software.amazon.awssdk.services.cloudwatchlogs.model.StartQueryRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.StartQueryResponse;

/**
 * CloudWatch 日志查询服务
 *
 * 提供 SageMaker 训练作业和推理端点的日志查询与分析功能，底层调用 CloudWatch Logs API。
 *
 * 主要功能:
 * - 训练作业日志流查询（DescribeLogStreams API）
 * - 训练作业日志获取（GetLogEvents API）
 * - 按关键字/时间范围过滤日志（FilterLogEvents API）
 * - 推理端点日志查询与过滤
 *
 * 日志组说明:
 * - 训练作业日志组: /aws/sagemaker/TrainingJobs
 *   日志流格式: [training-job-name]/algo-[instance-number]-[epoch_timestamp]
 * - 端点日志组: /aws/sagemaker/Endpoints/[endpoint-name]
 *   日志流格式: [variant-name]/[instance-id]
 *
 * 使用示例:
 * <pre>
 * AwsConfig config = new AwsConfig();
 * CloudWatchLogService logService = new CloudWatchLogService(config);
 *
 * // 查看训练日志
 * logService.printTrainingJobLogs("my-training-job", 100);
 *
 * // 过滤错误日志
 * List&lt;FilteredLogEvent&gt; errors = logService.getTrainingErrorLogs("my-training-job");
 * </pre>
 */
public class CloudWatchLogService {

    /** SageMaker 训练作业日志组，训练脚本中 stdout/stderr 的输出会自动发送到此日志组 */
    private static final String TRAINING_LOG_GROUP = "/aws/sagemaker/TrainingJobs";
    /** SageMaker 端点日志组前缀，实际日志组为 /aws/sagemaker/Endpoints/[endpoint-name] */
    private static final String ENDPOINT_LOG_GROUP = "/aws/sagemaker/Endpoints";

    private final CloudWatchLogsClient logsClient;

    public CloudWatchLogService(AwsConfig config) {
        this.logsClient = CloudWatchLogsClient.builder()
                .region(config.getRegion())
                .credentialsProvider(config.getCredentialsProvider())
                .build();
    }

    // ==================== 通用日志查询 ====================

    /**
     * 查询指定日志组的日志流
     *
     * 底层调用 CloudWatch Logs DescribeLogStreams API，按最后事件时间降序排列。
     * 适用于任意日志组（SSM 命令日志、自定义应用日志等）。
     *
     * @param logGroupName       日志组名称（如 "/ssm/script-execution"、"/aws/lambda/my-function"）
     * @param logStreamNamePrefix 日志流名称前缀过滤，可为 null 表示不过滤
     * @return 日志流列表，按最后事件时间降序排列
     */
    public List<LogStream> getLogStreams(String logGroupName, String logStreamNamePrefix) {
        System.out.println("查询日志流: " + logGroupName
                + (logStreamNamePrefix != null ? ", 前缀: " + logStreamNamePrefix : ""));

        DescribeLogStreamsRequest.Builder requestBuilder = DescribeLogStreamsRequest.builder()
                .logGroupName(logGroupName)
                .orderBy(OrderBy.LAST_EVENT_TIME)
                .descending(true);

        if (logStreamNamePrefix != null && !logStreamNamePrefix.isEmpty()) {
            requestBuilder.logStreamNamePrefix(logStreamNamePrefix);
        }

        DescribeLogStreamsResponse response = logsClient.describeLogStreams(requestBuilder.build());
        List<LogStream> streams = response.logStreams();
        System.out.println("找到 " + streams.size() + " 个日志流");
        return streams;
    }

    /**
     * 获取指定日志组和日志流的日志事件
     *
     * 底层调用 CloudWatch Logs GetLogEvents API，从日志流头部开始读取。
     *
     * @param logGroupName  日志组名称
     * @param logStreamName 日志流名称
     * @param limit         最大返回条数
     * @return 日志事件列表，按时间正序排列
     */
    public List<OutputLogEvent> getLogEvents(String logGroupName, String logStreamName, int limit) {
        GetLogEventsRequest request = GetLogEventsRequest.builder()
                .logGroupName(logGroupName)
                .logStreamName(logStreamName)
                .startFromHead(true)
                .limit(limit)
                .build();

        GetLogEventsResponse response = logsClient.getLogEvents(request);
        return response.events();
    }

    /**
     * 获取指定日志组的全部日志（合并所有日志流）
     *
     * 遍历日志组下所有日志流（或按前缀过滤），合并日志事件后按时间戳排序。
     *
     * @param logGroupName       日志组名称
     * @param logStreamNamePrefix 日志流名称前缀过滤，可为 null
     * @param limit              每个日志流的最大返回条数
     * @return 所有日志事件，按时间戳正序排列
     */
    public List<OutputLogEvent> getLogs(String logGroupName, String logStreamNamePrefix, int limit) {
        System.out.println("获取日志: " + logGroupName);

        List<LogStream> streams = getLogStreams(logGroupName, logStreamNamePrefix);
        List<OutputLogEvent> allEvents = new ArrayList<>();

        for (LogStream stream : streams) {
            List<OutputLogEvent> events = getLogEvents(logGroupName, stream.logStreamName(), limit);
            allEvents.addAll(events);
        }

        allEvents.sort((a, b) -> Long.compare(a.timestamp(), b.timestamp()));
        System.out.println("共获取 " + allEvents.size() + " 条日志");
        return allEvents;
    }

    /**
     * 按关键字和时间范围过滤指定日志组的日志
     *
     * 底层调用 CloudWatch Logs FilterLogEvents API，适用于任意日志组。
     *
     * @param logGroupName       日志组名称
     * @param logStreamNamePrefix 日志流名称前缀过滤，可为 null
     * @param filterPattern      过滤模式（如 "ERROR"、"Exception"），大小写敏感
     * @param startTime          开始时间，可为 null
     * @param endTime            结束时间，可为 null
     * @param limit              最大返回条数
     * @return 过滤后的日志事件列表
     */
    public List<FilteredLogEvent> filterLogs(String logGroupName,
                                              String logStreamNamePrefix,
                                              String filterPattern,
                                              Instant startTime,
                                              Instant endTime,
                                              int limit) {
        System.out.println("过滤日志: " + logGroupName + ", 模式: " + filterPattern);

        FilterLogEventsRequest.Builder requestBuilder = FilterLogEventsRequest.builder()
                .logGroupName(logGroupName)
                .filterPattern(filterPattern)
                .limit(limit);

        if (logStreamNamePrefix != null && !logStreamNamePrefix.isEmpty()) {
            requestBuilder.logStreamNamePrefix(logStreamNamePrefix);
        }
        if (startTime != null) {
            requestBuilder.startTime(startTime.toEpochMilli());
        }
        if (endTime != null) {
            requestBuilder.endTime(endTime.toEpochMilli());
        }

        FilterLogEventsResponse response = logsClient.filterLogEvents(requestBuilder.build());
        List<FilteredLogEvent> events = response.events();
        System.out.println("过滤后获取 " + events.size() + " 条日志");
        return events;
    }

    /**
     * 打印指定日志组的日志到控制台
     *
     * @param logGroupName       日志组名称
     * @param logStreamNamePrefix 日志流名称前缀过滤，可为 null
     * @param limit              每个日志流的最大返回条数
     */
    public void printLogs(String logGroupName, String logStreamNamePrefix, int limit) {
        List<OutputLogEvent> events = getLogs(logGroupName, logStreamNamePrefix, limit);
        System.out.println("==================== 日志输出 ====================");
        System.out.println("日志组: " + logGroupName);
        System.out.println("日志条数: " + events.size());
        System.out.println();

        for (OutputLogEvent event : events) {
            Instant timestamp = Instant.ofEpochMilli(event.timestamp());
            System.out.println("[" + timestamp + "] " + event.message());
        }
        System.out.println("==================================================");
    }

    /**
     * 打印过滤后的日志事件列表
     *
     * @param events 过滤后的日志事件列表
     */
    public void printFilteredEvents(List<FilteredLogEvent> events) {
        System.out.println("日志条数: " + events.size());
        System.out.println();
        for (FilteredLogEvent event : events) {
            Instant timestamp = Instant.ofEpochMilli(event.timestamp());
            System.out.println("[" + timestamp + "] " + event.message());
        }
    }

    // ==================== SageMaker 训练作业日志查询 ====================

    /**
     * 获取训练作业的所有日志流
     *
     * 底层调用 CloudWatch Logs DescribeLogStreams API，按最后事件时间降序排列。
     * 每个训练实例会生成一个独立的日志流，日志流名称格式为:
     * [training-job-name]/algo-[N]-[epoch_timestamp]
     * 单实例训练只有一个日志流（algo-1），多实例训练有多个。
     *
     * @param trainingJobName 训练作业名称，用作日志流名称前缀过滤
     * @return 日志流列表，按最后事件时间降序排列
     */
    public List<LogStream> getTrainingJobLogStreams(String trainingJobName) {
        return getLogStreams(TRAINING_LOG_GROUP, trainingJobName);
    }

    /**
     * 获取训练作业指定日志流的日志事件
     *
     * @param logStreamName 日志流名称（如 "my-job/algo-1-1704067200"）
     * @param limit         最大返回条数
     * @return 日志事件列表，按时间正序排列
     */
    public List<OutputLogEvent> getTrainingLogEvents(String logStreamName, int limit) {
        return getLogEvents(TRAINING_LOG_GROUP, logStreamName, limit);
    }

    /**
     * 获取训练作业的全部日志
     *
     * 遍历该训练作业的所有日志流，合并日志事件后按时间戳排序。
     * 多实例训练时，会合并所有实例（algo-1、algo-2 等）的日志。
     *
     * @param trainingJobName 训练作业名称
     * @param limit           每个日志流的最大返回条数
     * @return 所有日志事件，按时间戳正序排列
     */
    public List<OutputLogEvent> getTrainingJobLogs(String trainingJobName, int limit) {
        return getLogs(TRAINING_LOG_GROUP, trainingJobName, limit);
    }

    /**
     * 按关键字和时间范围过滤训练作业日志
     *
     * @param trainingJobName 训练作业名称，用作日志流名称前缀
     * @param filterPattern   过滤模式（如 "Loss"、"ERROR"、"Accuracy"），大小写敏感
     * @param startTime       开始时间（可为 null）
     * @param endTime         结束时间（可为 null）
     * @param limit           最大返回条数
     * @return 过滤后的日志事件列表
     */
    public List<FilteredLogEvent> filterTrainingJobLogs(String trainingJobName,
                                                         String filterPattern,
                                                         Instant startTime,
                                                         Instant endTime,
                                                         int limit) {
        return filterLogs(TRAINING_LOG_GROUP, trainingJobName, filterPattern, startTime, endTime, limit);
    }

    /**
     * 获取训练作业的损失日志
     *
     * 过滤包含 "Loss" 关键字的日志，对应训练脚本中输出的损失值日志
     * （如 "Train Loss: 0.1234"、"Validation Loss: 0.5678"）。
     * 最多返回 500 条，不限制时间范围。
     *
     * @param trainingJobName 训练作业名称
     * @return 包含 "Loss" 关键字的日志事件列表
     */
    public List<FilteredLogEvent> getTrainingLossLogs(String trainingJobName) {
        return filterTrainingJobLogs(trainingJobName, "Loss", null, null, 500);
    }

    /**
     * 获取训练作业的错误日志
     *
     * 过滤包含 "ERROR" 关键字的日志，用于快速定位训练过程中的异常。
     * 常见错误包括: OOM（内存不足）、CUDA 错误、数据加载失败等。
     * 最多返回 200 条，不限制时间范围。
     *
     * @param trainingJobName 训练作业名称
     * @return 包含 "ERROR" 关键字的日志事件列表
     */
    public List<FilteredLogEvent> getTrainingErrorLogs(String trainingJobName) {
        return filterTrainingJobLogs(trainingJobName, "ERROR", null, null, 200);
    }

    /**
     * 获取推理端点的日志流
     *
     * @param endpointName 端点名称
     * @return 日志流列表，按最后事件时间降序排列
     */
    public List<LogStream> getEndpointLogStreams(String endpointName) {
        return getLogStreams(ENDPOINT_LOG_GROUP + "/" + endpointName, null);
    }

    /**
     * 按关键字过滤推理端点日志
     *
     * @param endpointName  端点名称
     * @param filterPattern 过滤模式（如 "ERROR"、"model"、"prediction"），大小写敏感
     * @param limit         最大返回条数
     * @return 过滤后的日志事件列表
     */
    public List<FilteredLogEvent> filterEndpointLogs(String endpointName,
                                                      String filterPattern,
                                                      int limit) {
        return filterLogs(ENDPOINT_LOG_GROUP + "/" + endpointName, null, filterPattern, null, null, limit);
    }

    /**
     * 打印训练作业日志到控制台
     *
     * @param trainingJobName 训练作业名称
     * @param limit           每个日志流的最大返回条数
     */
    public void printTrainingJobLogs(String trainingJobName, int limit) {
        printLogs(TRAINING_LOG_GROUP, trainingJobName, limit);
    }

    /**
     * 打印按关键字过滤后的训练日志到控制台
     *
     * 使用 FilterLogEvents API 过滤后格式化输出，最多返回 200 条。
     *
     * @param trainingJobName 训练作业名称
     * @param filterPattern   过滤模式（如 "Loss"、"ERROR"、"Accuracy"）
     */
    public void printFilteredTrainingLogs(String trainingJobName, String filterPattern) {
        List<FilteredLogEvent> events = filterTrainingJobLogs(
                trainingJobName, filterPattern, null, null, 200);
        System.out.println("==================== 过滤日志 ====================");
        System.out.println("作业名称: " + trainingJobName);
        System.out.println("过滤模式: " + filterPattern);
        printFilteredEvents(events);
        System.out.println("==================================================");
    }

    /**
     * 设置日志组的过期时间（保留天数）
     *
     * 底层调用 CloudWatch Logs PutRetentionPolicy API。
     * 超过保留天数的日志事件会被自动删除。
     *
     * @param logGroupName  日志组名称（如 "/aws/sagemaker/TrainingJobs"）
     * @param retentionDays 保留天数，支持的值: 1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557, 2922, 3288, 3653
     */
    public void setLogGroupRetention(String logGroupName, int retentionDays) {
        System.out.println("设置日志组过期时间: " + logGroupName + ", 保留天数: " + retentionDays);

        PutRetentionPolicyRequest request = PutRetentionPolicyRequest.builder()
                .logGroupName(logGroupName)
                .retentionInDays(retentionDays)
                .build();

        logsClient.putRetentionPolicy(request);
        System.out.println("日志组过期时间设置成功");
    }

    /**
     * 设置训练作业日志组的过期时间
     *
     * @param retentionDays 保留天数
     */
    public void setTrainingLogRetention(int retentionDays) {
        setLogGroupRetention(TRAINING_LOG_GROUP, retentionDays);
    }

    /**
     * 设置推理端点日志组的过期时间
     *
     * @param endpointName  端点名称
     * @param retentionDays 保留天数
     */
    public void setEndpointLogRetention(String endpointName, int retentionDays) {
        setLogGroupRetention(ENDPOINT_LOG_GROUP + "/" + endpointName, retentionDays);
    }

    /**
     * 删除日志组
     *
     * 底层调用 CloudWatch Logs DeleteLogGroup API。
     * 删除后该日志组下的所有日志流和日志事件将被永久删除，不可恢复。
     *
     * @param logGroupName 日志组名称
     */
    public void deleteLogGroup(String logGroupName) {
        System.out.println("删除日志组: " + logGroupName);

        DeleteLogGroupRequest request = DeleteLogGroupRequest.builder()
                .logGroupName(logGroupName)
                .build();

        logsClient.deleteLogGroup(request);
        System.out.println("日志组删除成功");
    }

    /**
     * 删除推理端点的日志组
     *
     * @param endpointName 端点名称
     */
    public void deleteEndpointLogGroup(String endpointName) {
        deleteLogGroup(ENDPOINT_LOG_GROUP + "/" + endpointName);
    }

    // ==================== CloudWatch Logs Insights 查询 ====================

    /**
     * 使用 CloudWatch Logs Insights 执行查询
     *
     * 底层调用 CloudWatch Logs StartQuery + GetQueryResults API。
     * Logs Insights 支持丰富的查询语法，包括 fields、filter、stats、sort、limit 等命令，
     * 比 FilterLogEvents 更灵活，适合复杂的日志分析场景。
     *
     * @param logGroupName 日志组名称
     * @param queryString  Logs Insights 查询语句（如 "fields @timestamp, @message | filter @message like /ERROR/"）
     * @param startTime    查询开始时间
     * @param endTime      查询结束时间
     * @param limit        最大返回条数（Logs Insights 最大 10000）
     * @return 查询结果列表，每条结果为一组 ResultField（字段名-值对）
     */
    public List<List<ResultField>> runInsightsQuery(String logGroupName,
                                                     String queryString,
                                                     Instant startTime,
                                                     Instant endTime,
                                                     int limit) {
        System.out.println("执行 Logs Insights 查询: " + logGroupName);
        System.out.println("查询语句: " + queryString);

        StartQueryRequest startRequest = StartQueryRequest.builder()
                .logGroupName(logGroupName)
                .queryString(queryString)
                .startTime(startTime.getEpochSecond())
                .endTime(endTime.getEpochSecond())
                .limit(limit)
                .build();

        StartQueryResponse startResponse = logsClient.startQuery(startRequest);
        String queryId = startResponse.queryId();
        System.out.println("查询已提交，queryId: " + queryId);

        // 轮询等待查询完成
        GetQueryResultsResponse resultsResponse;
        while (true) {
            GetQueryResultsRequest getRequest = GetQueryResultsRequest.builder()
                    .queryId(queryId)
                    .build();
            resultsResponse = logsClient.getQueryResults(getRequest);

            QueryStatus status = resultsResponse.status();
            if (status == QueryStatus.COMPLETE || status == QueryStatus.FAILED || status == QueryStatus.CANCELLED) {
                break;
            }

            // 查询进行中，等待 1 秒后重试
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Logs Insights 查询被中断", e);
            }
        }

        if (resultsResponse.status() != QueryStatus.COMPLETE) {
            System.out.println("查询未成功完成，状态: " + resultsResponse.status());
            return new ArrayList<>();
        }

        List<List<ResultField>> results = resultsResponse.results();
        System.out.println("查询完成，返回 " + results.size() + " 条结果");
        return results;
    }

    /**
     * 使用 Logs Insights 查询训练作业日志
     *
     * @param trainingJobName 训练作业名称（用于 filter 日志流前缀）
     * @param queryString     Logs Insights 查询语句
     * @param startTime       查询开始时间
     * @param endTime         查询结束时间
     * @param limit           最大返回条数
     * @return 查询结果列表
     */
    public List<List<ResultField>> runTrainingInsightsQuery(String trainingJobName,
                                                             String queryString,
                                                             Instant startTime,
                                                             Instant endTime,
                                                             int limit) {
        return runInsightsQuery(TRAINING_LOG_GROUP, queryString, startTime, endTime, limit);
    }

    /**
     * 使用 Logs Insights 查询推理端点日志
     *
     * @param endpointName 端点名称
     * @param queryString  Logs Insights 查询语句
     * @param startTime    查询开始时间
     * @param endTime      查询结束时间
     * @param limit        最大返回条数
     * @return 查询结果列表
     */
    public List<List<ResultField>> runEndpointInsightsQuery(String endpointName,
                                                             String queryString,
                                                             Instant startTime,
                                                             Instant endTime,
                                                             int limit) {
        return runInsightsQuery(ENDPOINT_LOG_GROUP + "/" + endpointName, queryString, startTime, endTime, limit);
    }

    /**
     * 使用 Logs Insights 判断指定日志组是否存在 ERROR 日志
     *
     * @param logGroupName 日志组名称
     * @param startTime    查询开始时间
     * @param endTime      查询结束时间
     * @return true 表示存在 ERROR 日志，false 表示无错误
     */
    public boolean hasErrors(String logGroupName, Instant startTime, Instant endTime) {
        String query = "fields @timestamp, @message "
                + "| filter @message like /(?i)ERROR/ "
                + "| sort @timestamp desc "
                + "| limit 1";

        List<List<ResultField>> results = runInsightsQuery(logGroupName, query, startTime, endTime, 1);
        boolean hasErrors = !results.isEmpty();
        System.out.println("日志组 " + logGroupName + " 是否存在错误: " + (hasErrors ? "是" : "否"));
        return hasErrors;
    }

    /** 判断训练作业是否存在 ERROR 日志 */
    public boolean hasTrainingErrors(String trainingJobName, Instant startTime, Instant endTime) {
        return hasErrors(TRAINING_LOG_GROUP, startTime, endTime);
    }

    /** 判断推理端点是否存在 ERROR 日志 */
    public boolean hasEndpointErrors(String endpointName, Instant startTime, Instant endTime) {
        return hasErrors(ENDPOINT_LOG_GROUP + "/" + endpointName, startTime, endTime);
    }

    /**
     * 使用 Logs Insights 统计指定日志组的错误数量
     *
     * @param logGroupName 日志组名称
     * @param startTime    查询开始时间
     * @param endTime      查询结束时间
     * @return 错误日志条数
     */
    public long countErrors(String logGroupName, Instant startTime, Instant endTime) {
        String query = "fields @message "
                + "| filter @message like /(?i)ERROR/ "
                + "| stats count() as errorCount";

        List<List<ResultField>> results = runInsightsQuery(logGroupName, query, startTime, endTime, 1);

        long errorCount = 0;
        if (!results.isEmpty()) {
            for (ResultField field : results.get(0)) {
                if ("errorCount".equals(field.field())) {
                    errorCount = Long.parseLong(field.value());
                    break;
                }
            }
        }
        System.out.println("日志组 " + logGroupName + " 错误数量: " + errorCount);
        return errorCount;
    }

    /** 统计训练作业的错误数量 */
    public long countTrainingErrors(String trainingJobName, Instant startTime, Instant endTime) {
        return countErrors(TRAINING_LOG_GROUP, startTime, endTime);
    }

    /**
     * 使用 Logs Insights 获取指定日志组的 ERROR 日志详情
     *
     * @param logGroupName 日志组名称
     * @param startTime    查询开始时间
     * @param endTime      查询结束时间
     * @param limit        最大返回条数
     * @return 查询结果列表
     */
    public List<List<ResultField>> getErrorDetails(String logGroupName,
                                                    Instant startTime,
                                                    Instant endTime,
                                                    int limit) {
        String query = "fields @timestamp, @message, @logStream "
                + "| filter @message like /(?i)ERROR/ "
                + "| sort @timestamp desc "
                + "| limit " + limit;

        return runInsightsQuery(logGroupName, query, startTime, endTime, limit);
    }

    /** 获取训练作业的 ERROR 日志详情 */
    public List<List<ResultField>> getTrainingErrorDetails(String trainingJobName,
                                                            Instant startTime,
                                                            Instant endTime,
                                                            int limit) {
        return getErrorDetails(TRAINING_LOG_GROUP, startTime, endTime, limit);
    }

    /**
     * 打印 Logs Insights 查询结果到控制台
     *
     * @param results 查询结果列表
     */
    public void printInsightsResults(List<List<ResultField>> results) {
        System.out.println("==================== Logs Insights 查询结果 ====================");
        System.out.println("结果条数: " + results.size());
        System.out.println();

        for (int i = 0; i < results.size(); i++) {
            System.out.println("--- 第 " + (i + 1) + " 条 ---");
            for (ResultField field : results.get(i)) {
                // 跳过内部指针字段
                if (!"@ptr".equals(field.field())) {
                    System.out.println("  " + field.field() + ": " + field.value());
                }
            }
        }
        System.out.println("================================================================");
    }

    /**
     * 打印指定日志组的错误诊断报告
     *
     * 综合使用 Logs Insights 查询，输出错误数量和错误详情。
     *
     * @param logGroupName 日志组名称
     * @param startTime    查询开始时间
     * @param endTime      查询结束时间
     */
    public void printErrorDiagnostics(String logGroupName, Instant startTime, Instant endTime) {
        System.out.println("==================== 错误诊断报告 ====================");
        System.out.println("日志组: " + logGroupName);
        System.out.println("时间范围: " + startTime + " ~ " + endTime);
        System.out.println();

        long errorCount = countErrors(logGroupName, startTime, endTime);
        System.out.println("错误总数: " + errorCount);

        if (errorCount == 0) {
            System.out.println("诊断结果: 未发现错误日志，运行正常");
        } else {
            System.out.println("诊断结果: 发现 " + errorCount + " 条错误日志，请检查以下详情");
            System.out.println();
            List<List<ResultField>> errors = getErrorDetails(logGroupName, startTime, endTime, 20);
            printInsightsResults(errors);
        }
        System.out.println("============================================================");
    }

    /** 打印训练作业错误诊断报告 */
    public void printTrainingErrorDiagnostics(String trainingJobName, Instant startTime, Instant endTime) {
        printErrorDiagnostics(TRAINING_LOG_GROUP, startTime, endTime);
    }

    // ==================== SSM 脚本执行日志监控 ====================

    /**
     * 通过 CloudWatch Logs 判断 SSM 脚本执行是否成功
     *
     * 查询 SsmService.executeScriptWithLogs 输出到 CloudWatch Logs 的日志，
     * 通过匹配脚本执行结束时输出的标记来判断执行结果:
     * - "执行结果: 成功" 表示脚本正常退出（exit code 0）
     * - "ERROR: 执行结果: 失败" 表示脚本异常退出
     *
     * @param logGroupName CloudWatch Logs 日志组名称（与 executeScriptWithLogs 中传入的一致）
     * @param startTime    查询开始时间
     * @param endTime      查询结束时间
     * @return true 表示执行成功，false 表示执行失败或未找到结果日志
     */
    public boolean isSsmScriptSuccess(String logGroupName, Instant startTime, Instant endTime) {
        System.out.println("查询 SSM 脚本执行结果: " + logGroupName);

        // 先查是否有成功标记
        String successQuery = "fields @timestamp, @message "
                + "| filter @message like /执行结果: 成功/ "
                + "| sort @timestamp desc "
                + "| limit 1";

        List<List<ResultField>> successResults = runInsightsQuery(
                logGroupName, successQuery, startTime, endTime, 1);

        if (!successResults.isEmpty()) {
            System.out.println("SSM 脚本执行结果: 成功");
            return true;
        }

        // 查是否有失败标记
        String failQuery = "fields @timestamp, @message "
                + "| filter @message like /ERROR: 执行结果: 失败/ "
                + "| sort @timestamp desc "
                + "| limit 1";

        List<List<ResultField>> failResults = runInsightsQuery(
                logGroupName, failQuery, startTime, endTime, 1);

        if (!failResults.isEmpty()) {
            System.out.println("SSM 脚本执行结果: 失败");
        } else {
            System.out.println("SSM 脚本执行结果: 未找到执行结果日志（可能仍在运行中）");
        }
        return false;
    }

    /**
     * 通过 CloudWatch Logs 获取 SSM 脚本执行的完整诊断报告
     *
     * 综合查询脚本执行状态、ERROR 日志数量和错误详情，
     * 一次调用输出完整的执行诊断信息。
     *
     * @param logGroupName CloudWatch Logs 日志组名称
     * @param startTime    查询开始时间
     * @param endTime      查询结束时间
     */
    public void printSsmScriptDiagnostics(String logGroupName, Instant startTime, Instant endTime) {
        System.out.println("==================== SSM 脚本执行诊断 ====================");
        System.out.println("日志组: " + logGroupName);
        System.out.println("时间范围: " + startTime + " ~ " + endTime);
        System.out.println();

        // 1. 判断执行结果
        boolean success = isSsmScriptSuccess(logGroupName, startTime, endTime);
        System.out.println("执行状态: " + (success ? "成功" : "失败或未完成"));
        System.out.println();

        // 2. 复用通用错误诊断（统计错误数量 + 输出错误详情）
        long errorCount = countErrors(logGroupName, startTime, endTime);
        System.out.println("ERROR 日志数量: " + errorCount);

        if (errorCount > 0) {
            System.out.println();
            List<List<ResultField>> errors = getErrorDetails(logGroupName, startTime, endTime, 20);
            printInsightsResults(errors);
        }

        System.out.println("============================================================");
    }

    public void close() {
        logsClient.close();
    }
}
