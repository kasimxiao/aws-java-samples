package com.aws.sample.sagemaker;

import java.util.List;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.sagemaker.model.EndpointConfig;

import software.amazon.awssdk.services.sagemaker.SageMakerClient;
import software.amazon.awssdk.services.sagemaker.model.CaptureContentTypeHeader;
import software.amazon.awssdk.services.sagemaker.model.CaptureMode;
import software.amazon.awssdk.services.sagemaker.model.CaptureOption;
import software.amazon.awssdk.services.sagemaker.model.ContainerDefinition;
import software.amazon.awssdk.services.sagemaker.model.CreateEndpointConfigRequest;
import software.amazon.awssdk.services.sagemaker.model.CreateEndpointConfigResponse;
import software.amazon.awssdk.services.sagemaker.model.CreateEndpointRequest;
import software.amazon.awssdk.services.sagemaker.model.CreateEndpointResponse;
import software.amazon.awssdk.services.sagemaker.model.CreateModelRequest;
import software.amazon.awssdk.services.sagemaker.model.CreateModelResponse;
import software.amazon.awssdk.services.sagemaker.model.DataCaptureConfig;
import software.amazon.awssdk.services.sagemaker.model.DeleteEndpointConfigRequest;
import software.amazon.awssdk.services.sagemaker.model.DeleteEndpointRequest;
import software.amazon.awssdk.services.sagemaker.model.DeleteModelRequest;
import software.amazon.awssdk.services.sagemaker.model.DescribeEndpointRequest;
import software.amazon.awssdk.services.sagemaker.model.DescribeEndpointResponse;
import software.amazon.awssdk.services.sagemaker.model.EndpointSortKey;
import software.amazon.awssdk.services.sagemaker.model.EndpointStatus;
import software.amazon.awssdk.services.sagemaker.model.EndpointSummary;
import software.amazon.awssdk.services.sagemaker.model.ListEndpointsRequest;
import software.amazon.awssdk.services.sagemaker.model.ListEndpointsResponse;
import software.amazon.awssdk.services.sagemaker.model.ListModelsRequest;
import software.amazon.awssdk.services.sagemaker.model.ListModelsResponse;
import software.amazon.awssdk.services.sagemaker.model.ModelSortKey;
import software.amazon.awssdk.services.sagemaker.model.ModelSummary;
import software.amazon.awssdk.services.sagemaker.model.OrderKey;
import software.amazon.awssdk.services.sagemaker.model.ProductionVariant;
import software.amazon.awssdk.services.sagemaker.model.ProductionVariantInstanceType;
import software.amazon.awssdk.services.sagemaker.model.UpdateEndpointRequest;

/**
 * SageMaker 部署服务
 * 提供模型部署、端点管理和推理功能
 */
public class SageMakerDeploymentService {

    private final SageMakerClient sageMakerClient;
    private final AwsConfig config;

    public SageMakerDeploymentService(AwsConfig config) {
        this.config = config;
        this.sageMakerClient = SageMakerClient.builder()
                .region(config.getRegion())
                .credentialsProvider(config.getCredentialsProvider())
                .build();
    }

    /**
     * 创建 SageMaker 模型
     */
    public String createModel(EndpointConfig endpointConfig) {
        System.out.println("创建模型: " + endpointConfig.getModelName());

        // 构建容器定义
        ContainerDefinition.Builder containerBuilder = ContainerDefinition.builder()
                .image(endpointConfig.getInferenceImage())
                .modelDataUrl(endpointConfig.getModelDataUrl());

        if (!endpointConfig.getEnvironment().isEmpty()) {
            containerBuilder.environment(endpointConfig.getEnvironment());
        }

        CreateModelRequest request = CreateModelRequest.builder()
                .modelName(endpointConfig.getModelName())
                .executionRoleArn(endpointConfig.getRoleArn())
                .primaryContainer(containerBuilder.build())
                .build();

        CreateModelResponse response = sageMakerClient.createModel(request);
        System.out.println("模型已创建: " + response.modelArn());
        return response.modelArn();
    }

