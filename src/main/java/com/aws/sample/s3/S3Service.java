package com.aws.sample.s3;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.aws.sample.common.AwsConfig;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.GetBucketCorsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;

/**
 * S3 服务类
 * 提供 S3 存储桶管理、对象操作和 CORS 跨域配置功能
 */
public class S3Service implements AutoCloseable {

    private final S3Client s3Client;

    public S3Service(AwsConfig config) {
        this.s3Client = S3Client.builder()
                .region(config.getRegion())
                .credentialsProvider(config.getCredentialsProvider())
                .build();
    }

    // ==================== 存储桶管理 ====================

    /**
     * 创建 S3 存储桶
     * @param bucketName 存储桶名称
     * @return 存储桶名称
     */
    public String createBucket(String bucketName) {
        return createBucket(bucketName, null);
    }

    /**
     * 创建带标签的 S3 存储桶
     * @param bucketName 存储桶名称
     * @param tags 标签（key-value）
     * @return 存储桶名称
     */
    public String createBucket(String bucketName, Map<String, String> tags) {
        CreateBucketRequest request = CreateBucketRequest.builder()
                .bucket(bucketName)
                .build();

        CreateBucketResponse response = s3Client.createBucket(request);
        System.out.println("S3 存储桶创建成功: " + bucketName);
        System.out.println("Location: " + response.location());

        if (tags != null && !tags.isEmpty()) {
            tagBucket(bucketName, tags);
        }

        return bucketName;
    }

    /**
     * 为存储桶设置标签
     * @param bucketName 存储桶名称
     * @param tags 标签
     */
    public void tagBucket(String bucketName, Map<String, String> tags) {
        List<Tag> tagList = tags.entrySet().stream()
                .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                .toList();

        s3Client.putBucketTagging(b -> b
                .bucket(bucketName)
                .tagging(Tagging.builder().tagSet(tagList).build()));

        System.out.println("存储桶标签设置成功: " + tags);
    }

    /**
     * 删除 S3 存储桶（存储桶必须为空）
     * @param bucketName 存储桶名称
     */
    public void deleteBucket(String bucketName) {
        DeleteBucketRequest request = DeleteBucketRequest.builder()
                .bucket(bucketName)
                .build();

        s3Client.deleteBucket(request);
        System.out.println("S3 存储桶已删除: " + bucketName);
    }

    /**
     * 强制删除存储桶（先清空所有对象，再删除存储桶）
     * @param bucketName 存储桶名称
     */
    public void forceDeleteBucket(String bucketName) {
        deleteAllObjects(bucketName);
        deleteBucket(bucketName);
    }

    /**
     * 列出所有存储桶
     * @return 存储桶列表
     */
    public List<Bucket> listBuckets() {
        ListBucketsResponse response = s3Client.listBuckets();
        List<Bucket> buckets = response.buckets();

        System.out.println("==================== S3 存储桶列表 ====================");
        System.out.println("共 " + buckets.size() + " 个存储桶:");
        for (Bucket bucket : buckets) {
            System.out.printf("  - %s (创建时间: %s)%n", bucket.name(), bucket.creationDate());
        }
        System.out.println("=====================================================");
        return buckets;
    }

