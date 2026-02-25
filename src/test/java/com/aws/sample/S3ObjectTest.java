package com.aws.sample;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.s3.S3Service;

import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * S3 完整测试
 * 测试存储桶创建（带标签）、文件上传、下载、列表、删除、存储桶删除
 */
public class S3ObjectTest {

    public static void main(String[] args) throws Exception {
        System.out.println("开始 S3 完整测试...\n");

        AwsConfig config = new AwsConfig();
        String bucketName = "wongxiao-javasdk-test";

        // 准备本地测试文件
        File tempFile = File.createTempFile("s3-test-", ".txt");
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("这是一个 S3 上传测试文件\n时间: " + System.currentTimeMillis());
        }
        String downloadPath = System.getProperty("java.io.tmpdir") + "/s3-download-test.txt";

        try (S3Service s3Service = new S3Service(config)) {

            // 1. 创建存储桶（带标签）
            System.out.println("===== 1. 创建存储桶（带标签）=====");
            if (s3Service.bucketExists(bucketName)) {
                System.out.println("存储桶已存在，先删除...");
                s3Service.forceDeleteBucket(bucketName);
            }
            s3Service.createBucket(bucketName, Map.of(
                    "project", "javasdk-test",
                    "env", "dev"
            ));

            // 2. 验证存储桶存在
            System.out.println("\n===== 2. 验证存储桶 =====");
            boolean exists = s3Service.bucketExists(bucketName);
            System.out.println("存储桶 " + bucketName + " 存在: " + exists);

            // 3. 列出存储桶
            System.out.println("\n===== 3. 列出存储桶 =====");
            s3Service.listBuckets();

            // 4. 上传文件
            System.out.println("\n===== 4. 上传文件 =====");
            s3Service.uploadFile(bucketName, "test/upload-file.txt", tempFile.getAbsolutePath());

            // 5. 上传文本内容
            System.out.println("\n===== 5. 上传文本内容 =====");
            s3Service.uploadContent(bucketName, "test/hello.txt", "你好，S3！这是一段测试内容。");
            s3Service.uploadContent(bucketName, "test/data.json", "{\"name\": \"测试\", \"value\": 123}");

            // 6. 列出所有对象
            System.out.println("\n===== 6. 列出所有对象 =====");
            s3Service.listObjects(bucketName);

            // 7. 按前缀列出对象
            System.out.println("\n===== 7. 按前缀列出对象 =====");
            s3Service.listObjects(bucketName, "test/", 100);

            // 8. 下载文件
            System.out.println("\n===== 8. 下载文件 =====");
            s3Service.downloadFile(bucketName, "test/hello.txt", downloadPath);
            System.out.println("下载文件内容验证: " + new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(downloadPath))));

            // 9. 删除单个对象
            System.out.println("\n===== 9. 删除单个对象 =====");
            s3Service.deleteObject(bucketName, "test/data.json");

            // 10. 批量删除对象
            System.out.println("\n===== 10. 批量删除对象 =====");
            s3Service.deleteObjects(bucketName, List.of("test/upload-file.txt", "test/hello.txt"));

            // 11. 确认对象已清空
            System.out.println("\n===== 11. 确认对象已清空 =====");
            List<S3Object> remaining = s3Service.listObjects(bucketName, "test/", 100);
            System.out.println("剩余对象数: " + remaining.size());

            // 12. 删除存储桶
            System.out.println("\n===== 12. 删除存储桶 =====");
            s3Service.deleteBucket(bucketName);
            exists = s3Service.bucketExists(bucketName);
            System.out.println("存储桶 " + bucketName + " 存在: " + exists);

            System.out.println("\n========== S3 完整测试通过！==========");

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            tempFile.delete();
            new File(downloadPath).delete();
        }
    }
}