    /**
     * 创建端点配置
     */
    public String createEndpointConfig(EndpointConfig endpointConfig) {
        System.out.println("创建端点配置: " + endpointConfig.getEndpointConfigName());

        // 构建生产变体
        ProductionVariant.Builder variantBuilder = ProductionVariant.builder()
                .variantName("AllTraffic")
                .modelName(endpointConfig.getModelName())
                .instanceType(ProductionVariantInstanceType.fromValue(endpointConfig.getInstanceType()))
                .initialInstanceCount(endpointConfig.getInitialInstanceCount())
                .initialVariantWeight(1.0f);

        CreateEndpointConfigRequest.Builder requestBuilder = CreateEndpointConfigRequest.builder()
                .endpointConfigName(endpointConfig.getEndpointConfigName())
                .productionVariants(variantBuilder.build());

        // 数据捕获配置（用于模型监控）
        if (endpointConfig.isEnableDataCapture()) {
            DataCaptureConfig dataCaptureConfig = DataCaptureConfig.builder()
                    .enableCapture(true)
                    .initialSamplingPercentage(endpointConfig.getDataCapturePercentage())
                    .destinationS3Uri(endpointConfig.getDataCaptureS3Uri())
                    .captureOptions(
                            CaptureOption.builder().captureMode(CaptureMode.INPUT).build(),
                            CaptureOption.builder().captureMode(CaptureMode.OUTPUT).build()
                    )
                    .captureContentTypeHeader(CaptureContentTypeHeader.builder()
                            .csvContentTypes("text/csv")
                            .jsonContentTypes("application/json")
                            .build())
                    .build();
            requestBuilder.dataCaptureConfig(dataCaptureConfig);
        }

        CreateEndpointConfigResponse response = sageMakerClient.createEndpointConfig(requestBuilder.build());
        System.out.println("端点配置已创建: " + response.endpointConfigArn());
        return response.endpointConfigArn();
    }

    /**
     * 创建端点
     */
    public String createEndpoint(EndpointConfig endpointConfig) {
        System.out.println("创建端点: " + endpointConfig.getEndpointName());

        CreateEndpointRequest request = CreateEndpointRequest.builder()
                .endpointName(endpointConfig.getEndpointName())
                .endpointConfigName(endpointConfig.getEndpointConfigName())
                .build();

        CreateEndpointResponse response = sageMakerClient.createEndpoint(request);
        System.out.println("端点创建中: " + response.endpointArn());
        return response.endpointArn();
    }

    /**
     * 一键部署模型（创建模型 + 端点配置 + 端点）
     */
    public String deployModel(EndpointConfig endpointConfig) {
        System.out.println("开始部署模型...");
        
        // 1. 创建模型
        createModel(endpointConfig);
        
        // 2. 创建端点配置
        createEndpointConfig(endpointConfig);
        
        // 3. 创建端点
        String endpointArn = createEndpoint(endpointConfig);
        
        System.out.println("部署已启动，端点正在创建中...");
        return endpointArn;
    }

    /**
     * 获取端点状态
     */
    public EndpointStatus getEndpointStatus(String endpointName) {
        DescribeEndpointRequest request = DescribeEndpointRequest.builder()
                .endpointName(endpointName)
                .build();
        DescribeEndpointResponse response = sageMakerClient.describeEndpoint(request);
        return response.endpointStatus();
    }

    /**
     * 获取端点详情
     */
    public DescribeEndpointResponse describeEndpoint(String endpointName) {
        DescribeEndpointRequest request = DescribeEndpointRequest.builder()
                .endpointName(endpointName)
                .build();
        return sageMakerClient.describeEndpoint(request);
    }

