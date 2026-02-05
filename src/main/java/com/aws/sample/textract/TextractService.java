package com.aws.sample.textract;

import com.aws.sample.common.AwsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AWS Textract 服务类
 * 用于 OCR 文档识别，提取发票金额、税额等信息
 */
public class TextractService {

    private static final Logger logger = LoggerFactory.getLogger(TextractService.class);
    private final TextractClient textractClient;

    public TextractService(AwsConfig config) {
        this.textractClient = TextractClient.builder()
                .region(config.getRegion())
                .credentialsProvider(config.getCredentialsProvider())
                .build();
    }

    /**
     * 分析文档并提取所有文本
     * @param filePath 文档路径（支持 PDF、PNG、JPEG）
     * @return 提取的文本内容
     */
    public String analyzeDocument(String filePath) throws IOException {
        byte[] fileBytes = Files.readAllBytes(Path.of(filePath));
        SdkBytes sdkBytes = SdkBytes.fromByteArray(fileBytes);

        Document document = Document.builder()
                .bytes(sdkBytes)
                .build();

        DetectDocumentTextRequest request = DetectDocumentTextRequest.builder()
                .document(document)
                .build();

        DetectDocumentTextResponse response = textractClient.detectDocumentText(request);

        StringBuilder text = new StringBuilder();
        for (Block block : response.blocks()) {
            if (block.blockType() == BlockType.LINE) {
                text.append(block.text()).append("\n");
            }
        }

        logger.info("文档分析完成，提取 {} 行文本", response.blocks().stream()
                .filter(b -> b.blockType() == BlockType.LINE).count());

        return text.toString();
    }

    /**
     * 分析发票文档，提取费用相关信息
     * @param filePath 发票文件路径
     * @return 包含金额、税额等信息的 Map
     */
    public Map<String, String> analyzeExpenseDocument(String filePath) throws IOException {
        byte[] fileBytes = Files.readAllBytes(Path.of(filePath));
        SdkBytes sdkBytes = SdkBytes.fromByteArray(fileBytes);

        Document document = Document.builder()
                .bytes(sdkBytes)
                .build();

        AnalyzeExpenseRequest request = AnalyzeExpenseRequest.builder()
                .document(document)
                .build();

        AnalyzeExpenseResponse response = textractClient.analyzeExpense(request);

        Map<String, String> result = new HashMap<>();
        List<String> lineItems = new ArrayList<>();

        for (ExpenseDocument expenseDoc : response.expenseDocuments()) {
            // 提取摘要字段（如总金额、税额、供应商等）
            for (ExpenseField field : expenseDoc.summaryFields()) {
                String fieldType = field.type() != null ? field.type().text() : "UNKNOWN";
                String fieldValue = field.valueDetection() != null ? field.valueDetection().text() : "";
                String fieldLabel = field.labelDetection() != null ? field.labelDetection().text() : fieldType;

                logger.debug("摘要字段 - 类型: {}, 标签: {}, 值: {}", fieldType, fieldLabel, fieldValue);

                // 根据字段类型存储
                switch (fieldType.toUpperCase()) {
                    case "TOTAL" -> result.put("总金额", fieldValue);
                    case "TAX" -> result.put("税额", fieldValue);
                    case "SUBTOTAL" -> result.put("小计", fieldValue);
                    case "INVOICE_RECEIPT_ID" -> result.put("发票号", fieldValue);
                    case "INVOICE_RECEIPT_DATE" -> result.put("发票日期", fieldValue);
                    case "VENDOR_NAME" -> result.put("供应商", fieldValue);
                    case "RECEIVER_NAME" -> result.put("收款方", fieldValue);
                    case "AMOUNT_PAID" -> result.put("已付金额", fieldValue);
                    case "AMOUNT_DUE" -> result.put("应付金额", fieldValue);
                    default -> {
                        // 使用标签作为 key
                        if (fieldLabel != null && !fieldLabel.isEmpty() && fieldValue != null && !fieldValue.isEmpty()) {
                            result.put(fieldLabel, fieldValue);
                        }
                    }
                }
            }

            // 提取行项目
            for (LineItemGroup lineItemGroup : expenseDoc.lineItemGroups()) {
                for (LineItemFields lineItem : lineItemGroup.lineItems()) {
                    StringBuilder itemInfo = new StringBuilder();
                    for (ExpenseField field : lineItem.lineItemExpenseFields()) {
                        String fieldType = field.type() != null ? field.type().text() : "";
                        String fieldValue = field.valueDetection() != null ? field.valueDetection().text() : "";
                        if (!fieldValue.isEmpty()) {
                            itemInfo.append(fieldType).append(": ").append(fieldValue).append(" | ");
                        }
                    }
                    if (!itemInfo.isEmpty()) {
                        lineItems.add(itemInfo.toString());
                    }
                }
            }
        }

        if (!lineItems.isEmpty()) {
            result.put("行项目", String.join("\n", lineItems));
        }

        logger.info("发票分析完成，提取 {} 个字段", result.size());
        return result;
    }

