package com.aws.sample;

import java.util.List;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.ec2.Ec2Service;
import com.aws.sample.ec2.model.PingStatus;

/**
 * EC2 实例 Ping 状态检查测试
 *
 * 使用方式：
 *   mvn test -Dtest=Ec2PingStatusTest
 */
public class Ec2PingStatusTest {

    public static void main(String[] args) {
        // 使用 ap-northeast-1 区域测试
        AwsConfig config = new AwsConfig();

        try (Ec2Service ec2Service = new Ec2Service(config)) {

            String instanceId = "i-0b1563a104a2811dc";

            // 1. 查询指定实例的 Ping 状态
            System.out.println("========== 查询实例 Ping 状态 ==========");
            System.out.println("实例 ID: " + instanceId);
            System.out.println("区域: " + config.getRegion());

            PingStatus status = ec2Service.getPingStatus(instanceId);
            if (status == null) {
                System.out.println("实例不存在或未运行: " + instanceId);
            } else {
                System.out.println("  实例状态: " + status.getInstanceState());
                System.out.println("  系统检查: " + status.getSystemStatus());
                System.out.println("  实例检查: " + status.getInstanceStatus());
                System.out.println("  可用区:   " + status.getAvailabilityZone());
                System.out.println("  可达:     " + (status.isReachable() ? "是 (2/2 passed)" : "否"));
            }

            // 2. 查询所有运行中实例的 Ping 状态
            System.out.println("\n========== 所有运行中实例的 Ping 状态 ==========");
            List<PingStatus> allStatuses = ec2Service.getAllPingStatuses();
            if (allStatuses.isEmpty()) {
                System.out.println("当前没有运行中的实例");
            } else {
                for (PingStatus s : allStatuses) {
                    System.out.println("  实例: " + s.getInstanceId()
                            + " | 状态: " + s.getInstanceState()
                            + " | 系统检查: " + s.getSystemStatus()
                            + " | 实例检查: " + s.getInstanceStatus()
                            + " | 可达: " + (s.isReachable() ? "是" : "否"));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
