package com.aws.sample.bedrock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentFormat;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentSource;
import software.amazon.awssdk.services.bedrockruntime.model.ImageBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ImageFormat;
import software.amazon.awssdk.services.bedrockruntime.model.ImageSource;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

/**
 * AWS Bedrock LLM 服务
 * 使用 Converse API 支持多模态对话，兼容 Nova Pro 和 Qwen3-VL 模型
 */
public class BedrockLlmService {
    private static final Logger logger = LoggerFactory.getLogger(BedrockLlmService.class);

    private final BedrockRuntimeClient bedrockClient;

    // 支持的模型 ID
    // Nova 系列（Amazon 官方模型，默认可用）
    public static final String MODEL_NOVA_PRO = "us.amazon.nova-pro-v1:0";
    public static final String MODEL_NOVA_LITE = "us.amazon.nova-2-lite-v1:0";
    
    // 第三方模型（需要在 Bedrock 控制台申请访问权限）
    // Qwen3-VL 模型 ID 格式可能因区域而异，请在 Bedrock 控制台确认
    public static final String MODEL_QWEN3_VL = "qwen.qwen3-vl-235b-a22b";
    
    // Claude 系列（Anthropic，需要申请访问权限）
    public static final String MODEL_CLAUDE_SONNET = "us.anthropic.claude-sonnet-4-5-20250929-v1:0";
    public static final String MODEL_CLAUDE_HAIKU = "us.anthropic.claude-haiku-4-5-20251001-v1:0";

    public BedrockLlmService(BedrockRuntimeClient bedrockClient) {
        this.bedrockClient = bedrockClient;
    }

    /**
     * 文本对话
     *
     * @param modelId      模型 ID
     * @param prompt       用户提示
     * @param systemPrompt 系统提示（可选）
     * @return 模型响应文本
     */
    public String chat(String modelId, String prompt, String systemPrompt) {
        return chat(modelId, prompt, systemPrompt, null);
    }

    /**
     * 多模态对话（支持图片和 PDF）
     *
     * @param modelId      模型 ID
     * @param prompt       用户提示
     * @param systemPrompt 系统提示（可选）
     * @param filePath     文件路径（支持图片和 PDF，可选）
     * @return 模型响应文本
     */
    public String chat(String modelId, String prompt, String systemPrompt, String filePath) {
        List<ContentBlock> contentBlocks = new ArrayList<>();

        // 添加文件内容（如果有）
        if (filePath != null && !filePath.isEmpty()) {
            ContentBlock fileBlock = createFileContentBlock(filePath);
            if (fileBlock != null) {
                contentBlocks.add(fileBlock);
            }
        }

        // 添加文本内容
        contentBlocks.add(ContentBlock.builder().text(prompt).build());

        return converse(modelId, contentBlocks, systemPrompt);
    }

    /**
     * 使用 Base64 编码的图片进行多模态对话
     *
     * @param modelId       模型 ID
     * @param prompt        用户提示
     * @param systemPrompt  系统提示（可选）
     * @param imageBase64   Base64 编码的图片数据
     * @param imageFormat   图片格式（png, jpeg, gif, webp）
     * @return 模型响应文本
     */
    public String chatWithBase64Image(String modelId, String prompt, String systemPrompt,
                                       String imageBase64, String imageFormat) {
        List<ContentBlock> contentBlocks = new ArrayList<>();

        // 添加图片内容
        if (imageBase64 != null && !imageBase64.isEmpty()) {
            ImageFormat format = parseImageFormat(imageFormat);
            byte[] imageBytes = Base64.getDecoder().decode(imageBase64);

            ImageBlock imageBlock = ImageBlock.builder()
                    .format(format)
                    .source(ImageSource.builder()
                            .bytes(SdkBytes.fromByteArray(imageBytes))
                            .build())
                    .build();

            contentBlocks.add(ContentBlock.builder().image(imageBlock).build());
        }

        // 添加文本内容
        contentBlocks.add(ContentBlock.builder().text(prompt).build());

        return converse(modelId, contentBlocks, systemPrompt);
    }

