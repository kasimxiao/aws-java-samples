package com.aws.sample.sagemaker;

import java.nio.charset.StandardCharsets;

import com.aws.sample.common.AwsConfig;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;

/**
 * SageMaker 端点推理服务
 *
 * 提供 SageMaker 实时推理端点的调用功能，底层调用 SageMaker Runtime InvokeEndpoint API。
 *
 * 主要功能:
 * - JSON 格式推理调用（适用于自定义模型、HuggingFace 模型等）
 * - CSV 格式推理调用（适用于 XGBoost、线性学习器等内置算法）
 * - 指定变体推理调用（用于 A/B 测试场景）
 *
 * 使用前提:
 * - 端点必须处于 InService 状态
 * - 请求格式（ContentType）必须与模型推理脚本的 input_fn 实现匹配
 * - 响应格式（Accept）必须与模型推理脚本的 output_fn 实现匹配
 *
 * 使用示例:
 * <pre>
 * AwsConfig config = new AwsConfig();
 * SageMakerInferenceService inferenceService = new SageMakerInferenceService(config);
 *
 * // JSON 格式推理
 * String result = inferenceService.invokeEndpointJson("my-endpoint",
 *     "{\"instances\": [[1.0, 2.0, 3.0]]}");
 *
 * // CSV 格式推理
 * String csvResult = inferenceService.invokeEndpointCsv("my-endpoint", "1.0,2.0,3.0");
 * </pre>
 */
public class SageMakerInferenceService {

    private final SageMakerRuntimeClient runtimeClient;

    public SageMakerInferenceService(AwsConfig config) {
        this.runtimeClient = SageMakerRuntimeClient.builder()
                .region(config.getRegion())
                .credentialsProvider(config.getCredentialsProvider())
                .build();
    }

    /**
     * 调用端点进行 JSON 格式推理
     *
     * 底层调用 SageMaker Runtime InvokeEndpoint API。
     * ContentType 和 Accept 均设置为 application/json。
     * 请求体格式取决于模型推理脚本中 input_fn 的实现，
     * 响应体格式取决于 output_fn 的实现。
     *
     * 常见请求格式:
     * - PyTorch/TensorFlow: {"instances": [[1.0, 2.0, 3.0]]}
     * - HuggingFace: {"inputs": "text to classify"}
     *
     * @param endpointName 端点名称，端点必须处于 InService 状态
     * @param jsonPayload  JSON 格式的请求体，具体格式取决于部署的模型
     * @return 推理结果（JSON 字符串），格式取决于模型的 output_fn 实现
     */
    public String invokeEndpointJson(String endpointName, String jsonPayload) {
        System.out.println("调用端点: " + endpointName);

        InvokeEndpointRequest request = InvokeEndpointRequest.builder()
                .endpointName(endpointName)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        InvokeEndpointResponse response = runtimeClient.invokeEndpoint(request);
        String result = response.body().asString(StandardCharsets.UTF_8);
        System.out.println("推理完成，响应长度: " + result.length());
        return result;
    }

    /**
     * 调用端点进行 CSV 格式推理
     *
     * 底层调用 SageMaker Runtime InvokeEndpoint API。
     * ContentType 和 Accept 均设置为 text/csv。
     * 适用于 SageMaker 内置算法（如 XGBoost、线性学习器、KNN 等），
     * CSV 格式不包含表头，特征值用逗号分隔，每行一个样本。
     *
     * @param endpointName 端点名称，端点必须处于 InService 状态
     * @param csvPayload   CSV 格式的请求体（如 "1.0,2.0,3.0,4.0"），不含表头
     * @return 推理结果（CSV 格式字符串）
     */
    public String invokeEndpointCsv(String endpointName, String csvPayload) {
        System.out.println("调用端点 (CSV): " + endpointName);

        InvokeEndpointRequest request = InvokeEndpointRequest.builder()
                .endpointName(endpointName)
                .contentType("text/csv")
                .accept("text/csv")
                .body(SdkBytes.fromString(csvPayload, StandardCharsets.UTF_8))
                .build();

        InvokeEndpointResponse response = runtimeClient.invokeEndpoint(request);
        return response.body().asString(StandardCharsets.UTF_8);
    }

    /**
     * 调用端点进行推理（指定变体）
     *
     * 底层调用 SageMaker Runtime InvokeEndpoint API，通过 TargetVariant 参数指定变体。
     * 用于 A/B 测试场景，将请求路由到指定的模型变体。
     * 变体在 EndpointConfig 中通过 ProductionVariants 定义，
     * 每个变体可以使用不同的模型、实例类型和流量权重。
     *
     * @param endpointName 端点名称，端点必须处于 InService 状态
     * @param variantName  变体名称（如 "AllTraffic"、"VariantA"、"VariantB"）
     * @param jsonPayload  JSON 格式的请求体
     * @return 推理结果（JSON 字符串）
     */
    public String invokeEndpointVariant(String endpointName, String variantName, String jsonPayload) {
        System.out.println("调用端点变体: " + endpointName + " / " + variantName);

        InvokeEndpointRequest request = InvokeEndpointRequest.builder()
                .endpointName(endpointName)
                .targetVariant(variantName)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        InvokeEndpointResponse response = runtimeClient.invokeEndpoint(request);
        return response.body().asString(StandardCharsets.UTF_8);
    }

    public void close() {
        runtimeClient.close();
    }
}
