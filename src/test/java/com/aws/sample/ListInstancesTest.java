package com.aws.sample;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.ec2.Ec2Service;
import com.aws.sample.ec2.model.InstanceInfo;

import java.util.List;

public class ListInstancesTest {

    public static void main(String[] args) {
        AwsConfig config = new AwsConfig();

        try (Ec2Service ec2Service = new Ec2Service(config)) {
            List<InstanceInfo> instances = ec2Service.listAllInstances();

            System.out.println("共有 " + instances.size() + " 个实例:");
            for (InstanceInfo instance : instances) {
                System.out.println("  - " + instance.getInstanceId()
                        + " | " + instance.getState() + " | " + instance.getInstanceType());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
