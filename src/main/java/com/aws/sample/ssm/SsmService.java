package com.aws.sample.ssm;

import java.util.List;
import java.util.Map;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.common.model.CommandResult;
import com.aws.sample.ssm.model.SsmConnectionStatus;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.CloudWatchOutputConfig;
import software.amazon.awssdk.services.ssm.model.DescribeInstanceInformationRequest;
import software.amazon.awssdk.services.ssm.model.DescribeInstanceInformationResponse;
import software.amazon.awssdk.services.ssm.model.GetCommandInvocationRequest;
import software.amazon.awssdk.services.ssm.model.GetCommandInvocationResponse;
import software.amazon.awssdk.services.ssm.model.InstanceInformationFilter;
import software.amazon.awssdk.services.ssm.model.InstanceInformationFilterKey;
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

        String regionId = config.getRegion().id();
        String iamRole = config.getInstanceProfile();
        String fstabEntry = s3fsSource + " " + mountPoint + " fuse.s3fs _netdev,iam_role=" + iamRole
                + ",allow_other,umask=0000,url=https://s3." + regionId + ".amazonaws.com,endpoint=" + regionId + " 0 0";

        List<String> commands = List.of(
                "#!/bin/bash",
                "set -x",
                "echo '===== 环境检查 ====='",
                "echo '操作系统:' $(cat /etc/os-release 2>/dev/null | grep PRETTY_NAME | cut -d= -f2)",
                "echo 'IAM 角色:' $(curl -s http://169.254.169.254/latest/meta-data/iam/security-credentials/ 2>/dev/null || echo '未绑定')",
                "command -v s3fs && echo 's3fs 版本:' $(s3fs --version 2>&1 | head -1) || { echo 's3fs 未安装，开始安装...'; sudo apt-get update -y && sudo apt-get install -y s3fs; }",
                "echo '===== 开始挂载 ====='",
                "echo '挂载源: " + s3fsSource + "'",
                "echo '挂载点: " + mountPoint + "'",
                "echo 'IAM Role: " + iamRole + "'",
                "echo 'Region: " + regionId + "'",
                "sudo mkdir -p " + mountPoint,
                "sudo fusermount -u " + mountPoint + " 2>/dev/null || sudo umount -l " + mountPoint + " 2>/dev/null || true",
                "grep -qF '" + s3fsSource + "' /etc/fstab || echo '" + fstabEntry + "' | sudo tee -a /etc/fstab",
                "echo '===== 执行 s3fs 挂载 ====='",
                "sudo s3fs " + s3fsSource + " " + mountPoint + " -o iam_role=" + iamRole + " -o allow_other -o nonempty -o umask=0000 -o url=https://s3." + regionId + ".amazonaws.com -o endpoint=" + regionId + " -o dbglevel=err 2>/tmp/s3fs_err.log; S3FS_EXIT=$?",
                "echo 's3fs 退出码:' $S3FS_EXIT",
                "sleep 3",
                "echo '===== 挂载结果检查 ====='",
                "echo 'mount 输出:' $(mount | grep " + mountPoint + " || echo '未找到挂载点')",
                "echo 'fstab 内容:' $(grep " + s3fsSource + " /etc/fstab || echo '未找到 fstab 条目')",
                "if [ -f /tmp/s3fs_err.log ] && [ -s /tmp/s3fs_err.log ]; then echo 's3fs 错误日志:'; cat /tmp/s3fs_err.log; fi",
                "echo 'syslog 中的 s3fs 日志:'; tail -30 /var/log/syslog 2>/dev/null | grep -i s3fs || echo '无 syslog 日志'",
                "if mount | grep -q " + mountPoint + "; then echo '===== 挂载成功 ====='; else echo '===== 挂载失败 ====='; fi",
                "df -h " + mountPoint + " || true",
                "ls -la " + mountPoint,
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

    // ==================== 脚本执行（带 CloudWatch 日志监控）====================

    /**
     * 在 EC2 实例上执行脚本命令，并将所有输出发送到指定的 CloudWatch Logs 日志组
     *
     * 底层调用 SSM SendCommand API，通过 CloudWatchOutputConfig 配置日志输出。
     * SSM 会自动将命令的 stdout 和 stderr 写入指定的 CloudWatch Logs 日志组，
     * 日志流名称格式为: [command-id]/[instance-id]/aws-runShellScript/stdout 和 .../stderr
     *
     * 适用场景:
     * - 执行 Python 训练/推理脚本，需要完整日志记录
     * - 执行长时间运行的任务，需要实时查看输出
     * - 需要事后通过 CloudWatch Logs Insights 分析执行日志
     *
     * 注意: EC2 实例需要有 CloudWatch Logs 写入权限（logs:CreateLogGroup、logs:CreateLogStream、logs:PutLogEvents）
     *
     * @param instanceId        EC2 实例 ID
     * @param commands          要执行的命令列表（如 ["python3 /opt/scripts/train.py --epochs 10"]）
     * @param logGroupName      CloudWatch Logs 日志组名称（如 "/ssm/script-execution"）
     * @param timeoutSeconds    命令超时时间（秒），0 表示使用默认值 3600
     * @return SSM 命令 ID，可通过 getCommandResult 获取执行结果
     */
    public String executeCommandWithLogs(String instanceId,
                                          List<String> commands,
                                          String logGroupName,
                                          int timeoutSeconds) {
        System.out.println("执行命令（带 CloudWatch 日志）: " + instanceId);
        System.out.println("日志组: " + logGroupName);

        int timeout = timeoutSeconds > 0 ? timeoutSeconds : 3600;

        SendCommandRequest request = SendCommandRequest.builder()
                .instanceIds(instanceId)
                .documentName("AWS-RunShellScript")
                .parameters(Map.of("commands", commands))
                .timeoutSeconds(timeout)
                .cloudWatchOutputConfig(CloudWatchOutputConfig.builder()
                        .cloudWatchLogGroupName(logGroupName)
                        .cloudWatchOutputEnabled(true)
                        .build())
                .build();

        SendCommandResponse response = ssmClient.sendCommand(request);
        String commandId = response.command().commandId();
        System.out.println("命令已发送，命令 ID: " + commandId);
        System.out.println("日志将输出到: " + logGroupName + "/" + commandId);
        return commandId;
    }

    /**
     * 在 EC2 实例上执行脚本，并将日志输出到 CloudWatch Logs
     *
     * 封装了脚本执行的常见模式:
     * 1. 使用 set -euo pipefail 确保脚本出错时立即退出
     * 2. 记录脚本开始/结束时间
     * 3. 捕获脚本退出码，输出执行结果（成功/失败）
     * 4. 所有 stdout/stderr 输出自动发送到 CloudWatch Logs
     *
     * @param instanceId     EC2 实例 ID
     * @param executeCommand 完整的执行命令（如 "python3 /opt/scripts/train.py --epochs 10"、"bash /opt/run.sh"、"/opt/myapp --config prod.yaml"）
     * @param logGroupName   CloudWatch Logs 日志组名称
     * @param timeoutSeconds 超时时间（秒），0 表示默认 3600
     * @return SSM 命令 ID
     */
    public String executeScriptWithLogs(String instanceId,
                                         String executeCommand,
                                         String logGroupName,
                                         int timeoutSeconds) {
        System.out.println("执行脚本命令: " + executeCommand);

        List<String> commands = List.of(
                "set -euo pipefail",
                "echo '========== 脚本执行开始 =========='",
                "echo '执行命令: " + executeCommand.replace("'", "'\\''") + "'",
                "echo '开始时间: '$(date '+%Y-%m-%d %H:%M:%S')",
                "echo '========================================='",
                "",
                executeCommand,
                "EXIT_CODE=$?",
                "",
                "echo '========== 脚本执行结束 =========='",
                "echo '结束时间: '$(date '+%Y-%m-%d %H:%M:%S')",
                "if [ $EXIT_CODE -eq 0 ]; then",
                "    echo '执行结果: 成功 (exit code: 0)'",
                "else",
                "    echo 'ERROR: 执行结果: 失败 (exit code: '$EXIT_CODE')'",
                "fi",
                "echo '========================================='",
                "exit $EXIT_CODE"
        );

        return executeCommandWithLogs(instanceId, commands, logGroupName, timeoutSeconds);
    }

    /**
     * 执行脚本命令并等待完成，返回执行结果
     *
     * 组合调用 executeScriptWithLogs + getCommandResult，
     * 同步等待脚本执行完成并返回结果。
     * 适合需要立即获取执行状态的场景。
     *
     * @param instanceId     EC2 实例 ID
     * @param executeCommand 完整的执行命令（如 "python3 train.py --epochs 10"）
     * @param logGroupName   CloudWatch Logs 日志组名称
     * @param timeoutSeconds 超时时间（秒）
     * @return 命令执行结果，包含状态、stdout、stderr
     */
    public CommandResult executeScriptAndWait(String instanceId,
                                               String executeCommand,
                                               String logGroupName,
                                               int timeoutSeconds) {
        String commandId = executeScriptWithLogs(instanceId, executeCommand, logGroupName, timeoutSeconds);
        CommandResult result = getCommandResult(commandId, instanceId);

        if (result.isSuccess()) {
            System.out.println("脚本执行成功: " + executeCommand);
        } else {
            System.out.println("ERROR: 脚本执行失败: " + executeCommand + ", 状态: " + result.getStatus());
        }
        System.out.println("完整日志请查看 CloudWatch Logs: " + logGroupName);
        return result;
    }

    // ==================== SSM 连接状态查询 ====================

    /**
     * 获取单个实例的 SSM Agent 连接状态
     * @param instanceId 实例 ID
     * @return 连接状态信息，未注册 SSM 时返回 null
     */
    public SsmConnectionStatus getConnectionStatus(String instanceId) {
        DescribeInstanceInformationRequest request = DescribeInstanceInformationRequest.builder()
                .instanceInformationFilterList(InstanceInformationFilter.builder()
                        .key(InstanceInformationFilterKey.INSTANCE_IDS)
                        .valueSet(instanceId)
                        .build())
                .build();
        DescribeInstanceInformationResponse response = ssmClient.describeInstanceInformation(request);
        if (response.instanceInformationList().isEmpty()) {
            return null;
        }
        return new SsmConnectionStatus(response.instanceInformationList().get(0));
    }

    /**
     * 获取所有已注册 SSM 的实例连接状态
     * @return 连接状态列表
     */
    public List<SsmConnectionStatus> getAllConnectionStatuses() {
        DescribeInstanceInformationResponse response = ssmClient.describeInstanceInformation(
                DescribeInstanceInformationRequest.builder().build()
        );
        return response.instanceInformationList().stream()
                .map(SsmConnectionStatus::new)
                .toList();
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
