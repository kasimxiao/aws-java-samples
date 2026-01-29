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
 * 提供创建、管理和监控训练任务的功能
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
     */
    public DescribeTrainingJobResponse describeTrainingJob(String jobName) {
        DescribeTrainingJobRequest request = DescribeTrainingJobRequest.builder()
                .trainingJobName(jobName)
                .build();
        return sageMakerClient.describeTrainingJob(request);
    }

    /**
     * 等待训练任务完成
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