    /**
     * 检查存储桶是否存在
     * @param bucketName 存储桶名称
     * @return 是否存在
     */
    public boolean bucketExists(String bucketName) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        }
    }

    // ==================== 对象操作 ====================

    /**
     * 上传文件到 S3
     * @param bucketName 存储桶名称
     * @param key 对象键（S3 中的路径）
     * @param filePath 本地文件路径
     */
    public void uploadFile(String bucketName, String key, String filePath) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.putObject(request, RequestBody.fromFile(new File(filePath)));
        System.out.println("文件上传成功: " + filePath + " -> s3://" + bucketName + "/" + key);
    }

    /**
     * 上传字符串内容到 S3
     * @param bucketName 存储桶名称
     * @param key 对象键
     * @param content 文本内容
     */
    public void uploadContent(String bucketName, String key, String content) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.putObject(request, RequestBody.fromString(content));
        System.out.println("内容上传成功: s3://" + bucketName + "/" + key);
    }

    /**
     * 下载文件到本地
     * @param bucketName 存储桶名称
     * @param key 对象键
     * @param downloadPath 本地保存路径
     */
    public void downloadFile(String bucketName, String key, String downloadPath) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.getObject(request, Path.of(downloadPath));
        System.out.println("文件下载成功: s3://" + bucketName + "/" + key + " -> " + downloadPath);
    }

    /**
     * 删除单个对象
     * @param bucketName 存储桶名称
     * @param key 对象键
     */
    public void deleteObject(String bucketName, String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.deleteObject(request);
        System.out.println("对象已删除: s3://" + bucketName + "/" + key);
    }

    /**
     * 批量删除对象
     * @param bucketName 存储桶名称
     * @param keys 对象键列表
     */
    public void deleteObjects(String bucketName, List<String> keys) {
        List<ObjectIdentifier> identifiers = keys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();

        DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                .bucket(bucketName)
                .delete(Delete.builder().objects(identifiers).build())
                .build();

        s3Client.deleteObjects(request);
        System.out.println("批量删除完成，共删除 " + keys.size() + " 个对象");
    }

    /**
     * 删除存储桶中的所有对象
     * @param bucketName 存储桶名称
     */
    public void deleteAllObjects(String bucketName) {
        List<S3Object> objects = listObjects(bucketName);
        if (objects.isEmpty()) {
            System.out.println("存储桶为空，无需清理: " + bucketName);
            return;
        }

        List<String> keys = objects.stream().map(S3Object::key).toList();
        deleteObjects(bucketName, keys);
        System.out.println("已清空存储桶: " + bucketName);
    }

    /**
     * 列出存储桶中的对象
     * @param bucketName 存储桶名称
     * @return 对象列表
     */
    public List<S3Object> listObjects(String bucketName) {
        return listObjects(bucketName, null, 1000);
    }

    /**
     * 列出存储桶中指定前缀的对象
     * @param bucketName 存储桶名称
     * @param prefix 前缀过滤
     * @param maxKeys 最大返回数量
     * @return 对象列表
     */
    public List<S3Object> listObjects(String bucketName, String prefix, int maxKeys) {
        ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .maxKeys(maxKeys);

        if (prefix != null && !prefix.isEmpty()) {
            requestBuilder.prefix(prefix);
        }

        List<S3Object> allObjects = new ArrayList<>();
        ListObjectsV2Response response;
        do {
            response = s3Client.listObjectsV2(requestBuilder.build());
            allObjects.addAll(response.contents());
            requestBuilder.continuationToken(response.nextContinuationToken());
        } while (response.isTruncated());

        System.out.println("==================== 对象列表 ====================");
        System.out.println("存储桶: " + bucketName + (prefix != null ? " | 前缀: " + prefix : ""));
        System.out.println("共 " + allObjects.size() + " 个对象:");
        for (S3Object obj : allObjects) {
            System.out.printf("  - %s (大小: %s, 最后修改: %s)%n",
                    obj.key(), formatSize(obj.size()), obj.lastModified());
        }
        System.out.println("=================================================");
        return allObjects;
    }

    // ==================== CORS 跨域配置 ====================

    /**
     * 为存储桶设置 CORS 跨域配置（适用于前端 JavaScript 直接访问 S3 的场景）
     * 默认配置允许常见的 S3 操作：PUT、GET、DELETE、HEAD、POST
     *
     * @param bucketName     存储桶名称
     * @param allowedOrigins 允许的源域名列表，如 ["http://localhost:3000", "https://example.com"]
     */
    public void putBucketCorsForOrigins(String bucketName, List<String> allowedOrigins) {
        CORSRule corsRule = CORSRule.builder()
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "PUT", "POST", "DELETE", "HEAD")
                .allowedHeaders("*")
                .exposeHeaders("ETag", "x-amz-request-id", "x-amz-id-2", "Content-Length", "Content-Type")
                .maxAgeSeconds(3600)
                .build();

        putBucketCors(bucketName, List.of(corsRule));
    }

    /**
     * 为存储桶设置自定义 CORS 规则
     *
     * @param bucketName 存储桶名称
     * @param corsRules  CORS 规则列表
     */
    public void putBucketCors(String bucketName, List<CORSRule> corsRules) {
        CORSConfiguration corsConfiguration = CORSConfiguration.builder()
                .corsRules(corsRules)
                .build();

        PutBucketCorsRequest request = PutBucketCorsRequest.builder()
                .bucket(bucketName)
                .corsConfiguration(corsConfiguration)
                .build();

        s3Client.putBucketCors(request);
        System.out.println("存储桶 CORS 配置已设置: " + bucketName);
        for (CORSRule rule : corsRules) {
            System.out.println("  允许的源: " + rule.allowedOrigins());
            System.out.println("  允许的方法: " + rule.allowedMethods());
            System.out.println("  允许的头: " + rule.allowedHeaders());
        }
    }

    /**
     * 获取存储桶的 CORS 配置
     *
     * @param bucketName 存储桶名称
     * @return CORS 规则列表
     */
    public List<CORSRule> getBucketCors(String bucketName) {
        try {
            GetBucketCorsRequest request = GetBucketCorsRequest.builder()
                    .bucket(bucketName)
                    .build();

            GetBucketCorsResponse response = s3Client.getBucketCors(request);
            List<CORSRule> rules = response.corsRules();

            System.out.println("==================== CORS 配置 ====================");
            System.out.println("存储桶: " + bucketName);
            for (int i = 0; i < rules.size(); i++) {
                CORSRule rule = rules.get(i);
                System.out.println("规则 " + (i + 1) + ":");
                System.out.println("  允许的源: " + rule.allowedOrigins());
                System.out.println("  允许的方法: " + rule.allowedMethods());
                System.out.println("  允许的头: " + rule.allowedHeaders());
                System.out.println("  暴露的头: " + rule.exposeHeaders());
                System.out.println("  缓存时间: " + rule.maxAgeSeconds() + " 秒");
            }
            System.out.println("===================================================");
            return rules;
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("NoSuchCORSConfiguration")) {
                System.out.println("存储桶 " + bucketName + " 未配置 CORS");
                return List.of();
            }
            throw e;
        }
    }

    /**
     * 删除存储桶的 CORS 配置
     *
     * @param bucketName 存储桶名称
     */
    public void deleteBucketCors(String bucketName) {
        DeleteBucketCorsRequest request = DeleteBucketCorsRequest.builder()
                .bucket(bucketName)
                .build();

        s3Client.deleteBucketCors(request);
        System.out.println("存储桶 CORS 配置已删除: " + bucketName);
    }

    /**
     * 创建存储桶并配置 CORS（便捷方法，适用于前端直传场景）
     *
     * @param bucketName     存储桶名称
     * @param allowedOrigins 允许的源域名列表
     * @param tags           标签（可为 null）
     * @return 存储桶名称
     */
    public String createBucketWithCors(String bucketName, List<String> allowedOrigins, Map<String, String> tags) {
        createBucket(bucketName, tags);
        putBucketCorsForOrigins(bucketName, allowedOrigins);
        return bucketName;
    }

    /**
     * 格式化文件大小
     */
    private String formatSize(Long bytes) {
        if (bytes == null) return "未知";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    @Override
    public void close() {
        if (s3Client != null) {
            s3Client.close();
            System.out.println("S3 客户端已关闭");
        }
    }
}
