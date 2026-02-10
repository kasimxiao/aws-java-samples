package com.aws.sample.sagemaker;

import java.util.ArrayList;
import java.util.List;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.sagemaker.model.TrainingJobConfig;

import software.amazon.awssdk.services.sagemaker.SageMakerClient;
import software.amazon.awssdk.services.sagemaker.model.AlgorithmSpecification;
import software.amazon.awssdk.services.sagemaker.model.Channel;
import software.amazon.awssdk.services.sagemaker.model.CreateTrainingJobRequest;
import software.amazon.awssdk.services.sagemaker.model.CreateTrainingJobResponse;
import software.amazon.awssdk.services.sagemaker.model.DataSource;
import software.amazon.awssdk.services.sagemaker.model.DescribeTrainingJobRequest;
import software.amazon.awssdk.services.sagemaker.model.DescribeTrainingJobResponse;
import software.amazon.awssdk.services.sagemaker.model.ListTrainingJobsRequest;
import software.amazon.awssdk.services.sagemaker.model.ListTrainingJobsResponse;
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
 * 提供 SageMaker 训练作业的全生命周期管理，底层调用 SageMaker API。
 *
 * 主要功能:
 * - 创建训练作业（CreateTrainingJob API）
 * - 查询训练状态和详情（DescribeTrainingJob API）
 * - 等待训练完成（轮询 DescribeTrainingJob）
 * - 停止训练作业（StopTrainingJob API）
 * - 列出训练作业（ListTrainingJobs API）
 * - 获取模型输出路径（训练完成后的 model.tar.gz 路径）
 *
 * 训练状态流转:
 * InProgress → Completed（成功）/ Failed（失败）/ Stopping → Stopped（手动停止）
 *
 * 使用示例:
 * <pre>
 * AwsConfig config = new AwsConfig();
 * SageMakerTrainingService trainingService = new SageMakerTrainingService(config);
 *
 * // 创建训练作业
 * TrainingJobConfig jobConfig = TrainingJobConfig.builder()
 *     .jobName("my-training-job")
 *     .roleArn("arn:aws:iam::123456789012:role/SageMakerRole")
 *     .trainingImage("763104351884.dkr.ecr.us-east-1.amazonaws.com/pytorch-training:2.0.1-gpu-py310")
 *     .s3TrainDataUri("s3://bucket/train/")
 *     .s3OutputPath("s3://bucket/output/")
 *     .build();
 * trainingService.createTrainingJob(jobConfig);
 *
 * // 等待训练完成
 * TrainingJobStatus status = trainingService.waitForTrainingJob("my-training-job", 60);
 * </pre>
 */
public class SageMakerTrainingService {

    private final SageMakerClient sageMakerClient;
    private final AwsConfig config;

    public SageMakerTrainingService(AwsConfig config) {
        this.config = config;
        this.sageMakerClient = SageMakerClient.builder()
                .region(config.getRegion())
                .credentialsProvider(config.getCredentialsProvider())
                .build();
    }

