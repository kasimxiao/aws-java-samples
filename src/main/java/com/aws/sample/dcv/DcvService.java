package com.aws.sample.dcv;

import com.aws.sample.common.AwsConfig;

import java.util.List;

/**
 * DCV 服务类 - 生成免密登录 URL
 * 
 * 使用说明：
 * 1. 前置条件：EC2 实例已安装并运行 DCV Server，安全组开放 DCV 端口（默认 8443）
 * 2. URL 格式：https://{ip}:{port}/?authToken={token}#{sessionId}
 */
public class DcvService {

    private final AwsConfig config;

    public DcvService(AwsConfig config) {
        this.config = config;
    }

    /**
     * 生成 DCV 免密登录 URL（web浏览器使用）
     */
    public String generatePresignedUrl(String serverIp, int serverPort, String sessionId, String authToken) {
        String url = String.format("https://%s:%d/?authToken=%s#%s",
                serverIp, serverPort, authToken, sessionId);
        System.out.println("DCV 免密 URL: " + url);
        return url;
    }

    /**
     * 使用默认端口生成免密 URL（DCV客户端使用）
     */
    public String generatePresignedUrl(String serverIp, String sessionId, String authToken) {
        return generatePresignedUrl(serverIp, config.getDcvPort(), sessionId, authToken);
    }

    /**
     * 生成用于 SSM 执行的令牌生成命令
     */
    public List<String> getTokenGenerationCommands(String sessionId, String user) {
        String session = (sessionId != null && !sessionId.isEmpty()) ? sessionId : "console";
        String dcvUser = (user != null && !user.isEmpty()) ? user : "ec2-user";

        return List.of(
                "dcv generate-auth-token --session " + session + " --user " + dcvUser
        );
    }
}
