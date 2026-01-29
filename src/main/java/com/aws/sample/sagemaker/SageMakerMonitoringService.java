package com.aws.sample.sagemaker;

import java.util.List;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.sagemaker.model.MonitoringConfig;

import software.amazon.awssdk.services.sagemaker.SageMakerClient;
import software.amazon.awssdk.services.sagemaker.model.BatchTransformInput;
import software.amazon.awssdk.services.sagemaker.model.CreateDataQualityJobDefinitionRequest;
import software.amazon.awssdk.services.sagemaker.model.CreateDataQualityJobDefinitionResponse;
import software.amazon.awssdk.services.sagemaker.model.CreateMonitoringScheduleRequest;
import software.amazon.awssdk.services.sagemaker.model.CreateMonitoringScheduleResponse;
import software.amazon.awssdk.services.sagemaker.model.DataQualityAppSpecification;
import software.amazon.awssdk.services.sagemaker.model.DataQualityBaselineConfig;
import software.amazon.awssdk.services.sagemaker.model.DataQualityJobInput;
import software.amazon.awssdk.services.sagemaker.model.DeleteMonitoringScheduleRequest;
import software.amazon.awssdk.services.sagemaker.model.DescribeMonitoringScheduleRequest;
import software.amazon.awssdk.services.sagemaker.model.DescribeMonitoringScheduleResponse;
import software.amazon.awssdk.services.sagemaker.model.EndpointInput;
import software.amazon.awssdk.services.sagemaker.model.ListMonitoringExecutionsRequest;
import software.amazon.awssdk.services.sagemaker.model.ListMonitoringExecutionsResponse;
import software.amazon.awssdk.services.sagemaker.model.ListMonitoringSchedulesRequest;
import software.amazon.awssdk.services.sagemaker.model.ListMonitoringSchedulesResponse;
import software.amazon.awssdk.services.sagemaker.model.MonitoringAppSpecification;
import software.amazon.awssdk.services.sagemaker.model.MonitoringBaselineConfig;
import software.amazon.awssdk.services.sagemaker.model.MonitoringClusterConfig;
import software.amazon.awssdk.services.sagemaker.model.MonitoringConstraintsResource;
import software.amazon.awssdk.services.sagemaker.model.MonitoringCsvDatasetFormat;
import software.amazon.awssdk.services.sagemaker.model.MonitoringDatasetFormat;
import software.amazon.awssdk.services.sagemaker.model.MonitoringExecutionSortKey;
import software.amazon.awssdk.services.sagemaker.model.MonitoringExecutionSummary;
import software.amazon.awssdk.services.sagemaker.model.MonitoringInput;
import software.amazon.awssdk.services.sagemaker.model.MonitoringJobDefinition;
import software.amazon.awssdk.services.sagemaker.model.MonitoringOutput;
import software.amazon.awssdk.services.sagemaker.model.MonitoringOutputConfig;
import software.amazon.awssdk.services.sagemaker.model.MonitoringResources;
import software.amazon.awssdk.services.sagemaker.model.MonitoringS3Output;
import software.amazon.awssdk.services.sagemaker.model.MonitoringScheduleConfig;
import software.amazon.awssdk.services.sagemaker.model.MonitoringScheduleSortKey;
import software.amazon.awssdk.services.sagemaker.model.MonitoringScheduleSummary;
import software.amazon.awssdk.services.sagemaker.model.MonitoringStatisticsResource;
import software.amazon.awssdk.services.sagemaker.model.ProcessingInstanceType;
import software.amazon.awssdk.services.sagemaker.model.ProcessingS3DataDistributionType;
import software.amazon.awssdk.services.sagemaker.model.ProcessingS3InputMode;
import software.amazon.awssdk.services.sagemaker.model.ProcessingS3UploadMode;
import software.amazon.awssdk.services.sagemaker.model.ScheduleConfig;
import software.amazon.awssdk.services.sagemaker.model.SortOrder;
import software.amazon.awssdk.services.sagemaker.model.StartMonitoringScheduleRequest;
import software.amazon.awssdk.services.sagemaker.model.StopMonitoringScheduleRequest;

/**
 * SageMaker 监控服务
 * 提供模型监控、数据质量监控和模型偏差检测功能
 */
public class SageMakerMonitoringService {

