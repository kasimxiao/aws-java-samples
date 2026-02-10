package com.aws.sample;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aws.sample.cloudwatch.CloudWatchLogService;
import com.aws.sample.cloudwatch.CloudWatchMetricService;
import com.aws.sample.common.AwsConfig;
import com.aws.sample.sagemaker.SageMakerDeploymentService;
import com.aws.sample.sagemaker.SageMakerInferenceService;
import com.aws.sample.sagemaker.SageMakerTrainingService;
import com.aws.sample.sagemaker.model.EndpointConfig;
import com.aws.sample.sagemaker.model.TrainingJobConfig;

import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.Metric;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilteredLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream;

/**
 * CloudWatch 监控服务测试示例
 *
 * 演示如何使用 CloudWatch 日志和指标服务监控 SageMaker 训练和部署，包括：
 * - 训练作业日志查询与过滤（CloudWatch Logs API）
 * - 训练作业自定义指标查询（CloudWatch Metrics API，命名空间 AWS/SageMaker）
 * - 训练实例资源利用率查询（CloudWatch Metrics API，命名空间 /aws/sagemaker/TrainingJobs）
 * - 推理端点调用指标查询（CloudWatch Metrics API，命名空间 AWS/SageMaker）
 * - 推理端点实例资源利用率查询（CloudWatch Metrics API，命名空间 /aws/sagemaker/Endpoints）
 * - CloudWatch 告警创建与管理（PutMetricAlarm / DescribeAlarms API）
 * - SageMaker 端点推理调用（SageMaker Runtime InvokeEndpoint API）
 * - 完整的训练 → 部署 → 监控工作流演示
 *
 * 使用前请确保:
 * 1. 在 application.properties 中配置好 AWS 凭证和区域
 * 2. 有已完成或正在运行的 SageMaker 训练作业（日志和指标测试）
 * 3. 有已部署且状态为 InService 的 SageMaker 端点（端点相关测试）
 * 4. 如需告警通知，需提前创建 SNS 主题并订阅
 */
public class CloudWatchMonitoringTest {

    private AwsConfig config;
    private CloudWatchLogService logService;
    private CloudWatchMetricService metricService;
    private SageMakerTrainingService trainingService;
    private SageMakerDeploymentService deploymentService;
    private SageMakerInferenceService inferenceService;

    // ==================== 配置参数（请根据实际情况修改）====================

    /** SageMaker 执行角色 ARN，需要有 SageMaker、S3、CloudWatch 等权限 */
    private static final String ROLE_ARN = "arn:aws:iam::YOUR_ACCOUNT:role/SageMakerExecutionRole";

    /** S3 存储桶名称，用于存放训练数据、模型输出、数据捕获等 */
    private static final String S3_BUCKET = "your-sagemaker-bucket";

    /** 已存在的训练作业名称，用于日志和指标查询测试 */
    private static final String TRAINING_JOB_NAME = "your-training-job-name";

    /** 已部署的端点名称，用于端点监控和推理测试 */
    private static final String ENDPOINT_NAME = "your-endpoint-name";

    /** SNS 主题 ARN，用于 CloudWatch 告警通知 */
    private static final String SNS_TOPIC_ARN = "arn:aws:sns:us-east-1:YOUR_ACCOUNT:your-topic";

    /**
     * 训练实例主机名，SageMaker 训练实例的主机名格式为 "algo-N"
     * 单实例训练默认为 "algo-1"，多实例训练依次为 "algo-1"、"algo-2" 等
     */
    private static final String TRAINING_HOST = "algo-1";

    /**
     * 初始化所有服务实例
     * 每个测试方法执行前都会重新创建，确保状态隔离
     */
    @BeforeEach
    void setUp() {
        config = new AwsConfig();
        logService = new CloudWatchLogService(config);           // CloudWatch Logs 日志查询
        metricService = new CloudWatchMetricService(config);     // CloudWatch Metrics 指标查询
        trainingService = new SageMakerTrainingService(config);  // SageMaker 训练管理
        deploymentService = new SageMakerDeploymentService(config); // SageMaker 部署管理
        inferenceService = new SageMakerInferenceService(config);   // SageMaker 端点推理
    }