    /**
     * 核心 Converse 调用方法
     */
    private String converse(String modelId, List<ContentBlock> contentBlocks, String systemPrompt) {
        // 构建用户消息
        Message userMessage = Message.builder()
                .role(ConversationRole.USER)
                .content(contentBlocks)
                .build();

        ConverseRequest.Builder requestBuilder = ConverseRequest.builder()
                .modelId(modelId)
                .messages(userMessage);

        // 添加系统提示
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            requestBuilder.system(SystemContentBlock.builder().text(systemPrompt).build());
        }

        // 添加推理配置
        requestBuilder.inferenceConfig(InferenceConfiguration.builder()
                .maxTokens(4096)
                .temperature(0.7f)
                .topP(0.9f)
                .build());

        try {
            logger.info("调用 Bedrock Converse API，模型: {}", modelId);
            ConverseResponse response = bedrockClient.converse(requestBuilder.build());

            return extractResponseText(response);
        } catch (Exception e) {
            logger.error("Bedrock 调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("Bedrock 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据文件类型创建内容块（支持图片和 PDF）
     */
    private ContentBlock createFileContentBlock(String filePath) {
        String fileNameLower = filePath.toLowerCase();
        
        if (fileNameLower.endsWith(".pdf")) {
            return createDocumentContentBlock(filePath);
        } else {
            return createImageContentBlock(filePath);
        }
    }

    /**
     * 从文件路径创建文档内容块（PDF）
     */
    private ContentBlock createDocumentContentBlock(String documentPath) {
        try {
            Path path = Path.of(documentPath);
            byte[] documentBytes = Files.readAllBytes(path);
            String fileName = path.getFileName().toString();

            logger.info("加载文档: {}，格式: PDF", documentPath);

            DocumentBlock documentBlock = DocumentBlock.builder()
                    .format(DocumentFormat.PDF)
                    .name(fileName.replace(".pdf", "").replace(".PDF", ""))
                    .source(DocumentSource.builder()
                            .bytes(SdkBytes.fromByteArray(documentBytes))
                            .build())
                    .build();

            return ContentBlock.builder().document(documentBlock).build();
        } catch (IOException e) {
            logger.error("读取文档失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从文件路径创建图片内容块
     */
    private ContentBlock createImageContentBlock(String imagePath) {
        try {
            Path path = Path.of(imagePath);
            byte[] imageBytes = Files.readAllBytes(path);
            String fileName = path.getFileName().toString().toLowerCase();

            ImageFormat format = ImageFormat.PNG;
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                format = ImageFormat.JPEG;
            } else if (fileName.endsWith(".gif")) {
                format = ImageFormat.GIF;
            } else if (fileName.endsWith(".webp")) {
                format = ImageFormat.WEBP;
            }

            logger.info("加载图片: {}，格式: {}", imagePath, format);

            ImageBlock imageBlock = ImageBlock.builder()
                    .format(format)
                    .source(ImageSource.builder()
                            .bytes(SdkBytes.fromByteArray(imageBytes))
                            .build())
                    .build();

            return ContentBlock.builder().image(imageBlock).build();
        } catch (IOException e) {
            logger.error("读取图片失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析图片格式
     */
    private ImageFormat parseImageFormat(String format) {
        if (format == null) return ImageFormat.PNG;

        return switch (format.toLowerCase()) {
            case "jpeg", "jpg" -> ImageFormat.JPEG;
            case "gif" -> ImageFormat.GIF;
            case "webp" -> ImageFormat.WEBP;
            default -> ImageFormat.PNG;
        };
    }

    /**
     * 提取响应文本
     */
    private String extractResponseText(ConverseResponse response) {
        if (response.output() == null || response.output().message() == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (ContentBlock block : response.output().message().content()) {
            if (block.text() != null) {
                result.append(block.text());
            }
        }

        // 记录 token 使用情况
        if (response.usage() != null) {
            logger.info("Token 使用 - 输入: {}, 输出: {}, 总计: {}",
                    response.usage().inputTokens(),
                    response.usage().outputTokens(),
                    response.usage().totalTokens());
        }

        return result.toString();
    }

}