package com.aws.sample.sagemaker.model;

import java.util.HashMap;
import java.util.Map;

/**
 * SageMaker 训练任务配置
 *
 * 封装 CreateTrainingJob API 所需的全部参数，使用 Builder 模式构建。
 *
 * 必填参数:
 * - jobName: 训练作业名称（全局唯一）
 * - roleArn: SageMaker 执行角色 ARN（需有 S3、ECR、CloudWatch 等权限）
 * - trainingImage: 训练容器镜像 URI（可通过 SageMakerImageService 获取）
 *
 * 默认值:
 * - instanceType: ml.m5.xlarge
 * - instanceCount: 1
 * - volumeSizeGB: 50 GB
 * - maxRuntimeSeconds: 86400（24 小时）
 * - inputContentType: text/csv
 */
public class TrainingJobConfig {
    
    private String jobName;
    private String roleArn;
    private String trainingImage;
    private String instanceType;
    private int instanceCount;
    private int volumeSizeGB;
    private int maxRuntimeSeconds;
    
    // 输入数据配置
    private String s3TrainDataUri;
    private String s3ValidationDataUri;
    private String inputContentType;
    
    // 输出配置
    private String s3OutputPath;
    
    // 超参数
    private Map<String, String> hyperParameters;
    
    // VPC 配置（可选）
    private String subnetId;
    private String securityGroupId;
    
    public TrainingJobConfig() {
        this.instanceType = "ml.m5.xlarge";
        this.instanceCount = 1;
        this.volumeSizeGB = 50;
        this.maxRuntimeSeconds = 86400; // 24小时
        this.inputContentType = "text/csv";
        this.hyperParameters = new HashMap<>();
    }
    
    // Builder 模式
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final TrainingJobConfig config = new TrainingJobConfig();
        
        public Builder jobName(String jobName) {
            config.jobName = jobName;
            return this;
        }
        
        public Builder roleArn(String roleArn) {
            config.roleArn = roleArn;
            return this;
        }
        
        public Builder trainingImage(String trainingImage) {
            config.trainingImage = trainingImage;
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
        
        public Builder maxRuntimeSeconds(int maxRuntimeSeconds) {
            config.maxRuntimeSeconds = maxRuntimeSeconds;
            return this;
        }
        
        public Builder s3TrainDataUri(String s3TrainDataUri) {
            config.s3TrainDataUri = s3TrainDataUri;
            return this;
        }
        
        public Builder s3ValidationDataUri(String s3ValidationDataUri) {
            config.s3ValidationDataUri = s3ValidationDataUri;
            return this;
        }
        
        public Builder inputContentType(String inputContentType) {
            config.inputContentType = inputContentType;
            return this;
        }
        
        public Builder s3OutputPath(String s3OutputPath) {
            config.s3OutputPath = s3OutputPath;
            return this;
        }
        
        public Builder hyperParameter(String key, String value) {
            config.hyperParameters.put(key, value);
            return this;
        }
        
        public Builder hyperParameters(Map<String, String> hyperParameters) {
            config.hyperParameters.putAll(hyperParameters);
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
        
        public TrainingJobConfig build() {
            if (config.jobName == null || config.jobName.isEmpty()) {
                throw new IllegalArgumentException("jobName 不能为空");
            }
            if (config.roleArn == null || config.roleArn.isEmpty()) {
                throw new IllegalArgumentException("roleArn 不能为空");
            }
            if (config.trainingImage == null || config.trainingImage.isEmpty()) {
                throw new IllegalArgumentException("trainingImage 不能为空");
            }
            return config;
        }
    }
    
    // Getters
    public String getJobName() { return jobName; }
    public String getRoleArn() { return roleArn; }
    public String getTrainingImage() { return trainingImage; }
    public String getInstanceType() { return instanceType; }
    public int getInstanceCount() { return instanceCount; }
    public int getVolumeSizeGB() { return volumeSizeGB; }
    public int getMaxRuntimeSeconds() { return maxRuntimeSeconds; }
    public String getS3TrainDataUri() { return s3TrainDataUri; }
    public String getS3ValidationDataUri() { return s3ValidationDataUri; }
    public String getInputContentType() { return inputContentType; }
    public String getS3OutputPath() { return s3OutputPath; }
    public Map<String, String> getHyperParameters() { return hyperParameters; }
    public String getSubnetId() { return subnetId; }
    public String getSecurityGroupId() { return securityGroupId; }
}
