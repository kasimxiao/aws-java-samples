package com.aws.sample.ecr;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.aws.sample.common.AwsConfig;

import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.AuthorizationData;
import software.amazon.awssdk.services.ecr.model.BatchDeleteImageRequest;
import software.amazon.awssdk.services.ecr.model.BatchDeleteImageResponse;
import software.amazon.awssdk.services.ecr.model.CreateRepositoryRequest;
import software.amazon.awssdk.services.ecr.model.CreateRepositoryResponse;
import software.amazon.awssdk.services.ecr.model.DeleteRepositoryRequest;
import software.amazon.awssdk.services.ecr.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ecr.model.DescribeImagesResponse;
import software.amazon.awssdk.services.ecr.model.DescribeRepositoriesRequest;
import software.amazon.awssdk.services.ecr.model.DescribeRepositoriesResponse;
import software.amazon.awssdk.services.ecr.model.EncryptionConfiguration;
import software.amazon.awssdk.services.ecr.model.EncryptionType;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenRequest;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenResponse;
import software.amazon.awssdk.services.ecr.model.GetLifecyclePolicyRequest;
import software.amazon.awssdk.services.ecr.model.GetLifecyclePolicyResponse;
import software.amazon.awssdk.services.ecr.model.ImageDetail;
import software.amazon.awssdk.services.ecr.model.ImageFailure;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;
import software.amazon.awssdk.services.ecr.model.ImageScanningConfiguration;
import software.amazon.awssdk.services.ecr.model.ImageTagMutability;
import software.amazon.awssdk.services.ecr.model.LifecyclePolicyNotFoundException;
import software.amazon.awssdk.services.ecr.model.ListImagesRequest;
import software.amazon.awssdk.services.ecr.model.ListImagesResponse;
import software.amazon.awssdk.services.ecr.model.PutLifecyclePolicyRequest;
import software.amazon.awssdk.services.ecr.model.Repository;
import software.amazon.awssdk.services.ecr.model.Tag;

/**
 * ECR 服务类
 * 提供 ECR 仓库管理、镜像操作、授权令牌获取等功能
 */
public class EcrService implements AutoCloseable {

    private final EcrClient ecrClient;
    private final AwsConfig config;

    public EcrService(AwsConfig config) {
        this.config = config;
        this.ecrClient = EcrClient.builder()
                .region(config.getRegion())
                .credentialsProvider(config.getCredentialsProvider())
                .build();
    }

    // ==================== 仓库管理 ====================

    /**
     * 创建 ECR 仓库
     * @param repositoryName 仓库名称
     * @return 仓库 URI
     */
    public String createRepository(String repositoryName) {
        return createRepository(repositoryName, ImageTagMutability.MUTABLE, true);
    }

    /**
     * 创建 ECR 仓库（完整参数）
     * @param repositoryName 仓库名称
     * @param tagMutability 标签可变性（MUTABLE 或 IMMUTABLE）
     * @param scanOnPush 推送时是否自动扫描漏洞
     * @return 仓库 URI
     */
    public String createRepository(String repositoryName, ImageTagMutability tagMutability, boolean scanOnPush) {
        CreateRepositoryRequest request = CreateRepositoryRequest.builder()
                .repositoryName(repositoryName)
                .imageTagMutability(tagMutability)
                .imageScanningConfiguration(ImageScanningConfiguration.builder()
                        .scanOnPush(scanOnPush)
                        .build())
                .encryptionConfiguration(EncryptionConfiguration.builder()
                        .encryptionType(EncryptionType.AES256)
                        .build())
                .build();

        CreateRepositoryResponse response = ecrClient.createRepository(request);
        Repository repo = response.repository();
        System.out.println("ECR 仓库创建成功: " + repo.repositoryName());
        System.out.println("仓库 URI: " + repo.repositoryUri());
        return repo.repositoryUri();
    }

