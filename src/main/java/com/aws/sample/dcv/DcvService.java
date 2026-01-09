package com.aws.sample.dcv;

import java.util.List;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.common.model.CommandResult;
import com.aws.sample.ssm.SsmService;

/**
 * DCV 免密登录服务
 * 
 * 前置条件：
 * 1. EC2 实例已安装 DCV Server 和 nice-dcv-simple-external-authenticator
 * 2. DCV 配置了 auth-token-verifier="http://127.0.0.1:8444"
 * 3. 实例已配置 SSM Agent，安全组开放 8443 端口
 */
public class DcvService {

    private final AwsConfig config;

    public DcvService(AwsConfig config) {
        this.config = config;
    }

    /**
     * 生成 DCV 免密登录 URL
     */
    public String generatePresignedUrl(String instanceId, String serverIp, String sessionId, String user) {
        String session = defaultIfEmpty(sessionId, "console");
        String dcvUser = defaultIfEmpty(user, "ubuntu");

        try (SsmService ssmService = new SsmService(config)) {
            // 检查服务状态
            List<String> checkCommands = List.of(
                "systemctl status dcvsimpleextauth | head -3",
                "systemctl status dcvserver | head -3"
            );
            String checkId = ssmService.executeCommand(instanceId, checkCommands);
            CommandResult checkResult = ssmService.getCommandResult(checkId, instanceId);
            System.out.println("服务状态:\n" + checkResult.getStandardOutput());

            List<String> commands = List.of(
                // 生成 token 并注册
                "TOKEN=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1)",
                "sudo rm -f /var/run/dcvsimpleextauth/" + session,
                "echo $TOKEN | sudo python3 /usr/lib/x86_64-linux-gnu/dcvsimpleextauth.py add-user --user " + dcvUser + " --session " + session + " --auth-dir /var/run/dcvsimpleextauth",
                "sudo chown dcv:dcv /var/run/dcvsimpleextauth/" + session,
                // 解锁会话
                "sudo loginctl unlock-sessions",
                // 输出 token (最后一行)
                "echo $TOKEN"
            );

            String commandId = ssmService.executeCommand(instanceId, commands);
            CommandResult result = ssmService.getCommandResult(commandId, instanceId);

            System.out.println("执行结果:\n" + result.getStandardOutput());
            if (!result.getStandardError().isEmpty()) {
                System.out.println("错误:\n" + result.getStandardError());
            }

            String[] lines = result.getStandardOutput().trim().split("\n");
            String token = lines[lines.length - 1].trim();

            return String.format("https://%s:%d/?authToken=%s#%s", 
                serverIp, config.getDcvPort(), token, session);
        }
    }

    /**
     * 简化版：使用默认参数
     */
    public String generatePresignedUrl(String instanceId, String serverIp) {
        return generatePresignedUrl(instanceId, serverIp, "console", "ubuntu");
    }

    /**
     * 修改实例用户密码
     * 
     * @param instanceId EC2 实例 ID
     * @param username   用户名
     * @param newPassword 新密码
     * @return 执行结果
     */
    public CommandResult changeUserPassword(String instanceId, String username, String newPassword) {
        // 验证参数，防止命令注入
        validateUsername(username);
        validatePassword(newPassword);

        try (SsmService ssmService = new SsmService(config)) {
            // 检查用户是否存在
            List<String> checkUserCommands = List.of(
                "id " + username + " && echo 'USER_EXISTS' || echo 'USER_NOT_FOUND'"
            );
            String checkId = ssmService.executeCommand(instanceId, checkUserCommands);
            CommandResult checkResult = ssmService.getCommandResult(checkId, instanceId);
            
            if (checkResult.getStandardOutput().contains("USER_NOT_FOUND")) {
                throw new IllegalArgumentException("用户不存在: " + username);
            }

            // 修改密码
            List<String> commands = List.of(
                "echo '" + username + ":" + newPassword + "' | sudo chpasswd",
                "echo 'PASSWORD_CHANGED_SUCCESS'"
            );

            String commandId = ssmService.executeCommand(instanceId, commands);
            CommandResult result = ssmService.getCommandResult(commandId, instanceId);

            if (!"Success".equals(result.getStatus())) {
                throw new RuntimeException("修改密码失败: " + result.getStandardError());
            }

            System.out.println("用户 " + username + " 密码修改成功");
            return result;
        }
    }

    /**
     * 验证用户名格式，防止命令注入
     */
    private void validateUsername(String username) {
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        // 只允许字母、数字、下划线、连字符
        if (!username.matches("^[a-zA-Z0-9_-]{1,32}$")) {
            throw new IllegalArgumentException("用户名格式无效，只允许字母、数字、下划线和连字符，长度1-32");
        }
    }

    /**
     * 验证密码格式，防止命令注入
     */
    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("密码长度至少8位");
        }
        if (password.length() > 128) {
            throw new IllegalArgumentException("密码长度不能超过128位");
        }
        // 禁止包含可能导致命令注入的特殊字符
        if (password.contains("'") || password.contains("\"") || password.contains("`") ||
            password.contains("$") || password.contains("\\") || password.contains(";") ||
            password.contains("|") || password.contains("&") || password.contains("\n")) {
            throw new IllegalArgumentException("密码包含非法字符");
        }
    }

    private String defaultIfEmpty(String value, String defaultValue) {
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }
}
