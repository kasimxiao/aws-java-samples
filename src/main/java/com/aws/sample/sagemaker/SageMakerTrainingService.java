package com.aws.sample.sagemaker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.sagemaker.model.TrainingJobConfig;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sagemaker.SageMakerClient;
import software.amazon.awssdk.services.sagemaker.model.AlgorithmSpecification;
import software.amazon.awssdk.services.sagemaker.model.Channel;
import software.amazon.awssdk.services.sagemaker.model.CreateTrainingJobRequest;
import software.amazon.awssdk.services.sagemaker.model.CreateTrainingJobResponse;
import software.amazon.awssdk.services.sagemaker.model.DataSource;
import software.amazon.awssdk.services.sagemaker.model.DescribeTrainingJobRequest;
import software.amazon.awssdk.services.sagemaker.model.DescribeTrainingJobResponse;
import software.amazon.awssdk.services.sagemaker.model.ListTrainingJobsRequest;
import software.amazon.awssdk.services.sagemaker.model.OutputDataConfig;
import software.amazon.awssdk.services.sagemaker.model.ResourceConfig;
import software.amazon.awssdk.services.sagemaker.model.S3DataDistribution;
import software.amazon.awssdk.services.sagemaker.model.S3DataSource;
import software.amazon.awssdk.services.sagemaker.model.S3DataType;
import software.amazon.awssdk.services.sagemaker.model.SortBy;
import software.amazon.awssdk.services.sagemaker.model.SortOrder;
import software.amazon.awssdk.services.sagemaker.model.StopTrainingJobRequest;
import software.amazon.awssdk.services.sagemaker.model.StoppingCondition;
import software.amazon.awssdk.services.sagemaker.model.TrainingInputMode;
import software.amazon.awssdk.services.sagemaker.model.TrainingInstanceType;
import software.amazon.awssdk.services.sagemaker.model.TrainingJobStatus;
import software.amazon.awssdk.services.sagemaker.model.TrainingJobSummary;
import software.amazon.awssdk.services.sagemaker.model.VpcConfig;

/**
 * SageMaker 训练服务
 *
 * 提供训练作业的全生命周期管理：创建、查询、等待、停止、列出。
 *
 * 训练状态流转:
 * InProgress → Completed（成功）/ Failed（失败）/ Stopping → Stopped（手动停止）
 *
 * 使用示例:
 * <pre>
 * // 方式一：通过 AwsConfig（读取 application.properties 区域）
 * SageMakerTrainingService service = new SageMakerTrainingService(new AwsConfig());
 *
 * // 方式二：指定区域（跨区域场景）
 * SageMakerTrainingService service = new SageMakerTrainingService(Region.US_EAST_1);
 *
 * // 创建训练作业
 * TrainingJobConfig jobConfig = TrainingJobConfig.builder()
 *     .jobName("my-training-job")
 *     .roleArn("arn:aws:iam::123456789012:role/SageMakerRole")
 *     .trainingImage("镜像URI")
 *     .s3TrainDataUri("s3://bucket/train/")
 *     .s3OutputPath("s3://bucket/output/")
 *     .s3SubmitDirectory("s3://bucket/code/sourcedir.tar.gz")
 *     .entryPoint("train.py")
 *     .build();
 * service.createTrainingJob(jobConfig);
 *
 * // 等待训练完成
 * TrainingJobStatus status = service.waitForTrainingJob("my-training-job", 60);
 * </pre>
 */
public class SageMakerTrainingService {

    private final SageMakerClient sageMakerClient;

    /**
     * 通过 AwsConfig 构建（使用配置文件中的区域和凭证）
     */
    public SageMakerTrainingService(AwsConfig config) {
        this.sageMakerClient = SageMakerClient.builder()
                .region(config.getRegion())
                .credentialsProvider(config.getCredentialsProvider())
                .build();
    }

    /**
     * 指定区域构建（使用默认凭证链，适用于跨区域场景）
     */
    public SageMakerTrainingService(Region region) {
        this.sageMakerClient = SageMakerClient.builder()
                .region(region)
                .build();
    }

    /**
     * 直接传入 SageMakerClient（用于测试或自定义客户端配置）
     */
    public SageMakerTrainingService(SageMakerClient client) {
        this.sageMakerClient = client;
    }

