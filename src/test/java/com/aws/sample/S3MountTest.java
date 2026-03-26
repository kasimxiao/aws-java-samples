package com.aws.sample;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.ssm.SsmService;

/**
 * S3 存储桶挂载测试（通过 fstab 持久化挂载）
 *
 * 使用方式：
 *   mvn test-compile exec:java -Dexec.mainClass="com.aws.sample.S3MountTest" -Dexec.classpathScope=test
 */
public class S3MountTest {

    public static void main(String[] args) {
        AwsConfig config = new AwsConfig();
        // 此测试的实例和 S3 桶在 ap-northeast-1
        config.setRegion("ap-northeast-1");

        try (SsmService ssmService = new SsmService(config)) {

            String instanceId = "i-0dd93cdc44ea2a471";
            String s3Bucket = "wongxiao-file";
            String s3SubDir = "uploads";
            String mountPoint = "/root/test";

            System.out.println("========== S3 挂载测试 ==========");
            System.out.println("实例 ID:   " + instanceId);
            System.out.println("S3 路径:   " + s3Bucket + "/" + s3SubDir);
            System.out.println("挂载点:    " + mountPoint);
            System.out.println("区域:      " + config.getRegion());
            System.out.println("IAM 角色:  " + config.getInstanceProfile());

            String commandId = ssmService.mountS3Bucket(instanceId, s3Bucket, s3SubDir, mountPoint);
            System.out.println("\n命令已发送，命令 ID: " + commandId);

            // 获取命令执行结果（内部自动轮询等待）
            var result = ssmService.getCommandResult(commandId, instanceId);
            System.out.println("\n执行状态: " + result.getStatus());
            System.out.println("标准输出:\n" + result.getStandardOutput());
            if (result.getStandardError() != null && !result.getStandardError().isEmpty()) {
                System.out.println("错误输出:\n" + result.getStandardError());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
