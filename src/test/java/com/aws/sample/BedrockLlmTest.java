package com.aws.sample;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.aws.sample.bedrock.BedrockLlmService;
import com.aws.sample.common.AwsConfig;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Bedrock LLM 服务测试
 * 演示如何使用 Converse API 调用 Nova Pro 和 Qwen3-VL 模型
 */
public class BedrockLlmTest {

    private static BedrockLlmService llmService;

    @BeforeAll
    static void setup() {
        // 通过 AwsConfig 获取凭证，优先使用配置文件中的 AKSK，没有则使用 aws configure 凭证
        AwsConfig config = new AwsConfig();
        BedrockRuntimeClient bedrockClient = BedrockRuntimeClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(config.getCredentialsProvider())
                .build();

        llmService = new BedrockLlmService(bedrockClient);
    }

    /**
     * 测试 Nova Pro 纯文本对话
     */
    @Test
    void testNovaProTextChat() {
        String response = llmService.chat(
                BedrockLlmService.MODEL_NOVA_PRO,
                "请用中文简要介绍一下 AWS Lambda 的主要特点",
                null
        );

        System.out.println("Nova Pro 响应:");
        System.out.println(response);
    }

    /**
     * 测试 Nova Pro 带系统提示的对话
     */
    @Test
    void testNovaProWithSystemPrompt() {
        String systemPrompt = "你是一位 AWS 解决方案架构师，擅长用简洁的语言解释技术概念。";

        String response = llmService.chat(
                BedrockLlmService.MODEL_NOVA_PRO,
                "什么是 Amazon S3？",
                systemPrompt
        );

        System.out.println("Nova Pro（带系统提示）响应:");
        System.out.println(response);
    }

    /**
     * 测试 Qwen3-VL 纯文本对话
     */
    @Test
    void testQwen3VlTextChat() {
        String response = llmService.chat(
                BedrockLlmService.MODEL_QWEN3_VL,
                "请用中文解释什么是机器学习中的迁移学习",
                null
        );

        System.out.println("Qwen3-VL 响应:");
        System.out.println(response);
    }

    /**
     * 测试 多模态
     * 注意：需要提供有效的图片路径
     */
    @Test
    void testMultimodal() {
        // 示例：分析本地图片
        String filePath = "/Users/wongxiao/project/aws-java/src/test/java/com/aws/sample/invoice.pdf"; // 替换为实际图片路径
        
        String modelID = BedrockLlmService.MODEL_NOVA_LITE;
        // String modelID = BedrockLlmService.MODEL_QWEN3_VL;

        // 如果图片存在，执行多模态测试
        if (java.nio.file.Files.exists(java.nio.file.Path.of(filePath))) {
            String response = llmService.chat(
                    modelID,
                    "请从发票中提取金额和税额信息，请严格按照以下JSON格式输出，不要包含任何其他文字：\n" +
                    "{\"amount\": \"金额数值\", \"tax\": \"税额数值\"}\n" +
                    "如果无法识别某个字段，请填写\"未识别\"。",
                    "你是一位财务分析专家。识别提取发票信息。",
                    filePath
            );

            System.out.println("发票信息（JSON格式）:");
            System.out.println(response);
        }
    }



}
