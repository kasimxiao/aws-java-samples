package com.aws.sample;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.aws.sample.bedrock.BedrockLlmService;
import com.aws.sample.bedrock.BedrockStreamService;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;

/**
 * Bedrock 流式输出测试
 */
public class BedrockStreamTest {

    private static BedrockStreamService streamService;

    @BeforeAll
    static void setup() {
        BedrockRuntimeAsyncClient asyncClient = BedrockRuntimeAsyncClient.builder()
                .region(Region.US_EAST_1)
                .build();

        streamService = new BedrockStreamService(asyncClient);
    }

    /**
     * 测试流式文本对话
     */
    @Test
    void testStreamChat() {
        System.out.println("=== 流式输出测试 ===");
        System.out.print("响应: ");
        
        String response = streamService.chatStream(
                BedrockLlmService.MODEL_NOVA_PRO,
                "用三句话介绍 AWS Lambda",
                null,
                token -> System.out.print(token)  // 实时打印每个 token
        );
        
        System.out.println("\n\n完整响应长度: " + response.length());
    }

    /**
     * 测试流式多模态（PDF）
     */
    @Test
    void testStreamMultimodal() {
        String filePath = "/Users/wongxiao/project/aws-java/src/test/java/com/aws/sample/invoice.pdf";
        
        String modelID = BedrockLlmService.MODEL_NOVA_LITE;
        // String modelID = BedrockLlmService.MODEL_QWEN3_VL;

        if (java.nio.file.Files.exists(java.nio.file.Path.of(filePath))) {
            System.out.println("=== 流式 PDF 分析测试 ===");
            System.out.print("响应: ");
            
            String response = streamService.chatStream(
                    modelID,
                    "请从发票中提取金额和税额信息，请严格按照以下JSON格式输出，不要包含任何其他文字：\n" +
                    "{\"amount\": \"金额数值\", \"tax\": \"税额数值\"}\n" +
                    "如果无法识别某个字段，请填写\"未识别\"。",
                    "你是一位财务分析专家。识别提取发票信息。",
                    filePath,
                    token -> System.out.print(token)
            );
            
            System.out.println("\n\n完整响应长度: " + response.length());
        } else {
            System.out.println("跳过测试：PDF 文件不存在");
        }
    }
}
