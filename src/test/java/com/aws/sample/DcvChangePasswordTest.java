package com.aws.sample;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.common.model.CommandResult;
import com.aws.sample.dcv.DcvService;

/**
 * DCV 修改用户密码测试
 */
public class DcvChangePasswordTest {

    public static void main(String[] args) {
        // 直接测试实际修改密码
        testChangePassword("i-0bc286927a1779157", "ubuntu", "NewPass123");
    }

    /**
     * 测试实际修改密码（需要真实实例）
     */
    private static void testChangePassword(String instanceId, String username, String newPassword) {
        System.out.println("\n=== 测试实际修改密码 ===");
        System.out.println("实例: " + instanceId);
        System.out.println("用户: " + username);
        
        DcvService dcvService = new DcvService(new AwsConfig());
        
        try {
            CommandResult result = dcvService.changeUserPassword(instanceId, username, newPassword);
            System.out.println("状态: " + result.getStatus());
            System.out.println("输出: " + result.getStandardOutput());
            if (!result.getStandardError().isEmpty()) {
                System.out.println("错误: " + result.getStandardError());
            }
        } catch (Exception e) {
            System.out.println("失败: " + e.getMessage());
        }
    }
}
