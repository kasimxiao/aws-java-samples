package com.aws.sample.sagemaker.model;

/**
 * SageMaker 模型监控配置
 */
public class MonitoringConfig {
    
    public enum MonitoringType {
        DATA_QUALITY,      // 数据质量监控
        MODEL_QUALITY,     // 模型质量监控
        MODEL_BIAS,        // 模型偏差监控
        MODEL_EXPLAINABILITY // 模型可解释性监控
    }
    
    private String monitoringScheduleName;
    private String endpointName;
    private MonitoringType monitoringType;
    private String roleArn;
    private String instanceType;
    private int instanceCount;
    private int volumeSizeGB;
    
    // 基线配置
    private String baselineDatasetUri;
    private String baselineConstraintsUri;
    private String baselineStatisticsUri;
    
    // 输出配置
    private String s3OutputPath;
    
    // 调度配置
    private String scheduleExpression; // cron 表达式
    
    // 网络配置
    private String subnetId;
    private String securityGroupId;
    
    public MonitoringConfig() {
        this.monitoringType = MonitoringType.DATA_QUALITY;
        this.instanceType = "ml.m5.xlarge";
        this.instanceCount = 1;
        this.volumeSizeGB = 20;
        this.scheduleExpression = "cron(0 * ? * * *)"; // 每小时执行
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final MonitoringConfig config = new MonitoringConfig();
        
        public Builder monitoringScheduleName(String name) {
            config.monitoringScheduleName = name;
            return this;
        }
        
        public Builder endpointName(String endpointName) {
            config.endpointName = endpointName;
            return this;
        }
        
        public Builder monitoringType(MonitoringType type) {
            config.monitoringType = type;
            return this;
        }
        
        public Builder roleArn(String roleArn) {
            config.roleArn = roleArn;
            return this;
        }
        
        public Builder instanceType(String instanceType) {
            config.instanceType = instanceType;
            return this;
        }
        
        public Builder instanceCount(int instanceCount) {
            config.instanceCount = instanceCount;
            return this;
        }
        
        public Builder volumeSizeGB(int volumeSizeGB) {
            config.volumeSizeGB = volumeSizeGB;
            return this;
        }
        
        public Builder baselineDatasetUri(String uri) {
            config.baselineDatasetUri = uri;
            return this;
        }
        
        public Builder baselineConstraintsUri(String uri) {
            config.baselineConstraintsUri = uri;
            return this;
        }
        
        public Builder baselineStatisticsUri(String uri) {
            config.baselineStatisticsUri = uri;
            return this;
        }
        
        public Builder s3OutputPath(String s3OutputPath) {
            config.s3OutputPath = s3OutputPath;
            return this;
        }
        
        public Builder scheduleExpression(String scheduleExpression) {
            config.scheduleExpression = scheduleExpression;
            return this;
        }
        
        public Builder hourlySchedule() {
            config.scheduleExpression = "cron(0 * ? * * *)";
            return this;
        }
        
        public Builder dailySchedule(int hour) {
            config.scheduleExpression = String.format("cron(0 %d ? * * *)", hour);
            return this;
        }
        
        public Builder subnetId(String subnetId) {
            config.subnetId = subnetId;
            return this;
        }
        
        public Builder securityGroupId(String securityGroupId) {
            config.securityGroupId = securityGroupId;
            return this;
        }
        
        public MonitoringConfig build() {
            if (config.monitoringScheduleName == null) {
                throw new IllegalArgumentException("monitoringScheduleName 不能为空");
            }
            if (config.endpointName == null) {
                throw new IllegalArgumentException("endpointName 不能为空");
            }
            return config;
        }
    }
    
    // Getters
    public String getMonitoringScheduleName() { return monitoringScheduleName; }
    public String getEndpointName() { return endpointName; }
    public MonitoringType getMonitoringType() { return monitoringType; }
    public String getRoleArn() { return roleArn; }
    public String getInstanceType() { return instanceType; }
    public int getInstanceCount() { return instanceCount; }
    public int getVolumeSizeGB() { return volumeSizeGB; }
    public String getBaselineDatasetUri() { return baselineDatasetUri; }
    public String getBaselineConstraintsUri() { return baselineConstraintsUri; }
    public String getBaselineStatisticsUri() { return baselineStatisticsUri; }
    public String getS3OutputPath() { return s3OutputPath; }
    public String getScheduleExpression() { return scheduleExpression; }
    public String getSubnetId() { return subnetId; }
    public String getSecurityGroupId() { return securityGroupId; }
}
