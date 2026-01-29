package com.aws.sample;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.iam.IamService;

/**
 * IAM 策略和角色管理测试
 * 演示创建 Policy、Role 以及绑定操作
 */
public class IamPolicyRoleTest {

    private IamService iamService;
    private String testPolicyArn;
    private String testRoleName;

    // EC2 信任策略 - 允许 EC2 服务代入此角色
    private static final String EC2_TRUST_POLICY = """
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Effect": "Allow",
                        "Principal": {
                            "Service": "ec2.amazonaws.com"
                        },
                        "Action": "sts:AssumeRole"
                    }
                ]
            }
            """;

    // S3 只读访问策略
    private static final String S3_READ_ONLY_POLICY = """
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Effect": "Allow",
                        "Action": [
                            "s3:GetObject",
                            "s3:ListBucket"
                        ],
                        "Resource": [
                            "arn:aws:s3:::*",
                            "arn:aws:s3:::*/*"
                        ]
                    }
                ]
            }
            """;

    @BeforeEach
    void setUp() {
        AwsConfig config = new AwsConfig();
        iamService = new IamService(config);
    }

    @AfterEach
    void tearDown() {
        if (iamService != null) {
            iamService.close();
        }
    }

    /**
     * 测试完整流程：创建策略 -> 创建角色 -> 绑定策略 -> 查看 -> 清理
     */
    @Test
    void testCreatePolicyAndRoleWithBinding() {
        String policyName = "test-s3-readonly-policy-" + System.currentTimeMillis();
        testRoleName = "test-ec2-role-" + System.currentTimeMillis();

        try {
            // 1. 创建 IAM 策略
            System.out.println("=== 步骤 1: 创建 IAM 策略 ===");
            testPolicyArn = iamService.createPolicy(
                    policyName,
                    S3_READ_ONLY_POLICY,
                    "Test S3 read-only access policy"
            );
            System.out.println("策略 ARN: " + testPolicyArn);

            // 2. 创建 IAM 角色
            System.out.println("\n=== 步骤 2: 创建 IAM 角色 ===");
            String roleArn = iamService.createRole(
                    testRoleName,
                    EC2_TRUST_POLICY,
                    "Test EC2 role"
            );
            System.out.println("角色 ARN: " + roleArn);

            // 3. 将策略附加到角色
            System.out.println("\n=== 步骤 3: 将策略附加到角色 ===");
            iamService.attachRolePolicy(testRoleName, testPolicyArn);

            // 4. 列出角色附加的策略
            System.out.println("\n=== 步骤 4: 查看角色附加的策略 ===");
            iamService.listAttachedRolePolicies(testRoleName);

            // 5. 获取角色信息
            System.out.println("\n=== 步骤 5: 获取角色详情 ===");
            var role = iamService.getRole(testRoleName);
            System.out.println("角色名称: " + role.roleName());
            System.out.println("角色 ARN: " + role.arn());
            System.out.println("创建时间: " + role.createDate());

            System.out.println("\n✅ 测试完成！");

        } finally {
            // 清理资源
            cleanup();
        }
    }

    /**
     * 测试仅创建策略
     */
    @Test
    void testCreatePolicyOnly() {
        String policyName = "test-policy-" + System.currentTimeMillis();

        try {
            testPolicyArn = iamService.createPolicy(
                    policyName,
                    S3_READ_ONLY_POLICY,
                    "Test policy"
            );
            System.out.println("策略创建成功: " + testPolicyArn);
        } finally {
            if (testPolicyArn != null) {
                iamService.deletePolicy(testPolicyArn);
            }
        }
    }

    /**
     * 测试仅创建角色
     */
    @Test
    void testCreateRoleOnly() {
        testRoleName = "test-role-" + System.currentTimeMillis();

        try {
            String roleArn = iamService.createRole(
                    testRoleName,
                    EC2_TRUST_POLICY,
                    "Test role"
            );
            System.out.println("角色创建成功: " + roleArn);
        } finally {
            if (testRoleName != null) {
                iamService.deleteRole(testRoleName);
            }
        }
    }

    /**
     * 清理测试资源
     */
    private void cleanup() {
        System.out.println("\n=== 清理资源 ===");
        try {
            if (testRoleName != null && testPolicyArn != null) {
                // 先分离策略
                iamService.detachRolePolicy(testRoleName, testPolicyArn);
            }
            if (testRoleName != null) {
                // 删除角色
                iamService.deleteRole(testRoleName);
            }
            if (testPolicyArn != null) {
                // 删除策略
                iamService.deletePolicy(testPolicyArn);
            }
            System.out.println("资源清理完成");
        } catch (Exception e) {
            System.err.println("清理资源时出错: " + e.getMessage());
        }
    }
}