    /**
     * 创建训练任务
     *
     * 底层调用 SageMaker CreateTrainingJob API。
     * 根据 TrainingJobConfig 构建输入数据通道（train/validation）、资源配置、
     * 输出配置、停止条件和算法规格，可选配置 VPC 网络隔离。
     *
     * 数据输入模式为 FILE（先下载到本地再训练），数据分发类型为 FULLY_REPLICATED
     * （每个实例获取完整数据副本）。
     *
     * @param jobConfig 训练任务配置，包含作业名称、角色、镜像、实例、数据路径、超参数等
     * @return 训练作业 ARN
     */
    public String createTrainingJob(TrainingJobConfig jobConfig) {
        System.out.println("创建训练任务: " + jobConfig.getJobName());

        // 构建输入数据通道
        List<Channel> inputChannels = new ArrayList<>();
        
        // 训练数据通道
        if (jobConfig.getS3TrainDataUri() != null) {
            Channel trainChannel = Channel.builder()
                    .channelName("train")
                    .dataSource(DataSource.builder()
                            .s3DataSource(S3DataSource.builder()
                                    .s3DataType(S3DataType.S3_PREFIX)
                                    .s3Uri(jobConfig.getS3TrainDataUri())
                                    .s3DataDistributionType(S3DataDistribution.FULLY_REPLICATED)
                                    .build())
                            .build())
                    .contentType(jobConfig.getInputContentType())
                    .inputMode(TrainingInputMode.FILE)
                    .build();
            inputChannels.add(trainChannel);
        }

        // 验证数据通道（可选）
        if (jobConfig.getS3ValidationDataUri() != null) {
            Channel validationChannel = Channel.builder()
                    .channelName("validation")
                    .dataSource(DataSource.builder()
                            .s3DataSource(S3DataSource.builder()
                                    .s3DataType(S3DataType.S3_PREFIX)
                                    .s3Uri(jobConfig.getS3ValidationDataUri())
                                    .s3DataDistributionType(S3DataDistribution.FULLY_REPLICATED)
                                    .build())
                            .build())
                    .contentType(jobConfig.getInputContentType())
                    .inputMode(TrainingInputMode.FILE)
                    .build();
            inputChannels.add(validationChannel);
        }

        // 构建资源配置
        ResourceConfig resourceConfig = ResourceConfig.builder()
                .instanceType(TrainingInstanceType.fromValue(jobConfig.getInstanceType()))
                .instanceCount(jobConfig.getInstanceCount())
                .volumeSizeInGB(jobConfig.getVolumeSizeGB())
                .build();

        // 构建输出配置
        OutputDataConfig outputConfig = OutputDataConfig.builder()
                .s3OutputPath(jobConfig.getS3OutputPath())
                .build();

        // 构建停止条件
        StoppingCondition stoppingCondition = StoppingCondition.builder()
                .maxRuntimeInSeconds(jobConfig.getMaxRuntimeSeconds())
                .build();

        // 构建算法规格
        AlgorithmSpecification algorithmSpec = AlgorithmSpecification.builder()
                .trainingImage(jobConfig.getTrainingImage())
                .trainingInputMode(TrainingInputMode.FILE)
                .build();

        // 构建请求
        CreateTrainingJobRequest.Builder requestBuilder = CreateTrainingJobRequest.builder()
                .trainingJobName(jobConfig.getJobName())
                .roleArn(jobConfig.getRoleArn())
                .algorithmSpecification(algorithmSpec)
                .resourceConfig(resourceConfig)
                .outputDataConfig(outputConfig)
                .stoppingCondition(stoppingCondition)
                .hyperParameters(jobConfig.getHyperParameters());

        if (!inputChannels.isEmpty()) {
            requestBuilder.inputDataConfig(inputChannels);
        }

        // VPC 配置（可选）
        if (jobConfig.getSubnetId() != null && jobConfig.getSecurityGroupId() != null) {
            VpcConfig vpcConfig = VpcConfig.builder()
                    .subnets(jobConfig.getSubnetId())
                    .securityGroupIds(jobConfig.getSecurityGroupId())
                    .build();
            requestBuilder.vpcConfig(vpcConfig);
        }

        CreateTrainingJobResponse response = sageMakerClient.createTrainingJob(requestBuilder.build());
        String trainingJobArn = response.trainingJobArn();
        System.out.println("训练任务已创建: " + trainingJobArn);
        return trainingJobArn;
    }

    /**
     * 获取训练任务状态
     *
     * 底层调用 SageMaker DescribeTrainingJob API，仅返回状态枚举。
     * 可能的状态值: InProgress、Completed、Failed、Stopping、Stopped
     *
     * @param jobName 训练作业名称
     * @return 训练作业状态枚举
     */
    public TrainingJobStatus getTrainingJobStatus(String jobName) {
        DescribeTrainingJobRequest request = DescribeTrainingJobRequest.builder()
                .trainingJobName(jobName)
                .build();
        DescribeTrainingJobResponse response = sageMakerClient.describeTrainingJob(request);
        return response.trainingJobStatus();
    }

    /**
     * 获取训练任务详情
     *
     * 底层调用 SageMaker DescribeTrainingJob API，返回完整的训练作业信息，
     * 包括状态、创建时间、训练时长、资源配置、模型输出路径、失败原因等。
     *
     * @param jobName 训练作业名称
     * @return 训练作业详情响应对象
     */
    public DescribeTrainingJobResponse describeTrainingJob(String jobName) {
        DescribeTrainingJobRequest request = DescribeTrainingJobRequest.builder()
                .trainingJobName(jobName)
                .build();
        return sageMakerClient.describeTrainingJob(request);
    }