    // ==================== 一、训练日志查询 ====================
    // 底层 API: CloudWatch Logs DescribeLogStreams / GetLogEvents / FilterLogEvents
    // 日志组: /aws/sagemaker/TrainingJobs
    // 日志流格式: [training-job-name]/algo-[instance-number]-[epoch_timestamp]
    // 训练脚本中 stdout/stderr 的输出会自动发送到 CloudWatch Logs

    /**
     * 示例：查看训练作业的日志流
     *
     * 底层调用 CloudWatch Logs DescribeLogStreams API
     * 每个训练实例会生成一个独立的日志流，日志流名称包含实例编号
     * 例如: my-training-job/algo-1-1704067200
     */
    @Test
    void testGetTrainingJobLogStreams() {
        List<LogStream> streams = logService.getTrainingJobLogStreams(TRAINING_JOB_NAME);
        System.out.println("==================== 训练作业日志流 ====================");
        for (LogStream stream : streams) {
            System.out.println("日志流: " + stream.logStreamName());
            System.out.println("  最后事件时间: " + Instant.ofEpochMilli(stream.lastEventTimestamp()));
            System.out.println("  存储大小: " + stream.storedBytes() + " 字节");
            System.out.println();
        }
        System.out.println("======================================================");
    }

    /**
     * 示例：获取训练作业全部日志
     *
     * 底层调用 CloudWatch Logs GetLogEvents API
     * 遍历所有日志流，合并后按时间排序输出
     * limit 参数控制每个日志流的最大返回条数
     */
    @Test
    void testGetTrainingJobLogs() {
        // 获取最近 50 条日志，包含训练脚本的所有 print/logging 输出
        logService.printTrainingJobLogs(TRAINING_JOB_NAME, 50);
    }

    /**
     * 示例：过滤训练损失日志
     *
     * 底层调用 CloudWatch Logs FilterLogEvents API
     * filterPattern 支持简单文本匹配和 CloudWatch Logs 过滤语法
     * 对应文档中训练脚本输出的 "Train Loss: xxx" 格式日志
     */
    @Test
    void testFilterTrainingLossLogs() {
        // 过滤包含 "Loss" 关键字的日志，用于追踪训练损失变化
        logService.printFilteredTrainingLogs(TRAINING_JOB_NAME, "Loss");
    }

    /**
     * 示例：过滤训练错误日志
     *
     * 底层调用 CloudWatch Logs FilterLogEvents API，过滤模式为 "ERROR"
     * 用于快速定位训练过程中的异常和错误信息
     */
    @Test
    void testFilterTrainingErrorLogs() {
        List<FilteredLogEvent> errors = logService.getTrainingErrorLogs(TRAINING_JOB_NAME);
        System.out.println("==================== 训练错误日志 ====================");
        System.out.println("错误数量: " + errors.size());
        for (FilteredLogEvent event : errors) {
            Instant timestamp = Instant.ofEpochMilli(event.timestamp());
            System.out.println("[" + timestamp + "] " + event.message());
        }
        System.out.println("====================================================");
    }

    /**
     * 示例：按时间范围过滤日志
     *
     * 底层调用 CloudWatch Logs FilterLogEvents API，支持 startTime/endTime 参数
     * startTime 和 endTime 为 Unix 时间戳（毫秒），用于限定查询的时间窗口
     * 适用于只关注特定时间段内的训练日志
     */
    @Test
    void testFilterLogsByTimeRange() {
        // 查询最近 2 小时内包含 "Accuracy" 的日志
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(Duration.ofHours(2));

        List<FilteredLogEvent> events = logService.filterTrainingJobLogs(
                TRAINING_JOB_NAME, "Accuracy", startTime, endTime, 100);

        System.out.println("==================== 准确率日志（最近2小时）====================");
        for (FilteredLogEvent event : events) {
            Instant timestamp = Instant.ofEpochMilli(event.timestamp());
            System.out.println("[" + timestamp + "] " + event.message());
        }
        System.out.println("============================================================");
    }

    // ==================== 二、训练自定义指标查询 ====================
    // 底层 API: CloudWatch GetMetricStatistics / ListMetrics
    // 命名空间: AWS/SageMaker，维度: TrainingJobName
    // 这些指标来自训练脚本中通过 metric_definitions 正则提取的自定义指标
    // 例如训练脚本输出 "Train Loss: 0.1234"，通过正则 "Train Loss: ([0-9\\.]+)" 提取

