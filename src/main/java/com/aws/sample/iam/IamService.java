package com.aws.sample.iam;

import com.aws.sample.common.AwsConfig;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AddRoleToInstanceProfileRequest;
import software.amazon.awssdk.services.iam.model.CreateInstanceProfileRequest;
import software.amazon.awssdk.services.iam.model.CreateInstanceProfileResponse;

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

    @Override
    public void close() {
        if (ec2Client != null) ec2Client.close();
        if (iamClient != null) iamClient.close();
    }
}
