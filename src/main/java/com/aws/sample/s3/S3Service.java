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
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;

/**
 * S3 服务类
 * 提供 S3 存储桶管理和对象操作功能
 */
public class S3Service implements AutoCloseable {

    private final S3Client s3Client;
    private final AwsConfig config;

    public S3Service(AwsConfig config) {
        this.config = config;
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

        // 创建后打标签
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
        // 先清空存储桶
        deleteAllObjects(bucketName);
        // 再删除存储桶
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
