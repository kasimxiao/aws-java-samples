package com.aws.sample;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.ec2.Ec2Service;
import com.aws.sample.ec2.Ec2Service.ImageInfo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * EC2 实例打包成 AMI 镜像测试
 */
public class CreateAmiTest {

    public static void main(String[] args) {
        System.out.println("开始创建 AMI 镜像...\n");

        AwsConfig config = new AwsConfig();

        try (Ec2Service ec2Service = new Ec2Service(config)) {
            // 要打包的实例 ID
            String instanceId = "i-0bc286927a1779157";

            // 生成镜像名称（带时间戳）
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String imageName = "my-app-image-" + timestamp;
            String description = "Created from instance " + instanceId;

            // 创建 AMI（noReboot=true 不重启实例）
            String imageId = ec2Service.createImage(instanceId, imageName, description, true);
            System.out.println("AMI 创建中: " + imageId);

            // 等待 AMI 可用（最多等待 30 分钟）
            ec2Service.waitForImageAvailable(imageId, 30);

            // 获取镜像信息
            ImageInfo info = ec2Service.getImageInfo(imageId);
            System.out.println("\n========== 镜像信息 ==========");
            System.out.println("镜像 ID: " + info.getImageId());
            System.out.println("名称: " + info.getName());
            System.out.println("状态: " + info.getState());
            System.out.println("架构: " + info.getArchitecture());
            System.out.println("创建时间: " + info.getCreationDate());
            System.out.println("================================");

            // 列出所有镜像
            System.out.println("\n所有 AMI 镜像:");
            List<ImageInfo> images = ec2Service.listOwnedImages();
            for (ImageInfo image : images) {
                System.out.println("  - " + image.getImageId() + " | " + image.getName() + " | " + image.getState());
            }

        } catch (Exception e) {
            System.err.println("创建失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
