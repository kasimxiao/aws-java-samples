package com.aws.sample;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.sts.StsService;

import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

/**
 * STS 临时凭证测试
 * 测试 AssumeRole 获取临时凭证，适用于前端 JavaScript 通过临时凭证直接操作 S3
 *
 * 使用前请确保：
 * 1. 已创建用于 AssumeRole 的 IAM 角色，并配置信任策略允许当前用户/角色扮演
 * 2. 该角色附加了 S3 相关权限策略
 */
public class StsAssumeRoleTest {

    // 请替换为实际的角色 ARN
    private static final String ROLE_ARN = "arn:aws:iam::123456789012:role/S3AccessRole";
    private static final String BUCKET_NAME = "wongxiao-javasdk-test";

    public static void main(String[] args) {
        System.out.println("开始 STS 临时凭证测试...\n");

        AwsConfig config = new AwsConfig();

        try (StsService stsService = new StsService(config)) {

            // 1. 获取当前调用者身份
            System.out.println("===== 1. 获取当前调用者身份 =====");
            GetCallerIdentityResponse identity = stsService.getCallerIdentity();
            System.out.println("当前账户: " + identity.account());

            // 2. 基本 AssumeRole（获取通用临时凭证）
            System.out.println("\n===== 2. 基本 AssumeRole =====");
            Credentials credentials = stsService.assumeRole(ROLE_ARN, "frontend-session");
            printCredentialsForFrontend(credentials);

            // 3. 指定有效期的 AssumeRole（15 分钟）
            System.out.println("\n===== 3. 短期临时凭证（15 分钟）=====");
            Credentials shortLived = stsService.assumeRole(ROLE_ARN, "short-session", 900);
            System.out.println("过期时间: " + shortLived.expiration());

            // 4. 限定 S3 桶访问的临时凭证
            System.out.println("\n===== 4. 限定 S3 桶访问的临时凭证 =====");
            Credentials bucketScoped = stsService.assumeRoleForS3Bucket(
                    ROLE_ARN, "bucket-session", BUCKET_NAME);
            printCredentialsForFrontend(bucketScoped);

            // 5. 限定 S3 桶 + 前缀的临时凭证（多租户场景）
            System.out.println("\n===== 5. 限定前缀的临时凭证（多租户）=====");
            String userId = "user123";
            Credentials prefixScoped = stsService.assumeRoleForS3Bucket(
                    ROLE_ARN, "tenant-" + userId, BUCKET_NAME, userId + "/");
            printCredentialsForFrontend(prefixScoped);

            System.out.println("\n========== STS 临时凭证测试完成！==========");

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 打印前端 JavaScript 可直接使用的凭证信息
     */
    private static void printCredentialsForFrontend(Credentials credentials) {
        System.out.println("\n--- 前端 JavaScript 使用示例 ---");
        System.out.println("const s3Client = new S3Client({");
        System.out.println("  region: 'eu-central-1',");
        System.out.println("  credentials: {");
        System.out.println("    accessKeyId: '" + credentials.accessKeyId() + "',");
        System.out.println("    secretAccessKey: '" + credentials.secretAccessKey() + "',");
        System.out.println("    sessionToken: '" + credentials.sessionToken() + "'");
        System.out.println("  }");
        System.out.println("});");
        System.out.println("// 过期时间: " + credentials.expiration());
    }
}