    /**
     * 创建带标签的 ECR 仓库
     */
    public String createRepositoryWithTags(String repositoryName, Map<String, String> tags) {
        List<Tag> tagList = tags.entrySet().stream()
                .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                .toList();

        CreateRepositoryRequest request = CreateRepositoryRequest.builder()
                .repositoryName(repositoryName)
                .imageTagMutability(ImageTagMutability.MUTABLE)
                .imageScanningConfiguration(ImageScanningConfiguration.builder()
                        .scanOnPush(true)
                        .build())
                .tags(tagList)
                .build();

        CreateRepositoryResponse response = ecrClient.createRepository(request);
        System.out.println("ECR 仓库创建成功（带标签）: " + response.repository().repositoryName());
        return response.repository().repositoryUri();
    }


    /**
     * 获取仓库描述信息
     * @param repositoryName 仓库名称
     * @return Repository 对象
     */
    public Repository describeRepository(String repositoryName) {
        DescribeRepositoriesRequest request = DescribeRepositoriesRequest.builder()
                .repositoryNames(repositoryName)
                .build();

        DescribeRepositoriesResponse response = ecrClient.describeRepositories(request);
        if (response.repositories().isEmpty()) {
            System.out.println("未找到仓库: " + repositoryName);
            return null;
        }

        Repository repo = response.repositories().get(0);
        printRepositoryInfo(repo);
        return repo;
    }

    /**
     * 列出所有仓库
     */
    public List<Repository> listRepositories() {
        DescribeRepositoriesRequest request = DescribeRepositoriesRequest.builder().build();
        DescribeRepositoriesResponse response = ecrClient.describeRepositories(request);

        System.out.println("找到 " + response.repositories().size() + " 个仓库:");
        for (Repository repo : response.repositories()) {
            System.out.println("  - " + repo.repositoryName() + " (" + repo.repositoryUri() + ")");
        }
        return response.repositories();
    }

    /**
     * 删除 ECR 仓库
     * @param repositoryName 仓库名称
     * @param force 是否强制删除（包含镜像时也删除）
     */
    public void deleteRepository(String repositoryName, boolean force) {
        DeleteRepositoryRequest request = DeleteRepositoryRequest.builder()
                .repositoryName(repositoryName)
                .force(force)
                .build();

        ecrClient.deleteRepository(request);
        System.out.println("ECR 仓库已删除: " + repositoryName);
    }

    /**
     * 删除 ECR 仓库（非强制）
     */
    public void deleteRepository(String repositoryName) {
        deleteRepository(repositoryName, false);
    }

    // ==================== 镜像管理 ====================

    /**
     * 列出仓库中的所有镜像
     * @param repositoryName 仓库名称
     */
    public List<ImageIdentifier> listImages(String repositoryName) {
        ListImagesRequest request = ListImagesRequest.builder()
                .repositoryName(repositoryName)
                .build();

        ListImagesResponse response = ecrClient.listImages(request);
        System.out.println("仓库 " + repositoryName + " 中的镜像:");
        for (ImageIdentifier image : response.imageIds()) {
            String tag = image.imageTag() != null ? image.imageTag() : "<untagged>";
            String digest = image.imageDigest() != null ? image.imageDigest().substring(0, 20) + "..." : "N/A";
            System.out.println("  - Tag: " + tag + ", Digest: " + digest);
        }
        return response.imageIds();
    }

    /**
     * 获取镜像详细信息
     * @param repositoryName 仓库名称
     * @param imageTag 镜像标签
     */
    public ImageDetail describeImage(String repositoryName, String imageTag) {
        DescribeImagesRequest request = DescribeImagesRequest.builder()
                .repositoryName(repositoryName)
                .imageIds(ImageIdentifier.builder().imageTag(imageTag).build())
                .build();

        DescribeImagesResponse response = ecrClient.describeImages(request);
        if (response.imageDetails().isEmpty()) {
            System.out.println("未找到镜像: " + repositoryName + ":" + imageTag);
            return null;
        }

        ImageDetail detail = response.imageDetails().get(0);
        printImageDetail(detail);
        return detail;
    }

