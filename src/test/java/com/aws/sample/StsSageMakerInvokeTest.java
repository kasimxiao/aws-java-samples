package com.aws.sample;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.sagemaker.SageMakerInferenceService;
import com.aws.sample.sts.StsService;

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.sts.model.Credentials;

/**
 * STS + SageMaker 多租户推理调用测试
 *
 * 场景说明:
 * - 多租户在 AWS SageMaker 上各自部署了模型端点
 * - 外部 client 没有 AKSK，无法直接调用 SageMaker
 * - 后端通过 STS AssumeRole 为每个租户生成临时凭证，限定只能调用该租户的端点
 * - 外部 client 拿到临时凭证后调用 SageMaker InvokeEndpoint
 *
 * 流程:
 * 1. 后端服务部署在 AWS 上（EC2/ECS/Lambda），通过 IAM Role 自动获取凭证，无需 AKSK
 * 2. 外部 client 请求后端 API，携带租户标识（如 JWT 中的 tenantId）
 * 3. 后端根据 tenantId 查到对应的 IAM Role ARN，调用 STS AssumeRole
 * 4. AssumeRole 时附加内联策略，限制临时凭证只能调用该租户的端点
 * 5. 后端将临时凭证（AccessKeyId + SecretAccessKey + SessionToken）返回给外部 client
 * 6. 外部 client 使用临时凭证构建 SageMaker Runtime Client 调用模型
 *
 * 凭证来源说明:
 * - 后端服务: 通过 IAM Role 自动获取凭证（部署在 EC2/ECS/Lambda 上自动具备），无需 AKSK
 * - 外部 client: 通过后端下发的 STS 临时凭证，无需 AKSK
 * - 整个链路中没有任何长期 AKSK 的传递和存储
 *
 * IAM 角色配置要求:
 * - 后端服务的 IAM Role 需要有 sts:AssumeRole 权限
 * - 每个租户有独立的 IAM 角色（如 tenant-A-sagemaker-role）
 * - 租户角色的信任策略（Trust Policy）允许后端服务的 Role 进行 AssumeRole
 * - 租户角色的权限策略包含 sagemaker:InvokeEndpoint
 *
 * 使用前请替换:
 * - TENANT_A_ROLE_ARN / TENANT_B_ROLE_ARN: 租户对应的 IAM 角色 ARN
 * - TENANT_A_ENDPOINT / TENANT_B_ENDPOINT: 租户部署的 SageMaker 端点名称
 * - ACCOUNT_ID / REGION: 实际的 AWS 账户 ID 和区域
 */
public class StsSageMakerInvokeTest {

    // ========== 请替换为实际值 ==========
    private static final String ACCOUNT_ID = "123456789012";
    private static final String REGION = "eu-central-1";

    // 租户 A 配置
    private static final String TENANT_A_ROLE_ARN = "arn:aws:iam::" + ACCOUNT_ID + ":role/tenant-A-sagemaker-role";
    private static final String TENANT_A_ENDPOINT = "tenant-a-model-endpoint";

    // 租户 B 配置
    private static final String TENANT_B_ROLE_ARN = "arn:aws:iam::" + ACCOUNT_ID + ":role/tenant-B-sagemaker-role";
    private static final String TENANT_B_ENDPOINT = "tenant-b-model-endpoint";
    // =====================================