    private final SageMakerClient sageMakerClient;
    private final AwsConfig config;

    public SageMakerMonitoringService(AwsConfig config) {
        this.config = config;
        this.sageMakerClient = SageMakerClient.builder()
                .region(config.getRegion())
                .credentialsProvider(config.getCredentialsProvider())
                .build();
    }

    /**
     * 创建数据质量监控基线任务
     */
    public String createDataQualityBaseline(String jobName, String roleArn,
                                            String baselineDatasetUri, String outputS3Uri,
                                            String instanceType) {
        System.out.println("创建数据质量基线任务: " + jobName);

        // 数据质量基线配置
        DataQualityBaselineConfig baselineConfig = DataQualityBaselineConfig.builder()
                .statisticsResource(MonitoringStatisticsResource.builder()
                        .s3Uri(outputS3Uri + "/statistics.json")
                        .build())
                .constraintsResource(MonitoringConstraintsResource.builder()
                        .s3Uri(outputS3Uri + "/constraints.json")
                        .build())
                .build();

        // 应用配置
        DataQualityAppSpecification appSpec = DataQualityAppSpecification.builder()
                .imageUri(getDefaultMonitoringImage())
                .build();

        // 输入配置
        DataQualityJobInput jobInput = DataQualityJobInput.builder()
                .batchTransformInput(BatchTransformInput.builder()
                        .dataCapturedDestinationS3Uri(baselineDatasetUri)
                        .datasetFormat(MonitoringDatasetFormat.builder()
                                .csv(MonitoringCsvDatasetFormat.builder().header(true).build())
                                .build())
                        .localPath("/opt/ml/processing/input")
                        .build())
                .build();

        // 输出配置
        MonitoringOutputConfig outputConfig = MonitoringOutputConfig.builder()
                .monitoringOutputs(MonitoringOutput.builder()
                        .s3Output(MonitoringS3Output.builder()
                                .s3Uri(outputS3Uri)
                                .localPath("/opt/ml/processing/output")
                                .s3UploadMode(ProcessingS3UploadMode.END_OF_JOB)
                                .build())
                        .build())
                .build();

        // 资源配置
        MonitoringResources resources = MonitoringResources.builder()
                .clusterConfig(MonitoringClusterConfig.builder()
                        .instanceType(ProcessingInstanceType.fromValue(instanceType))
                        .instanceCount(1)
                        .volumeSizeInGB(20)
                        .build())
                .build();

        CreateDataQualityJobDefinitionRequest request = CreateDataQualityJobDefinitionRequest.builder()
                .jobDefinitionName(jobName)
                .roleArn(roleArn)
                .dataQualityBaselineConfig(baselineConfig)
                .dataQualityAppSpecification(appSpec)
                .dataQualityJobInput(jobInput)
                .dataQualityJobOutputConfig(outputConfig)
                .jobResources(resources)
                .build();

        CreateDataQualityJobDefinitionResponse response = 
                sageMakerClient.createDataQualityJobDefinition(request);
        System.out.println("数据质量基线任务已创建: " + response.jobDefinitionArn());
        return response.jobDefinitionArn();
    }