    /**
     * 示例：查询训练作业的自定义指标（train:loss）
     *
     * 底层调用 CloudWatch GetMetricStatistics API
     * 命名空间: AWS/SageMaker，维度: TrainingJobName
     * 指标名称对应 SageMaker Estimator 中 metric_definitions 定义的 Name 字段
     * 返回的 Datapoint 包含 Average、Minimum、Maximum 统计值
     */
    @Test
    void testGetTrainingJobMetrics() {
        // 查询最近 24 小时的训练损失指标，统计周期 60 秒
        metricService.printTrainingJobMetrics(TRAINING_JOB_NAME, "train:loss", 24);
    }

    /**
     * 示例：列出训练作业所有可用指标
     *
     * 底层调用 CloudWatch ListMetrics API
     * 命名空间: AWS/SageMaker，维度过滤: TrainingJobName
     * 返回该训练作业下所有已发布的指标名称
     * 常见指标: train:loss, train:accuracy, validation:loss, validation:accuracy, learning_rate
     */
    @Test
    void testListTrainingJobMetrics() {
        metricService.printAvailableMetrics(TRAINING_JOB_NAME);
    }

    /**
     * 示例：查询多个训练指标并对比
     *
     * 分别查询 train:loss、validation:loss、validation:accuracy 三个指标
     * 用于判断模型是否过拟合（训练损失下降但验证损失上升）
     * 每个指标独立调用 CloudWatch GetMetricStatistics API
     */
    @Test
    void testCompareTrainingMetrics() {
        System.out.println("==================== 训练指标对比 ====================");

        // 训练损失 - 对应训练脚本输出的 "Train Loss: xxx"
        List<Datapoint> trainLoss = metricService.getRecentTrainingMetric(
                TRAINING_JOB_NAME, "train:loss", 24);
        System.out.println("训练损失数据点: " + trainLoss.size());
        if (!trainLoss.isEmpty()) {
            Datapoint last = trainLoss.get(trainLoss.size() - 1);
            System.out.printf("  最新值: %.6f%n", last.average());
        }

        // 验证损失 - 对应训练脚本输出的 "Validation Loss: xxx"
        List<Datapoint> valLoss = metricService.getRecentTrainingMetric(
                TRAINING_JOB_NAME, "validation:loss", 24);
        System.out.println("验证损失数据点: " + valLoss.size());
        if (!valLoss.isEmpty()) {
            Datapoint last = valLoss.get(valLoss.size() - 1);
            System.out.printf("  最新值: %.6f%n", last.average());
        }

        // 验证准确率 - 对应训练脚本输出的 "Validation Accuracy: xxx"
        List<Datapoint> valAcc = metricService.getRecentTrainingMetric(
                TRAINING_JOB_NAME, "validation:accuracy", 24);
        System.out.println("验证准确率数据点: " + valAcc.size());
        if (!valAcc.isEmpty()) {
            Datapoint last = valAcc.get(valAcc.size() - 1);
            System.out.printf("  最新值: %.6f%n", last.average());
        }

        System.out.println("====================================================");
    }

    // ==================== 三、训练实例资源利用率查询 ====================
    // 底层 API: CloudWatch GetMetricStatistics
    // 命名空间: /aws/sagemaker/TrainingJobs，维度: Host
    // 这些是 SageMaker 自动采集的实例级系统指标，无需在训练脚本中额外配置
    // Host 值格式为 "algo-N"，N 从 1 开始，对应 instanceCount 中的实例编号

    /**
     * 示例：查询训练实例 CPU/内存/GPU/GPU显存 利用率
     *
     * 底层调用 CloudWatch GetMetricStatistics API
     * 命名空间: /aws/sagemaker/TrainingJobs，维度: Host=algo-1
     * 可用指标:
     *   - CPUUtilization: CPU 利用率百分比（所有核心的平均值）
     *   - MemoryUtilization: 内存利用率百分比
     *   - GPUUtilization: GPU 计算利用率百分比（仅 GPU 实例，如 ml.p3/ml.g4dn）
     *   - GPUMemoryUtilization: GPU 显存利用率百分比（仅 GPU 实例）
     *   - DiskUtilization: 磁盘利用率百分比
     * 统计周期: 60 秒（训练实例资源指标的最小粒度）
     */
    @Test
    void testGetTrainingInstanceResources() {
        // 一次性打印所有资源利用率摘要
        metricService.printTrainingResourceSummary(TRAINING_JOB_NAME, TRAINING_HOST, 24);
    }