    public static void main(String[] args) {
        System.out.println("开始 STS + SageMaker 多租户推理调用测试...\n");

        AwsConfig config = new AwsConfig();

        try (StsService stsService = new StsService(config)) {

            // 1. 验证后端服务身份
            System.out.println("===== 1. 验证后端服务身份 =====");
            stsService.getCallerIdentity();

            // 2. 模拟租户 A 的外部 client 调用流程
            System.out.println("\n===== 2. 租户 A: 获取临时凭证并调用模型 =====");
            invokeTenantModel(stsService,
                    "tenant-A", TENANT_A_ROLE_ARN, TENANT_A_ENDPOINT,
                    "{\"instances\": [[1.0, 2.0, 3.0, 4.0]]}");

            // 3. 模拟租户 B 的外部 client 调用流程
            System.out.println("\n===== 3. 租户 B: 获取临时凭证并调用模型 =====");
            invokeTenantModel(stsService,
                    "tenant-B", TENANT_B_ROLE_ARN, TENANT_B_ENDPOINT,
                    "{\"inputs\": \"This product is amazing!\"}");

            // 4. 验证租户隔离: 租户 A 的凭证不能调用租户 B 的端点
            System.out.println("\n===== 4. 验证租户隔离 =====");
            testTenantIsolation(stsService);

            System.out.println("\n========== STS + SageMaker 多租户推理测试完成！==========");

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 模拟外部 client 通过临时凭证调用租户模型的完整流程
     *
     * @param stsService    STS 服务（后端持有）
     * @param tenantId      租户 ID
     * @param roleArn       租户对应的 IAM 角色 ARN
     * @param endpointName  租户的 SageMaker 端点名称
     * @param payload       推理请求体
     */
    private static void invokeTenantModel(StsService stsService,
                                           String tenantId, String roleArn,
                                           String endpointName, String payload) {
        // 步骤 1: 后端为租户生成临时凭证（限定只能调用该租户的端点）
        String endpointArn = String.format(
                "arn:aws:sagemaker:%s:%s:endpoint/%s", REGION, ACCOUNT_ID, endpointName);

        System.out.println("[后端] 为租户 " + tenantId + " 生成临时凭证...");
        Credentials credentials = stsService.assumeRoleForSageMaker(
                roleArn, tenantId + "-inference-session", endpointArn);

        // 步骤 2: 外部 client 使用临时凭证构建 SageMaker 推理客户端
        System.out.println("[外部 client] 使用临时凭证调用模型...");
        AwsConfig tenantConfig = new AwsConfig();
        tenantConfig.setCredentialsProvider(
                StaticCredentialsProvider.create(
                        AwsSessionCredentials.create(
                                credentials.accessKeyId(),
                                credentials.secretAccessKey(),
                                credentials.sessionToken()
                        )
                )
        );

        // 步骤 3: 调用 SageMaker 端点进行推理
        SageMakerInferenceService inferenceService = new SageMakerInferenceService(tenantConfig);
        try {
            String result = inferenceService.invokeEndpointJson(endpointName, payload);
            System.out.println("[外部 client] 推理结果: " + result);
        } catch (Exception e) {
            System.err.println("[外部 client] 推理调用失败: " + e.getMessage());
        } finally {
            inferenceService.close();
        }
    }

    /**
     * 验证租户隔离: 使用租户 A 的临时凭证尝试调用租户 B 的端点，预期被拒绝
     */
    private static void testTenantIsolation(StsService stsService) {
        // 用租户 A 的角色获取临时凭证（仅限调用租户 A 的端点）
        String tenantAEndpointArn = String.format(
                "arn:aws:sagemaker:%s:%s:endpoint/%s", REGION, ACCOUNT_ID, TENANT_A_ENDPOINT);

        Credentials tenantACredentials = stsService.assumeRoleForSageMaker(
                TENANT_A_ROLE_ARN, "isolation-test", tenantAEndpointArn);

        // 用租户 A 的凭证尝试调用租户 B 的端点
        AwsConfig crossConfig = new AwsConfig();
        crossConfig.setCredentialsProvider(
                StaticCredentialsProvider.create(
                        AwsSessionCredentials.create(
                                tenantACredentials.accessKeyId(),
                                tenantACredentials.secretAccessKey(),
                                tenantACredentials.sessionToken()
                        )
                )
        );

        SageMakerInferenceService crossService = new SageMakerInferenceService(crossConfig);
        try {
            crossService.invokeEndpointJson(TENANT_B_ENDPOINT, "{\"test\": true}");
            System.out.println("⚠ 警告: 租户 A 的凭证成功调用了租户 B 的端点，隔离失败！");
        } catch (Exception e) {
            System.out.println("✓ 租户隔离验证通过: 租户 A 无法调用租户 B 的端点");
            System.out.println("  拒绝原因: " + e.getMessage());
        } finally {
            crossService.close();
        }
    }
}
