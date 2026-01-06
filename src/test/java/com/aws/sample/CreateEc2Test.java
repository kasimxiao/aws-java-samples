package com.aws.sample;

import com.aws.sample.ec2.model.InstanceInfo;

/**
 * 创建 EC2 实例测试
 */
public class CreateEc2Test {

    public static void main(String[] args) {
        System.out.println("开始创建 EC2 实例...\n");

        try (Ec2Manager manager = new Ec2Manager()) {
            manager.getConfig().printConfig();

            System.out.println("\n正在创建 EC2 实例...");
            String instanceId = manager.createInstanceWithDefaults("Kiro-Test-Instance");

            System.out.println("\n========== 创建成功 ==========");
            System.out.println("实例 ID: " + instanceId);

            System.out.println("\n等待实例启动...");
            Thread.sleep(10000);

            InstanceInfo info = manager.getInstanceInfo(instanceId);
            if (info != null) {
                System.out.println("\n========== 实例信息 ==========");
                System.out.println("实例 ID: " + info.getInstanceId());
                System.out.println("实例类型: " + info.getInstanceType());
                System.out.println("状态: " + info.getState());
                System.out.println("公网 IP: " + info.getPublicIpAddress());
                System.out.println("私网 IP: " + info.getPrivateIpAddress());
                System.out.println("VPC: " + info.getVpcId());
                System.out.println("子网: " + info.getSubnetId());
                System.out.println("可用区: " + info.getAvailabilityZone());
                System.out.println("AMI: " + info.getImageId());
                System.out.println("================================");
            }

        } catch (Exception e) {
            System.err.println("创建失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