    /**
     * 示例：单独查询训练实例 CPU 利用率
     *
     * 返回 Datapoint 列表，每个数据点包含:
     *   - timestamp: 数据点时间戳
     *   - average: 该周期内的平均 CPU 利用率
     *   - minimum: 该周期内的最低 CPU 利用率
     *   - maximum: 该周期内的最高 CPU 利用率
     * CPU 利用率过低可能意味着 I/O 瓶颈或数据加载效率不足
     */
    @Test
    void testGetTrainingCpuUtilization() {
        List<Datapoint> cpuData = metricService.getTrainingCpuUtilization(
                TRAINING_JOB_NAME, TRAINING_HOST, 24);
        System.out.println("==================== 训练实例 CPU 利用率 ====================");
        for (Datapoint dp : cpuData) {
            System.out.printf("[%s] 平均: %.1f%%, 最小: %.1f%%, 最大: %.1f%%%n",
                    dp.timestamp(), dp.average(), dp.minimum(), dp.maximum());
        }
        System.out.println("============================================================");
    }

    /**
     * 示例：单独查询训练实例 GPU 利用率和 GPU 显存利用率
     *
     * 仅在使用 GPU 实例（如 ml.p3.2xlarge、ml.g4dn.xlarge）时有数据
     * GPU 利用率过低可能意味着:
     *   - batch_size 太小，GPU 计算不饱和
     *   - 数据预处理成为瓶颈，GPU 等待数据
     *   - 模型太小，无法充分利用 GPU 算力
     * GPU 显存利用率过高可能导致 OOM（Out of Memory）错误
     */
    @Test
    void testGetTrainingGpuUtilization() {
        // GPU 计算利用率
        List<Datapoint> gpuData = metricService.getTrainingGpuUtilization(
                TRAINING_JOB_NAME, TRAINING_HOST, 24);
        System.out.println("==================== 训练实例 GPU 利用率 ====================");
        for (Datapoint dp : gpuData) {
            System.out.printf("[%s] 平均: %.1f%%, 最大: %.1f%%%n",
                    dp.timestamp(), dp.average(), dp.maximum());
        }

        // GPU 显存利用率
        List<Datapoint> gpuMemData = metricService.getTrainingGpuMemoryUtilization(
                TRAINING_JOB_NAME, TRAINING_HOST, 24);
        System.out.println("==================== 训练实例 GPU 显存利用率 ====================");
        for (Datapoint dp : gpuMemData) {
            System.out.printf("[%s] 平均: %.1f%%, 最大: %.1f%%%n",
                    dp.timestamp(), dp.average(), dp.maximum());
        }
        System.out.println("==============================================================");
    }

    // ==================== 四、端点调用级指标查询 ====================
    // 底层 API: CloudWatch GetMetricStatistics / ListMetrics
    // 命名空间: AWS/SageMaker，维度: EndpointName + VariantName
    // 这些是 SageMaker 自动发布的端点调用级指标，反映推理请求的整体情况

    /**
     * 示例：查看端点运行状况摘要
     *
     * 一次性查询并汇总以下指标:
     *   - Invocations: 调用总次数（SUM 统计）
     *   - ModelLatency: 模型推理延迟，单位微秒（AVERAGE/MAX 统计）
     *   - Invocation4XXErrors: 客户端错误次数（如请求格式错误）
     *   - Invocation5XXErrors: 服务端错误次数（如模型异常）
     *   - 错误率: (4xx + 5xx) / 总调用次数 * 100%
     */
    @Test
    void testEndpointHealthSummary() {
        // 查询最近 24 小时的端点运行状况
        metricService.printEndpointHealthSummary(ENDPOINT_NAME, 24);
    }

    /**
     * 示例：查询端点调用次数
     *
     * 底层调用 CloudWatch GetMetricStatistics API
     * 指标: Invocations，统计: SUM，周期: 300 秒（5 分钟）
     * 每个数据点的 sum 值表示该 5 分钟内的总调用次数
     */
    @Test
    void testGetEndpointInvocations() {
        List<Datapoint> invocations = metricService.getEndpointInvocations(ENDPOINT_NAME, 24);
        System.out.println("==================== 端点调用次数 ====================");
        for (Datapoint dp : invocations) {
            System.out.printf("[%s] 调用次数: %.0f%n", dp.timestamp(), dp.sum());
        }
        System.out.println("====================================================");
    }

