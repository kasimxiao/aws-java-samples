package com.aws.sample.ssm;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.common.model.CommandResult;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.util.List;
import java.util.Map;

/**
 * Systems Manager 服务类
 * 提供远程命令执行功能
 */
public class SsmService implements AutoCloseable {

    private final SsmClient ssmClient;
    private final AwsConfig config;

    public SsmService(AwsConfig config) {
        this.config = config;
        this.ssmClient = SsmClient.builder()
                .region(config.getRegion())
                .credentialsProvider(config.getCredentialsProvider())
                .build();
    }

    /**
     * 执行 Shell 命令
     */
    public String executeCommand(String instanceId, List<String> commands) {
        SendCommandRequest request = SendCommandRequest.builder()
                .instanceIds(instanceId)
                .documentName("AWS-RunShellScript")
                .parameters(Map.of("commands", commands))
                .timeoutSeconds(600)
                .build();

        SendCommandResponse response = ssmClient.sendCommand(request);
        String commandId = response.command().commandId();
        System.out.println("命令已发送，命令 ID: " + commandId);
        return commandId;
    }

    /**
     * 挂载 S3 存储桶
     */
    public String mountS3Bucket(String instanceId, String s3BucketName, String mountPoint) {
        List<String> commands = List.of(
                "if ! command -v s3fs &> /dev/null; then",
                "    sudo yum install -y epel-release || sudo amazon-linux-extras install epel -y",
                "    sudo yum install -y s3fs-fuse",
                "fi",
                "sudo mkdir -p " + mountPoint,
                "sudo s3fs " + s3BucketName + " " + mountPoint + " -o iam_role=auto -o allow_other -o use_cache=/tmp/s3fs",
                "df -h " + mountPoint,
                "echo 'S3 存储桶 " + s3BucketName + " 已挂载到 " + mountPoint + "'"
        );
        return executeCommand(instanceId, commands);
    }

    /**
     * 使用默认配置挂载 S3
     */
    public String mountS3WithDefaults(String instanceId) {
        return mountS3Bucket(instanceId, config.getS3Bucket(), config.getS3MountPoint());
    }

    /**
     * 获取命令执行结果
     */
    public CommandResult getCommandResult(String commandId, String instanceId) {
        GetCommandInvocationRequest request = GetCommandInvocationRequest.builder()
                .commandId(commandId)
                .instanceId(instanceId)
                .build();

        int maxAttempts = 30;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                GetCommandInvocationResponse response = ssmClient.getCommandInvocation(request);
                String status = response.statusAsString();

                if ("Success".equals(status) || "Failed".equals(status) || 
                    "Cancelled".equals(status) || "TimedOut".equals(status)) {
                    return new CommandResult(
                            commandId, status,
                            response.standardOutputContent(),
                            response.standardErrorContent()
                    );
                }
                Thread.sleep(2000);
            } catch (Exception e) {
                if (i >= maxAttempts - 1) {
                    throw new RuntimeException("获取命令结果失败", e);
                }
                try { Thread.sleep(2000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new RuntimeException("获取命令结果超时: " + commandId);
    }

    @Override
    public void close() {
        if (ssmClient != null) {
            ssmClient.close();
        }
    }
}
