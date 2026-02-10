package com.aws.sample.cloudwatch;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.aws.sample.common.AwsConfig;

import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.ComparisonOperator;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsRequest;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.DimensionFilter;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.cloudwatch.model.ListMetricsRequest;
import software.amazon.awssdk.services.cloudwatch.model.ListMetricsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Metric;
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricAlarmRequest;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;

/**
 * CloudWatch 指标查询服务
 * 提供 SageMaker 训练和端点的指标查询、告警管理功能
 */
public class CloudWatchMetricService {

    /** SageMaker 指标命名空间 */
    private static final String SAGEMAKER_NAMESPACE = "AWS/SageMaker";
    /** SageMaker 训练作业实例资源指标命名空间 */
    private static final String SAGEMAKER_TRAINING_NAMESPACE = "/aws/sagemaker/TrainingJobs";
    /** SageMaker 端点实例资源指标命名空间 */
    private static final String SAGEMAKER_ENDPOINT_NAMESPACE = "/aws/sagemaker/Endpoints";

    private final CloudWatchClient cloudWatchClient;

    public CloudWatchMetricService(AwsConfig config) {
        this.cloudWatchClient = CloudWatchClient.builder()
                .region(config.getRegion())
                .credentialsProvider(config.getCredentialsProvider())
                .build();
    }

    // ==================== 训练实例资源利用率查询 ====================