    /**
     * 示例：查询端点模型延迟
     *
     * 底层调用 CloudWatch GetMetricStatistics API
     * 指标: ModelLatency，单位: 微秒（1 毫秒 = 1000 微秒）
     * 统计: AVERAGE（平均延迟）和 MAXIMUM（最大延迟），周期: 300 秒
     * ModelLatency 仅包含模型推理时间，不包含网络传输和容器开销
     * 容器开销对应 OverheadLatency 指标
     */
    @Test
    void testGetEndpointLatency() {
        List<Datapoint> latency = metricService.getEndpointModelLatency(ENDPOINT_NAME, 24);
        System.out.println("==================== 端点模型延迟 ====================");
        for (Datapoint dp : latency) {
            System.out.printf("[%s] 平均: %.2f μs (%.2f ms), 最大: %.2f μs%n",
                    dp.timestamp(),
                    dp.average(), dp.average() / 1000,
                    dp.maximum());
        }
        System.out.println("====================================================");
    }

    /**
     * 示例：查询端点所有可用指标
     *
     * 底层调用 CloudWatch ListMetrics API
     * 常见端点指标包括:
     *   - Invocations: 调用次数
     *   - InvocationsPerInstance: 每个实例的调用次数
     *   - ModelLatency: 模型推理延迟（微秒）
     *   - OverheadLatency: 容器开销延迟（微秒）
     *   - Invocation4XXErrors: 4xx 错误次数
     *   - Invocation5XXErrors: 5xx 错误次数
     *   - InvocationModelErrors: 模型错误次数
     *   - ModelSetupTime: 模型加载时间（冷启动时）
     */
    @Test
    void testListEndpointMetrics() {
        List<Metric> metrics = metricService.listEndpointMetrics(ENDPOINT_NAME);
        System.out.println("==================== 端点可用指标 ====================");
        for (Metric metric : metrics) {
            System.out.println("指标: " + metric.metricName());
        }
        System.out.println("====================================================");
    }

    // ==================== 五、端点实例资源利用率查询 ====================
    // 底层 API: CloudWatch GetMetricStatistics
    // 命名空间: /aws/sagemaker/Endpoints，维度: EndpointName + VariantName
    // 这些是推理实例的系统级资源指标，SageMaker 自动采集，无需额外配置

    /**
     * 示例：查询推理端点实例 CPU/内存/GPU/GPU显存 利用率
     *
     * 底层调用 CloudWatch GetMetricStatistics API
     * 命名空间: /aws/sagemaker/Endpoints，维度: EndpointName + VariantName=AllTraffic
     * 可用指标（与训练实例相同）:
     *   - CPUUtilization: CPU 利用率百分比
     *   - MemoryUtilization: 内存利用率百分比
     *   - GPUUtilization: GPU 计算利用率百分比（仅 GPU 实例）
     *   - GPUMemoryUtilization: GPU 显存利用率百分比（仅 GPU 实例）
     *   - DiskUtilization: 磁盘利用率百分比
     * 统计周期: 300 秒（端点资源指标推荐的查询粒度）
     *
     * 资源利用率过高时应考虑:
     *   - 增加 initialInstanceCount 水平扩展
     *   - 配置 Auto Scaling 自动扩缩容
     *   - 升级到更大的实例类型
     */
    @Test
    void testGetEndpointInstanceResources() {
        // 一次性打印所有端点实例资源利用率摘要
        metricService.printEndpointResourceSummary(ENDPOINT_NAME, 24);
    }

    /**
     * 示例：单独查询端点实例 CPU 利用率
     *
     * 推理端点 CPU 利用率持续高于 80% 时，建议扩容
     * 如果配置了 Auto Scaling，可通过 TargetTrackingScaling 策略
     * 基于 InvocationsPerInstance 或 CPUUtilization 自动扩缩
     */
    @Test
    void testGetEndpointCpuUtilization() {
        List<Datapoint> cpuData = metricService.getEndpointCpuUtilization(ENDPOINT_NAME, 24);
        System.out.println("==================== 端点实例 CPU 利用率 ====================");
        for (Datapoint dp : cpuData) {
            System.out.printf("[%s] 平均: %.1f%%, 最大: %.1f%%%n",
                    dp.timestamp(), dp.average(), dp.maximum());
        }
        System.out.println("============================================================");
    }