    /**
     * 创建模型监控调度
     */
    public String createMonitoringSchedule(MonitoringConfig monitoringConfig) {
        System.out.println("创建监控调度: " + monitoringConfig.getMonitoringScheduleName());

        // 调度配置
        ScheduleConfig scheduleConfig = ScheduleConfig.builder()
                .scheduleExpression(monitoringConfig.getScheduleExpression())
                .build();

        // 端点输入配置
        EndpointInput endpointInput = EndpointInput.builder()
                .endpointName(monitoringConfig.getEndpointName())
                .localPath("/opt/ml/processing/input")
                .s3InputMode(ProcessingS3InputMode.FILE)
                .s3DataDistributionType(ProcessingS3DataDistributionType.FULLY_REPLICATED)
                .build();

        // 输出配置
        MonitoringOutputConfig outputConfig = MonitoringOutputConfig.builder()
                .monitoringOutputs(MonitoringOutput.builder()
                        .s3Output(MonitoringS3Output.builder()
                                .s3Uri(monitoringConfig.getS3OutputPath())
                                .localPath("/opt/ml/processing/output")
                                .s3UploadMode(ProcessingS3UploadMode.END_OF_JOB)
                                .build())
                        .build())
                .build();

        // 资源配置
        MonitoringResources resources = MonitoringResources.builder()
                .clusterConfig(MonitoringClusterConfig.builder()
                        .instanceType(ProcessingInstanceType.fromValue(monitoringConfig.getInstanceType()))
                        .instanceCount(monitoringConfig.getInstanceCount())
                        .volumeSizeInGB(monitoringConfig.getVolumeSizeGB())
                        .build())
                .build();

        // 基线配置
        MonitoringBaselineConfig.Builder baselineConfigBuilder = MonitoringBaselineConfig.builder();
        if (monitoringConfig.getBaselineConstraintsUri() != null) {
            baselineConfigBuilder.constraintsResource(MonitoringConstraintsResource.builder()
                    .s3Uri(monitoringConfig.getBaselineConstraintsUri())
                    .build());
        }
        if (monitoringConfig.getBaselineStatisticsUri() != null) {
            baselineConfigBuilder.statisticsResource(MonitoringStatisticsResource.builder()
                    .s3Uri(monitoringConfig.getBaselineStatisticsUri())
                    .build());
        }

        // 监控任务定义
        MonitoringJobDefinition jobDefinition = MonitoringJobDefinition.builder()
                .monitoringInputs(MonitoringInput.builder()
                        .endpointInput(endpointInput)
                        .build())
                .monitoringOutputConfig(outputConfig)
                .monitoringResources(resources)
                .monitoringAppSpecification(MonitoringAppSpecification.builder()
                        .imageUri(getDefaultMonitoringImage())
                        .build())
                .roleArn(monitoringConfig.getRoleArn())
                .baselineConfig(baselineConfigBuilder.build())
                .build();

        // 监控调度配置
        MonitoringScheduleConfig monitoringScheduleConfig = MonitoringScheduleConfig.builder()
                .scheduleConfig(scheduleConfig)
                .monitoringJobDefinition(jobDefinition)
                .build();

        CreateMonitoringScheduleRequest request = CreateMonitoringScheduleRequest.builder()
                .monitoringScheduleName(monitoringConfig.getMonitoringScheduleName())
                .monitoringScheduleConfig(monitoringScheduleConfig)
                .build();

        CreateMonitoringScheduleResponse response = sageMakerClient.createMonitoringSchedule(request);
        System.out.println("监控调度已创建: " + response.monitoringScheduleArn());
        return response.monitoringScheduleArn();
    }

    /**
     * 获取监控调度状态
     */
    public DescribeMonitoringScheduleResponse describeMonitoringSchedule(String scheduleName) {
        DescribeMonitoringScheduleRequest request = DescribeMonitoringScheduleRequest.builder()
                .monitoringScheduleName(scheduleName)
                .build();
        return sageMakerClient.describeMonitoringSchedule(request);
    }

    /**
     * 启动监控调度
     */
    public void startMonitoringSchedule(String scheduleName) {
        System.out.println("启动监控调度: " + scheduleName);
        StartMonitoringScheduleRequest request = StartMonitoringScheduleRequest.builder()
                .monitoringScheduleName(scheduleName)
                .build();
        sageMakerClient.startMonitoringSchedule(request);
        System.out.println("监控调度已启动");
    }

    /**
     * 停止监控调度
     */
    public void stopMonitoringSchedule(String scheduleName) {
        System.out.println("停止监控调度: " + scheduleName);
        StopMonitoringScheduleRequest request = StopMonitoringScheduleRequest.builder()
                .monitoringScheduleName(scheduleName)
                .build();
        sageMakerClient.stopMonitoringSchedule(request);
        System.out.println("监控调度已停止");
    }

    /**
     * 删除监控调度
     */
    public void deleteMonitoringSchedule(String scheduleName) {
        System.out.println("删除监控调度: " + scheduleName);
        DeleteMonitoringScheduleRequest request = DeleteMonitoringScheduleRequest.builder()
                .monitoringScheduleName(scheduleName)
                .build();
        sageMakerClient.deleteMonitoringSchedule(request);
        System.out.println("监控调度已删除");
    }

