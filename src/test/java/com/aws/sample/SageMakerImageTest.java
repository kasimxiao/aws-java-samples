package com.aws.sample;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.sagemaker.SageMakerImageService;

/**
 * SageMaker 镜像服务测试
 */
public class SageMakerImageTest {

    public static void main(String[] args) {
        System.out.println("开始 SageMaker 镜像服务测试...\n");

        AwsConfig config = new AwsConfig();
        try (SageMakerImageService imageService = new SageMakerImageService(config)) {

            // 打印常用镜像信息
            imageService.printAvailableImages();

            // 统一查询接口测试
            System.out.println("\n===== 统一查询接口 getImageUri =====");
            System.out.println("PyTorch 训练 GPU: " + imageService.getImageUri("pytorch", "training", "2.0.1", "py310", true));
            System.out.println("PyTorch 推理 CPU: " + imageService.getImageUri("pytorch", "inference", "2.0.1", "py310", false));
            System.out.println("TensorFlow 推理 CPU: " + imageService.getImageUri("tensorflow", "inference", "2.13.0", "py310", false));
            System.out.println("HuggingFace 训练: " + imageService.getImageUri("huggingface", "training", "4.28.1:2.0.0", "py310", true));
            System.out.println("XGBoost: " + imageService.getImageUri("xgboost", "training", "1.7-1", null, false));
            System.out.println("Sklearn: " + imageService.getImageUri("sklearn", "training", "1.2-1", "py3", false));
            System.out.println("MXNet 推理: " + imageService.getImageUri("mxnet", "inference", "1.9.0", "py38", false));
            System.out.println("内置算法 BlazingText: " + imageService.getBuiltInAlgorithmImage("blazingtext", "1"));

            // ECR 动态查询框架版本
            System.out.println("\n===== ECR 动态查询框架版本 =====");
            System.out.println("\nPyTorch 训练可用版本:");
            System.out.println(imageService.listFrameworkVersions("pytorch", "training"));

            System.out.println("\nXGBoost 可用版本:");
            System.out.println(imageService.listFrameworkVersions("xgboost", "training"));

            // 打印所有框架的可用版本
            System.out.println();
            imageService.printSupportedFrameworks();
        }

        System.out.println("\nSageMaker 镜像服务测试完成！");
    }
}
