package com.aws.sample.iam;

import com.aws.sample.common.AwsConfig;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.AssociateIamInstanceProfileRequest;
import software.amazon.awssdk.services.ec2.model.AssociateIamInstanceProfileResponse;
import software.amazon.awssdk.services.ec2.model.DescribeIamInstanceProfileAssociationsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeIamInstanceProfileAssociationsResponse;
import software.amazon.awssdk.services.ec2.model.DisassociateIamInstanceProfileRequest;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.IamInstanceProfileAssociation;
import software.amazon.awssdk.services.ec2.model.IamInstanceProfileSpecification;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AddRoleToInstanceProfileRequest;
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.AttachedPolicy;
import software.amazon.awssdk.services.iam.model.CreateInstanceProfileRequest;
import software.amazon.awssdk.services.iam.model.CreateInstanceProfileResponse;
import software.amazon.awssdk.services.iam.model.CreatePolicyRequest;
import software.amazon.awssdk.services.iam.model.CreatePolicyResponse;
import software.amazon.awssdk.services.iam.model.CreateRoleRequest;
import software.amazon.awssdk.services.iam.model.CreateRoleResponse;
import software.amazon.awssdk.services.iam.model.DeletePolicyRequest;
import software.amazon.awssdk.services.iam.model.DeleteRoleRequest;
import software.amazon.awssdk.services.iam.model.DetachRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.GetRoleRequest;
import software.amazon.awssdk.services.iam.model.GetRoleResponse;
import software.amazon.awssdk.services.iam.model.ListAttachedRolePoliciesRequest;
import software.amazon.awssdk.services.iam.model.ListAttachedRolePoliciesResponse;
import software.amazon.awssdk.services.iam.model.Role;

/**
 * IAM 角色服务类
 * 提供 EC2 实例的 IAM 角色管理
 */
public class IamService implements AutoCloseable {

    private final Ec2Client ec2Client;
    private final IamClient iamClient;

    public IamService(AwsConfig config) {
        this.ec2Client = Ec2Client.builder()
                .region(config.getRegion())
                .credentialsProvider(config.getCredentialsProvider())
                .build();
        this.iamClient = IamClient.builder()
                .region(Region.AWS_GLOBAL)
                .credentialsProvider(config.getCredentialsProvider())
                .build();
    }

    /**
     * 为实例附加 IAM 角色
     */
    public String attachIamRole(String instanceId, String instanceProfileName) {
        disassociateIamRole(instanceId);

        AssociateIamInstanceProfileRequest request = AssociateIamInstanceProfileRequest.builder()
                .instanceId(instanceId)
                .iamInstanceProfile(IamInstanceProfileSpecification.builder()
                        .name(instanceProfileName)
                        .build())
                .build();

        AssociateIamInstanceProfileResponse response = ec2Client.associateIamInstanceProfile(request);
        String associationId = response.iamInstanceProfileAssociation().associationId();
        System.out.println("IAM 角色已关联，关联 ID: " + associationId);
        return associationId;
    }

    /**
     * 解除实例的 IAM 角色关联
     */
    public void disassociateIamRole(String instanceId) {
        DescribeIamInstanceProfileAssociationsRequest describeRequest = 
                DescribeIamInstanceProfileAssociationsRequest.builder()
                        .filters(Filter.builder().name("instance-id").values(instanceId).build())
                        .build();

        DescribeIamInstanceProfileAssociationsResponse response = 
                ec2Client.describeIamInstanceProfileAssociations(describeRequest);

        for (IamInstanceProfileAssociation association : response.iamInstanceProfileAssociations()) {
            ec2Client.disassociateIamInstanceProfile(
                    DisassociateIamInstanceProfileRequest.builder()
                            .associationId(association.associationId())
                            .build()
            );
            System.out.println("IAM 角色已解除关联: " + association.associationId());
        }
    }

    /**
     * 创建 IAM 实例配置文件
     */
    public String createInstanceProfile(String profileName, String roleName) {
        CreateInstanceProfileResponse createResponse = iamClient.createInstanceProfile(
                CreateInstanceProfileRequest.builder().instanceProfileName(profileName).build()
        );

        iamClient.addRoleToInstanceProfile(
                AddRoleToInstanceProfileRequest.builder()
                        .instanceProfileName(profileName)
                        .roleName(roleName)
                        .build()
        );

        System.out.println("实例配置文件创建成功: " + profileName);
        return createResponse.instanceProfile().arn();
    }