    /**
     * 创建训练任务
     *
     * 根据 TrainingJobConfig 构建输入数据通道、资源配置、输出配置、
     * 停止条件和算法规格。自动将 s3SubmitDirectory 和 entryPoint
     * 转换为 sagemaker_submit_directory 和 sagemaker_program 超参数。
     *
     * @param jobConfig 训练任务配置
     * @return 训练作业 ARN
     */
    public String createTrainingJob(TrainingJobConfig jobConfig) {
        System.out.println("创建训练任务: " + jobConfig.getJobName());

        // 输入数据通道
        List<Channel> inputChannels = buildInputChannels(jobConfig);

        // 超参数（合并用户超参数 + 代码包配置）
        Map<String, String> hyperParameters = buildHyperParameters(jobConfig);

        // 构建请求
        CreateTrainingJobRequest.Builder requestBuilder = CreateTrainingJobRequest.builder()
                .trainingJobName(jobConfig.getJobName())
                .roleArn(jobConfig.getRoleArn())
                .algorithmSpecification(AlgorithmSpecification.builder()
                        .trainingImage(jobConfig.getTrainingImage())
                        .trainingInputMode(TrainingInputMode.FILE)
                        .build())
                .resourceConfig(ResourceConfig.builder()
                        .instanceType(TrainingInstanceType.fromValue(jobConfig.getInstanceType()))
                        .instanceCount(jobConfig.getInstanceCount())
                        .volumeSizeInGB(jobConfig.getVolumeSizeGB())
                        .build())
                .outputDataConfig(OutputDataConfig.builder()
                        .s3OutputPath(jobConfig.getS3OutputPath())
                        .build())
                .stoppingCondition(StoppingCondition.builder()
                        .maxRuntimeInSeconds(jobConfig.getMaxRuntimeSeconds())
                        .build())
                .hyperParameters(hyperParameters);

        if (!inputChannels.isEmpty()) {
            requestBuilder.inputDataConfig(inputChannels);
        }

        // VPC 配置（可选）
        if (jobConfig.getSubnetId() != null && jobConfig.getSecurityGroupId() != null) {
            requestBuilder.vpcConfig(VpcConfig.builder()
                    .subnets(jobConfig.getSubnetId())
                    .securityGroupIds(jobConfig.getSecurityGroupId())
                    .build());
        }

        CreateTrainingJobResponse response = sageMakerClient.createTrainingJob(requestBuilder.build());
        System.out.println("训练任务已创建: " + response.trainingJobArn());
        return response.trainingJobArn();
    }

    /**
     * 获取训练任务详情
     *
     * @param jobName 训练作业名称
     * @return 训练作业详情
     */
    public DescribeTrainingJobResponse describeTrainingJob(String jobName) {
        return sageMakerClient.describeTrainingJob(
                DescribeTrainingJobRequest.builder().trainingJobName(jobName).build());
    }

    /**
     * 获取训练任务状态
     *
     * @param jobName 训练作业名称
     * @return 训练作业状态（InProgress/Completed/Failed/Stopping/Stopped）
     */
    public TrainingJobStatus getTrainingJobStatus(String jobName) {
        return describeTrainingJob(jobName).trainingJobStatus();
    }

    /**
     * 等待训练任务完成（每 30 秒轮询一次）
     *
     * @param jobName        训练作业名称
     * @param maxWaitMinutes 最大等待时间（分钟）
     * @return 训练作业最终状态
     * @throws InterruptedException 等待过程中线程被中断
     */
    public TrainingJobStatus waitForTrainingJob(String jobName, int maxWaitMinutes)
            throws InterruptedException {
        System.out.println("等待训练任务完成: " + jobName);
        long deadline = System.currentTimeMillis() + maxWaitMinutes * 60_000L;

        while (System.currentTimeMillis() < deadline) {
            TrainingJobStatus status = getTrainingJobStatus(jobName);
            System.out.println("  当前状态: " + status);

            if (isTerminalStatus(status)) {
                return status;
            }
            Thread.sleep(30_000);
        }
        System.out.println("等待超时");
        return getTrainingJobStatus(jobName);
    }

    /**
     * 停止训练任务（异步，调用后状态变为 Stopping → Stopped）
     *
     * @param jobName 训练作业名称
     */
    public void stopTrainingJob(String jobName) {
        System.out.println("停止训练任务: " + jobName);
        sageMakerClient.stopTrainingJob(
                StopTrainingJobRequest.builder().trainingJobName(jobName).build());
        System.out.println("已发送停止请求");
    }