    /**
     * 示例：单独查询端点实例内存利用率
     *
     * 内存利用率过高可能导致推理请求失败（5xx 错误）
     * 常见原因: 模型太大、并发请求过多、内存泄漏
     */
    @Test
    void testGetEndpointMemoryUtilization() {
        List<Datapoint> memData = metricService.getEndpointMemoryUtilization(ENDPOINT_NAME, 24);
        System.out.println("==================== 端点实例内存利用率 ====================");
        for (Datapoint dp : memData) {
            System.out.printf("[%s] 平均: %.1f%%, 最大: %.1f%%%n",
                    dp.timestamp(), dp.average(), dp.maximum());
        }
        System.out.println("============================================================");
    }

    // ==================== 六、告警管理 ====================
    // 底层 API: CloudWatch PutMetricAlarm / DescribeAlarms
    // 告警触发后会发送通知到指定的 SNS 主题
    // SNS 主题可配置邮件、短信、Lambda 等订阅方式

    /**
     * 示例：创建端点延迟告警
     *
     * 底层调用 CloudWatch PutMetricAlarm API
     * 配置说明:
     *   - 指标: ModelLatency（命名空间 AWS/SageMaker）
     *   - 统计: AVERAGE（平均值）
     *   - 周期: 300 秒（5 分钟）
     *   - 评估周期: 2（连续 2 个周期超过阈值才触发）
     *   - 阈值: 500000 微秒 = 500 毫秒
     *   - 比较: GREATER_THAN_THRESHOLD（大于阈值）
     * 触发条件: 连续 10 分钟内平均延迟超过 500ms
     */
    @Test
    void testCreateLatencyAlarm() {
        metricService.createEndpointLatencyAlarm(
                ENDPOINT_NAME + "-latency-alarm",  // 告警名称
                ENDPOINT_NAME,                      // 端点名称
                500000,                             // 阈值: 500ms = 500000 微秒
                SNS_TOPIC_ARN                       // 通知目标
        );
        System.out.println("延迟告警创建完成");
    }

    /**
     * 示例：创建端点错误告警
     *
     * 底层调用 CloudWatch PutMetricAlarm API
     * 配置说明:
     *   - 指标: Invocation5XXErrors（命名空间 AWS/SageMaker）
     *   - 统计: SUM（总和）
     *   - 周期: 300 秒（5 分钟）
     *   - 评估周期: 1（1 个周期超过阈值即触发）
     *   - 阈值: 10 次
     * 触发条件: 5 分钟内 5xx 错误超过 10 次
     */
    @Test
    void testCreateErrorAlarm() {
        metricService.createEndpointErrorAlarm(
                ENDPOINT_NAME + "-error-alarm",  // 告警名称
                ENDPOINT_NAME,                    // 端点名称
                10,                               // 阈值: 10 次错误
                SNS_TOPIC_ARN                     // 通知目标
        );
        System.out.println("错误告警创建完成");
    }

    /**
     * 示例：列出所有 CloudWatch 告警
     *
     * 底层调用 CloudWatch DescribeAlarms API
     * 告警状态说明:
     *   - OK: 指标在正常范围内
     *   - ALARM: 指标超过阈值，已触发告警
     *   - INSUFFICIENT_DATA: 数据不足，无法判断状态
     */
    @Test
    void testListAlarms() {
        var alarms = metricService.listSageMakerAlarms(null);
        System.out.println("==================== CloudWatch 告警 ====================");
        for (var alarm : alarms) {
            System.out.println("告警名称: " + alarm.alarmName());
            System.out.println("  状态: " + alarm.stateValue());       // OK / ALARM / INSUFFICIENT_DATA
            System.out.println("  指标: " + alarm.metricName());       // 监控的指标名称
            System.out.println("  阈值: " + alarm.threshold());        // 触发阈值
            System.out.println("  比较: " + alarm.comparisonOperator()); // 比较运算符
            System.out.println();
        }
        System.out.println("======================================================");
    }