    /**
     * 列出镜像详细信息
     */
    public List<ImageDetail> describeImages(String repositoryName) {
        DescribeImagesRequest request = DescribeImagesRequest.builder()
                .repositoryName(repositoryName)
                .build();

        DescribeImagesResponse response = ecrClient.describeImages(request);
        System.out.println("仓库 " + repositoryName + " 镜像详情:");
        for (ImageDetail detail : response.imageDetails()) {
            printImageDetail(detail);
        }
        return response.imageDetails();
    }

    /**
     * 删除镜像
     * @param repositoryName 仓库名称
     * @param imageTag 镜像标签
     */
    public void deleteImage(String repositoryName, String imageTag) {
        BatchDeleteImageRequest request = BatchDeleteImageRequest.builder()
                .repositoryName(repositoryName)
                .imageIds(ImageIdentifier.builder().imageTag(imageTag).build())
                .build();

        BatchDeleteImageResponse response = ecrClient.batchDeleteImage(request);
        if (!response.imageIds().isEmpty()) {
            System.out.println("镜像已删除: " + repositoryName + ":" + imageTag);
        }
        if (!response.failures().isEmpty()) {
            for (ImageFailure failure : response.failures()) {
                System.out.println("删除失败: " + failure.failureReason());
            }
        }
    }

    /**
     * 批量删除镜像
     */
    public void deleteImages(String repositoryName, List<String> imageTags) {
        List<ImageIdentifier> imageIds = imageTags.stream()
                .map(tag -> ImageIdentifier.builder().imageTag(tag).build())
                .toList();

        BatchDeleteImageRequest request = BatchDeleteImageRequest.builder()
                .repositoryName(repositoryName)
                .imageIds(imageIds)
                .build();

        BatchDeleteImageResponse response = ecrClient.batchDeleteImage(request);
        System.out.println("成功删除 " + response.imageIds().size() + " 个镜像");
        if (!response.failures().isEmpty()) {
            System.out.println("失败 " + response.failures().size() + " 个");
        }
    }


    // ==================== 授权令牌 ====================

    /**
     * 获取 ECR 授权令牌
     * 用于 Docker 登录 ECR
     * @return AuthorizationData 包含 token 和 endpoint
     */
    public AuthorizationData getAuthorizationToken() {
        GetAuthorizationTokenRequest request = GetAuthorizationTokenRequest.builder().build();
        GetAuthorizationTokenResponse response = ecrClient.getAuthorizationToken(request);

        if (response.authorizationData().isEmpty()) {
            throw new RuntimeException("无法获取授权令牌");
        }

        AuthorizationData authData = response.authorizationData().get(0);
        System.out.println("授权令牌获取成功");
        System.out.println("Proxy Endpoint: " + authData.proxyEndpoint());
        System.out.println("Token 有效期至: " + authData.expiresAt());
        return authData;
    }

    /**
     * 获取 Docker 登录命令
     * @return Docker login 命令字符串
     */
    public String getDockerLoginCommand() {
        AuthorizationData authData = getAuthorizationToken();
        String token = authData.authorizationToken();
        String endpoint = authData.proxyEndpoint();

        // 解码 Base64 token（格式为 AWS:password）
        String decoded = new String(Base64.getDecoder().decode(token));
        String password = decoded.split(":")[1];

        String loginCommand = String.format(
                "docker login --username AWS --password %s %s",
                password, endpoint
        );

        System.out.println("\nDocker 登录命令:");
        System.out.println("docker login --username AWS --password-stdin " + endpoint);
        System.out.println("\n或使用 AWS CLI:");
        System.out.println("aws ecr get-login-password --region " + config.getRegion().id() +
                " | docker login --username AWS --password-stdin " + endpoint);

        return loginCommand;
    }

