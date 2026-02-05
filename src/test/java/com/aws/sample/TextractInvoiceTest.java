package com.aws.sample;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.textract.TextractService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Textract 发票识别测试
 * 使用 AWS Textract 提取发票中的金额和税额
 */
public class TextractInvoiceTest {

    private TextractService textractService;
    private static final String INVOICE_PATH = "src/test/java/com/aws/sample/invoice.pdf";

    @BeforeEach
    void setUp() {
        AwsConfig config = new AwsConfig();
        textractService = new TextractService(config);
    }

    @AfterEach
    void tearDown() {
        if (textractService != null) {
            textractService.close();
        }
    }

    /**
     * 测试中文发票分析 - 提取金额和税额
     */
    @Test
    void testAnalyzeChineseInvoice() throws Exception {
        System.out.println("==================== 中文发票分析 ====================");
        
        Map<String, String> result = textractService.analyzeChineseInvoice(INVOICE_PATH);
        
        System.out.println("\n提取的发票信息：");
        System.out.println("------------------------------------------------");
        
        // 显示关键字段
        String[] keyFields = {"发票代码", "金额（不含税）", "税率", "税额", "价税合计"};
        for (String key : keyFields) {
            if (result.containsKey(key)) {
                System.out.printf("%-15s: %s%n", key, result.get(key));
            }
        }
        
        // 显示其他字段
        System.out.println("\n其他识别字段：");
        for (Map.Entry<String, String> entry : result.entrySet()) {
            boolean isKeyField = false;
            for (String key : keyFields) {
                if (key.equals(entry.getKey())) {
                    isKeyField = true;
                    break;
                }
            }
            if (!isKeyField) {
                System.out.printf("%-20s: %s%n", entry.getKey(), entry.getValue());
            }
        }
        
        System.out.println("================================================");
    }

    /**
     * 测试发票费用分析 - 提取金额和税额
     */
    @Test
    void testAnalyzeInvoiceExpense() throws Exception {
        System.out.println("==================== 发票费用分析 ====================");
        
        Map<String, String> result = textractService.analyzeExpenseDocument(INVOICE_PATH);
        
        System.out.println("\n提取的发票信息：");
        System.out.println("------------------------------------------------");
        
        // 优先显示关键字段
        String[] keyFields = {"发票号", "发票日期", "供应商", "小计", "税额", "总金额", "已付金额", "应付金额"};
        for (String key : keyFields) {
            if (result.containsKey(key)) {
                System.out.printf("%-12s: %s%n", key, result.get(key));
            }
        }
        
        // 显示其他字段
        System.out.println("\n其他字段：");
        for (Map.Entry<String, String> entry : result.entrySet()) {
            boolean isKeyField = false;
            for (String key : keyFields) {
                if (key.equals(entry.getKey())) {
                    isKeyField = true;
                    break;
                }
            }
            if (!isKeyField && !entry.getKey().equals("行项目")) {
                System.out.printf("%-20s: %s%n", entry.getKey(), entry.getValue());
            }
        }
        
        // 显示行项目
        if (result.containsKey("行项目")) {
            System.out.println("\n行项目明细：");
            System.out.println(result.get("行项目"));
        }
        
        System.out.println("================================================");
    }

    /**
     * 测试文档文本提取
     */
    @Test
    void testAnalyzeDocumentText() throws Exception {
        System.out.println("==================== 文档文本提取 ====================");
        
        String text = textractService.analyzeDocument(INVOICE_PATH);
        
        System.out.println("提取的文本内容：");
        System.out.println("------------------------------------------------");
        System.out.println(text);
        System.out.println("================================================");
    }

    /**
     * 测试表单键值对提取
     */
    @Test
    void testAnalyzeDocumentForms() throws Exception {
        System.out.println("==================== 表单键值对提取 ====================");
        
        Map<String, String> keyValuePairs = textractService.analyzeDocumentForms(INVOICE_PATH);
        
        System.out.println("提取的键值对：");
        System.out.println("------------------------------------------------");
        for (Map.Entry<String, String> entry : keyValuePairs.entrySet()) {
            System.out.printf("%-30s: %s%n", entry.getKey(), entry.getValue());
        }
        System.out.println("================================================");
    }
}
