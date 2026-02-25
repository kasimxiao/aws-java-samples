package com.aws.sample;

import java.util.List;
import java.util.Map;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.common.model.CommandResult;
import com.aws.sample.dcv.DcvService;
import com.aws.sample.ec2.Ec2Service;
import com.aws.sample.ec2.model.InstanceInfo;
import com.aws.sample.iam.IamService;
import com.aws.sample.s3.S3Service;
import com.aws.sample.ssm.SsmService;
import com.aws.sample.sts.StsService;

import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.sts.model.Credentials;

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
    private final S3Service s3Service;
    private final StsService stsService;

    public Ec2Manager() {
        this(new AwsConfig());
    }

    public Ec2Manager(AwsConfig config) {
        this.config = config;
        this.ec2Service = new Ec2Service(config);
        this.iamService = new IamService(config);
        this.ssmService = new SsmService(config);
        this.dcvService = new DcvService(config);
        this.s3Service = new S3Service(config);
        this.stsService = new StsService(config);
    }

    public AwsConfig getConfig() { return config; }

    // ==================== EC2 操作 ====================

    public String createInstanceWithDefaults(String instanceName) {
        return ec2Service.createInstanceWithDefaults(instanceName);
    }

    public String createInstanceWithDefaults(String instanceName, Map<String, String> additionalTags) {
        return ec2Service.createInstanceWithDefaults(instanceName, additionalTags);
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

    /**
     * 生成 DCV 免密登录 URL
     */
    public String generateDcvPresignedUrl(String instanceId, String serverIp) {
        return dcvService.generatePresignedUrl(instanceId, serverIp);
    }

    /**
     * 生成 DCV 免密登录 URL（指定用户和会话）
     */
    public String generateDcvPresignedUrl(String instanceId, String serverIp, String sessionId, String user) {
        return dcvService.generatePresignedUrl(instanceId, serverIp, sessionId, user);
    }

    /**
     * 一键生成 DCV 免密登录 URL（自动获取 IP）
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
        
        return dcvService.generatePresignedUrl(instanceId, ip);
    }

    // ==================== S3 CORS 跨域配置 ====================

    /**
     * 为 S3 存储桶设置 CORS 跨域配置
     * @param bucketName 存储桶名称
     * @param allowedOrigins 允许的源域名列表
     */
    public void putBucketCorsForOrigins(String bucketName, List<String> allowedOrigins) {
        s3Service.putBucketCorsForOrigins(bucketName, allowedOrigins);
    }

    /**
     * 为 S3 存储桶设置自定义 CORS 规则
     */
    public void putBucketCors(String bucketName, List<CORSRule> corsRules) {
        s3Service.putBucketCors(bucketName, corsRules);
    }

    /**
     * 获取 S3 存储桶的 CORS 配置
     */
    public List<CORSRule> getBucketCors(String bucketName) {
        return s3Service.getBucketCors(bucketName);
    }

    /**
     * 删除 S3 存储桶的 CORS 配置
     */
    public void deleteBucketCors(String bucketName) {
        s3Service.deleteBucketCors(bucketName);
    }

    /**
     * 创建 S3 存储桶并配置 CORS（适用于前端直传场景）
     */
    public String createBucketWithCors(String bucketName, List<String> allowedOrigins, Map<String, String> tags) {
        return s3Service.createBucketWithCors(bucketName, allowedOrigins, tags);
    }

    // ==================== STS 临时凭证 ====================

    /**
     * 通过 AssumeRole 获取临时凭证（前端 JS 使用临时凭证直接操作 S3）
     * @param roleArn 要扮演的 IAM 角色 ARN
     * @param sessionName 会话名称
     * @return 临时凭证
     */
    public Credentials assumeRole(String roleArn, String sessionName) {
        return stsService.assumeRole(roleArn, sessionName);
    }

    /**
     * 通过 AssumeRole 获取临时凭证（指定有效期）
     */
    public Credentials assumeRole(String roleArn, String sessionName, int durationSeconds) {
        return stsService.assumeRole(roleArn, sessionName, durationSeconds);
    }

    /**
     * 获取限定 S3 桶访问权限的临时凭证
     */
    public Credentials assumeRoleForS3Bucket(String roleArn, String sessionName, String bucketName) {
        return stsService.assumeRoleForS3Bucket(roleArn, sessionName, bucketName);
    }

    /**
     * 获取限定 S3 桶和前缀访问权限的临时凭证（多租户场景）
     */
    public Credentials assumeRoleForS3Bucket(String roleArn, String sessionName,
                                              String bucketName, String prefix) {
        return stsService.assumeRoleForS3Bucket(roleArn, sessionName, bucketName, prefix);
    }

    @Override
    public void close() {
        ec2Service.close();
        iamService.close();
        ssmService.close();
        s3Service.close();
        stsService.close();
    }
}
