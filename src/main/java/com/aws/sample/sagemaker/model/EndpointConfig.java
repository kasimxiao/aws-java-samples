package com.aws.sample.sagemaker.model;

import java.util.HashMap;
import java.util.Map;

/**
 * SageMaker 端点配置
 *
 * 封装 CreateModel / CreateEndpointConfig / CreateEndpoint API 所需的全部参数，
 * 使用 Builder 模式构建。
 *
 * 必填参数:
 * - modelName: 模型名称
 *
 * 默认值:
 * - endpointConfigName: {modelName}-config
 * - endpointName: {modelName}-endpoint
 * - instanceType: ml.m5.xlarge
 * - initialInstanceCount: 1
 * - enableAutoScaling: false
 * - enableDataCapture: false
 * - dataCapturePercentage: 100（启用数据捕获时的采样率）
 *
 * 可选功能:
 * - 自动扩缩容: enableAutoScaling + minCapacity/maxCapacity/targetInvocationsPerInstance
 * - 数据捕获: enableDataCapture + dataCaptureS3Uri + dataCapturePercentage
 * - 环境变量: 传递给推理容器的配置参数
 */
public class EndpointConfig {
    
    private String modelName;
    private String endpointConfigName;
    private String endpointName;
    private String roleArn;
    private String inferenceImage;
    private String modelDataUrl;
    private String instanceType;
    private int initialInstanceCount;
    
    // 自动扩缩容配置
    private boolean enableAutoScaling;
    private int minCapacity;
    private int maxCapacity;
    private int targetInvocationsPerInstance;
    
    // 数据捕获配置（用于模型监控）
    private boolean enableDataCapture;
    private String dataCaptureS3Uri;
    private int dataCapturePercentage;
    
    // 环境变量
    private Map<String, String> environment;
    
    public EndpointConfig() {
        this.instanceType = "ml.m5.xlarge";
        this.initialInstanceCount = 1;
        this.enableAutoScaling = false;
        this.minCapacity = 1;
        this.maxCapacity = 4;
        this.targetInvocationsPerInstance = 1000;
        this.enableDataCapture = false;
        this.dataCapturePercentage = 100;
        this.environment = new HashMap<>();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final EndpointConfig config = new EndpointConfig();
        
        public Builder modelName(String modelName) {
            config.modelName = modelName;
            return this;
        }
        
        public Builder endpointConfigName(String endpointConfigName) {
            config.endpointConfigName = endpointConfigName;
            return this;
        }
        
        public Builder endpointName(String endpointName) {
            config.endpointName = endpointName;
            return this;
        }
        
        public Builder roleArn(String roleArn) {
            config.roleArn = roleArn;
            return this;
        }
        
        public Builder inferenceImage(String inferenceImage) {
            config.inferenceImage = inferenceImage;
            return this;
        }
        
        public Builder modelDataUrl(String modelDataUrl) {
            config.modelDataUrl = modelDataUrl;
            return this;
        }
        
        public Builder instanceType(String instanceType) {
            config.instanceType = instanceType;
            return this;
        }
        
        public Builder initialInstanceCount(int initialInstanceCount) {
            config.initialInstanceCount = initialInstanceCount;
            return this;
        }
        
        public Builder enableAutoScaling(boolean enable) {
            config.enableAutoScaling = enable;
            return this;
        }
        
        public Builder autoScalingConfig(int minCapacity, int maxCapacity, int targetInvocations) {
            config.enableAutoScaling = true;
            config.minCapacity = minCapacity;
            config.maxCapacity = maxCapacity;
            config.targetInvocationsPerInstance = targetInvocations;
            return this;
        }
        
        public Builder enableDataCapture(String s3Uri, int percentage) {
            config.enableDataCapture = true;
            config.dataCaptureS3Uri = s3Uri;
            config.dataCapturePercentage = percentage;
            return this;
        }
        
        public Builder environment(String key, String value) {
            config.environment.put(key, value);
            return this;
        }
        
        public EndpointConfig build() {
            if (config.modelName == null) {
                throw new IllegalArgumentException("modelName 不能为空");
            }
            if (config.endpointConfigName == null) {
                config.endpointConfigName = config.modelName + "-config";
            }
            if (config.endpointName == null) {
                config.endpointName = config.modelName + "-endpoint";
            }
            return config;
        }
    }
    
    // Getters
    public String getModelName() { return modelName; }
    public String getEndpointConfigName() { return endpointConfigName; }
    public String getEndpointName() { return endpointName; }
    public String getRoleArn() { return roleArn; }
    public String getInferenceImage() { return inferenceImage; }
    public String getModelDataUrl() { return modelDataUrl; }
    public String getInstanceType() { return instanceType; }
    public int getInitialInstanceCount() { return initialInstanceCount; }
    public boolean isEnableAutoScaling() { return enableAutoScaling; }
    public int getMinCapacity() { return minCapacity; }
    public int getMaxCapacity() { return maxCapacity; }
    public int getTargetInvocationsPerInstance() { return targetInvocationsPerInstance; }
    public boolean isEnableDataCapture() { return enableDataCapture; }
    public String getDataCaptureS3Uri() { return dataCaptureS3Uri; }
    public int getDataCapturePercentage() { return dataCapturePercentage; }
    public Map<String, String> getEnvironment() { return environment; }
}
