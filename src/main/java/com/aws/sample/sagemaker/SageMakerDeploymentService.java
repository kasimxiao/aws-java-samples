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
 *
 * 提供 SageMaker 模型部署的全生命周期管理，包括模型创建、端点配置、端点管理。
 *
 * 主要功能:
 * - 创建模型（CreateModel API）— 关联推理镜像和模型文件
 * - 创建端点配置（CreateEndpointConfig API）— 定义实例类型、数量、数据捕获
 * - 创建/更新/删除端点（CreateEndpoint / UpdateEndpoint / DeleteEndpoint API）
 * - 一键部署（依次创建模型 → 端点配置 → 端点）
 * - 资源清理（依次删除端点 → 端点配置 → 模型）
 *
 * 端点状态流转:
 * Creating → InService（就绪）/ Failed（失败）
 * Updating → InService（更新完成，蓝绿部署）
 * Deleting → 已删除
 *
 * 使用示例:
 * <pre>
 * EndpointConfig config = EndpointConfig.builder()
 *     .modelName("my-model")
 *     .roleArn("arn:aws:iam::123456789012:role/SageMakerRole")
 *     .inferenceImage("763104351884.dkr.ecr.us-east-1.amazonaws.com/pytorch-inference:2.0.1-cpu-py310")
 *     .modelDataUrl("s3://bucket/output/model.tar.gz")
 *     .instanceType("ml.m5.xlarge")
 *     .build();
 * deploymentService.deployModel(config);
 * </pre>
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
     *
     * 底层调用 SageMaker CreateModel API。
     * 将推理镜像（Docker 容器）和模型文件（S3 上的 model.tar.gz）关联为一个模型资源。
     * 支持通过环境变量向推理容器传递配置参数。
     *
     * @param endpointConfig 端点配置，需包含 modelName、roleArn、inferenceImage、modelDataUrl
     * @return 模型 ARN
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
     *
     * 底层调用 SageMaker CreateEndpointConfig API。
     * 定义生产变体（ProductionVariant），包括模型名称、实例类型、实例数量和流量权重。
     * 可选启用数据捕获（DataCapture），将推理请求和响应保存到 S3，用于模型监控。
     *
     * @param endpointConfig 端点配置，需包含 endpointConfigName、modelName、instanceType 等
     * @return 端点配置 ARN
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
     *
     * 底层调用 SageMaker CreateEndpoint API。
     * 根据端点配置启动推理实例，加载模型并启动推理容器。
     * 创建过程通常需要 5-15 分钟，可通过 waitForEndpoint 等待就绪。
     *
     * @param endpointConfig 端点配置，需包含 endpointName 和 endpointConfigName
     * @return 端点 ARN
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
     *
     * 依次调用 CreateModel → CreateEndpointConfig → CreateEndpoint API。
     * 端点创建后处于 Creating 状态，需调用 waitForEndpoint 等待变为 InService。
     *
     * @param endpointConfig 完整的端点配置
     * @return 端点 ARN
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
     *
     * 底层调用 SageMaker DescribeEndpoint API，仅返回状态枚举。
     * 可能的状态值: Creating、Updating、InService、Deleting、Failed、SystemUpdating、RollingBack
     *
     * @param endpointName 端点名称
     * @return 端点状态枚举
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
     *
     * 底层调用 SageMaker DescribeEndpoint API，返回完整的端点信息，
     * 包括状态、端点配置名称、创建时间、生产变体列表等。
     *
     * @param endpointName 端点名称
     * @return 端点详情响应对象
     */
    public DescribeEndpointResponse describeEndpoint(String endpointName) {
        DescribeEndpointRequest request = DescribeEndpointRequest.builder()
                .endpointName(endpointName)
                .build();
        return sageMakerClient.describeEndpoint(request);
    }

    /**
     * 等待端点就绪
     *
     * 每 30 秒轮询一次 DescribeEndpoint API，直到状态变为 InService 或 Failed，
     * 或超过最大等待时间。端点创建通常需要 5-15 分钟。
     *
     * @param endpointName   端点名称
     * @param maxWaitMinutes 最大等待时间（分钟）
     * @return 端点最终状态
     * @throws InterruptedException 等待过程中线程被中断
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
     *
     * 底层调用 SageMaker UpdateEndpoint API。
     * 使用新的端点配置替换当前配置，SageMaker 会自动执行蓝绿部署：
     * 先启动新实例，健康检查通过后切换流量，最后销毁旧实例。
     * 更新过程中端点保持可用。
     *
     * @param endpointName          端点名称
     * @param newEndpointConfigName 新的端点配置名称
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
     *
     * 底层调用 SageMaker DeleteEndpoint API。
     * 删除后推理实例会被释放，停止计费。不会自动删除关联的端点配置和模型。
     *
     * @param endpointName 端点名称
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
     *
     * 底层调用 SageMaker DeleteEndpointConfig API。
     * 删除前需确保没有端点正在使用该配置。
     *
     * @param endpointConfigName 端点配置名称
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
     *
     * 底层调用 SageMaker DeleteModel API。
     * 仅删除 SageMaker 模型资源，不会删除 S3 上的模型文件（model.tar.gz）。
     *
     * @param modelName 模型名称
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
     *
     * 依次删除端点、端点配置和模型，每步失败不影响后续步骤。
     * 删除端点后等待 5 秒再删除端点配置，确保端点已开始释放。
     *
     * @param endpointName       端点名称
     * @param endpointConfigName 端点配置名称
     * @param modelName          模型名称
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
     *
     * 底层调用 SageMaker ListEndpoints API，按创建时间降序排列。
     *
     * @param nameContains 名称过滤关键字（可为 null，不过滤）
     * @param maxResults   最大返回数量
     * @return 端点摘要列表
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
     *
     * 底层调用 SageMaker ListModels API，按创建时间降序排列。
     *
     * @param nameContains 名称过滤关键字（可为 null，不过滤）
     * @param maxResults   最大返回数量
     * @return 模型摘要列表
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
     * 打印端点详情到控制台
     *
     * 格式化输出端点名称、ARN、状态、配置名称、创建时间、生产变体等信息。
     *
     * @param endpointName 端点名称
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
