package com.aws.sample.ec2;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.ec2.model.InstanceInfo;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Override
    public void close() {
        if (ec2Client != null) {
            ec2Client.close();
        }
    }
}
