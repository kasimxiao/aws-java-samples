package com.aws.sample;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.common.model.CommandResult;
import com.aws.sample.dcv.DcvService;
import com.aws.sample.ec2.Ec2Service;
import com.aws.sample.ec2.model.InstanceInfo;
import com.aws.sample.iam.IamService;
import com.aws.sample.ssm.SsmService;

import java.util.List;
import java.util.Map;

/**
 * EC2 管理器 - 统一入口类
 * 整合 EC2、IAM、SSM、DCV 等服务
 */
public class Ec2Manager implements AutoCloseable {

    private final AwsConfig config;
    private final Ec2Service ec2Service;
    private final IamService iamService;
    private final SsmService ssmService;
    private final DcvService dcvService;

    public Ec2Manager() {
        this(new AwsConfig());
    }

    public Ec2Manager(AwsConfig config) {
        this.config = config;
        this.ec2Service = new Ec2Service(config);
        this.iamService = new IamService(config);
        this.ssmService = new SsmService(config);
        this.dcvService = new DcvService(config);
    }

    public AwsConfig getConfig() { return config; }

    // ==================== EC2 操作 ====================

    public String createInstanceWithDefaults(String instanceName) {
        return ec2Service.createInstanceWithDefaults(instanceName);
    }

    public String createInstance(String amiId, String instanceType, String keyName,
                                  List<String> securityGroupIds, String subnetId,
                                  int ebsVolumeSize, String ebsVolumeType,
                                  String instanceName, String iamInstanceProfile) {
        return ec2Service.createInstance(amiId, instanceType, keyName, securityGroupIds,
                subnetId, ebsVolumeSize, ebsVolumeType, instanceName, iamInstanceProfile);
    }

    public void startInstance(String instanceId) { ec2Service.startInstance(instanceId); }
    public void stopInstance(String instanceId) { ec2Service.stopInstance(instanceId); }
    public void terminateInstance(String instanceId) { ec2Service.terminateInstance(instanceId); }
    public void rebootInstance(String instanceId) { ec2Service.rebootInstance(instanceId); }
    public InstanceInfo getInstanceInfo(String instanceId) { return ec2Service.getInstanceInfo(instanceId); }
    public List<InstanceInfo> listAllInstances() { return ec2Service.listAllInstances(); }
    public List<InstanceInfo> findInstancesByTag(String tagKey, String tagValue) {
        return ec2Service.findInstancesByTag(tagKey, tagValue);
    }

    // ==================== 标签管理 ====================

    public void addTag(String instanceId, String key, String value) { ec2Service.addTag(instanceId, key, value); }
    public void addTags(String instanceId, Map<String, String> tags) { ec2Service.addTags(instanceId, tags); }
    public void deleteTag(String instanceId, String key) { ec2Service.deleteTag(instanceId, key); }
    public void deleteTags(String instanceId, List<String> keys) { ec2Service.deleteTags(instanceId, keys); }
    public void updateTag(String instanceId, String key, String value) { ec2Service.updateTag(instanceId, key, value); }
    public Map<String, String> getTags(String instanceId) { return ec2Service.getTags(instanceId); }
    public String getTagValue(String instanceId, String key) { return ec2Service.getTagValue(instanceId, key); }

    // ==================== IAM 操作 ====================

    public String attachIamRole(String instanceId, String instanceProfileName) {
        return iamService.attachIamRole(instanceId, instanceProfileName);
    }
    public void disassociateIamRole(String instanceId) { iamService.disassociateIamRole(instanceId); }
    public String createInstanceProfile(String profileName, String roleName) {
        return iamService.createInstanceProfile(profileName, roleName);
    }

    // ==================== SSM 操作 ====================

    public String executeCommand(String instanceId, List<String> commands) {
        return ssmService.executeCommand(instanceId, commands);
    }
    public String mountS3Bucket(String instanceId, String s3BucketName, String mountPoint) {
        return ssmService.mountS3Bucket(instanceId, s3BucketName, mountPoint);
    }
    public String mountS3WithDefaults(String instanceId) { return ssmService.mountS3WithDefaults(instanceId); }
    public CommandResult getCommandResult(String commandId, String instanceId) {
        return ssmService.getCommandResult(commandId, instanceId);
    }

    // ==================== DCV 操作（免密登录）====================

    public String generateDcvPresignedUrl(String serverIp, int serverPort, String sessionId, String authToken) {
        return dcvService.generatePresignedUrl(serverIp, serverPort, sessionId, authToken);
    }

    public String generateDcvAuthToken(String instanceId, String sessionId, String user) {
        List<String> commands = dcvService.getTokenGenerationCommands(sessionId, user);
        String commandId = ssmService.executeCommand(instanceId, commands);
        CommandResult result = ssmService.getCommandResult(commandId, instanceId);
        
        if (!result.isSuccess()) {
            throw new RuntimeException("生成 DCV 令牌失败: " + result.getStandardError());
        }
        
        String output = result.getStandardOutput();
        String[] lines = output.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                return line;
            }
        }
        throw new RuntimeException("无法从输出中提取 DCV 令牌");
    }

    /**
     * 一键生成 DCV 免密登录 URL（推荐使用）
     */
    public String generateDcvPresignedUrlForInstance(String instanceId) {
        InstanceInfo info = getInstanceInfo(instanceId);
        if (info == null) {
            throw new RuntimeException("实例不存在: " + instanceId);
        }
        
        String ip = info.getPublicIpAddress();
        if (ip == null || ip.isEmpty()) {
            ip = info.getPrivateIpAddress();
        }
        
        String token = generateDcvAuthToken(instanceId, "console", "ec2-user");
        return generateDcvPresignedUrl(ip, config.getDcvPort(), "console", token);
    }

    @Override
    public void close() {
        ec2Service.close();
        iamService.close();
        ssmService.close();
    }
}
