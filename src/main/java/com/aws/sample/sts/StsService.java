package com.aws.sample.sts;

import com.aws.sample.common.AwsConfig;

import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

/**
 * STS 临时凭证服务类
 * 提供 AssumeRole 获取临时凭证功能，适用于前端 JavaScript 通过临时凭证直接操作 S3 的场景
 */
public class StsService implements AutoCloseable {

    private final StsClient stsClient;

    /** 默认临时凭证有效期（秒），1 小时 */
    private static final int DEFAULT_DURATION_SECONDS = 3600;

    public StsService(AwsConfig config) {
        this.stsClient = StsClient.builder()
                .region(config.getRegion())
                .credentialsProvider(config.getCredentialsProvider())
                .build();
    }

    /**
     * 获取当前调用者身份信息
     *
     * @return 调用者身份响应
     */
    public GetCallerIdentityResponse getCallerIdentity() {
        GetCallerIdentityResponse response = stsClient.getCallerIdentity();
        System.out.println("==================== 调用者身份 ====================");
        System.out.println("账户 ID: " + response.account());
        System.out.println("用户 ARN: " + response.arn());
        System.out.println("用户 ID: " + response.userId());
        System.out.println("===================================================");
        return response;
    }

    /**
     * 通过 AssumeRole 获取临时凭证（使用默认有效期 1 小时）
     * 前端 JavaScript 可使用返回的临时凭证直接操作 S3
     *
     * @param roleArn        要扮演的 IAM 角色 ARN
     * @param sessionName    会话名称，用于标识此次临时凭证的用途
     * @return 临时凭证（包含 AccessKeyId、SecretAccessKey、SessionToken）
     */
    public Credentials assumeRole(String roleArn, String sessionName) {
        return assumeRole(roleArn, sessionName, DEFAULT_DURATION_SECONDS, null);
    }

    /**
     * 通过 AssumeRole 获取临时凭证（指定有效期）
     *
     * @param roleArn           要扮演的 IAM 角色 ARN
     * @param sessionName       会话名称
     * @param durationSeconds   有效期（秒），范围 900~43200
     * @return 临时凭证
     */
    public Credentials assumeRole(String roleArn, String sessionName, int durationSeconds) {
        return assumeRole(roleArn, sessionName, durationSeconds, null);
    }

    /**
     * 通过 AssumeRole 获取临时凭证（带内联策略，用于进一步限制权限）
     * 可通过 policy 参数限制临时凭证只能访问特定的 S3 桶或前缀
     *
     * @param roleArn           要扮演的 IAM 角色 ARN
     * @param sessionName       会话名称
     * @param durationSeconds   有效期（秒），范围 900~43200
     * @param policy            内联策略（JSON 格式），用于进一步限制权限，可为 null
     * @return 临时凭证
     */
    public Credentials assumeRole(String roleArn, String sessionName, int durationSeconds, String policy) {
        AssumeRoleRequest.Builder requestBuilder = AssumeRoleRequest.builder()
                .roleArn(roleArn)
                .roleSessionName(sessionName)
                .durationSeconds(durationSeconds);

        if (policy != null && !policy.isEmpty()) {
            requestBuilder.policy(policy);
        }

        AssumeRoleResponse response = stsClient.assumeRole(requestBuilder.build());
        Credentials credentials = response.credentials();

        System.out.println("==================== 临时凭证 ====================");
        System.out.println("角色 ARN: " + roleArn);
        System.out.println("会话名称: " + sessionName);
        System.out.println("AccessKeyId: " + credentials.accessKeyId());
        System.out.println("SecretAccessKey: " + credentials.secretAccessKey().substring(0, 4) + "****");
        System.out.println("SessionToken: " + credentials.sessionToken().substring(0, 20) + "...");
        System.out.println("过期时间: " + credentials.expiration());
        System.out.println("有效期: " + durationSeconds + " 秒");
        System.out.println("===================================================");

        return credentials;
    }

    /**
     * 获取限定 S3 桶访问权限的临时凭证（便捷方法）
     * 生成的临时凭证仅允许对指定桶执行 put、get、list、delete 操作
     *
     * @param roleArn        要扮演的 IAM 角色 ARN
     * @param sessionName    会话名称
     * @param bucketName     允许访问的 S3 桶名称
     * @return 临时凭证
     */
    public Credentials assumeRoleForS3Bucket(String roleArn, String sessionName, String bucketName) {
        return assumeRoleForS3Bucket(roleArn, sessionName, bucketName, null);
    }

    /**
     * 获取限定 S3 桶和前缀访问权限的临时凭证
     * 适用于多租户场景，每个用户只能访问自己的前缀路径
     *
     * @param roleArn        要扮演的 IAM 角色 ARN
     * @param sessionName    会话名称
     * @param bucketName     允许访问的 S3 桶名称
     * @param prefix         允许访问的前缀路径（如 "user123/"），为 null 则允许访问整个桶
     * @return 临时凭证
     */
    public Credentials assumeRoleForS3Bucket(String roleArn, String sessionName,
                                              String bucketName, String prefix) {
        String resourceArn;
        if (prefix != null && !prefix.isEmpty()) {
            resourceArn = "arn:aws:s3:::" + bucketName + "/" + prefix + "*";
        } else {
            resourceArn = "arn:aws:s3:::" + bucketName + "/*";
        }

        String policy = """
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Effect": "Allow",
                            "Action": [
                                "s3:PutObject",
                                "s3:GetObject",
                                "s3:DeleteObject"
                            ],
                            "Resource": "%s"
                        },
                        {
                            "Effect": "Allow",
                            "Action": [
                                "s3:ListBucket"
                            ],
                            "Resource": "arn:aws:s3:::%s",
                            "Condition": %s
                        }
                    ]
                }
                """.formatted(
                resourceArn,
                bucketName,
                prefix != null && !prefix.isEmpty()
                        ? """
                        {
                            "StringLike": {
                                "s3:prefix": ["%s*"]
                            }
                        }
                        """.formatted(prefix)
                        : "{}"
        );

        System.out.println("使用内联策略限制 S3 访问范围:");
        System.out.println("  桶: " + bucketName);
        System.out.println("  前缀: " + (prefix != null ? prefix : "（整个桶）"));

        return assumeRole(roleArn, sessionName, DEFAULT_DURATION_SECONDS, policy);
    }

    @Override
    public void close() {
        if (stsClient != null) {
            stsClient.close();
            System.out.println("STS 客户端已关闭");
        }
    }
}
