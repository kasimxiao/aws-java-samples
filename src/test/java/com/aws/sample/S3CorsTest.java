package com.aws.sample;

import java.util.List;
import java.util.Map;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.s3.S3Service;

import software.amazon.awssdk.services.s3.model.CORSRule;

/**
 * S3 CORS 跨域配置测试
 * 测试为 S3 存储桶设置 CORS 配置，使前端 JavaScript 可通过临时凭证直接操作 S3
 */
public class S3CorsTest {

    private static final String BUCKET_NAME = "wongxiao-javasdk-cors-test";

    public static void main(String[] args) {
        System.out.println("开始 S3 CORS 跨域配置测试...\n");

        AwsConfig config = new AwsConfig();

        try (S3Service s3Service = new S3Service(config)) {

            // 1. 创建存储桶
            System.out.println("===== 1. 创建测试存储桶 =====");
            if (s3Service.bucketExists(BUCKET_NAME)) {
                System.out.println("存储桶已存在，跳过创建");
            } else {
                s3Service.createBucket(BUCKET_NAME, Map.of("project", "cors-test"));
            }

            // 2. 设置默认 CORS 配置（允许前端常见操作）
            System.out.println("\n===== 2. 设置默认 CORS 配置 =====");
            s3Service.putBucketCorsForOrigins(BUCKET_NAME, List.of(
                    "http://localhost:3000",
                    "http://localhost:5173",
                    "https://example.com"
            ));

            // 3. 获取并验证 CORS 配置
            System.out.println("\n===== 3. 获取 CORS 配置 =====");
            List<CORSRule> rules = s3Service.getBucketCors(BUCKET_NAME);
            System.out.println("CORS 规则数量: " + rules.size());

            // 4. 更新为自定义 CORS 规则
            System.out.println("\n===== 4. 更新为自定义 CORS 规则 =====");
            CORSRule readOnlyRule = CORSRule.builder()
                    .allowedOrigins("https://readonly.example.com")
                    .allowedMethods("GET", "HEAD")
                    .allowedHeaders("*")
                    .maxAgeSeconds(600)
                    .build();

            CORSRule fullAccessRule = CORSRule.builder()
                    .allowedOrigins("https://admin.example.com")
                    .allowedMethods("GET", "PUT", "POST", "DELETE", "HEAD")
                    .allowedHeaders("*")
                    .exposeHeaders("ETag", "x-amz-request-id", "Content-Length", "Content-Type")
                    .maxAgeSeconds(3600)
                    .build();

            s3Service.putBucketCors(BUCKET_NAME, List.of(readOnlyRule, fullAccessRule));

            // 5. 再次获取验证
            System.out.println("\n===== 5. 验证更新后的 CORS 配置 =====");
            List<CORSRule> updatedRules = s3Service.getBucketCors(BUCKET_NAME);
            System.out.println("更新后 CORS 规则数量: " + updatedRules.size());

            // 6. 使用便捷方法创建带 CORS 的存储桶
            System.out.println("\n===== 6. 一键创建带 CORS 的存储桶 =====");
            String corsTestBucket2 = BUCKET_NAME + "-2";
            if (!s3Service.bucketExists(corsTestBucket2)) {
                s3Service.createBucketWithCors(
                        corsTestBucket2,
                        List.of("http://localhost:3000", "https://app.example.com"),
                        Map.of("project", "cors-test-2")
                );
                // 验证
                s3Service.getBucketCors(corsTestBucket2);
                // 清理
                s3Service.deleteBucket(corsTestBucket2);
            }

            // 7. 删除 CORS 配置
            System.out.println("\n===== 7. 删除 CORS 配置 =====");
            s3Service.deleteBucketCors(BUCKET_NAME);

            // 8. 确认 CORS 已删除
            System.out.println("\n===== 8. 确认 CORS 已删除 =====");
            List<CORSRule> emptyRules = s3Service.getBucketCors(BUCKET_NAME);
            System.out.println("CORS 规则数量: " + emptyRules.size());

            // 9. 清理测试存储桶
            System.out.println("\n===== 9. 清理测试存储桶 =====");
            s3Service.deleteBucket(BUCKET_NAME);

            System.out.println("\n========== S3 CORS 测试通过！==========");

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