    /**
     * 等待训练任务完成
     *
     * 每 30 秒轮询一次 DescribeTrainingJob API，直到状态变为终态
     * （Completed/Failed/Stopped）或超过最大等待时间。
     *
     * @param jobName        训练作业名称
     * @param maxWaitMinutes 最大等待时间（分钟），超时后返回当前状态
     * @return 训练作业最终状态
     * @throws InterruptedException 等待过程中线程被中断
     */
    public TrainingJobStatus waitForTrainingJob(String jobName, int maxWaitMinutes) 
            throws InterruptedException {
        System.out.println("等待训练任务完成: " + jobName);
        long startTime = System.currentTimeMillis();
        long maxWaitMs = maxWaitMinutes * 60 * 1000L;

        while (true) {
            TrainingJobStatus status = getTrainingJobStatus(jobName);
            System.out.println("当前状态: " + status);

            if (status == TrainingJobStatus.COMPLETED ||
                status == TrainingJobStatus.FAILED ||
                status == TrainingJobStatus.STOPPED) {
                return status;
            }

            if (System.currentTimeMillis() - startTime > maxWaitMs) {
                System.out.println("等待超时");
                return status;
            }

            Thread.sleep(30000); // 每30秒检查一次
        }
    }

    /**
     * 停止训练任务
     *
     * 底层调用 SageMaker StopTrainingJob API，发送异步停止请求。
     * 调用后状态变为 Stopping，最终变为 Stopped。
     * 已产生的训练费用仍会计费，部分训练的模型文件可能不完整。
     *
     * @param jobName 训练作业名称
     */
    public void stopTrainingJob(String jobName) {
        System.out.println("停止训练任务: " + jobName);
        StopTrainingJobRequest request = StopTrainingJobRequest.builder()
                .trainingJobName(jobName)
                .build();
        sageMakerClient.stopTrainingJob(request);
        System.out.println("已发送停止请求");
    }

    /**
     * 列出训练任务
     *
     * 底层调用 SageMaker ListTrainingJobs API，按创建时间降序排列。
     * 支持按名称模糊过滤。
     *
     * @param nameContains 名称过滤关键字（可为 null，不过滤）
     * @param maxResults   最大返回数量
     * @return 训练作业摘要列表，包含名称、状态、创建时间等
     */
    public List<TrainingJobSummary> listTrainingJobs(String nameContains, int maxResults) {
        ListTrainingJobsRequest.Builder requestBuilder = ListTrainingJobsRequest.builder()
                .maxResults(maxResults)
                .sortBy(SortBy.CREATION_TIME)
                .sortOrder(SortOrder.DESCENDING);

        if (nameContains != null && !nameContains.isEmpty()) {
            requestBuilder.nameContains(nameContains);
        }

        ListTrainingJobsResponse response = sageMakerClient.listTrainingJobs(requestBuilder.build());
        return response.trainingJobSummaries();
    }

    /**
     * 获取训练任务的模型输出路径
     *
     * 仅在训练状态为 Completed 时返回模型文件的 S3 路径（model.tar.gz），
     * 该路径可直接用于 SageMaker 模型部署。
     *
     * @param jobName 训练作业名称
     * @return 模型文件 S3 路径（如 s3://bucket/output/job-name/output/model.tar.gz），
     *         训练未完成时返回 null
     */
    public String getModelArtifactPath(String jobName) {
        DescribeTrainingJobResponse response = describeTrainingJob(jobName);
        if (response.trainingJobStatus() == TrainingJobStatus.COMPLETED) {
            return response.modelArtifacts().s3ModelArtifacts();
        }
        return null;
    }

    /**
     * 打印训练任务详情到控制台
     *
     * 格式化输出训练作业的名称、ARN、状态、创建时间、实例配置等信息。
     * 训练完成时额外显示训练时长和模型输出路径，训练失败时显示失败原因。
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
        System.out.println("====================================================");
    }

    public void close() {
        sageMakerClient.close();
    }
}