    /**
     * 查询训练实例的资源利用率指标
     * 命名空间: /aws/sagemaker/TrainingJobs，维度: Host
     *
     * @param trainingJobName 训练作业名称
     * @param host            实例主机名（如 "algo-1"）
     * @param metricName      指标名称（CPUUtilization / MemoryUtilization / GPUUtilization / GPUMemoryUtilization / DiskUtilization）
     * @param startTime       开始时间
     * @param endTime         结束时间
     * @param periodSeconds   统计周期（秒）
     * @return 指标数据点列表
     */
    public List<Datapoint> getTrainingInstanceResourceMetric(String trainingJobName,
                                                              String host,
                                                              String metricName,
                                                              Instant startTime,
                                                              Instant endTime,
                                                              int periodSeconds) {
        System.out.println("查询训练实例资源: " + metricName + " (作业: " + trainingJobName + ", 主机: " + host + ")");

        Dimension hostDimension = Dimension.builder()
                .name("Host")
                .value(host)
                .build();

        GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                .namespace(SAGEMAKER_TRAINING_NAMESPACE)
                .metricName(metricName)
                .dimensions(hostDimension)
                .startTime(startTime)
                .endTime(endTime)
                .period(periodSeconds)
                .statistics(Statistic.AVERAGE, Statistic.MINIMUM, Statistic.MAXIMUM)
                .build();

        GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
        List<Datapoint> datapoints = new ArrayList<>(response.datapoints());
        datapoints.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));
        return datapoints;
    }

    /**
     * 查询训练实例 CPU 利用率
     *
     * @param trainingJobName 训练作业名称
     * @param host            实例主机名（默认 "algo-1"）
     * @param hours           最近 N 小时
     */
    public List<Datapoint> getTrainingCpuUtilization(String trainingJobName, String host, int hours) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(Duration.ofHours(hours));
        return getTrainingInstanceResourceMetric(trainingJobName, host, "CPUUtilization", startTime, endTime, 60);
    }

    /**
     * 查询训练实例内存利用率
     *
     * @param trainingJobName 训练作业名称
     * @param host            实例主机名（默认 "algo-1"）
     * @param hours           最近 N 小时
     * @return 内存利用率数据点列表，值为百分比（0-100）
     */
    public List<Datapoint> getTrainingMemoryUtilization(String trainingJobName, String host, int hours) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(Duration.ofHours(hours));
        return getTrainingInstanceResourceMetric(trainingJobName, host, "MemoryUtilization", startTime, endTime, 60);
    }

    /**
     * 查询训练实例 GPU 利用率
     * 仅在使用 GPU 实例（如 ml.p3、ml.g4dn、ml.p4d）时有数据
     *
     * @param trainingJobName 训练作业名称
     * @param host            实例主机名（默认 "algo-1"）
     * @param hours           最近 N 小时
     * @return GPU 利用率数据点列表，值为百分比（0-100）
     */
    public List<Datapoint> getTrainingGpuUtilization(String trainingJobName, String host, int hours) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(Duration.ofHours(hours));
        return getTrainingInstanceResourceMetric(trainingJobName, host, "GPUUtilization", startTime, endTime, 60);
    }

    /**
     * 查询训练实例 GPU 显存利用率
     * 仅在使用 GPU 实例时有数据，显存利用率过高可能导致 OOM 错误
     *
     * @param trainingJobName 训练作业名称
     * @param host            实例主机名（默认 "algo-1"）
     * @param hours           最近 N 小时
     * @return GPU 显存利用率数据点列表，值为百分比（0-100）
     */
    public List<Datapoint> getTrainingGpuMemoryUtilization(String trainingJobName, String host, int hours) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(Duration.ofHours(hours));
        return getTrainingInstanceResourceMetric(trainingJobName, host, "GPUMemoryUtilization", startTime, endTime, 60);
    }

    /**
     * 打印训练实例资源利用率摘要
     *
     * @param trainingJobName 训练作业名称
     * @param host            实例主机名（默认 "algo-1"）
     * @param hours           最近 N 小时
     */
    public void printTrainingResourceSummary(String trainingJobName, String host, int hours) {
        System.out.println("==================== 训练实例资源利用率 ====================");
        System.out.println("作业名称: " + trainingJobName);
        System.out.println("主机: " + host);
        System.out.println("时间范围: 最近 " + hours + " 小时");
        System.out.println();

        // CPU
        List<Datapoint> cpu = getTrainingCpuUtilization(trainingJobName, host, hours);
        if (!cpu.isEmpty()) {
            double avgCpu = cpu.stream().mapToDouble(dp -> dp.average() != null ? dp.average() : 0).average().orElse(0);
            double maxCpu = cpu.stream().mapToDouble(dp -> dp.maximum() != null ? dp.maximum() : 0).max().orElse(0);
            System.out.printf("CPU 利用率: 平均 %.1f%%, 最大 %.1f%%%n", avgCpu, maxCpu);
        } else {
            System.out.println("CPU 利用率: 无数据");
        }

        // 内存
        List<Datapoint> mem = getTrainingMemoryUtilization(trainingJobName, host, hours);
        if (!mem.isEmpty()) {
            double avgMem = mem.stream().mapToDouble(dp -> dp.average() != null ? dp.average() : 0).average().orElse(0);
            double maxMem = mem.stream().mapToDouble(dp -> dp.maximum() != null ? dp.maximum() : 0).max().orElse(0);
            System.out.printf("内存利用率: 平均 %.1f%%, 最大 %.1f%%%n", avgMem, maxMem);
        } else {
            System.out.println("内存利用率: 无数据");
        }

        // GPU
        List<Datapoint> gpu = getTrainingGpuUtilization(trainingJobName, host, hours);
        if (!gpu.isEmpty()) {
            double avgGpu = gpu.stream().mapToDouble(dp -> dp.average() != null ? dp.average() : 0).average().orElse(0);
            double maxGpu = gpu.stream().mapToDouble(dp -> dp.maximum() != null ? dp.maximum() : 0).max().orElse(0);
            System.out.printf("GPU 利用率: 平均 %.1f%%, 最大 %.1f%%%n", avgGpu, maxGpu);
        } else {
            System.out.println("GPU 利用率: 无数据（非 GPU 实例或未启用）");
        }

        // GPU 显存
        List<Datapoint> gpuMem = getTrainingGpuMemoryUtilization(trainingJobName, host, hours);
        if (!gpuMem.isEmpty()) {
            double avgGpuMem = gpuMem.stream().mapToDouble(dp -> dp.average() != null ? dp.average() : 0).average().orElse(0);
            double maxGpuMem = gpuMem.stream().mapToDouble(dp -> dp.maximum() != null ? dp.maximum() : 0).max().orElse(0);
            System.out.printf("GPU 显存利用率: 平均 %.1f%%, 最大 %.1f%%%n", avgGpuMem, maxGpuMem);
        } else {
            System.out.println("GPU 显存利用率: 无数据（非 GPU 实例或未启用）");
        }

        System.out.println("============================================================");
    }

    // ==================== 推理端点实例资源利用率查询 ====================

    /**
     * 查询推理端点实例的资源利用率指标
     * 命名空间: /aws/sagemaker/Endpoints，维度: EndpointName + VariantName
     *
     * @param endpointName  端点名称
     * @param variantName   变体名称（通常为 "AllTraffic"）
     * @param metricName    指标名称（CPUUtilization / MemoryUtilization / GPUUtilization / GPUMemoryUtilization / DiskUtilization）
     * @param startTime     开始时间
     * @param endTime       结束时间
     * @param periodSeconds 统计周期（秒）
     * @return 指标数据点列表
     */
    public List<Datapoint> getEndpointInstanceResourceMetric(String endpointName,
                                                              String variantName,
                                                              String metricName,
                                                              Instant startTime,
                                                              Instant endTime,
                                                              int periodSeconds) {
        System.out.println("查询端点实例资源: " + metricName + " (端点: " + endpointName + ")");

        Dimension endpointDim = Dimension.builder()
                .name("EndpointName")
                .value(endpointName)
                .build();

        Dimension variantDim = Dimension.builder()
                .name("VariantName")
                .value(variantName)
                .build();

        GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                .namespace(SAGEMAKER_ENDPOINT_NAMESPACE)
                .metricName(metricName)
                .dimensions(endpointDim, variantDim)
                .startTime(startTime)
                .endTime(endTime)
                .period(periodSeconds)
                .statistics(Statistic.AVERAGE, Statistic.MINIMUM, Statistic.MAXIMUM)
                .build();

        GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
        List<Datapoint> datapoints = new ArrayList<>(response.datapoints());
        datapoints.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));
        return datapoints;
    }

    /**
     * 查询端点实例 CPU 利用率
     *
     * @param endpointName 端点名称
     * @param hours        最近 N 小时
     * @return CPU 利用率数据点列表，值为百分比（0-100），统计周期 300 秒
     */
    public List<Datapoint> getEndpointCpuUtilization(String endpointName, int hours) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(Duration.ofHours(hours));
        return getEndpointInstanceResourceMetric(endpointName, "AllTraffic", "CPUUtilization", startTime, endTime, 300);
    }

    /**
     * 查询端点实例内存利用率
     *
     * @param endpointName 端点名称
     * @param hours        最近 N 小时
     * @return 内存利用率数据点列表，值为百分比（0-100），统计周期 300 秒
     */
    public List<Datapoint> getEndpointMemoryUtilization(String endpointName, int hours) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(Duration.ofHours(hours));
        return getEndpointInstanceResourceMetric(endpointName, "AllTraffic", "MemoryUtilization", startTime, endTime, 300);
    }

    /**
     * 查询端点实例 GPU 利用率
     * 仅在使用 GPU 推理实例（如 ml.g4dn、ml.p3、ml.inf1）时有数据
     *
     * @param endpointName 端点名称
     * @param hours        最近 N 小时
     * @return GPU 利用率数据点列表，值为百分比（0-100），统计周期 300 秒
     */
    public List<Datapoint> getEndpointGpuUtilization(String endpointName, int hours) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(Duration.ofHours(hours));
        return getEndpointInstanceResourceMetric(endpointName, "AllTraffic", "GPUUtilization", startTime, endTime, 300);
    }

    /**
     * 查询端点实例 GPU 显存利用率
     * 仅在使用 GPU 推理实例时有数据，显存不足会导致推理请求失败
     *
     * @param endpointName 端点名称
     * @param hours        最近 N 小时
     * @return GPU 显存利用率数据点列表，值为百分比（0-100），统计周期 300 秒
     */
    public List<Datapoint> getEndpointGpuMemoryUtilization(String endpointName, int hours) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(Duration.ofHours(hours));
        return getEndpointInstanceResourceMetric(endpointName, "AllTraffic", "GPUMemoryUtilization", startTime, endTime, 300);
    }

    /**
     * 打印端点实例资源利用率摘要
     * 一次性查询并输出 CPU、内存、GPU、GPU 显存的利用率统计
     *
     * @param endpointName 端点名称
     * @param hours        最近 N 小时
     */
    public void printEndpointResourceSummary(String endpointName, int hours) {
        System.out.println("==================== 端点实例资源利用率 ====================");
        System.out.println("端点名称: " + endpointName);
        System.out.println("时间范围: 最近 " + hours + " 小时");
        System.out.println();

        // CPU
        List<Datapoint> cpu = getEndpointCpuUtilization(endpointName, hours);
        if (!cpu.isEmpty()) {
            double avgCpu = cpu.stream().mapToDouble(dp -> dp.average() != null ? dp.average() : 0).average().orElse(0);
            double maxCpu = cpu.stream().mapToDouble(dp -> dp.maximum() != null ? dp.maximum() : 0).max().orElse(0);
            System.out.printf("CPU 利用率: 平均 %.1f%%, 最大 %.1f%%%n", avgCpu, maxCpu);
        } else {
            System.out.println("CPU 利用率: 无数据");
        }

        // 内存
        List<Datapoint> mem = getEndpointMemoryUtilization(endpointName, hours);
        if (!mem.isEmpty()) {
            double avgMem = mem.stream().mapToDouble(dp -> dp.average() != null ? dp.average() : 0).average().orElse(0);
            double maxMem = mem.stream().mapToDouble(dp -> dp.maximum() != null ? dp.maximum() : 0).max().orElse(0);
            System.out.printf("内存利用率: 平均 %.1f%%, 最大 %.1f%%%n", avgMem, maxMem);
        } else {
            System.out.println("内存利用率: 无数据");
        }

        // GPU
        List<Datapoint> gpu = getEndpointGpuUtilization(endpointName, hours);
        if (!gpu.isEmpty()) {
            double avgGpu = gpu.stream().mapToDouble(dp -> dp.average() != null ? dp.average() : 0).average().orElse(0);
            double maxGpu = gpu.stream().mapToDouble(dp -> dp.maximum() != null ? dp.maximum() : 0).max().orElse(0);
            System.out.printf("GPU 利用率: 平均 %.1f%%, 最大 %.1f%%%n", avgGpu, maxGpu);
        } else {
            System.out.println("GPU 利用率: 无数据（非 GPU 实例或未启用）");
        }

        // GPU 显存
        List<Datapoint> gpuMem = getEndpointGpuMemoryUtilization(endpointName, hours);
        if (!gpuMem.isEmpty()) {
            double avgGpuMem = gpuMem.stream().mapToDouble(dp -> dp.average() != null ? dp.average() : 0).average().orElse(0);
            double maxGpuMem = gpuMem.stream().mapToDouble(dp -> dp.maximum() != null ? dp.maximum() : 0).max().orElse(0);
            System.out.printf("GPU 显存利用率: 平均 %.1f%%, 最大 %.1f%%%n", avgGpuMem, maxGpuMem);
        } else {
            System.out.println("GPU 显存利用率: 无数据（非 GPU 实例或未启用）");
        }

        System.out.println("============================================================");
    }

    // ==================== 训练作业自定义指标查询 ====================

    /**
     * 查询训练作业的自定义指标
     * 对应文档中通过 metric_definitions 正则提取的指标
     *
     * @param trainingJobName 训练作业名称
     * @param metricName      指标名称（如 "train:loss"、"validation:accuracy"）
     * @param startTime       开始时间
     * @param endTime         结束时间
     * @param periodSeconds   统计周期（秒）
     * @return 指标数据点列表
     */
    public List<Datapoint> getTrainingJobMetric(String trainingJobName,
                                                 String metricName,
                                                 Instant startTime,
                                                 Instant endTime,
                                                 int periodSeconds) {
        System.out.println("查询训练指标: " + metricName + " (作业: " + trainingJobName + ")");

        Dimension dimension = Dimension.builder()
                .name("TrainingJobName")
                .value(trainingJobName)
                .build();

        GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                .namespace(SAGEMAKER_NAMESPACE)
                .metricName(metricName)
                .dimensions(dimension)
                .startTime(startTime)
                .endTime(endTime)
                .period(periodSeconds)
                .statistics(Statistic.AVERAGE, Statistic.MINIMUM, Statistic.MAXIMUM)
                .build();

        GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
        List<Datapoint> datapoints = new ArrayList<>(response.datapoints());
        datapoints.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));

        System.out.println("获取 " + datapoints.size() + " 个数据点");
        return datapoints;
    }

    /**
     * 查询最近 N 小时的训练自定义指标
     *
     * @param trainingJobName 训练作业名称
     * @param metricName      指标名称（如 "train:loss"）
     * @param hours           最近 N 小时
     * @return 指标数据点列表，统计周期 60 秒
     */
    public List<Datapoint> getRecentTrainingMetric(String trainingJobName,
                                                    String metricName,
                                                    int hours) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(Duration.ofHours(hours));
        return getTrainingJobMetric(trainingJobName, metricName, startTime, endTime, 60);
    }

    /**
     * 列出训练作业的所有可用指标
     *
     * @param trainingJobName 训练作业名称
     * @return 可用指标列表
     */
    public List<Metric> listTrainingJobMetrics(String trainingJobName) {
        System.out.println("列出训练作业可用指标: " + trainingJobName);

        DimensionFilter dimensionFilter = DimensionFilter.builder()
                .name("TrainingJobName")
                .value(trainingJobName)
                .build();

        ListMetricsRequest request = ListMetricsRequest.builder()
                .namespace(SAGEMAKER_NAMESPACE)
                .dimensions(dimensionFilter)
                .build();

        ListMetricsResponse response = cloudWatchClient.listMetrics(request);
        List<Metric> metrics = response.metrics();
        System.out.println("找到 " + metrics.size() + " 个指标");
        return metrics;
    }

    // ==================== 端点指标查询 ====================

    /**
     * 查询端点调用指标
     *
     * @param endpointName  端点名称
     * @param metricName    指标名称（如 "Invocations"、"ModelLatency"、"OverheadLatency"）
     * @param startTime     开始时间
     * @param endTime       结束时间
     * @param periodSeconds 统计周期（秒）
     * @return 指标数据点列表
     */
    public List<Datapoint> getEndpointMetric(String endpointName,
                                              String metricName,
                                              Instant startTime,
                                              Instant endTime,
                                              int periodSeconds) {
        System.out.println("查询端点指标: " + metricName + " (端点: " + endpointName + ")");

        Dimension dimension = Dimension.builder()
                .name("EndpointName")
                .value(endpointName)
                .build();

        Dimension variantDimension = Dimension.builder()
                .name("VariantName")
                .value("AllTraffic")
                .build();

        GetMetricStatisticsRequest request = GetMetricStatisticsRequest.builder()
                .namespace(SAGEMAKER_NAMESPACE)
                .metricName(metricName)
                .dimensions(dimension, variantDimension)
                .startTime(startTime)
                .endTime(endTime)
                .period(periodSeconds)
                .statistics(Statistic.AVERAGE, Statistic.SUM, Statistic.MAXIMUM)
                .build();

        GetMetricStatisticsResponse response = cloudWatchClient.getMetricStatistics(request);
        List<Datapoint> datapoints = new ArrayList<>(response.datapoints());
        datapoints.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));
        return datapoints;
    }

    /**
     * 查询端点调用次数
     * 命名空间: AWS/SageMaker，指标: Invocations，统计: SUM，周期: 300 秒
     *
     * @param endpointName 端点名称
     * @param hours        最近 N 小时
     * @return 调用次数数据点列表，每个数据点的 sum 值为该周期内的总调用次数
     */
    public List<Datapoint> getEndpointInvocations(String endpointName, int hours) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(Duration.ofHours(hours));
        return getEndpointMetric(endpointName, "Invocations", startTime, endTime, 300);
    }

    /**
     * 查询端点模型延迟
     * 命名空间: AWS/SageMaker，指标: ModelLatency，单位: 微秒（1ms = 1000μs）
     * 仅包含模型推理时间，不包含网络传输和容器开销（OverheadLatency）
     *
     * @param endpointName 端点名称
     * @param hours        最近 N 小时
     * @return 延迟数据点列表，值为微秒，统计周期 300 秒
     */
    public List<Datapoint> getEndpointModelLatency(String endpointName, int hours) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(Duration.ofHours(hours));
        return getEndpointMetric(endpointName, "ModelLatency", startTime, endTime, 300);
    }

    /**
     * 查询端点 4xx 客户端错误数
     * 常见原因: 请求格式错误、ContentType 不匹配、请求体过大等
     *
     * @param endpointName 端点名称
     * @param hours        最近 N 小时
     * @return 错误次数数据点列表，统计周期 300 秒
     */
    public List<Datapoint> getEndpoint4xxErrors(String endpointName, int hours) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(Duration.ofHours(hours));
        return getEndpointMetric(endpointName, "Invocation4XXErrors", startTime, endTime, 300);
    }

    /**
     * 查询端点 5xx 服务端错误数
     * 常见原因: 模型推理异常、内存不足、容器崩溃等
     *
     * @param endpointName 端点名称
     * @param hours        最近 N 小时
     * @return 错误次数数据点列表，统计周期 300 秒
     */
    public List<Datapoint> getEndpoint5xxErrors(String endpointName, int hours) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(Duration.ofHours(hours));
        return getEndpointMetric(endpointName, "Invocation5XXErrors", startTime, endTime, 300);
    }

    /**
     * 列出端点的所有可用指标
     * 底层调用 CloudWatch ListMetrics API，使用 DimensionFilter 按端点名称过滤
     *
     * @param endpointName 端点名称
     * @return 该端点下所有已发布的 CloudWatch 指标列表
     */
    public List<Metric> listEndpointMetrics(String endpointName) {
        System.out.println("列出端点可用指标: " + endpointName);

        DimensionFilter dimensionFilter = DimensionFilter.builder()
                .name("EndpointName")
                .value(endpointName)
                .build();

        ListMetricsRequest request = ListMetricsRequest.builder()
                .namespace(SAGEMAKER_NAMESPACE)
                .dimensions(dimensionFilter)
                .build();

        ListMetricsResponse response = cloudWatchClient.listMetrics(request);
        return response.metrics();
    }

    // ==================== 告警管理 ====================

    /**
     * 创建端点延迟告警
     * 当模型延迟超过阈值时触发告警
     *
     * @param alarmName       告警名称
     * @param endpointName    端点名称
     * @param thresholdMicros 延迟阈值（微秒）
     * @param snsTopicArn     SNS 主题 ARN（告警通知目标）
     */
    public void createEndpointLatencyAlarm(String alarmName,
                                            String endpointName,
                                            double thresholdMicros,
                                            String snsTopicArn) {
        System.out.println("创建端点延迟告警: " + alarmName);

        PutMetricAlarmRequest.Builder requestBuilder = PutMetricAlarmRequest.builder()
                .alarmName(alarmName)
                .alarmDescription("端点 " + endpointName + " 模型延迟超过阈值")
                .namespace(SAGEMAKER_NAMESPACE)
                .metricName("ModelLatency")
                .dimensions(
                        Dimension.builder().name("EndpointName").value(endpointName).build(),
                        Dimension.builder().name("VariantName").value("AllTraffic").build()
                )
                .statistic(Statistic.AVERAGE)
                .period(300)
                .evaluationPeriods(2)
                .threshold(thresholdMicros)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD);

        if (snsTopicArn != null && !snsTopicArn.isEmpty()) {
            requestBuilder.alarmActions(snsTopicArn);
        }

        cloudWatchClient.putMetricAlarm(requestBuilder.build());
        System.out.println("延迟告警已创建");
    }

    /**
     * 创建端点错误率告警
     *
     * @param alarmName    告警名称
     * @param endpointName 端点名称
     * @param threshold    错误数阈值
     * @param snsTopicArn  SNS 主题 ARN
     */
    public void createEndpointErrorAlarm(String alarmName,
                                          String endpointName,
                                          double threshold,
                                          String snsTopicArn) {
        System.out.println("创建端点错误告警: " + alarmName);

        PutMetricAlarmRequest.Builder requestBuilder = PutMetricAlarmRequest.builder()
                .alarmName(alarmName)
                .alarmDescription("端点 " + endpointName + " 5xx 错误超过阈值")
                .namespace(SAGEMAKER_NAMESPACE)
                .metricName("Invocation5XXErrors")
                .dimensions(
                        Dimension.builder().name("EndpointName").value(endpointName).build(),
                        Dimension.builder().name("VariantName").value("AllTraffic").build()
                )
                .statistic(Statistic.SUM)
                .period(300)
                .evaluationPeriods(1)
                .threshold(threshold)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD);

        if (snsTopicArn != null && !snsTopicArn.isEmpty()) {
            requestBuilder.alarmActions(snsTopicArn);
        }

        cloudWatchClient.putMetricAlarm(requestBuilder.build());
        System.out.println("错误告警已创建");
    }

    /**
     * 列出 SageMaker 相关告警
     * 底层调用 CloudWatch DescribeAlarms API
     *
     * @param alarmNamePrefix 告警名称前缀过滤（可为 null，返回所有告警）
     * @return 告警列表，包含告警状态（OK/ALARM/INSUFFICIENT_DATA）、指标、阈值等信息
     */
    public List<MetricAlarm> listSageMakerAlarms(String alarmNamePrefix) {
        DescribeAlarmsRequest.Builder requestBuilder = DescribeAlarmsRequest.builder()
                .maxRecords(50);

        if (alarmNamePrefix != null && !alarmNamePrefix.isEmpty()) {
            requestBuilder.alarmNamePrefix(alarmNamePrefix);
        }

        DescribeAlarmsResponse response = cloudWatchClient.describeAlarms(requestBuilder.build());
        return response.metricAlarms();
    }

    // ==================== 打印方法 ====================

    /**
     * 打印训练作业自定义指标
     *
     * @param trainingJobName 训练作业名称
     * @param metricName      指标名称（如 "train:loss"、"validation:accuracy"）
     * @param hours           最近 N 小时
     */
    public void printTrainingJobMetrics(String trainingJobName, String metricName, int hours) {
        List<Datapoint> datapoints = getRecentTrainingMetric(trainingJobName, metricName, hours);
        System.out.println("==================== 训练指标 ====================");
        System.out.println("作业名称: " + trainingJobName);
        System.out.println("指标名称: " + metricName);
        System.out.println("时间范围: 最近 " + hours + " 小时");
        System.out.println("数据点数: " + datapoints.size());
        System.out.println();

        for (Datapoint dp : datapoints) {
            System.out.printf("[%s] 平均: %.6f, 最小: %.6f, 最大: %.6f%n",
                    dp.timestamp(), dp.average(), dp.minimum(), dp.maximum());
        }
        System.out.println("==================================================");
    }

    /**
     * 打印端点运行状况摘要
     * 汇总调用次数、模型延迟、4xx/5xx 错误数和错误率
     *
     * @param endpointName 端点名称
     * @param hours        最近 N 小时
     */
    public void printEndpointHealthSummary(String endpointName, int hours) {
        System.out.println("==================== 端点运行状况 ====================");
        System.out.println("端点名称: " + endpointName);
        System.out.println("时间范围: 最近 " + hours + " 小时");
        System.out.println();

        // 调用次数
        List<Datapoint> invocations = getEndpointInvocations(endpointName, hours);
        double totalInvocations = invocations.stream()
                .mapToDouble(dp -> dp.sum() != null ? dp.sum() : 0)
                .sum();
        System.out.printf("总调用次数: %.0f%n", totalInvocations);

        // 模型延迟
        List<Datapoint> latency = getEndpointModelLatency(endpointName, hours);
        if (!latency.isEmpty()) {
            double avgLatency = latency.stream()
                    .mapToDouble(dp -> dp.average() != null ? dp.average() : 0)
                    .average()
                    .orElse(0);
            double maxLatency = latency.stream()
                    .mapToDouble(dp -> dp.maximum() != null ? dp.maximum() : 0)
                    .max()
                    .orElse(0);
            System.out.printf("平均延迟: %.2f 微秒 (%.2f 毫秒)%n", avgLatency, avgLatency / 1000);
            System.out.printf("最大延迟: %.2f 微秒 (%.2f 毫秒)%n", maxLatency, maxLatency / 1000);
        }

        // 4xx 错误
        List<Datapoint> errors4xx = getEndpoint4xxErrors(endpointName, hours);
        double total4xx = errors4xx.stream()
                .mapToDouble(dp -> dp.sum() != null ? dp.sum() : 0)
                .sum();
        System.out.printf("4xx 错误数: %.0f%n", total4xx);

        // 5xx 错误
        List<Datapoint> errors5xx = getEndpoint5xxErrors(endpointName, hours);
        double total5xx = errors5xx.stream()
                .mapToDouble(dp -> dp.sum() != null ? dp.sum() : 0)
                .sum();
        System.out.printf("5xx 错误数: %.0f%n", total5xx);

        // 错误率
        if (totalInvocations > 0) {
            double errorRate = (total4xx + total5xx) / totalInvocations * 100;
            System.out.printf("错误率: %.2f%%%n", errorRate);
        }

        System.out.println("====================================================");
    }

    /**
     * 打印训练作业所有可用指标
     *
     * @param trainingJobName 训练作业名称
     */
    public void printAvailableMetrics(String trainingJobName) {
        List<Metric> metrics = listTrainingJobMetrics(trainingJobName);
        System.out.println("==================== 可用指标 ====================");
        System.out.println("作业名称: " + trainingJobName);
        System.out.println();

        for (Metric metric : metrics) {
            System.out.println("指标: " + metric.metricName());
            System.out.println("  命名空间: " + metric.namespace());
            for (Dimension dim : metric.dimensions()) {
                System.out.println("  维度: " + dim.name() + " = " + dim.value());
            }
            System.out.println();
        }
        System.out.println("==================================================");
    }

    public void close() {
        cloudWatchClient.close();
    }
}
