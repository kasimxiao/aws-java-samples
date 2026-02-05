package com.aws.sample.bedrock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentFormat;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentSource;
import software.amazon.awssdk.services.bedrockruntime.model.ImageBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ImageFormat;
import software.amazon.awssdk.services.bedrockruntime.model.ImageSource;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

/**
 * AWS Bedrock 流式输出服务
 * 使用 ConverseStream API 支持实时流式响应
 */
public class BedrockStreamService {
    private static final Logger logger = LoggerFactory.getLogger(BedrockStreamService.class);

    private final BedrockRuntimeAsyncClient bedrockAsyncClient;

    public BedrockStreamService(BedrockRuntimeAsyncClient bedrockAsyncClient) {
        this.bedrockAsyncClient = bedrockAsyncClient;
    }

    /**
     * 流式文本对话
     *
     * @param modelId      模型 ID
     * @param prompt       用户提示
     * @param systemPrompt 系统提示（可选）
     * @param onToken      每个 token 的回调
     * @return 完整响应文本
     */
    public String chatStream(String modelId, String prompt, String systemPrompt, Consumer<String> onToken) {
        return chatStream(modelId, prompt, systemPrompt, null, onToken);
    }

    /**
     * 流式多模态对话（支持图片和 PDF）
     *
     * @param modelId      模型 ID
     * @param prompt       用户提示
     * @param systemPrompt 系统提示（可选）
     * @param filePath     文件路径（支持图片和 PDF，可选）
     * @param onToken      每个 token 的回调
     * @return 完整响应文本
     */
    public String chatStream(String modelId, String prompt, String systemPrompt, 
                             String filePath, Consumer<String> onToken) {
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

        return converseStream(modelId, contentBlocks, systemPrompt, onToken);
    }

    /**
     * 核心流式调用方法
     */
    private String converseStream(String modelId, List<ContentBlock> contentBlocks, 
                                   String systemPrompt, Consumer<String> onToken) {
        // 构建用户消息
        Message userMessage = Message.builder()
                .role(ConversationRole.USER)
                .content(contentBlocks)
                .build();

        ConverseStreamRequest.Builder requestBuilder = ConverseStreamRequest.builder()
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

        StringBuilder fullResponse = new StringBuilder();

        // 构建流式响应处理器
        ConverseStreamResponseHandler handler = ConverseStreamResponseHandler.builder()
                .onEventStream(stream -> stream.subscribe(event -> {
                    if (event instanceof ContentBlockDeltaEvent deltaEvent) {
                        String text = deltaEvent.delta().text();
                        if (text != null) {
                            fullResponse.append(text);
                            if (onToken != null) {
                                onToken.accept(text);
                            }
                        }
                    } else if (event instanceof MessageStopEvent) {
                        logger.info("流式响应完成");
                    }
                }))
                .onError(error -> logger.error("流式调用失败: {}", error.getMessage(), error))
                .build();

        try {
            logger.info("调用 Bedrock ConverseStream API，模型: {}", modelId);
            bedrockAsyncClient.converseStream(requestBuilder.build(), handler).join();
            return fullResponse.toString();
        } catch (Exception e) {
            logger.error("Bedrock 流式调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("Bedrock 流式调用失败: " + e.getMessage(), e);
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
}
