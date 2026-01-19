package com.aws.sample.ec2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.ec2.model.InstanceInfo;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.BlockDeviceMapping;
import software.amazon.awssdk.services.ec2.model.CopyImageRequest;
import software.amazon.awssdk.services.ec2.model.CopyImageResponse;
import software.amazon.awssdk.services.ec2.model.CreateImageRequest;
import software.amazon.awssdk.services.ec2.model.CreateImageResponse;
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
import software.amazon.awssdk.services.ec2.model.DeleteSnapshotRequest;
import software.amazon.awssdk.services.ec2.model.DeleteTagsRequest;
import software.amazon.awssdk.services.ec2.model.DeregisterImageRequest;
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeImagesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeTagsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeTagsResponse;
import software.amazon.awssdk.services.ec2.model.EbsBlockDevice;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.IamInstanceProfileSpecification;
import software.amazon.awssdk.services.ec2.model.Image;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.LaunchPermission;
import software.amazon.awssdk.services.ec2.model.LaunchPermissionModifications;
import software.amazon.awssdk.services.ec2.model.ModifyImageAttributeRequest;
import software.amazon.awssdk.services.ec2.model.RebootInstancesRequest;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.ResourceType;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.ec2.model.StartInstancesRequest;
import software.amazon.awssdk.services.ec2.model.StopInstancesRequest;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.TagDescription;
import software.amazon.awssdk.services.ec2.model.TagSpecification;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import software.amazon.awssdk.services.ec2.model.VolumeType;

/**
 * EC2 实例服务类
 * 提供 EC2 实例的创建、启动、停止、删除等操作
 */
public class Ec2Service implements AutoCloseable {

    private final Ec2Client ec2Client;
    private final AwsConfig config;

    public Ec2Service(AwsConfig config) {
        this.config = config;
        this.ec2Client = Ec2Client.builder()
                .region(config.getRegion())
                .credentialsProvider(config.getCredentialsProvider())
                .build();
    }

    /**
     * 创建 EC2 实例（完整参数）
     */
    public String createInstance(String amiId, String instanceType, String keyName,
                                  List<String> securityGroupIds, String subnetId,
                                  int ebsVolumeSize, String ebsVolumeType,
                                  String instanceName, String iamInstanceProfile) {

        EbsBlockDevice ebsBlockDevice = EbsBlockDevice.builder()
                .volumeSize(ebsVolumeSize)
                .volumeType(VolumeType.fromValue(ebsVolumeType))
                .deleteOnTermination(true)
                .encrypted(true)
                .build();

        BlockDeviceMapping blockDeviceMapping = BlockDeviceMapping.builder()
                .deviceName("/dev/xvda")
                .ebs(ebsBlockDevice)
                .build();

        RunInstancesRequest.Builder requestBuilder = RunInstancesRequest.builder()
                .imageId(amiId)
                .instanceType(InstanceType.fromValue(instanceType))
                .keyName(keyName)
                .securityGroupIds(securityGroupIds)
                .subnetId(subnetId)
                .blockDeviceMappings(blockDeviceMapping)
                .minCount(1)
                .maxCount(1)
                .tagSpecifications(TagSpecification.builder()
                        .resourceType(ResourceType.INSTANCE)
                        .tags(Tag.builder().key("Name").value(instanceName).build())
                        .build());

        if (iamInstanceProfile != null && !iamInstanceProfile.isEmpty()) {
            requestBuilder.iamInstanceProfile(
                    IamInstanceProfileSpecification.builder().name(iamInstanceProfile).build()
            );
        }

        RunInstancesResponse response = ec2Client.runInstances(requestBuilder.build());
        String instanceId = response.instances().get(0).instanceId();
        System.out.println("EC2 实例创建成功，实例 ID: " + instanceId);
        return instanceId;
    }

    /**
     * 使用默认配置创建实例
     */
    public String createInstanceWithDefaults(String instanceName) {
        return createInstance(
                config.getDefaultAmi(),
                config.getInstanceType(),
                config.getKeyName(),
                config.getSecurityGroupIds(),
                config.getSubnetId(),
                config.getEbsSize(),
                config.getEbsType(),
                instanceName != null ? instanceName : config.getNamePrefix(),
                config.getInstanceProfile().isEmpty() ? null : config.getInstanceProfile()
        );
    }

    public void startInstance(String instanceId) {
        ec2Client.startInstances(StartInstancesRequest.builder().instanceIds(instanceId).build());
        System.out.println("实例启动请求已发送: " + instanceId);
        waitForState(instanceId, InstanceStateName.RUNNING);
    }

