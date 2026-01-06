package com.aws.sample.common.model;

/**
 * SSM 命令执行结果封装类
 */
public class CommandResult {

    private final String commandId;
    private final String status;
    private final String standardOutput;
    private final String standardError;

    public CommandResult(String commandId, String status, String standardOutput, String standardError) {
        this.commandId = commandId;
        this.status = status;
        this.standardOutput = standardOutput;
        this.standardError = standardError;
    }

    public String getCommandId() { return commandId; }
    public String getStatus() { return status; }
    public String getStandardOutput() { return standardOutput; }
    public String getStandardError() { return standardError; }

    public boolean isSuccess() {
        return "Success".equals(status);
    }

    @Override
    public String toString() {
        return String.format("CommandResult{id='%s', status='%s'}", commandId, status);
    }
}
