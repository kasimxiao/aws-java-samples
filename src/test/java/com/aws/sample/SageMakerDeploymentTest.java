package com.aws.sample;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.sagemaker.SageMakerDeploymentService;
import com.aws.sample.sagemaker.SageMakerImageService;
import com.aws.sample.sagemaker.model.EndpointConfig;

/**
 * SageMaker 部署服务测试示例
 * 
 * 使用前请确保:
 * 1. 配置好 AWS 凭证
 * 2. 已完成模型训练并获得模型文件
 * 3. 创建好 SageMaker 执行角色
 */
public class SageMakerDeploymentTest {

    private AwsConfig config;
    private SageMakerDeploymentService deploymentService;
    private SageMakerImageService imageService;

    // 配置参数 - 请根据实际情况修改
    private static final String ROLE_ARN = "arn:aws:iam::YOUR_ACCOUNT:role/SageMakerExecutionRole";
    private static final String S3_BUCKET = "your-sagemaker-bucket";
    private static final String MODEL_DATA_URL = "s3://your-bucket/sagemaker/output/model.tar.gz";

    @BeforeEach
    void setUp() {
        config = new AwsConfig();
        deploymentService = new SageMakerDeploymentService(config);
        imageService = new SageMakerImageService(config);
    }

    /**
     * 示例：部署 XGBoost 模型
     */
    @Test
    void testDeployXGBoostModel() {
        String modelName = "xgboost-model-" + System.currentTimeMillis();
        
        // 获取 XGBoost 推理镜像
        String xgboostImage = imageService.getXGBoostImage("1.7-1");
        System.out.println("XGBoost 镜像: " + xgboostImage);

        EndpointConfig endpointConfig = EndpointConfig.builder()
                .modelName(modelName)
                .roleArn(ROLE_ARN)
                .inferenceImage(xgboostImage)
                .modelDataUrl(MODEL_DATA_URL)
                .instanceType("ml.m5.xlarge")
                .initialInstanceCount(1)
                .build();

        System.out.println("==================== XGBoost 部署配置 ====================");
        System.out.println("模型名称: " + endpointConfig.getModelName());
        System.out.println("端点配置: " + endpointConfig.getEndpointConfigName());
        System.out.println("端点名称: " + endpointConfig.getEndpointName());
        System.out.println("镜像: " + endpointConfig.getInferenceImage());
        System.out.println("实例类型: " + endpointConfig.getInstanceType());
        System.out.println("实例数量: " + endpointConfig.getInitialInstanceCount());
        System.out.println("========================================================");

        // 实际部署（取消注释以执行）
        // String endpointArn = deploymentService.deployModel(endpointConfig);
        // System.out.println("端点 ARN: " + endpointArn);
    }

    /**
     * 示例：部署 PyTorch 模型
     */
    @Test
    void testDeployPyTorchModel() {
        String modelName = "pytorch-model-" + System.currentTimeMillis();
        
        // 获取 PyTorch 推理镜像
        String pytorchImage = imageService.getPyTorchInferenceImage("2.0.1", "py310", false);
        System.out.println("PyTorch 镜像: " + pytorchImage);

        EndpointConfig endpointConfig = EndpointConfig.builder()
                .modelName(modelName)
                .roleArn(ROLE_ARN)
                .inferenceImage(pytorchImage)
                .modelDataUrl("s3://" + S3_BUCKET + "/pytorch/output/model.tar.gz")
                .instanceType("ml.c5.xlarge")
                .initialInstanceCount(2)
                // 环境变量
                .environment("SAGEMAKER_PROGRAM", "inference.py")
                .environment("SAGEMAKER_SUBMIT_DIRECTORY", "/opt/ml/model/code")
                .build();

        System.out.println("==================== PyTorch 部署配置 ====================");
        System.out.println("模型名称: " + endpointConfig.getModelName());
        System.out.println("镜像: " + endpointConfig.getInferenceImage());
        System.out.println("实例类型: " + endpointConfig.getInstanceType());
        System.out.println("环境变量: " + endpointConfig.getEnvironment());
        System.out.println("=========================================================");
    }

