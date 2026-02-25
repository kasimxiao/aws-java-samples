package com.aws.sample.ssm;

import java.util.List;
import java.util.Map;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.common.model.CommandResult;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetCommandInvocationRequest;
import software.amazon.awssdk.services.ssm.model.GetCommandInvocationResponse;
import software.amazon.awssdk.services.ssm.model.SendCommandRequest;
import software.amazon.awssdk.services.ssm.model.SendCommandResponse;

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
     * 挂载 S3 存储桶或指定子目录
     *
     * 通过 SSM 远程执行 s3fs-fuse 挂载命令，支持挂载整个存储桶或指定前缀目录。
     * 自动检测并安装 s3fs-fuse 依赖，使用 IAM Role 自动获取凭证。
     *
     * @param instanceId   EC2 实例 ID
     * @param s3BucketName S3 存储桶名称（不含 s3:// 前缀）
     * @param s3SubDir     S3 子目录路径（如 "data/training"），为 null 或空字符串时挂载整个存储桶
     * @param mountPoint   本地挂载点路径（如 "/mnt/s3"）
     * @return SSM 命令 ID，可通过 getCommandResult 获取执行结果
     */
    public String mountS3Bucket(String instanceId, String s3BucketName, String s3SubDir, String mountPoint) {
        // 构建 s3fs 挂载参数，指定子目录时使用 -o servicepath=/ -o url=... 方式
        // s3fs 原生支持通过 bucket:/path 语法挂载子目录
        String s3fsSource = s3BucketName;
        if (s3SubDir != null && !s3SubDir.isEmpty()) {
            // 去除首尾斜杠，确保格式统一
            s3SubDir = s3SubDir.replaceAll("^/+|/+$", "");
            s3fsSource = s3BucketName + ":/" + s3SubDir;
        }

        String displayPath = (s3SubDir != null && !s3SubDir.isEmpty())
                ? s3BucketName + "/" + s3SubDir
                : s3BucketName;

        List<String> commands = List.of(
                "if ! command -v s3fs &> /dev/null; then",
                "    sudo yum install -y epel-release || sudo amazon-linux-extras install epel -y",
                "    sudo yum install -y s3fs-fuse",
                "fi",
                "sudo mkdir -p " + mountPoint,
                "sudo s3fs " + s3fsSource + " " + mountPoint + " -o iam_role=auto -o allow_other -o use_cache=/tmp/s3fs",
                "df -h " + mountPoint,
                "echo 'S3 路径 " + displayPath + " 已挂载到 " + mountPoint + "'"
        );
        return executeCommand(instanceId, commands);
    }

    /**
     * 挂载整个 S3 存储桶（兼容旧接口）
     *
     * @param instanceId   EC2 实例 ID
     * @param s3BucketName S3 存储桶名称
     * @param mountPoint   本地挂载点路径
     * @return SSM 命令 ID
     */
    public String mountS3Bucket(String instanceId, String s3BucketName, String mountPoint) {
        return mountS3Bucket(instanceId, s3BucketName, null, mountPoint);
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