    public void stopInstance(String instanceId) {
        ec2Client.stopInstances(StopInstancesRequest.builder().instanceIds(instanceId).build());
        System.out.println("实例停止请求已发送: " + instanceId);
        waitForState(instanceId, InstanceStateName.STOPPED);
    }

    public void terminateInstance(String instanceId) {
        ec2Client.terminateInstances(TerminateInstancesRequest.builder().instanceIds(instanceId).build());
        System.out.println("实例终止请求已发送: " + instanceId);
        waitForState(instanceId, InstanceStateName.TERMINATED);
    }

    public void rebootInstance(String instanceId) {
        ec2Client.rebootInstances(RebootInstancesRequest.builder().instanceIds(instanceId).build());
        System.out.println("实例重启请求已发送: " + instanceId);
    }

    public InstanceInfo getInstanceInfo(String instanceId) {
        DescribeInstancesResponse response = ec2Client.describeInstances(
                DescribeInstancesRequest.builder().instanceIds(instanceId).build()
        );
        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                if (instance.instanceId().equals(instanceId)) {
                    return new InstanceInfo(instance);
                }
            }
        }
        return null;
    }

    public List<InstanceInfo> listAllInstances() {
        DescribeInstancesResponse response = ec2Client.describeInstances();
        return response.reservations().stream()
                .flatMap(r -> r.instances().stream())
                .map(InstanceInfo::new)
                .toList();
    }

    public List<InstanceInfo> findInstancesByTag(String tagKey, String tagValue) {
        Filter filter = Filter.builder().name("tag:" + tagKey).values(tagValue).build();
        DescribeInstancesResponse response = ec2Client.describeInstances(
                DescribeInstancesRequest.builder().filters(filter).build()
        );
        return response.reservations().stream()
                .flatMap(r -> r.instances().stream())
                .map(InstanceInfo::new)
                .toList();
    }

    private void waitForState(String instanceId, InstanceStateName targetState) {
        int maxAttempts = 60;
        for (int i = 0; i < maxAttempts; i++) {
            InstanceInfo info = getInstanceInfo(instanceId);
            if (info != null && info.getState().equalsIgnoreCase(targetState.toString())) {
                System.out.println("实例已达到状态: " + targetState);
                return;
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待被中断", e);
            }
        }
        throw new RuntimeException("等待实例状态超时: " + instanceId);
    }

    // ==================== 标签管理 ====================

    public void addTag(String instanceId, String key, String value) {
        CreateTagsRequest request = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(Tag.builder().key(key).value(value).build())
                .build();
        ec2Client.createTags(request);
        System.out.println("标签已添加: " + key + "=" + value);
    }

    public void addTags(String instanceId, Map<String, String> tags) {
        List<Tag> tagList = tags.entrySet().stream()
                .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                .toList();

        CreateTagsRequest request = CreateTagsRequest.builder()
                .resources(instanceId)
                .tags(tagList)
                .build();
        ec2Client.createTags(request);
        System.out.println("已添加 " + tags.size() + " 个标签");
    }

    public void deleteTag(String instanceId, String key) {
        DeleteTagsRequest request = DeleteTagsRequest.builder()
                .resources(instanceId)
                .tags(Tag.builder().key(key).build())
                .build();
        ec2Client.deleteTags(request);
        System.out.println("标签已删除: " + key);
    }

    public void deleteTags(String instanceId, List<String> keys) {
        List<Tag> tagList = keys.stream()
                .map(k -> Tag.builder().key(k).build())
                .toList();

        DeleteTagsRequest request = DeleteTagsRequest.builder()
                .resources(instanceId)
                .tags(tagList)
                .build();
        ec2Client.deleteTags(request);
        System.out.println("已删除 " + keys.size() + " 个标签");
    }

    public void updateTag(String instanceId, String key, String value) {
        addTag(instanceId, key, value);
        System.out.println("标签已更新: " + key + "=" + value);
    }

    public Map<String, String> getTags(String instanceId) {
        DescribeTagsRequest request = DescribeTagsRequest.builder()
                .filters(
                        Filter.builder().name("resource-id").values(instanceId).build(),
                        Filter.builder().name("resource-type").values("instance").build()
                )
                .build();

        DescribeTagsResponse response = ec2Client.describeTags(request);
        Map<String, String> tags = new HashMap<>();
        for (TagDescription tag : response.tags()) {
            tags.put(tag.key(), tag.value());
        }
        return tags;
    }

    public String getTagValue(String instanceId, String key) {
        Map<String, String> tags = getTags(instanceId);
        return tags.get(key);
    }

    // ==================== AMI 镜像管理 ====================

    /**
     * 从 EC2 实例创建 AMI 镜像
     * @param instanceId 实例 ID
     * @param imageName 镜像名称
     * @param description 镜像描述
     * @param noReboot 是否不重启实例（true=不重启，可能导致文件系统不一致）
     * @return AMI ID
     */
    public String createImage(String instanceId, String imageName, String description, boolean noReboot) {
        CreateImageRequest request = CreateImageRequest.builder()
                .instanceId(instanceId)
                .name(imageName)
                .description(description)
                .noReboot(noReboot)
                .tagSpecifications(TagSpecification.builder()
                        .resourceType(ResourceType.IMAGE)
                        .tags(
                                Tag.builder().key("Name").value(imageName).build(),
                                Tag.builder().key("SourceInstance").value(instanceId).build()
                        )
                        .build())
                .build();

        CreateImageResponse response = ec2Client.createImage(request);
        String imageId = response.imageId();
        System.out.println("AMI 创建请求已提交，镜像 ID: " + imageId);
        return imageId;
    }

    /**
     * 从 EC2 实例创建 AMI 镜像（默认重启以确保数据一致性）
     */
    public String createImage(String instanceId, String imageName, String description) {
        return createImage(instanceId, imageName, description, false);
    }

    /**
     * 等待 AMI 镜像可用
     * @param imageId 镜像 ID
     * @param timeoutMinutes 超时时间（分钟）
     */
    public void waitForImageAvailable(String imageId, int timeoutMinutes) {
        System.out.println("等待 AMI 镜像可用: " + imageId);
        int maxAttempts = timeoutMinutes * 6; // 每10秒检查一次
        for (int i = 0; i < maxAttempts; i++) {
            ImageInfo info = getImageInfo(imageId);
            if (info != null) {
                String state = info.getState();
                System.out.println("当前状态: " + state + " (" + (i * 10) + "秒)");
                if ("available".equalsIgnoreCase(state)) {
                    System.out.println("AMI 镜像已可用: " + imageId);
                    return;
                } else if ("failed".equalsIgnoreCase(state)) {
                    throw new RuntimeException("AMI 创建失败: " + imageId);
                }
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待被中断", e);
            }
        }
        throw new RuntimeException("等待 AMI 可用超时: " + imageId);
    }

    /**
     * 获取 AMI 镜像信息
     */
    public ImageInfo getImageInfo(String imageId) {
        DescribeImagesRequest request = DescribeImagesRequest.builder()
                .imageIds(imageId)
                .build();
        DescribeImagesResponse response = ec2Client.describeImages(request);
        if (!response.images().isEmpty()) {
            return new ImageInfo(response.images().get(0));
        }
        return null;
    }

    /**
     * 列出自己拥有的所有 AMI 镜像
     */
    public List<ImageInfo> listOwnedImages() {
        DescribeImagesRequest request = DescribeImagesRequest.builder()
                .owners("self")
                .build();
        DescribeImagesResponse response = ec2Client.describeImages(request);
        return response.images().stream()
                .map(ImageInfo::new)
                .toList();
    }

    /**
     * 根据名称查找 AMI 镜像
     */
    public List<ImageInfo> findImagesByName(String namePattern) {
        DescribeImagesRequest request = DescribeImagesRequest.builder()
                .owners("self")
                .filters(Filter.builder()
                        .name("name")
                        .values("*" + namePattern + "*")
                        .build())
                .build();
        DescribeImagesResponse response = ec2Client.describeImages(request);
        return response.images().stream()
                .map(ImageInfo::new)
                .toList();
    }

    /**
     * 删除 AMI 镜像
     * @param imageId 镜像 ID
     * @param deleteSnapshots 是否同时删除关联的快照
     */
    public void deregisterImage(String imageId, boolean deleteSnapshots) {
        // 先获取镜像信息以获取关联的快照
        List<String> snapshotIds = new ArrayList<>();
        if (deleteSnapshots) {
            ImageInfo info = getImageInfo(imageId);
            if (info != null) {
                snapshotIds.addAll(info.getSnapshotIds());
            }
        }

        // 注销 AMI
        DeregisterImageRequest request = DeregisterImageRequest.builder()
                .imageId(imageId)
                .build();
        ec2Client.deregisterImage(request);
        System.out.println("AMI 已注销: " + imageId);

        // 删除关联的快照
        if (deleteSnapshots && !snapshotIds.isEmpty()) {
            for (String snapshotId : snapshotIds) {
                deleteSnapshot(snapshotId);
            }
        }
    }

    /**
     * 删除 EBS 快照
     */
    public void deleteSnapshot(String snapshotId) {
        DeleteSnapshotRequest request = DeleteSnapshotRequest.builder()
                .snapshotId(snapshotId)
                .build();
        ec2Client.deleteSnapshot(request);
        System.out.println("快照已删除: " + snapshotId);
    }

    /**
     * 复制 AMI 到其他区域
     * @param sourceImageId 源镜像 ID
     * @param sourceRegion 源区域
     * @param destinationRegion 目标区域
     * @param imageName 新镜像名称
     * @param description 描述
     * @return 新镜像 ID
     */
    public String copyImageToRegion(String sourceImageId, String sourceRegion, 
                                     String destinationRegion, String imageName, String description) {
        // 创建目标区域的 EC2 客户端
        try (Ec2Client destClient = Ec2Client.builder()
                .region(software.amazon.awssdk.regions.Region.of(destinationRegion))
                .credentialsProvider(config.getCredentialsProvider())
                .build()) {

            CopyImageRequest request = CopyImageRequest.builder()
                    .sourceImageId(sourceImageId)
                    .sourceRegion(sourceRegion)
                    .name(imageName)
                    .description(description)
                    .build();

            CopyImageResponse response = destClient.copyImage(request);
            String newImageId = response.imageId();
            System.out.println("AMI 复制请求已提交，新镜像 ID: " + newImageId + " (区域: " + destinationRegion + ")");
            return newImageId;
        }
    }

    /**
     * 为 AMI 添加标签
     */
    public void addImageTag(String imageId, String key, String value) {
        CreateTagsRequest request = CreateTagsRequest.builder()
                .resources(imageId)
                .tags(Tag.builder().key(key).value(value).build())
                .build();
        ec2Client.createTags(request);
        System.out.println("AMI 标签已添加: " + key + "=" + value);
    }

    /**
     * 修改 AMI 启动权限（公开/私有）
     */
    public void modifyImageLaunchPermission(String imageId, boolean makePublic) {
        ModifyImageAttributeRequest.Builder requestBuilder = ModifyImageAttributeRequest.builder()
                .imageId(imageId)
                .attribute("launchPermission");

        if (makePublic) {
            requestBuilder.launchPermission(LaunchPermissionModifications.builder()
                    .add(LaunchPermission.builder().group("all").build())
                    .build());
        } else {
            requestBuilder.launchPermission(LaunchPermissionModifications.builder()
                    .remove(LaunchPermission.builder().group("all").build())
                    .build());
        }

        ec2Client.modifyImageAttribute(requestBuilder.build());
        System.out.println("AMI 启动权限已修改: " + (makePublic ? "公开" : "私有"));
    }

    /**
     * 共享 AMI 给指定账户
     */
    public void shareImageWithAccount(String imageId, String accountId) {
        ModifyImageAttributeRequest request = ModifyImageAttributeRequest.builder()
                .imageId(imageId)
                .attribute("launchPermission")
                .launchPermission(LaunchPermissionModifications.builder()
                        .add(LaunchPermission.builder().userId(accountId).build())
                        .build())
                .build();
        ec2Client.modifyImageAttribute(request);
        System.out.println("AMI 已共享给账户: " + accountId);
    }

    /**
     * AMI 镜像信息类
     */
    public static class ImageInfo {
        private final String imageId;
        private final String name;
        private final String description;
        private final String state;
        private final String architecture;
        private final String creationDate;
        private final String ownerId;
        private final boolean isPublic;
        private final List<String> snapshotIds;
        private final Map<String, String> tags;

        public ImageInfo(Image image) {
            this.imageId = image.imageId();
            this.name = image.name();
            this.description = image.description();
            this.state = image.stateAsString();
            this.architecture = image.architectureAsString();
            this.creationDate = image.creationDate();
            this.ownerId = image.ownerId();
            this.isPublic = image.publicLaunchPermissions() != null && image.publicLaunchPermissions();

            // 提取快照 ID
            this.snapshotIds = image.blockDeviceMappings().stream()
                    .filter(bdm -> bdm.ebs() != null && bdm.ebs().snapshotId() != null)
                    .map(bdm -> bdm.ebs().snapshotId())
                    .toList();

            // 提取标签
            this.tags = new HashMap<>();
            if (image.tags() != null) {
                for (Tag tag : image.tags()) {
                    this.tags.put(tag.key(), tag.value());
                }
            }
        }

        public String getImageId() { return imageId; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getState() { return state; }
        public String getArchitecture() { return architecture; }
        public String getCreationDate() { return creationDate; }
        public String getOwnerId() { return ownerId; }
        public boolean isPublic() { return isPublic; }
        public List<String> getSnapshotIds() { return snapshotIds; }
        public Map<String, String> getTags() { return tags; }

        @Override
        public String toString() {
            return String.format("ImageInfo{id='%s', name='%s', state='%s', arch='%s', created='%s'}",
                    imageId, name, state, architecture, creationDate);
        }
    }

    @Override
    public void close() {
        if (ec2Client != null) {
            ec2Client.close();
        }
    }
}