    // ==================== 七、端点推理调用 ====================
    // 底层 API: SageMaker Runtime InvokeEndpoint
    // 端点必须处于 InService 状态才能接受推理请求
    // 请求和响应格式取决于模型的推理脚本实现

    /**
     * 示例：调用端点进行 JSON 格式推理
     *
     * 底层调用 SageMaker Runtime InvokeEndpoint API
     * ContentType: application/json，Accept: application/json
     * 请求体格式取决于模型的 input_fn 实现
     * 响应体格式取决于模型的 output_fn 实现
     */
    @Test
    void testInvokeEndpointJson() {
        // JSON 格式的推理请求，具体格式取决于部署的模型
        String payload = "{\"instances\": [[1.0, 2.0, 3.0, 4.0]]}";
        String result = inferenceService.invokeEndpointJson(ENDPOINT_NAME, payload);
        System.out.println("推理结果: " + result);
    }

    /**
     * 示例：调用端点进行 CSV 格式推理
     *
     * 底层调用 SageMaker Runtime InvokeEndpoint API
     * ContentType: text/csv，Accept: text/csv
     * 适用于 XGBoost、线性学习器等内置算法
     * CSV 格式不包含表头，特征值用逗号分隔
     */
    @Test
    void testInvokeEndpointCsv() {
        // CSV 格式的推理请求，每行一个样本
        String payload = "1.0,2.0,3.0,4.0";
        String result = inferenceService.invokeEndpointCsv(ENDPOINT_NAME, payload);
        System.out.println("推理结果: " + result);
    }

    // ==================== 八、完整工作流 ====================

