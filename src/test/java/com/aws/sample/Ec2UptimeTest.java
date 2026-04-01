package com.aws.sample;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.ec2.Ec2Service;
import com.aws.sample.ec2.model.InstanceInfo;

/**
 * EC2 实例运行时长查询测试
 *
 * 通过 DescribeInstances 返回的 LaunchTime 计算实例自最近一次启动以来的运行时长。
 *
 * 使用方式：
 *   mvn test -Dtest=Ec2UptimeTest
 */
public class Ec2UptimeTest {

    public static void main(String[] args) {
        AwsConfig config = new AwsConfig();

        try (Ec2Service ec2Service = new Ec2Service(config)) {

            // 1. 查询指定实例的运行时长
            String instanceId = "i-0b1563a104a2811dc";
            System.out.println("========== 查询实例运行时长 ==========");
            System.out.println("实例 ID: " + instanceId);
            System.out.println("区域: " + config.getRegion());

            InstanceInfo info = ec2Service.getInstanceInfo(instanceId);
            if (info == null) {
                System.out.println("实例不存在: " + instanceId);
            } else {
                System.out.println("  实例类型: " + info.getInstanceType());
                System.out.println("  实例状态: " + info.getState());
                System.out.println("  启动时间: " + info.getLaunchTime());
                printUptime(info);
            }

            // 2. 查询所有实例的运行时长
            System.out.println("\n========== 所有实例运行时长 ==========");
            List<InstanceInfo> allInstances = ec2Service.listAllInstances();
            if (allInstances.isEmpty()) {
                System.out.println("当前没有实例");
            } else {
                for (InstanceInfo inst : allInstances) {
                    System.out.println("  实例: " + inst.getInstanceId()
                            + " | 状态: " + inst.getState()
                            + " | 类型: " + inst.getInstanceType()
                            + " | 启动时间: " + inst.getLaunchTime()
                            + " | 运行时长: " + formatUptime(inst));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 打印实例运行时长详情
     */
    private static void printUptime(InstanceInfo info) {
        if (info.getLaunchTime() == null) {
            System.out.println("  运行时长: 未知（无启动时间）");
            return;
        }
        if (!"running".equals(info.getState())) {
            System.out.println("  运行时长: 实例未运行（当前状态: " + info.getState() + "）");
            return;
        }

        Instant launchTime = Instant.parse(info.getLaunchTime());
        Duration uptime = Duration.between(launchTime, Instant.now());

        long days = uptime.toDays();
        long hours = uptime.toHoursPart();
        long minutes = uptime.toMinutesPart();

        System.out.println("  运行时长: " + days + " 天 " + hours + " 小时 " + minutes + " 分钟");
    }

    /**
     * 格式化运行时长为简短字符串
     */
    private static String formatUptime(InstanceInfo info) {
        if (info.getLaunchTime() == null) {
            return "未知";
        }
        if (!"running".equals(info.getState())) {
            return "未运行";
        }

        Instant launchTime = Instant.parse(info.getLaunchTime());
        Duration uptime = Duration.between(launchTime, Instant.now());

        long days = uptime.toDays();
        long hours = uptime.toHoursPart();
        long minutes = uptime.toMinutesPart();

        return days + "天" + hours + "时" + minutes + "分";
    }
}