    /**
     * 创建 IAM 策略
     * @param policyName 策略名称
     * @param policyDocument 策略文档（JSON 格式）
     * @param description 策略描述
     * @return 策略 ARN
     */
    public String createPolicy(String policyName, String policyDocument, String description) {
        CreatePolicyRequest request = CreatePolicyRequest.builder()
                .policyName(policyName)
                .policyDocument(policyDocument)
                .description(description)
                .build();

        CreatePolicyResponse response = iamClient.createPolicy(request);
        String policyArn = response.policy().arn();
        System.out.println("IAM 策略创建成功: " + policyArn);
        return policyArn;
    }

    /**
     * 创建 IAM 角色
     * @param roleName 角色名称
     * @param assumeRolePolicyDocument 信任策略文档（JSON 格式）
     * @param description 角色描述
     * @return 角色 ARN
     */
    public String createRole(String roleName, String assumeRolePolicyDocument, String description) {
        CreateRoleRequest request = CreateRoleRequest.builder()
                .roleName(roleName)
                .assumeRolePolicyDocument(assumeRolePolicyDocument)
                .description(description)
                .build();

        CreateRoleResponse response = iamClient.createRole(request);
        String roleArn = response.role().arn();
        System.out.println("IAM 角色创建成功: " + roleArn);
        return roleArn;
    }

    /**
     * 将策略附加到角色
     * @param roleName 角色名称
     * @param policyArn 策略 ARN
     */
    public void attachRolePolicy(String roleName, String policyArn) {
        AttachRolePolicyRequest request = AttachRolePolicyRequest.builder()
                .roleName(roleName)
                .policyArn(policyArn)
                .build();

        iamClient.attachRolePolicy(request);
        System.out.println("策略已附加到角色: " + roleName + " <- " + policyArn);
    }

    /**
     * 从角色分离策略
     * @param roleName 角色名称
     * @param policyArn 策略 ARN
     */
    public void detachRolePolicy(String roleName, String policyArn) {
        DetachRolePolicyRequest request = DetachRolePolicyRequest.builder()
                .roleName(roleName)
                .policyArn(policyArn)
                .build();

        iamClient.detachRolePolicy(request);
        System.out.println("策略已从角色分离: " + roleName + " -x- " + policyArn);
    }

    /**
     * 删除 IAM 策略
     * @param policyArn 策略 ARN
     */
    public void deletePolicy(String policyArn) {
        DeletePolicyRequest request = DeletePolicyRequest.builder()
                .policyArn(policyArn)
                .build();

        iamClient.deletePolicy(request);
        System.out.println("IAM 策略已删除: " + policyArn);
    }

    /**
     * 删除 IAM 角色
     * @param roleName 角色名称
     */
    public void deleteRole(String roleName) {
        DeleteRoleRequest request = DeleteRoleRequest.builder()
                .roleName(roleName)
                .build();

        iamClient.deleteRole(request);
        System.out.println("IAM 角色已删除: " + roleName);
    }

    /**
     * 获取角色信息
     * @param roleName 角色名称
     * @return 角色对象
     */
    public Role getRole(String roleName) {
        GetRoleRequest request = GetRoleRequest.builder()
                .roleName(roleName)
                .build();

        GetRoleResponse response = iamClient.getRole(request);
        return response.role();
    }

    /**
     * 列出角色附加的策略
     * @param roleName 角色名称
     */
    public void listAttachedRolePolicies(String roleName) {
        ListAttachedRolePoliciesRequest request = ListAttachedRolePoliciesRequest.builder()
                .roleName(roleName)
                .build();

        ListAttachedRolePoliciesResponse response = iamClient.listAttachedRolePolicies(request);
        System.out.println("角色 " + roleName + " 附加的策略:");
        for (AttachedPolicy policy : response.attachedPolicies()) {
            System.out.println("  - " + policy.policyName() + " (" + policy.policyArn() + ")");
        }
    }

    @Override
    public void close() {
        if (ec2Client != null) ec2Client.close();
        if (iamClient != null) iamClient.close();
    }
}