    /**
     * 列出监控调度
     */
    public List<MonitoringScheduleSummary> listMonitoringSchedules(String endpointName, int maxResults) {
        ListMonitoringSchedulesRequest.Builder requestBuilder = ListMonitoringSchedulesRequest.builder()
                .maxResults(maxResults)
                .sortBy(MonitoringScheduleSortKey.CREATION_TIME)
                .sortOrder(SortOrder.DESCENDING);

        if (endpointName != null && !endpointName.isEmpty()) {
            requestBuilder.endpointName(endpointName);
        }

        ListMonitoringSchedulesResponse response = 
                sageMakerClient.listMonitoringSchedules(requestBuilder.build());
        return response.monitoringScheduleSummaries();
    }

    /**
     * 列出监控执行记录
     */
    public List<MonitoringExecutionSummary> listMonitoringExecutions(String scheduleName, int maxResults) {
        ListMonitoringExecutionsRequest request = ListMonitoringExecutionsRequest.builder()
                .monitoringScheduleName(scheduleName)
                .maxResults(maxResults)
                .sortBy(MonitoringExecutionSortKey.SCHEDULED_TIME)
                .sortOrder(SortOrder.DESCENDING)
                .build();

        ListMonitoringExecutionsResponse response = sageMakerClient.listMonitoringExecutions(request);
        return response.monitoringExecutionSummaries();
    }

    /**
     * 获取默认监控镜像 URI
     */
    private String getDefaultMonitoringImage() {
        String region = config.getRegion().id();
        String accountId = getMonitoringImageAccountId(region);
        String domain = region.startsWith("cn-") ? 
                "dkr.ecr." + region + ".amazonaws.com.cn" : 
                "dkr.ecr." + region + ".amazonaws.com";
        return String.format("%s.%s/sagemaker-model-monitor-analyzer", accountId, domain);
    }

    /**
     * 获取监控镜像账户 ID
     */
    private String getMonitoringImageAccountId(String region) {
        return switch (region) {
            case "us-east-1" -> "156813124566";
            case "us-east-2" -> "777275614652";
            case "us-west-1" -> "890145073186";
            case "us-west-2" -> "159807026194";
            case "eu-west-1" -> "468650794304";
            case "eu-west-2" -> "749857270468";
            case "eu-central-1" -> "048819808253";
            case "ap-northeast-1" -> "574779866223";
            case "ap-northeast-2" -> "709848358524";
            case "ap-southeast-1" -> "245545462676";
            case "ap-southeast-2" -> "563025443158";
            case "ap-south-1" -> "126357580389";
            case "cn-north-1" -> "453000072557";
            case "cn-northwest-1" -> "453252182341";
            default -> "156813124566";
        };
    }

    /**
     * 打印监控调度详情
     */
    public void printMonitoringScheduleDetails(String scheduleName) {
        DescribeMonitoringScheduleResponse schedule = describeMonitoringSchedule(scheduleName);
        System.out.println("==================== 监控调度详情 ====================");
        System.out.println("调度名称: " + schedule.monitoringScheduleName());
        System.out.println("调度 ARN: " + schedule.monitoringScheduleArn());
        System.out.println("状态: " + schedule.monitoringScheduleStatus());
        System.out.println("创建时间: " + schedule.creationTime());
        
        if (schedule.lastMonitoringExecutionSummary() != null) {
            var lastExecution = schedule.lastMonitoringExecutionSummary();
            System.out.println("最后执行时间: " + lastExecution.scheduledTime());
            System.out.println("最后执行状态: " + lastExecution.monitoringExecutionStatus());
        }
        System.out.println("====================================================");
    }

    /**
     * 打印监控执行历史
     */
    public void printMonitoringExecutionHistory(String scheduleName, int count) {
        List<MonitoringExecutionSummary> executions = listMonitoringExecutions(scheduleName, count);
        System.out.println("==================== 监控执行历史 ====================");
        System.out.println("调度名称: " + scheduleName);
        System.out.println("执行记录数: " + executions.size());
        System.out.println();
        
        for (MonitoringExecutionSummary execution : executions) {
            System.out.println("执行时间: " + execution.scheduledTime());
            System.out.println("  状态: " + execution.monitoringExecutionStatus());
            if (execution.failureReason() != null) {
                System.out.println("  失败原因: " + execution.failureReason());
            }
            System.out.println();
        }
        System.out.println("====================================================");
    }

    public void close() {
        sageMakerClient.close();
    }
}
