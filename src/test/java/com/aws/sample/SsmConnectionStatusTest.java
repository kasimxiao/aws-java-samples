package com.aws.sample;

import java.util.List;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.ssm.SsmService;
import com.aws.sample.ssm.model.SsmConnectionStatus;

/**
 * SSM Agent 连接状态测试
 *
 * 使用方式：
 *   mvn test-compile exec:java -Dexec.mainClass="com.aws.sample.SsmConnectionStatusTest" -Dexec.classpathScope=test
 */
public class SsmConnectionStatusTest {

    public static void main(String[] args) {
        AwsConfig config = new AwsConfig();

        try (SsmService ssmService = new SsmService(config)) {

            String instanceId = "i-0b1563a104a2811dc";

            // 1. 查询指定实例的 SSM 连接状态
            System.out.println("========== SSM Agent 连接状态 ==========");
            System.out.println("实例 ID: " + instanceId);
            System.out.println("区域: " + config.getRegion());

            SsmConnectionStatus status = ssmService.getConnectionStatus(instanceId);
            if (status == null) {
                System.out.println("实例未注册 SSM Agent: " + instanceId);
            } else {
                System.out.println("  连接状态:   " + status.getPingStatus());
                System.out.println("  在线:       " + (status.isOnline() ? "是" : "否"));
                System.out.println("  最后心跳:   " + status.getLastPingTime());
                System.out.println("  Agent 版本: " + status.getAgentVersion());
                System.out.println("  平台:       " + status.getPlatformName() + " " + status.getPlatformVersion());
                System.out.println("  IP 地址:    " + status.getIpAddress());
            }

            // 2. 查询所有已注册 SSM 的实例
            System.out.println("\n========== 所有 SSM 注册实例 ==========");
            List<SsmConnectionStatus> allStatuses = ssmService.getAllConnectionStatuses();
            if (allStatuses.isEmpty()) {
                System.out.println("当前没有注册 SSM 的实例");
            } else {
                for (SsmConnectionStatus s : allStatuses) {
                    System.out.println("  实例: " + s.getInstanceId()
                            + " | 状态: " + s.getPingStatus()
                            + " | Agent: " + s.getAgentVersion()
                            + " | 平台: " + s.getPlatformName()
                            + " | IP: " + s.getIpAddress());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