    /**
     * 列出训练任务（按创建时间降序）
     *
     * @param nameContains 名称过滤关键字（可为 null）
     * @param maxResults   最大返回数量
     * @return 训练作业摘要列表
     */
    public List<TrainingJobSummary> listTrainingJobs(String nameContains, int maxResults) {
        ListTrainingJobsRequest.Builder requestBuilder = ListTrainingJobsRequest.builder()
                .maxResults(maxResults)
                .sortBy(SortBy.CREATION_TIME)
                .sortOrder(SortOrder.DESCENDING);

        if (nameContains != null && !nameContains.isEmpty()) {
            requestBuilder.nameContains(nameContains);
        }
        return sageMakerClient.listTrainingJobs(requestBuilder.build()).trainingJobSummaries();
    }

    /**
     * 获取训练完成后的模型输出路径
     *
     * @param jobName 训练作业名称
     * @return 模型 S3 路径（如 s3://bucket/output/model.tar.gz），未完成返回 null
     */
    public String getModelArtifactPath(String jobName) {
        DescribeTrainingJobResponse response = describeTrainingJob(jobName);
        if (response.trainingJobStatus() == TrainingJobStatus.COMPLETED) {
            return response.modelArtifacts().s3ModelArtifacts();
        }
        return null;
    }

    /**
     * 打印训练任务详情
     *
     * @param jobName 训练作业名称
     */
    public void printTrainingJobDetails(String jobName) {
        DescribeTrainingJobResponse job = describeTrainingJob(jobName);
        System.out.println("==================== 训练任务详情 ====================");
        System.out.println("任务名称: " + job.trainingJobName());
        System.out.println("任务 ARN: " + job.trainingJobArn());
        System.out.println("状态: " + job.trainingJobStatus());
        System.out.println("创建时间: " + job.creationTime());
        System.out.println("实例类型: " + job.resourceConfig().instanceType());
        System.out.println("实例数量: " + job.resourceConfig().instanceCount());
        if (job.trainingJobStatus() == TrainingJobStatus.COMPLETED) {
            System.out.println("训练时长: " + job.trainingTimeInSeconds() + " 秒");
            System.out.println("模型输出: " + job.modelArtifacts().s3ModelArtifacts());
        }
        if (job.trainingJobStatus() == TrainingJobStatus.FAILED) {
            System.out.println("失败原因: " + job.failureReason());
        }
        if (job.hasHyperParameters() && !job.hyperParameters().isEmpty()) {
            System.out.println("超参数: " + job.hyperParameters());
        }
        System.out.println("====================================================");
    }

    public void close() {
        sageMakerClient.close();
    }

    // ===== 私有方法 =====

    /** 构建输入数据通道 */
    private List<Channel> buildInputChannels(TrainingJobConfig jobConfig) {
        List<Channel> channels = new ArrayList<>();
        if (jobConfig.getS3TrainDataUri() != null) {
            channels.add(buildChannel("train", jobConfig.getS3TrainDataUri(), jobConfig.getInputContentType()));
        }
        if (jobConfig.getS3ValidationDataUri() != null) {
            channels.add(buildChannel("validation", jobConfig.getS3ValidationDataUri(), jobConfig.getInputContentType()));
        }
        return channels;
    }

    /** 构建单个数据通道 */
    private Channel buildChannel(String name, String s3Uri, String contentType) {
        return Channel.builder()
                .channelName(name)
                .dataSource(DataSource.builder()
                        .s3DataSource(S3DataSource.builder()
                                .s3DataType(S3DataType.S3_PREFIX)
                                .s3Uri(s3Uri)
                                .s3DataDistributionType(S3DataDistribution.FULLY_REPLICATED)
                                .build())
                        .build())
                .contentType(contentType)
                .inputMode(TrainingInputMode.FILE)
                .build();
    }

    /** 合并用户超参数和代码包配置 */
    private Map<String, String> buildHyperParameters(TrainingJobConfig jobConfig) {
        Map<String, String> hp = new HashMap<>(jobConfig.getHyperParameters());
        if (jobConfig.getS3SubmitDirectory() != null) {
            hp.put("sagemaker_submit_directory", jobConfig.getS3SubmitDirectory());
        }
        if (jobConfig.getEntryPoint() != null) {
            hp.put("sagemaker_program", jobConfig.getEntryPoint());
        }
        return hp;
    }

    /** 判断是否为终态 */
    private boolean isTerminalStatus(TrainingJobStatus status) {
        return status == TrainingJobStatus.COMPLETED
                || status == TrainingJobStatus.FAILED
                || status == TrainingJobStatus.STOPPED;
    }
}
