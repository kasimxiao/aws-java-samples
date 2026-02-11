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
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream;
import software.amazon.awssdk.services.cloudwatchlogs.model.OrderBy;
import software.amazon.awssdk.services.cloudwatchlogs.model.OutputLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutRetentionPolicyRequest;

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
        System.out.println("查询训练作业日志流: " + trainingJobName);

        DescribeLogStreamsRequest request = DescribeLogStreamsRequest.builder()
                .logGroupName(TRAINING_LOG_GROUP)
                .logStreamNamePrefix(trainingJobName)
                .orderBy(OrderBy.LAST_EVENT_TIME)
                .descending(true)
                .build();

        DescribeLogStreamsResponse response = logsClient.describeLogStreams(request);
        List<LogStream> streams = response.logStreams();
        System.out.println("找到 " + streams.size() + " 个日志流");
        return streams;
    }

    /**
     * 获取指定日志流的日志事件
     *
     * 底层调用 CloudWatch Logs GetLogEvents API，从日志流头部开始读取。
     * 返回的日志事件包含时间戳（Unix 毫秒）和消息内容。
     *
     * @param logStreamName 日志流名称（如 "my-job/algo-1-1704067200"）
     * @param limit         最大返回条数，CloudWatch Logs 单次最多返回 10000 条
     * @return 日志事件列表，按时间正序排列
     */
    public List<OutputLogEvent> getLogEvents(String logStreamName, int limit) {
        GetLogEventsRequest request = GetLogEventsRequest.builder()
                .logGroupName(TRAINING_LOG_GROUP)
                .logStreamName(logStreamName)
                .startFromHead(true)
                .limit(limit)
                .build();

        GetLogEventsResponse response = logsClient.getLogEvents(request);
        return response.events();
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
        System.out.println("获取训练作业日志: " + trainingJobName);

        List<LogStream> streams = getTrainingJobLogStreams(trainingJobName);
        List<OutputLogEvent> allEvents = new ArrayList<>();

        for (LogStream stream : streams) {
            List<OutputLogEvent> events = getLogEvents(stream.logStreamName(), limit);
            allEvents.addAll(events);
        }

        // 按时间排序
        allEvents.sort((a, b) -> Long.compare(a.timestamp(), b.timestamp()));
        System.out.println("共获取 " + allEvents.size() + " 条日志");
        return allEvents;
    }

    /**
     * 按关键字和时间范围过滤训练作业日志
     *
     * 底层调用 CloudWatch Logs FilterLogEvents API，支持文本模式匹配和时间范围过滤。
     * filterPattern 支持简单文本匹配（如 "ERROR"）和 CloudWatch Logs 过滤语法
     * （如 "[ip, user, ...]" 格式的空格分隔过滤）。
     *
     * @param trainingJobName 训练作业名称，用作日志流名称前缀
     * @param filterPattern   过滤模式（如 "Loss"、"ERROR"、"Accuracy"），大小写敏感
     * @param startTime       开始时间（可为 null，不限制开始时间）
     * @param endTime         结束时间（可为 null，不限制结束时间）
     * @param limit           最大返回条数，CloudWatch Logs 单次最多返回 10000 条
     * @return 过滤后的日志事件列表
     */
    public List<FilteredLogEvent> filterTrainingJobLogs(String trainingJobName,
                                                         String filterPattern,
                                                         Instant startTime,
                                                         Instant endTime,
                                                         int limit) {
        System.out.println("过滤训练作业日志: " + trainingJobName + ", 模式: " + filterPattern);

        FilterLogEventsRequest.Builder requestBuilder = FilterLogEventsRequest.builder()
                .logGroupName(TRAINING_LOG_GROUP)
                .logStreamNamePrefix(trainingJobName)
                .filterPattern(filterPattern)
                .limit(limit);

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
     * 底层调用 CloudWatch Logs DescribeLogStreams API。
     * 端点日志组格式为 /aws/sagemaker/Endpoints/[endpoint-name]，
     * 每个推理实例会生成独立的日志流。
     *
     * @param endpointName 端点名称
     * @return 日志流列表，按最后事件时间降序排列
     */
    public List<LogStream> getEndpointLogStreams(String endpointName) {
        System.out.println("查询端点日志流: " + endpointName);

        DescribeLogStreamsRequest request = DescribeLogStreamsRequest.builder()
                .logGroupName(ENDPOINT_LOG_GROUP + "/" + endpointName)
                .orderBy(OrderBy.LAST_EVENT_TIME)
                .descending(true)
                .build();

        DescribeLogStreamsResponse response = logsClient.describeLogStreams(request);
        return response.logStreams();
    }

    /**
     * 按关键字过滤推理端点日志
     *
     * 底层调用 CloudWatch Logs FilterLogEvents API。
     * 可用于过滤推理错误、模型加载日志、自定义日志等。
     *
     * @param endpointName  端点名称
     * @param filterPattern 过滤模式（如 "ERROR"、"model"、"prediction"），大小写敏感
     * @param limit         最大返回条数
     * @return 过滤后的日志事件列表
     */
    public List<FilteredLogEvent> filterEndpointLogs(String endpointName,
                                                      String filterPattern,
                                                      int limit) {
        System.out.println("过滤端点日志: " + endpointName + ", 模式: " + filterPattern);

        FilterLogEventsRequest request = FilterLogEventsRequest.builder()
                .logGroupName(ENDPOINT_LOG_GROUP + "/" + endpointName)
                .filterPattern(filterPattern)
                .limit(limit)
                .build();

        FilterLogEventsResponse response = logsClient.filterLogEvents(request);
        return response.events();
    }

    /**
     * 打印训练作业日志到控制台
     *
     * 获取所有日志流的日志并按时间排序后格式化输出，
     * 每条日志显示时间戳和消息内容。
     *
     * @param trainingJobName 训练作业名称
     * @param limit           每个日志流的最大返回条数
     */
    public void printTrainingJobLogs(String trainingJobName, int limit) {
        List<OutputLogEvent> events = getTrainingJobLogs(trainingJobName, limit);
        System.out.println("==================== 训练作业日志 ====================");
        System.out.println("作业名称: " + trainingJobName);
        System.out.println("日志条数: " + events.size());
        System.out.println();

        for (OutputLogEvent event : events) {
            Instant timestamp = Instant.ofEpochMilli(event.timestamp());
            System.out.println("[" + timestamp + "] " + event.message());
        }
        System.out.println("====================================================");
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
        System.out.println("日志条数: " + events.size());
        System.out.println();

        for (FilteredLogEvent event : events) {
            Instant timestamp = Instant.ofEpochMilli(event.timestamp());
            System.out.println("[" + timestamp + "] " + event.message());
        }
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

    public void close() {
        logsClient.close();
    }
}
