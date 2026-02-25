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
        SageMakerImageService imageService = new SageMakerImageService(config);

        // 打印所有可用镜像
        imageService.printAvailableImages();

        // 验证各框架镜像 URI
        System.out.println("\n===== PyTorch =====");
        System.out.println("训练 GPU: " + imageService.getPyTorchTrainingImage("2.0.1", "py310", true));
        System.out.println("训练 CPU: " + imageService.getPyTorchTrainingImage("2.0.1", "py310", false));
        System.out.println("推理 GPU: " + imageService.getPyTorchInferenceImage("2.0.1", "py310", true));
        System.out.println("推理 CPU: " + imageService.getPyTorchInferenceImage("2.0.1", "py310", false));

        System.out.println("\n===== TensorFlow =====");
        System.out.println("训练 GPU: " + imageService.getTensorFlowTrainingImage("2.13.0", "py310", true));
        System.out.println("推理 CPU: " + imageService.getTensorFlowInferenceImage("2.13.0", "py310", false));

        System.out.println("\n===== HuggingFace =====");
        System.out.println("训练: " + imageService.getHuggingFaceTrainingImage("4.28.1", "2.0.0", "py310", true));
        System.out.println("推理: " + imageService.getHuggingFaceInferenceImage("4.28.1", "2.0.0", "py310", false));

        System.out.println("\n===== 其他框架 =====");
        System.out.println("XGBoost: " + imageService.getXGBoostImage("1.7-1"));
        System.out.println("Sklearn: " + imageService.getSklearnImage("1.2-1", "py3"));

        System.out.println("\n===== 内置算法 =====");
        System.out.println("BlazingText: " + imageService.getBuiltInAlgorithmImage("blazingtext", "1"));
        System.out.println("Image Classification: " + imageService.getBuiltInAlgorithmImage("image-classification", "1"));

        System.out.println("\nSageMaker 镜像服务测试完成！");
    }
}
