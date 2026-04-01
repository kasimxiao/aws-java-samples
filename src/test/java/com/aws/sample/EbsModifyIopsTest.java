package com.aws.sample;

import java.util.List;

/**
 * EBS 卷 IOPS 修改测试
 * 通过实例 ID 自动获取关联的 EBS 卷，然后修改 IOPS
 */
public class EbsModifyIopsTest {

    // 替换为实际的 EC2 实例 ID
    private static final String INSTANCE_ID = "i-0fbcf34ebe34980cc";
    private static final int TARGET_IOPS = 5000;

    public static void main(String[] args) {
        System.out.println("开始测试 EBS 卷 IOPS 修改...\n");

        try (Ec2Manager manager = new Ec2Manager()) {
            manager.getConfig().printConfig();

            // 步骤1: 获取实例关联的所有 EBS 卷
            System.out.println("\n========== 步骤1: 查询实例关联的 EBS 卷 ==========");
            System.out.println("实例 ID: " + INSTANCE_ID);

            List<String> volumeIds = manager.getVolumeIds(INSTANCE_ID);
            if (volumeIds.isEmpty()) {
                System.out.println("未找到关联的 EBS 卷，测试结束");
                return;
            }
            System.out.println("找到 " + volumeIds.size() + " 个 EBS 卷: " + volumeIds);

            // 步骤2: 获取根卷 ID
            System.out.println("\n========== 步骤2: 获取根卷 ID ==========");
            String rootVolumeId = manager.getRootVolumeId(INSTANCE_ID);
            System.out.println("根卷 ID: " + rootVolumeId);

            // 步骤3: 修改根卷 IOPS
            String targetVolumeId = rootVolumeId != null ? rootVolumeId : volumeIds.get(0);
            System.out.println("\n========== 步骤3: 修改 EBS 卷 IOPS ==========");
            System.out.println("目标卷 ID: " + targetVolumeId);
            System.out.println("目标 IOPS: " + TARGET_IOPS);

            String state = manager.modifyVolumeIops(targetVolumeId, TARGET_IOPS);
            System.out.println("修改状态: " + state);

            System.out.println("\n========== 测试完成 ==========");

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