    /**
     * 获取推送镜像的完整命令
     * @param repositoryName 仓库名称
     * @param localImage 本地镜像名称
     * @param tag 目标标签
     */
    public void printPushCommands(String repositoryName, String localImage, String tag) {
        Repository repo = describeRepository(repositoryName);
        if (repo == null) {
            System.out.println("仓库不存在，请先创建仓库");
            return;
        }

        String repositoryUri = repo.repositoryUri();
        String fullImageUri = repositoryUri + ":" + tag;

        System.out.println("\n==================== 推送镜像步骤 ====================");
        System.out.println("1. 获取 ECR 登录凭证:");
        System.out.println("   aws ecr get-login-password --region " + config.getRegion().id() +
                " | docker login --username AWS --password-stdin " + repositoryUri.split("/")[0]);

        System.out.println("\n2. 标记本地镜像:");
        System.out.println("   docker tag " + localImage + " " + fullImageUri);

        System.out.println("\n3. 推送镜像到 ECR:");
        System.out.println("   docker push " + fullImageUri);
        System.out.println("=====================================================");
    }

    // ==================== 生命周期策略 ====================

    /**
     * 设置镜像生命周期策略（保留最近 N 个镜像）
     * @param repositoryName 仓库名称
     * @param keepCount 保留的镜像数量
     */
    public void setLifecyclePolicy(String repositoryName, int keepCount) {
        String policyText = String.format("""
            {
                "rules": [
                    {
                        "rulePriority": 1,
                        "description": "保留最近 %d 个镜像",
                        "selection": {
                            "tagStatus": "any",
                            "countType": "imageCountMoreThan",
                            "countNumber": %d
                        },
                        "action": {
                            "type": "expire"
                        }
                    }
                ]
            }
            """, keepCount, keepCount);

        PutLifecyclePolicyRequest request = PutLifecyclePolicyRequest.builder()
                .repositoryName(repositoryName)
                .lifecyclePolicyText(policyText)
                .build();

        ecrClient.putLifecyclePolicy(request);
        System.out.println("生命周期策略已设置: 保留最近 " + keepCount + " 个镜像");
    }

    /**
     * 获取生命周期策略
     */
    public String getLifecyclePolicy(String repositoryName) {
        try {
            GetLifecyclePolicyRequest request = GetLifecyclePolicyRequest.builder()
                    .repositoryName(repositoryName)
                    .build();

            GetLifecyclePolicyResponse response = ecrClient.getLifecyclePolicy(request);
            System.out.println("生命周期策略:\n" + response.lifecyclePolicyText());
            return response.lifecyclePolicyText();
        } catch (LifecyclePolicyNotFoundException e) {
            System.out.println("仓库 " + repositoryName + " 未设置生命周期策略");
            return null;
        }
    }

    // ==================== 辅助方法 ====================

    private void printRepositoryInfo(Repository repo) {
        System.out.println("==================== 仓库信息 ====================");
        System.out.println("仓库名称: " + repo.repositoryName());
        System.out.println("仓库 ARN: " + repo.repositoryArn());
        System.out.println("仓库 URI: " + repo.repositoryUri());
        System.out.println("Registry ID: " + repo.registryId());
        System.out.println("创建时间: " + repo.createdAt());
        System.out.println("标签可变性: " + repo.imageTagMutability());
        if (repo.imageScanningConfiguration() != null) {
            System.out.println("推送时扫描: " + repo.imageScanningConfiguration().scanOnPush());
        }
        System.out.println("================================================");
    }

    private void printImageDetail(ImageDetail detail) {
        System.out.println("  镜像 Digest: " + detail.imageDigest());
        System.out.println("    Tags: " + (detail.imageTags() != null ? detail.imageTags() : "无"));
        System.out.println("    大小: " + formatSize(detail.imageSizeInBytes()));
        System.out.println("    推送时间: " + detail.imagePushedAt());
        if (detail.imageScanStatus() != null) {
            System.out.println("    扫描状态: " + detail.imageScanStatus().status());
        }
    }

    private String formatSize(Long bytes) {
        if (bytes == null) return "N/A";
        double mb = bytes / (1024.0 * 1024.0);
        return String.format("%.2f MB", mb);
    }

    @Override
    public void close() {
        if (ecrClient != null) {
            ecrClient.close();
        }
    }
}
