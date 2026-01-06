package com.aws.sample.ec2.model;

import software.amazon.awssdk.services.ec2.model.Instance;

/**
 * EC2 实例信息封装类
 */
public class InstanceInfo {

    private final String instanceId;
    private final String instanceType;
    private final String state;
    private final String publicIpAddress;
    private final String privateIpAddress;
    private final String vpcId;
    private final String subnetId;
    private final String keyName;
    private final String imageId;
    private final String iamInstanceProfile;
    private final String availabilityZone;
    private final String launchTime;

    public InstanceInfo(Instance instance) {
        this.instanceId = instance.instanceId();
        this.instanceType = instance.instanceTypeAsString();
        this.state = instance.state().nameAsString();
        this.publicIpAddress = instance.publicIpAddress();
        this.privateIpAddress = instance.privateIpAddress();
        this.vpcId = instance.vpcId();
        this.subnetId = instance.subnetId();
        this.keyName = instance.keyName();
        this.imageId = instance.imageId();
        this.iamInstanceProfile = instance.iamInstanceProfile() != null 
                ? instance.iamInstanceProfile().arn() : null;
        this.availabilityZone = instance.placement() != null 
                ? instance.placement().availabilityZone() : null;
        this.launchTime = instance.launchTime() != null 
                ? instance.launchTime().toString() : null;
    }

    public String getInstanceId() { return instanceId; }
    public String getInstanceType() { return instanceType; }
    public String getState() { return state; }
    public String getPublicIpAddress() { return publicIpAddress; }
    public String getPrivateIpAddress() { return privateIpAddress; }
    public String getVpcId() { return vpcId; }
    public String getSubnetId() { return subnetId; }
    public String getKeyName() { return keyName; }
    public String getImageId() { return imageId; }
    public String getIamInstanceProfile() { return iamInstanceProfile; }
    public String getAvailabilityZone() { return availabilityZone; }
    public String getLaunchTime() { return launchTime; }

    @Override
    public String toString() {
        return String.format(
                "InstanceInfo{id='%s', type='%s', state='%s', publicIp='%s', privateIp='%s', vpc='%s'}",
                instanceId, instanceType, state, publicIpAddress, privateIpAddress, vpcId
        );
    }
}