    /**
     * 分析文档表单，提取键值对
     * @param filePath 文档路径
     * @return 键值对 Map
     */
    public Map<String, String> analyzeDocumentForms(String filePath) throws IOException {
        byte[] fileBytes = Files.readAllBytes(Path.of(filePath));
        SdkBytes sdkBytes = SdkBytes.fromByteArray(fileBytes);

        Document document = Document.builder()
                .bytes(sdkBytes)
                .build();

        AnalyzeDocumentRequest request = AnalyzeDocumentRequest.builder()
                .document(document)
                .featureTypes(FeatureType.FORMS)
                .build();

        AnalyzeDocumentResponse response = textractClient.analyzeDocument(request);

        Map<String, Block> blockMap = new HashMap<>();
        Map<String, String> keyValuePairs = new HashMap<>();

        // 构建 block 映射
        for (Block block : response.blocks()) {
            blockMap.put(block.id(), block);
        }

        // 提取键值对
        for (Block block : response.blocks()) {
            if (block.blockType() == BlockType.KEY_VALUE_SET && 
                block.entityTypes() != null && 
                block.entityTypes().contains(EntityType.KEY)) {
                
                String key = getTextFromRelationships(block, blockMap, RelationshipType.CHILD);
                String value = "";

                // 查找对应的值
                if (block.relationships() != null) {
                    for (Relationship rel : block.relationships()) {
                        if (rel.type() == RelationshipType.VALUE) {
                            for (String valueId : rel.ids()) {
                                Block valueBlock = blockMap.get(valueId);
                                if (valueBlock != null) {
                                    value = getTextFromRelationships(valueBlock, blockMap, RelationshipType.CHILD);
                                }
                            }
                        }
                    }
                }

                if (!key.isEmpty()) {
                    keyValuePairs.put(key.trim(), value.trim());
                }
            }
        }

        logger.info("表单分析完成，提取 {} 个键值对", keyValuePairs.size());
        return keyValuePairs;
    }

    /**
     * 从关系中提取文本
     */
    private String getTextFromRelationships(Block block, Map<String, Block> blockMap, RelationshipType relType) {
        StringBuilder text = new StringBuilder();
        if (block.relationships() != null) {
            for (Relationship rel : block.relationships()) {
                if (rel.type() == relType) {
                    for (String childId : rel.ids()) {
                        Block childBlock = blockMap.get(childId);
                        if (childBlock != null && childBlock.text() != null) {
                            text.append(childBlock.text()).append(" ");
                        }
                    }
                }
            }
        }
        return text.toString();
    }

    /**
     * 关闭客户端
     */
    public void close() {
        if (textractClient != null) {
            textractClient.close();
        }
    }

    /**
     * 分析中文发票，提取金额和税额
     * 针对中文发票格式优化，使用正则表达式从 OCR 文本中提取关键信息
     * @param filePath 发票文件路径
     * @return 包含金额、税额等信息的 Map
     */
    public Map<String, String> analyzeChineseInvoice(String filePath) throws IOException {
        // 先获取 OCR 文本
        String text = analyzeDocument(filePath);
        Map<String, String> result = new HashMap<>();

        logger.info("开始解析中文发票...");
        logger.debug("OCR 文本内容:\n{}", text);

        // 提取发票代码
        Pattern invoiceCodePattern = Pattern.compile("(\\d{10,12})");
        Matcher codeMatcher = invoiceCodePattern.matcher(text);
        if (codeMatcher.find()) {
            result.put("发票代码", codeMatcher.group(1));
        }

        // 提取金额（不含税）- 匹配 ¥ 后面的数字或独立的金额数字
        Pattern amountPattern = Pattern.compile("¥?(\\d+\\.\\d{2})");
        Matcher amountMatcher = amountPattern.matcher(text);
        List<String> amounts = new ArrayList<>();
        while (amountMatcher.find()) {
            amounts.add(amountMatcher.group(1));
        }

        // 根据中文发票的典型格式解析金额
        // 通常顺序为：单价、金额、税率、税额、价税合计
        if (!amounts.isEmpty()) {
            logger.info("识别到 {} 个金额数值: {}", amounts.size(), amounts);
            
            // 查找税率
            Pattern taxRatePattern = Pattern.compile("(\\d+)%");
            Matcher taxRateMatcher = taxRatePattern.matcher(text);
            if (taxRateMatcher.find()) {
                result.put("税率", taxRateMatcher.group(1) + "%");
            }

            // 根据金额数量和大小关系推断各字段
            if (amounts.size() >= 2) {
                // 找出最大值作为价税合计
                double maxAmount = 0;
                String maxAmountStr = "";
                for (String amt : amounts) {
                    double val = Double.parseDouble(amt);
                    if (val > maxAmount) {
                        maxAmount = val;
                        maxAmountStr = amt;
                    }
                }
                result.put("价税合计", "¥" + maxAmountStr);

                // 查找税额（通常是较小的金额）
                for (String amt : amounts) {
                    double val = Double.parseDouble(amt);
                    if (val < maxAmount && val > 0) {
                        // 检查是否可能是税额（通常税额 = 价税合计 - 金额）
                        double possibleAmount = maxAmount - val;
                        // 查找是否存在对应的金额
                        for (String amt2 : amounts) {
                            double val2 = Double.parseDouble(amt2);
                            if (Math.abs(val2 - possibleAmount) < 0.01) {
                                result.put("税额", "¥" + amt);
                                result.put("金额（不含税）", "¥" + amt2);
                                break;
                            }
                        }
                    }
                }
            }
        }

        // 尝试从 AnalyzeExpense 获取更多结构化信息
        try {
            Map<String, String> expenseResult = analyzeExpenseDocument(filePath);
            // 合并结果，优先使用 expense 分析的结果
            for (Map.Entry<String, String> entry : expenseResult.entrySet()) {
                if (!entry.getValue().isEmpty() && !result.containsKey(entry.getKey())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            logger.warn("AnalyzeExpense 分析失败，使用 OCR 文本解析结果: {}", e.getMessage());
        }

        logger.info("中文发票解析完成，提取 {} 个字段", result.size());
        return result;
    }
}