    /**
     * 等待端点就绪
     */
    public EndpointStatus waitForEndpoint(String endpointName, int maxWaitMinutes) 
            throws InterruptedException {
        System.out.println("等待端点就绪: " + endpointName);
        long startTime = System.currentTimeMillis();
        long maxWaitMs = maxWaitMinutes * 60 * 1000L;

        while (true) {
            EndpointStatus status = getEndpointStatus(endpointName);
            System.out.println("当前状态: " + status);

            if (status == EndpointStatus.IN_SERVICE ||
                status == EndpointStatus.FAILED) {
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
     * 更新端点（蓝绿部署）
     */
    public void updateEndpoint(String endpointName, String newEndpointConfigName) {
        System.out.println("更新端点: " + endpointName + " -> " + newEndpointConfigName);

        UpdateEndpointRequest request = UpdateEndpointRequest.builder()
                .endpointName(endpointName)
                .endpointConfigName(newEndpointConfigName)
                .build();

        sageMakerClient.updateEndpoint(request);
        System.out.println("端点更新已启动");
    }

    /**
     * 删除端点
     */
    public void deleteEndpoint(String endpointName) {
        System.out.println("删除端点: " + endpointName);
        DeleteEndpointRequest request = DeleteEndpointRequest.builder()
                .endpointName(endpointName)
                .build();
        sageMakerClient.deleteEndpoint(request);
        System.out.println("端点已删除");
    }

    /**
     * 删除端点配置
     */
    public void deleteEndpointConfig(String endpointConfigName) {
        System.out.println("删除端点配置: " + endpointConfigName);
        DeleteEndpointConfigRequest request = DeleteEndpointConfigRequest.builder()
                .endpointConfigName(endpointConfigName)
                .build();
        sageMakerClient.deleteEndpointConfig(request);
        System.out.println("端点配置已删除");
    }

    /**
     * 删除模型
     */
    public void deleteModel(String modelName) {
        System.out.println("删除模型: " + modelName);
        DeleteModelRequest request = DeleteModelRequest.builder()
                .modelName(modelName)
                .build();
        sageMakerClient.deleteModel(request);
        System.out.println("模型已删除");
    }

    /**
     * 清理部署资源（端点 + 端点配置 + 模型）
     */
    public void cleanupDeployment(String endpointName, String endpointConfigName, String modelName) {
        System.out.println("清理部署资源...");
        try {
            deleteEndpoint(endpointName);
            Thread.sleep(5000);
        } catch (Exception e) {
            System.out.println("删除端点失败: " + e.getMessage());
        }
        
        try {
            deleteEndpointConfig(endpointConfigName);
        } catch (Exception e) {
            System.out.println("删除端点配置失败: " + e.getMessage());
        }
        
        try {
            deleteModel(modelName);
        } catch (Exception e) {
            System.out.println("删除模型失败: " + e.getMessage());
        }
        System.out.println("清理完成");
    }

    /**
     * 列出端点
     */
    public List<EndpointSummary> listEndpoints(String nameContains, int maxResults) {
        ListEndpointsRequest.Builder requestBuilder = ListEndpointsRequest.builder()
                .maxResults(maxResults)
                .sortBy(EndpointSortKey.CREATION_TIME)
                .sortOrder(OrderKey.DESCENDING);

        if (nameContains != null && !nameContains.isEmpty()) {
            requestBuilder.nameContains(nameContains);
        }

        ListEndpointsResponse response = sageMakerClient.listEndpoints(requestBuilder.build());
        return response.endpoints();
    }

    /**
     * 列出模型
     */
    public List<ModelSummary> listModels(String nameContains, int maxResults) {
        ListModelsRequest.Builder requestBuilder = ListModelsRequest.builder()
                .maxResults(maxResults)
                .sortBy(ModelSortKey.CREATION_TIME)
                .sortOrder(OrderKey.DESCENDING);

        if (nameContains != null && !nameContains.isEmpty()) {
            requestBuilder.nameContains(nameContains);
        }

        ListModelsResponse response = sageMakerClient.listModels(requestBuilder.build());
        return response.models();
    }

    /**
     * 打印端点详情
     */
    public void printEndpointDetails(String endpointName) {
        DescribeEndpointResponse endpoint = describeEndpoint(endpointName);
        System.out.println("==================== 端点详情 ====================");
        System.out.println("端点名称: " + endpoint.endpointName());
        System.out.println("端点 ARN: " + endpoint.endpointArn());
        System.out.println("状态: " + endpoint.endpointStatus());
        System.out.println("端点配置: " + endpoint.endpointConfigName());
        System.out.println("创建时间: " + endpoint.creationTime());
        System.out.println("最后修改: " + endpoint.lastModifiedTime());
        
        if (endpoint.hasProductionVariants()) {
            System.out.println("生产变体:");
            for (var variant : endpoint.productionVariants()) {
                System.out.println("  - " + variant.variantName() + 
                        " (实例数: " + variant.currentInstanceCount() + ")");
            }
        }
        System.out.println("================================================");
    }

    public void close() {
        sageMakerClient.close();
    }
}
