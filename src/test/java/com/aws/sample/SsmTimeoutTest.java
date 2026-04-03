package com.aws.sample;

import java.util.List;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.common.model.CommandResult;
import com.aws.sample.ssm.SsmService;

/**
 * SSM executionTimeout 超时验证测试
 *
 * 发送一个 sleep 30 的命令，但设置 executionTimeout 为 10 秒，
 * 预期命令会在 10 秒后被终止，状态为 TimedOut。
 *
 * 使用方式：
 *   mvn test-compile exec:java -Dexec.mainClass="com.aws.sample.SsmTimeoutTest" -Dexec.classpathScope=test
 */
public class SsmTimeoutTest {

    public static void main(String[] args) {
        AwsConfig config = new AwsConfig();
        config.setRegion("ap-northeast-1");

        String instanceId = "i-026e43e2006e24c9e";

        System.out.println("========== SSM executionTimeout 超时测试 ==========");
        System.out.println("实例 ID:          " + instanceId);
        System.out.println("区域:             ap-northeast-1");
        System.out.println("测试命令:         sleep 60");
        System.out.println("executionTimeout: 30 秒（API 最小值）");
        System.out.println("预期结果:         TimedOut（约 30 秒后超时）");
        System.out.println("==================================================\n");

        try (SsmService ssmService = new SsmService(config)) {
            // 使用 executeCommandWithLogs 方法，设置 10 秒超时
            List<String> commands = List.of(
                    "echo '开始执行 sleep 60...'",
                    "echo '当前时间:' $(date '+%H:%M:%S')",
                    "sleep 60",
                    "echo '如果看到这行，说明超时没有生效'",
                    "echo '结束时间:' $(date '+%H:%M:%S')"
            );

            long startTime = System.currentTimeMillis();
            String commandId = ssmService.executeCommandWithLogs(
                    instanceId, commands, "/ssm/timeout-test", 30);

            System.out.println("命令 ID: " + commandId);
            System.out.println("等待命令执行结果...\n");

            CommandResult result = ssmService.getCommandResult(commandId, instanceId);
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;

            System.out.println("========== 测试结果 ==========");
            System.out.println("执行状态: " + result.getStatus());
            System.out.println("耗时:     " + elapsed + " 秒");
            System.out.println("标准输出:\n" + result.getStandardOutput());
            if (result.getStandardError() != null && !result.getStandardError().isEmpty()) {
                System.out.println("错误输出:\n" + result.getStandardError());
            }

            if ("TimedOut".equals(result.getStatus())) {
                System.out.println("\n✅ 测试通过: executionTimeout 生效，命令在超时后被终止");
            } else if ("Success".equals(result.getStatus())) {
                System.out.println("\n❌ 测试失败: 命令正常完成，executionTimeout 未生效");
            } else {
                System.out.println("\n⚠️ 非预期状态: " + result.getStatus());
            }

        } catch (Exception e) {
            System.out.println("测试异常:");
            e.printStackTrace();
        }
    }
}
