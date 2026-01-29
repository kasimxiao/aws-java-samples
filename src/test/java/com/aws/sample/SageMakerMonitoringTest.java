package com.aws.sample;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.sagemaker.SageMakerMonitoringService;
import com.aws.sample.sagemaker.model.MonitoringConfig;

/**
 * SageMaker 监控服务测试示例
 */
public class SageMakerMonitoringTest {

    private AwsConfig config;
    private SageMakerMonitoringService monitoringService;

    private static final String ROLE_ARN = "arn:aws:iam::YOUR_ACCOUNT:role/SageMakerExecutionRole";
    private static final String S3_BUCKET = "your-sagemaker-bucket";
    private static final String ENDPOINT_NAME = "your-endpoint-name";

    @BeforeEach
    void setUp() {
        config = new AwsConfig();
        monitoringService = new SageMakerMonitoringService(config);
    }

    /**
     * 示例：创建数据质量监控
     */
    @Test
    void testCreateDataQualityMonitoring() {
        String scheduleName = "data-quality-monitor-" + System.currentTimeMillis();

        MonitoringConfig monitoringConfig = MonitoringConfig.builder()
                .monitoringScheduleName(scheduleName)
                .endpointName(ENDPOINT_NAME)
                .monitoringType(MonitoringConfig.MonitoringType.DATA_QUALITY)
                .roleArn(ROLE_ARN)
                .instanceType("ml.m5.xlarge")
                .instanceCount(1)
                .volumeSizeGB(20)
                .s3OutputPath("s3://" + S3_BUCKET + "/monitoring/" + scheduleName)
                .hourlySchedule()
                .build();

        System.out.println("==================== 数据质量监控配置 ====================");
        System.out.println("调度名称: " + monitoringConfig.getMonitoringScheduleName());
        System.out.println("端点名称: " + monitoringConfig.getEndpointName());
        System.out.println("监控类型: " + monitoringConfig.getMonitoringType());
        System.out.println("调度表达式: " + monitoringConfig.getScheduleExpression());
        System.out.println("实例类型: " + monitoringConfig.getInstanceType());
        System.out.println("输出路径: " + monitoringConfig.getS3OutputPath());
        System.out.println("========================================================");
    }

    /**
     * 示例：创建每日监控调度
     */
    @Test
    void testCreateDailyMonitoring() {
        String scheduleName = "daily-monitor-" + System.currentTimeMillis();

        MonitoringConfig monitoringConfig = MonitoringConfig.builder()
                .monitoringScheduleName(scheduleName)
                .endpointName(ENDPOINT_NAME)
                .roleArn(ROLE_ARN)
                .s3OutputPath("s3://" + S3_BUCKET + "/monitoring/" + scheduleName)
                .dailySchedule(8)  // 每天早上8点执行
                .baselineConstraintsUri("s3://" + S3_BUCKET + "/baseline/constraints.json")
                .baselineStatisticsUri("s3://" + S3_BUCKET + "/baseline/statistics.json")
                .build();

        System.out.println("==================== 每日监控配置 ====================");
        System.out.println("调度名称: " + monitoringConfig.getMonitoringScheduleName());
        System.out.println("调度表达式: " + monitoringConfig.getScheduleExpression());
        System.out.println("基线约束: " + monitoringConfig.getBaselineConstraintsUri());
        System.out.println("基线统计: " + monitoringConfig.getBaselineStatisticsUri());
        System.out.println("====================================================");
    }
}