    /**
     * 示例：完整的训练 → 部署 → 监控工作流
     *
     * 展示从训练到部署再到监控的完整流程，涉及的 AWS API:
     *
     * 训练阶段:
     *   1. SageMaker CreateTrainingJob - 创建训练作业
     *   2. SageMaker DescribeTrainingJob - 轮询训练状态
     *      状态流转: InProgress → Completed / Failed / Stopped
     *   3. CloudWatch Logs GetLogEvents - 查看训练日志
     *   4. CloudWatch GetMetricStatistics - 查询训练指标和资源利用率
     *
     * 部署阶段:
     *   5. SageMaker CreateModel - 创建模型（关联推理镜像和模型文件）
     *   6. SageMaker CreateEndpointConfig - 创建端点配置（实例类型、数量、数据捕获）
     *   7. SageMaker CreateEndpoint - 创建端点
     *   8. SageMaker DescribeEndpoint - 轮询端点状态
     *      状态流转: Creating → InService / Failed
     *
     * 推理阶段:
     *   9. SageMaker Runtime InvokeEndpoint - 调用端点进行推理
     *
     * 监控阶段:
     *   10. CloudWatch GetMetricStatistics - 查询端点调用指标和资源利用率
     *   11. CloudWatch PutMetricAlarm - 创建延迟/错误告警
     *   12. CloudWatch Logs FilterLogEvents - 查看端点推理日志
     */
    @Test
    void testFullWorkflow() {
        String jobName = "workflow-demo-" + System.currentTimeMillis();

        System.out.println("==================== 完整工作流演示 ====================");

        // 步骤 1: 创建训练配置
        // 对应 SageMaker CreateTrainingJob API
        TrainingJobConfig trainingConfig = TrainingJobConfig.builder()
                .jobName(jobName)
                .roleArn(ROLE_ARN)
                // PyTorch 2.0.1 GPU 训练镜像（us-east-1 区域）
                .trainingImage("763104351884.dkr.ecr.us-east-1.amazonaws.com/pytorch-training:2.0.1-gpu-py310")
                .instanceType("ml.p3.2xlarge")   // 1 块 V100 GPU，16GB 显存
                .instanceCount(1)                 // 单实例训练
                .volumeSizeGB(50)                 // EBS 存储卷 50GB
                .maxRuntimeSeconds(3600)          // 最大运行时间 1 小时
                .s3TrainDataUri("s3://" + S3_BUCKET + "/train/")
                .s3OutputPath("s3://" + S3_BUCKET + "/output/")
                // 训练超参数，会传递给训练脚本的命令行参数
                .hyperParameter("epochs", "10")
                .hyperParameter("batch-size", "32")
                .hyperParameter("learning-rate", "0.001")
                .build();

        System.out.println("步骤 1: 训练配置已创建");
        System.out.println("  作业名称: " + trainingConfig.getJobName());
        System.out.println("  实例类型: " + trainingConfig.getInstanceType());

        // 步骤 2: 创建部署配置（训练完成后使用）
        // 对应 SageMaker CreateModel + CreateEndpointConfig + CreateEndpoint API
        EndpointConfig endpointConfig = EndpointConfig.builder()
                .modelName(jobName + "-model")
                .endpointConfigName(jobName + "-config")
                .endpointName(jobName + "-endpoint")
                .roleArn(ROLE_ARN)
                // PyTorch 2.0.1 GPU 推理镜像
                .inferenceImage("763104351884.dkr.ecr.us-east-1.amazonaws.com/pytorch-inference:2.0.1-gpu-py310")
                // 模型文件路径，训练完成后自动生成在 s3OutputPath 下
                .modelDataUrl("s3://" + S3_BUCKET + "/output/" + jobName + "/output/model.tar.gz")
                .instanceType("ml.m5.xlarge")     // 推理实例类型（CPU 实例，成本较低）
                .initialInstanceCount(1)           // 初始实例数
                // 启用数据捕获，用于模型监控（捕获 100% 的请求和响应）
                .enableDataCapture("s3://" + S3_BUCKET + "/data-capture/", 100)
                .build();

        System.out.println("\n步骤 2: 部署配置已创建");
        System.out.println("  模型名称: " + endpointConfig.getModelName());
        System.out.println("  端点名称: " + endpointConfig.getEndpointName());
        System.out.println("  数据捕获: 已启用（100% 采样率）");

        // 步骤 3: 监控配置说明
        System.out.println("\n步骤 3: 监控配置");
        System.out.println("  训练日志: CloudWatch Logs /aws/sagemaker/TrainingJobs");
        System.out.println("  训练指标: CloudWatch Metrics AWS/SageMaker (TrainingJobName 维度)");
        System.out.println("  训练资源: CloudWatch Metrics /aws/sagemaker/TrainingJobs (Host 维度)");
        System.out.println("  端点指标: CloudWatch Metrics AWS/SageMaker (EndpointName 维度)");
        System.out.println("  端点资源: CloudWatch Metrics /aws/sagemaker/Endpoints (EndpointName 维度)");
        System.out.println("  延迟告警: 阈值 500ms，连续 2 个周期触发");
        System.out.println("  错误告警: 阈值 10 次/5分钟，1 个周期触发");

        // 步骤 4: 完整执行步骤说明
        System.out.println("\n==================== 执行步骤 ====================");
        System.out.println("// --- 训练阶段 ---");
        System.out.println("1. trainingService.createTrainingJob(trainingConfig)        // 创建训练作业");
        System.out.println("2. trainingService.waitForTrainingJob(jobName, 60)          // 等待训练完成（最多60分钟）");
        System.out.println("3. trainingService.getTrainingJobStatus(jobName)            // 查询训练状态");
        System.out.println("4. logService.printTrainingJobLogs(jobName, 100)            // 查看训练日志");
        System.out.println("5. metricService.printTrainingJobMetrics(jobName, \"train:loss\", 2)  // 查询训练损失");
        System.out.println("6. metricService.printTrainingResourceSummary(jobName, \"algo-1\", 2) // 查询资源利用率");
        System.out.println();
        System.out.println("// --- 部署阶段 ---");
        System.out.println("7. deploymentService.deployModel(endpointConfig)            // 一键部署模型");
        System.out.println("8. deploymentService.waitForEndpoint(endpointName, 15)      // 等待端点就绪（最多15分钟）");
        System.out.println();
        System.out.println("// --- 推理阶段 ---");
        System.out.println("9. inferenceService.invokeEndpointJson(endpointName, payload) // 调用端点推理");
        System.out.println();
        System.out.println("// --- 监控阶段 ---");
        System.out.println("10. metricService.printEndpointHealthSummary(endpointName, 1)  // 端点运行状况");
        System.out.println("11. metricService.printEndpointResourceSummary(endpointName, 1) // 端点资源利用率");
        System.out.println("12. metricService.createEndpointLatencyAlarm(...)               // 创建延迟告警");
        System.out.println("13. metricService.createEndpointErrorAlarm(...)                 // 创建错误告警");
        System.out.println("====================================================");
    }
}
