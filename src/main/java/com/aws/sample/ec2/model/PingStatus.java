package com.aws.sample.ec2.model;

import software.amazon.awssdk.services.ec2.model.InstanceStatus;

/**
 * EC2 实例 Ping 状态封装类
 * 包含系统状态检查、实例状态检查和可达性信息
 */
public class PingStatus {

    private final String instanceId;
    private final String instanceState;
    private final String systemStatus;
    private final String instanceStatus;
    private final String availabilityZone;

    public PingStatus(InstanceStatus status) {
        this.instanceId = status.instanceId();
        this.instanceState = status.instanceState() != null
                ? status.instanceState().nameAsString() : "unknown";
        this.systemStatus = status.systemStatus() != null
                ? status.systemStatus().statusAsString() : "unknown";
        this.instanceStatus = status.instanceStatus() != null
                ? status.instanceStatus().statusAsString() : "unknown";
        this.availabilityZone = status.availabilityZone();
    }

    public String getInstanceId() { return instanceId; }
    public String getInstanceState() { return instanceState; }
    public String getSystemStatus() { return systemStatus; }
    public String getInstanceStatus() { return instanceStatus; }
    public String getAvailabilityZone() { return availabilityZone; }

    /**
     * 判断实例是否完全可达（系统检查和实例检查均通过）
     */
    public boolean isReachable() {
        return "ok".equalsIgnoreCase(systemStatus) && "ok".equalsIgnoreCase(instanceStatus);
    }

    @Override
    public String toString() {
        return String.format(
                "PingStatus{id='%s', state='%s', system='%s', instance='%s', az='%s', reachable=%s}",
                instanceId, instanceState, systemStatus, instanceStatus, availabilityZone, isReachable()
        );
    }
}
