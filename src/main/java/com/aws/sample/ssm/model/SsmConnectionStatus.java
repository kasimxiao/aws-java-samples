package com.aws.sample.ssm.model;

import java.time.Instant;

import software.amazon.awssdk.services.ssm.model.InstanceInformation;
import software.amazon.awssdk.services.ssm.model.PingStatus;

/**
 * SSM Agent 连接状态封装类
 * 对应 AWS 控制台中 Session Manager 的连接状态
 */
public class SsmConnectionStatus {

    private final String instanceId;
    private final String pingStatus;
    private final Instant lastPingTime;
    private final String agentVersion;
    private final String platformType;
    private final String platformName;
    private final String platformVersion;
    private final String ipAddress;

    public SsmConnectionStatus(InstanceInformation info) {
        this.instanceId = info.instanceId();
        this.pingStatus = info.pingStatusAsString();
        this.lastPingTime = info.lastPingDateTime();
        this.agentVersion = info.agentVersion();
        this.platformType = info.platformTypeAsString();
        this.platformName = info.platformName();
        this.platformVersion = info.platformVersion();
        this.ipAddress = info.ipAddress();
    }

    public String getInstanceId() { return instanceId; }
    public String getPingStatus() { return pingStatus; }
    public Instant getLastPingTime() { return lastPingTime; }
    public String getAgentVersion() { return agentVersion; }
    public String getPlatformType() { return platformType; }
    public String getPlatformName() { return platformName; }
    public String getPlatformVersion() { return platformVersion; }
    public String getIpAddress() { return ipAddress; }

    /**
     * 判断 SSM Agent 是否在线
     */
    public boolean isOnline() {
        return PingStatus.ONLINE.toString().equalsIgnoreCase(pingStatus);
    }

    @Override
    public String toString() {
        return String.format(
                "SsmConnectionStatus{id='%s', ping='%s', lastPing='%s', agent='%s', platform='%s %s', ip='%s'}",
                instanceId, pingStatus, lastPingTime, agentVersion, platformName, platformVersion, ipAddress
        );
    }
}