    /**
     * 示例：部署带自动扩缩容的端点
     */
    @Test
    void testDeployWithAutoScaling() {
        String modelName = "autoscaling-model-" + System.currentTimeMillis();
        
        String xgboostImage = imageService.getXGBoostImage("1.7-1");

        EndpointConfig endpointConfig = EndpointConfig.builder()
                .modelName(modelName)
                .roleArn(ROLE_ARN)
                .inferenceImage(xgboostImage)
                .modelDataUrl(MODEL_DATA_URL)
                .instanceType("ml.m5.xlarge")
                .initialInstanceCount(2)
                // 自动扩缩容配置
                .autoScalingConfig(1, 10, 1000)  // 最小1，最大10，目标1000次调用/实例
                .build();

        System.out.println("==================== 自动扩缩容配置 ====================");
        System.out.println("模型名称: " + endpointConfig.getModelName());
        System.out.println("初始实例数: " + endpointConfig.getInitialInstanceCount());
        System.out.println("最小容量: " + endpointConfig.getMinCapacity());
        System.out.println("最大容量: " + endpointConfig.getMaxCapacity());
        System.out.println("目标调用数/实例: " + endpointConfig.getTargetInvocationsPerInstance());
        System.out.println("======================================================");
    }

    /**
     * 示例：部署带数据捕获的端点（用于模型监控）
     */
    @Test
    void testDeployWithDataCapture() {
        String modelName = "datacapture-model-" + System.currentTimeMillis();
        
        String xgboostImage = imageService.getXGBoostImage("1.7-1");

        EndpointConfig endpointConfig = EndpointConfig.builder()
                .modelName(modelName)
                .roleArn(ROLE_ARN)
                .inferenceImage(xgboostImage)
                .modelDataUrl(MODEL_DATA_URL)
                .instanceType("ml.m5.xlarge")
                .initialInstanceCount(1)
                // 数据捕获配置（用于模型监控）
                .enableDataCapture(
                        "s3://" + S3_BUCKET + "/datacapture/" + modelName,
                        100  // 捕获 100% 的请求
                )
                .build();

        System.out.println("==================== 数据捕获配置 ====================");
        System.out.println("模型名称: " + endpointConfig.getModelName());
        System.out.println("数据捕获启用: " + endpointConfig.isEnableDataCapture());
        System.out.println("捕获 S3 路径: " + endpointConfig.getDataCaptureS3Uri());
        System.out.println("捕获百分比: " + endpointConfig.getDataCapturePercentage() + "%");
        System.out.println("====================================================");
    }

    /**
     * 示例：部署 HuggingFace 模型
     */
    @Test
    void testDeployHuggingFaceModel() {
        String modelName = "huggingface-model-" + System.currentTimeMillis();
        
        // 获取 HuggingFace 推理镜像
        String hfImage = imageService.getHuggingFaceInferenceImage("4.28.1", "2.0.1", "py310", true);
        System.out.println("HuggingFace 镜像: " + hfImage);

        EndpointConfig endpointConfig = EndpointConfig.builder()
                .modelName(modelName)
                .roleArn(ROLE_ARN)
                .inferenceImage(hfImage)
                .modelDataUrl("s3://" + S3_BUCKET + "/huggingface/output/model.tar.gz")
                .instanceType("ml.g4dn.xlarge")  // GPU 实例
                .initialInstanceCount(1)
                // HuggingFace 环境变量
                .environment("HF_MODEL_ID", "bert-base-uncased")
                .environment("HF_TASK", "text-classification")
                .build();

        System.out.println("==================== HuggingFace 部署配置 ====================");
        System.out.println("模型名称: " + endpointConfig.getModelName());
        System.out.println("镜像: " + endpointConfig.getInferenceImage());
        System.out.println("实例类型: " + endpointConfig.getInstanceType());
        System.out.println("环境变量: " + endpointConfig.getEnvironment());
        System.out.println("=============================================================");
    }

    /**
     * 示例：列出端点
     */
    @Test
    void testListEndpoints() {
        var endpoints = deploymentService.listEndpoints(null, 10);
        System.out.println("==================== 端点列表 ====================");
        for (var endpoint : endpoints) {
            System.out.println("端点名称: " + endpoint.endpointName());
            System.out.println("  状态: " + endpoint.endpointStatus());
            System.out.println("  创建时间: " + endpoint.creationTime());
            System.out.println();
        }
        System.out.println("================================================");
    }

    /**
     * 示例：列出模型
     */
    @Test
    void testListModels() {
        var models = deploymentService.listModels(null, 10);
        System.out.println("==================== 模型列表 ====================");
        for (var model : models) {
            System.out.println("模型名称: " + model.modelName());
            System.out.println("  ARN: " + model.modelArn());
            System.out.println("  创建时间: " + model.creationTime());
            System.out.println();
        }
        System.out.println("================================================");
    }
}
