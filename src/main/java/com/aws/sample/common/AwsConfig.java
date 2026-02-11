package com.aws.sample.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

/**
 * AWS 配置管理类
 * 从 application.properties 文件加载配置
 */
public class AwsConfig {

    private final Properties properties;

    // AWS 凭证
    private String accessKeyId;
    private String secretAccessKey;
    private String region;

    // VPC 配置
    private String vpcId;
    private String subnetId;
    private String availabilityZone;

    // 安全组配置
    private List<String> securityGroupIds;

    // AMI 配置
    private String defaultAmi;
    private String windowsAmi;
    private String ubuntuAmi;

    // EC2 默认配置
    private String instanceType;
    private String keyName;
    private int ebsSize;
    private String ebsType;
    private String namePrefix;

    // IAM 配置
    private String instanceProfile;

    // DCV 配置
    private int dcvPort;

    // S3 配置
    private String s3Bucket;
    private String s3MountPoint;

    // 自定义凭证提供者（用于 AssumeRole 等场景）
    private AwsCredentialsProvider customCredentialsProvider;

    public AwsConfig() {
        this("application.properties");
    }

    public AwsConfig(String configFile) {
        this.properties = new Properties();
        loadConfig(configFile);
        parseConfig();
    }

    public static AwsConfig fromFile(String filePath) {
        AwsConfig config = new AwsConfig();
        try (InputStream input = new java.io.FileInputStream(filePath)) {
            config.properties.load(input);
            config.parseConfig();
        } catch (IOException e) {
            throw new RuntimeException("无法加载配置文件: " + filePath, e);
        }
        return config;
    }

    private void loadConfig(String configFile) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(configFile)) {
            if (input == null) {
                System.out.println("警告: 未找到配置文件 " + configFile + "，使用默认值");
                return;
            }
            properties.load(input);
            System.out.println("配置文件加载成功: " + configFile);
        } catch (IOException e) {
            throw new RuntimeException("加载配置文件失败: " + configFile, e);
        }
    }

    private void parseConfig() {
        this.accessKeyId = getProperty("aws.accessKeyId", "");
        this.secretAccessKey = getProperty("aws.secretAccessKey", "");
        this.region = getProperty("aws.region", "us-east-1");

        this.vpcId = getProperty("aws.vpc.id", "");
        this.subnetId = getProperty("aws.subnet.id", "");
        this.availabilityZone = getProperty("aws.availabilityZone", "");

        String sgIds = getProperty("aws.securityGroup.ids", "");
        this.securityGroupIds = Arrays.stream(sgIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        this.defaultAmi = getProperty("aws.ami.default", "");
        this.windowsAmi = getProperty("aws.ami.windows", "");
        this.ubuntuAmi = getProperty("aws.ami.ubuntu", "");

        this.instanceType = getProperty("aws.ec2.instanceType", "t3.medium");
        this.keyName = getProperty("aws.ec2.keyName", "");
        this.ebsSize = getIntProperty("aws.ec2.ebsSize", 50);
        this.ebsType = getProperty("aws.ec2.ebsType", "gp3");
        this.namePrefix = getProperty("aws.ec2.namePrefix", "EC2-Instance");

        this.instanceProfile = getProperty("aws.iam.instanceProfile", "");
        this.dcvPort = getIntProperty("aws.dcv.port", 8443);
        this.s3Bucket = getProperty("aws.s3.bucket", "");
        this.s3MountPoint = getProperty("aws.s3.mountPoint", "/mnt/s3data");
    }

    private String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    private int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public AwsCredentialsProvider getCredentialsProvider() {
        // 优先使用自定义凭证提供者（如 AssumeRole）
        if (customCredentialsProvider != null) {
            System.out.println("凭证来源: 自定义凭证提供者");
            return customCredentialsProvider;
        }
        // 其次使用配置文件中的 AKSK
        if (accessKeyId != null && !accessKeyId.isEmpty() && !accessKeyId.startsWith("YOUR_")
                && secretAccessKey != null && !secretAccessKey.isEmpty() && !secretAccessKey.startsWith("YOUR_")) {
            System.out.println("凭证来源: 配置文件 AKSK (AccessKeyId=" + accessKeyId.substring(0, 4) + "****)");
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        }
        // 最后使用默认凭证链（aws configure / 环境变量 / IAM Role）
        System.out.println("凭证来源: 默认凭证链（aws configure / 环境变量 / IAM Role）");
        return DefaultCredentialsProvider.create();
    }

    /**
     * 设置自定义凭证提供者（用于 AssumeRole 等场景）
     */
    public void setCredentialsProvider(AwsCredentialsProvider credentialsProvider) {
        this.customCredentialsProvider = credentialsProvider;
    }

    public Region getRegion() { return Region.of(region); }
    public String getVpcId() { return vpcId; }
    public String getSubnetId() { return subnetId; }
    public String getAvailabilityZone() { return availabilityZone; }
    public List<String> getSecurityGroupIds() { return securityGroupIds; }
    public String getDefaultAmi() { return defaultAmi; }
    public String getWindowsAmi() { return windowsAmi; }
    public String getUbuntuAmi() { return ubuntuAmi; }
    public String getInstanceType() { return instanceType; }
    public String getKeyName() { return keyName; }
    public int getEbsSize() { return ebsSize; }
    public String getEbsType() { return ebsType; }
    public String getNamePrefix() { return namePrefix; }
    public String getInstanceProfile() { return instanceProfile; }
    public void setInstanceProfile(String instanceProfile) { this.instanceProfile = instanceProfile; }
    public int getDcvPort() { return dcvPort; }
    public String getS3Bucket() { return s3Bucket; }
    public String getS3MountPoint() { return s3MountPoint; }

    public void printConfig() {
        System.out.println("==================== AWS 配置 ====================");
        System.out.println("Region: " + region);
        System.out.println("VPC ID: " + vpcId);
        System.out.println("Subnet ID: " + subnetId);
        System.out.println("Security Groups: " + securityGroupIds);
        System.out.println("Default AMI: " + defaultAmi);
        System.out.println("Instance Type: " + instanceType);
        System.out.println("Key Name: " + keyName);
        System.out.println("EBS: " + ebsSize + "GB " + ebsType);
        System.out.println("Instance Profile: " + instanceProfile);
        System.out.println("================================================");
    }
}
