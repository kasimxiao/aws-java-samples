package com.aws.sample;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.ec2.Ec2Service;

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.CreatePolicyRequest;
import software.amazon.awssdk.services.iam.model.CreateRoleRequest;
import software.amazon.awssdk.services.iam.model.DeletePolicyRequest;
import software.amazon.awssdk.services.iam.model.DeleteRoleRequest;
import software.amazon.awssdk.services.iam.model.DetachRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.NoSuchEntityException;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

/**
 * IAM 策略验证测试
 * 
 * 通过 AssumeRole 切换到受限角色，验证 EC2 RunInstances 策略中的标签条件限制
 * 策略要求：创建 EC2 实例时必须包含标签 project=Embodied AI
 */
public class PolicyValidationTest {

    private static final String TEST_ROLE_NAME = "PolicyValidationTestRole";
    private static final String TEST_POLICY_NAME = "PolicyValidationTestPolicy";
    private static final String POLICY_FILE = "docs/ec2-runinstances-test-policy.json";

    public static void main(String[] args) {
        System.out.println("========== IAM 策略验证测试 ==========\n");
        System.out.println("测试策略: EC2RunInstances");
        System.out.println("条件: aws:RequestTag/project = 'Embodied AI'\n");

        AwsConfig config = new AwsConfig();
        config.printConfig();

        String policyArn = null;
        String roleArn = null;

        try (IamClient iamClient = IamClient.builder()
                .region(Region.AWS_GLOBAL)
                .credentialsProvider(config.getCredentialsProvider())
                .build();
             StsClient stsClient = StsClient.builder()
                .region(config.getRegion())
                .credentialsProvider(config.getCredentialsProvider())
                .build()) {

            // 获取当前账户 ID
            GetCallerIdentityResponse identity = stsClient.getCallerIdentity();
            String accountId = identity.account();
            System.out.println("\n当前账户: " + accountId);
            System.out.println("当前身份: " + identity.arn());

            // 1. 清理可能存在的旧资源
            cleanupResources(iamClient, accountId);

            // 2. 创建测试策略
            System.out.println("\n---------- 创建测试策略 ----------");
            String policyDocument = Files.readString(Paths.get(POLICY_FILE));
            policyArn = createTestPolicy(iamClient, policyDocument);

            // 3. 创建测试角色
            System.out.println("\n---------- 创建测试角色 ----------");
            roleArn = createTestRole(iamClient, accountId, policyArn);

            // 等待角色生效
            System.out.println("等待角色生效（10秒）...");
            Thread.sleep(10000);

            // 4. AssumeRole 获取临时凭证
            System.out.println("\n---------- AssumeRole 获取临时凭证 ----------");
            Credentials credentials = assumeRole(stsClient, roleArn);

            // 5. 使用临时凭证创建 Ec2Service
            AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(
                    credentials.accessKeyId(),
                    credentials.secretAccessKey(),
                    credentials.sessionToken()
            );

            AwsConfig testConfig = new AwsConfig();
            testConfig.setCredentialsProvider(StaticCredentialsProvider.create(sessionCredentials));

            try (Ec2Service ec2Service = new Ec2Service(testConfig)) {
                // 执行测试（不使用实例配置文件，避免 PassRole 权限问题）
                testConfig.setInstanceProfile("");
                testWithoutProjectTag(ec2Service, testConfig);
                testWithCorrectProjectTag(ec2Service, testConfig);
                testWithWrongProjectTag(ec2Service, testConfig);
            }

        } catch (Exception e) {
            System.err.println("\n测试执行异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 清理资源
            System.out.println("\n---------- 清理测试资源 ----------");
            try (IamClient iamClient = IamClient.builder()
                    .region(Region.AWS_GLOBAL)
                    .build()) {
                AwsConfig config2 = new AwsConfig();
                String accountId = StsClient.builder()
                        .region(config2.getRegion())
                        .build()
                        .getCallerIdentity()
                        .account();
                cleanupResources(iamClient, accountId);
            } catch (Exception e) {
                System.err.println("清理资源失败: " + e.getMessage());
            }
        }

        System.out.println("\n========== 测试完成 ==========");
    }

    /**
     * 创建测试策略
     */
    private static String createTestPolicy(IamClient iamClient, String policyDocument) {
        CreatePolicyRequest request = CreatePolicyRequest.builder()
                .policyName(TEST_POLICY_NAME)
                .policyDocument(policyDocument)
                .description("Policy validation test policy")
                .build();

        String policyArn = iamClient.createPolicy(request).policy().arn();
        System.out.println("策略已创建: " + policyArn);
        return policyArn;
    }

    /**
     * 创建测试角色
     */
    private static String createTestRole(IamClient iamClient, String accountId, String policyArn) {
        // 信任策略：允许当前账户 AssumeRole
        String trustPolicy = String.format("""
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Effect": "Allow",
                            "Principal": {
                                "AWS": "arn:aws:iam::%s:root"
                            },
                            "Action": "sts:AssumeRole"
                        }
                    ]
                }
                """, accountId);

        CreateRoleRequest createRoleRequest = CreateRoleRequest.builder()
                .roleName(TEST_ROLE_NAME)
                .assumeRolePolicyDocument(trustPolicy)
                .description("Policy validation test role")
                .build();

        String roleArn = iamClient.createRole(createRoleRequest).role().arn();
        System.out.println("角色已创建: " + roleArn);

        // 附加策略
        iamClient.attachRolePolicy(AttachRolePolicyRequest.builder()
                .roleName(TEST_ROLE_NAME)
                .policyArn(policyArn)
                .build());
        System.out.println("策略已附加到角色");

        return roleArn;
    }

    /**
     * AssumeRole 获取临时凭证
     */
    private static Credentials assumeRole(StsClient stsClient, String roleArn) {
        AssumeRoleRequest request = AssumeRoleRequest.builder()
                .roleArn(roleArn)
                .roleSessionName("PolicyValidationTest")
                .durationSeconds(900)
                .build();

        AssumeRoleResponse response = stsClient.assumeRole(request);
        System.out.println("AssumeRole 成功");
        System.out.println("临时凭证有效期至: " + response.credentials().expiration());
        return response.credentials();
    }

    /**
     * 清理测试资源
     */
    private static void cleanupResources(IamClient iamClient, String accountId) {
        String policyArn = String.format("arn:aws:iam::%s:policy/%s", accountId, TEST_POLICY_NAME);

        try {
            // 分离策略
            iamClient.detachRolePolicy(DetachRolePolicyRequest.builder()
                    .roleName(TEST_ROLE_NAME)
                    .policyArn(policyArn)
                    .build());
            System.out.println("策略已分离");
        } catch (NoSuchEntityException e) {
            // 忽略
        }

        try {
            // 删除角色
            iamClient.deleteRole(DeleteRoleRequest.builder()
                    .roleName(TEST_ROLE_NAME)
                    .build());
            System.out.println("角色已删除: " + TEST_ROLE_NAME);
        } catch (NoSuchEntityException e) {
            // 忽略
        }

        try {
            // 删除策略
            iamClient.deletePolicy(DeletePolicyRequest.builder()
                    .policyArn(policyArn)
                    .build());
            System.out.println("策略已删除: " + policyArn);
        } catch (NoSuchEntityException e) {
            // 忽略
        }
    }

    /**
     * 测试1: 不带 project 标签创建实例
     */
    private static void testWithoutProjectTag(Ec2Service ec2Service, AwsConfig config) {
        System.out.println("\n---------- 测试1: 不带 project 标签 ----------");
        System.out.println("预期结果: 失败 (AccessDenied)");

        try {
            String instanceId = ec2Service.createInstanceWithDefaults("Policy-Test-NoTag");
            System.out.println("❌ 测试失败: 实例创建成功，但应该被拒绝！");
            System.out.println("实例 ID: " + instanceId);
            // 清理
            System.out.println("正在终止意外创建的实例...");
            ec2Service.terminateInstance(instanceId);
        } catch (Ec2Exception e) {
            if (isAccessDenied(e)) {
                System.out.println("✅ 测试通过: 请求被正确拒绝");
                System.out.println("错误码: " + e.awsErrorDetails().errorCode());
            } else {
                System.out.println("⚠️ 发生其他错误: " + e.awsErrorDetails().errorCode());
                System.out.println("错误信息: " + e.awsErrorDetails().errorMessage());
            }
        }
    }

    /**
     * 测试2: 带正确的 project 标签创建实例
     */
    private static void testWithCorrectProjectTag(Ec2Service ec2Service, AwsConfig config) {
        System.out.println("\n---------- 测试2: 带正确的 project 标签 ----------");
        System.out.println("标签: project = 'Embodied AI'");
        System.out.println("预期结果: 成功");

        String instanceId = null;
        try {
            Map<String, String> tags = Map.of("project", "Embodied AI");
            instanceId = ec2Service.createInstanceWithDefaults("Policy-Test-CorrectTag", tags);
            System.out.println("✅ 测试通过: 实例创建成功");
            System.out.println("实例 ID: " + instanceId);
        } catch (Ec2Exception e) {
            System.out.println("❌ 测试失败: 实例创建被拒绝");
            System.out.println("错误码: " + e.awsErrorDetails().errorCode());
            System.out.println("错误信息: " + e.awsErrorDetails().errorMessage());
        } finally {
            if (instanceId != null) {
                System.out.println("正在清理测试实例...");
                try {
                    ec2Service.terminateInstance(instanceId);
                } catch (Exception e) {
                    System.out.println("清理失败: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 测试3: 带错误的 project 标签值创建实例
     */
    private static void testWithWrongProjectTag(Ec2Service ec2Service, AwsConfig config) {
        System.out.println("\n---------- 测试3: 带错误的 project 标签值 ----------");
        System.out.println("标签: project = 'Wrong Project'");
        System.out.println("预期结果: 失败 (AccessDenied)");

        try {
            Map<String, String> tags = Map.of("project", "Wrong Project");
            String instanceId = ec2Service.createInstanceWithDefaults("Policy-Test-WrongTag", tags);
            System.out.println("❌ 测试失败: 实例创建成功，但应该被拒绝！");
            System.out.println("实例 ID: " + instanceId);
            // 清理
            System.out.println("正在终止意外创建的实例...");
            ec2Service.terminateInstance(instanceId);
        } catch (Ec2Exception e) {
            if (isAccessDenied(e)) {
                System.out.println("✅ 测试通过: 请求被正确拒绝");
                System.out.println("错误码: " + e.awsErrorDetails().errorCode());
            } else {
                System.out.println("⚠️ 发生其他错误: " + e.awsErrorDetails().errorCode());
                System.out.println("错误信息: " + e.awsErrorDetails().errorMessage());
            }
        }
    }

    private static boolean isAccessDenied(Ec2Exception e) {
        String code = e.awsErrorDetails().errorCode();
        String msg = e.awsErrorDetails().errorMessage();
        return code.contains("UnauthorizedOperation") || 
               code.contains("AccessDenied") ||
               msg.contains("not authorized");
    }

    /**
     * 从策略文件中提取 PolicyDocument 部分
     */
    private static String extractPolicyDocument(String content) {
        // 查找 "PolicyDocument": { 的位置
        String marker = "\"PolicyDocument\":";
        int start = content.indexOf(marker);
        if (start == -1) {
            throw new RuntimeException("策略文件中未找到 PolicyDocument");
        }
        
        // 找到 PolicyDocument 的起始 {
        int braceStart = content.indexOf('{', start + marker.length());
        if (braceStart == -1) {
            throw new RuntimeException("策略文件格式错误");
        }
        
        // 匹配括号找到结束位置
        int depth = 1;
        int pos = braceStart + 1;
        while (pos < content.length() && depth > 0) {
            char c = content.charAt(pos);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            pos++;
        }
        
        return content.substring(braceStart, pos);
    }
}
