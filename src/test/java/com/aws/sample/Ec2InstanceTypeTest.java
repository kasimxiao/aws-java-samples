package com.aws.sample;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.ec2.Ec2InstanceTypeService;
import com.aws.sample.ec2.model.InstanceTypeInfo;

/**
 * EC2 实例类型服务测试
 */
public class Ec2InstanceTypeTest {

    private static AwsConfig config;
    private static Ec2InstanceTypeService instanceTypeService;

    @BeforeAll
    static void setUp() {
        config = new AwsConfig();
        instanceTypeService = new Ec2InstanceTypeService(config);
    }

    @AfterAll
    static void tearDown() {
        if (instanceTypeService != null) {
            instanceTypeService.close();
        }
    }

    @Test
    void testListInstanceTypes() {
        System.out.println("========== 测试获取实例类型列表 ==========");
        
        // 查询所有实例类型
        String family = null;  // 设为 null 查所有，或指定系列如 "t3"、"g5"
        List<InstanceTypeInfo> types = instanceTypeService.listInstanceTypes(family);
        
        String title = (family == null) ? "所有实例类型" : family + " 系列";
        System.out.println(title + "（前 10 个）:");
        types.stream()
                .limit(10)
                .forEach(info -> {
                    String gpuInfo = info.isGpuSupported() 
                            ? String.format("GPU: %d个", info.getTotalGpuCount())
                            : "无GPU";
                    System.out.printf("  %s: %d vCPU, %.1f GiB, %s, 网络: %s%n", 
                            info.getInstanceType(), 
                            info.getVCpuCount(), 
                            info.getMemorySizeGiB(),
                            gpuInfo,
                            info.getNetworkPerformance());
                });
    }

    @Test
    void testGetInstanceTypeInfo() {
        System.out.println("========== 测试获取实例类型详情 ==========");
        
        // 查询具体实例类型信息
        String[] testTypes = {"t3.medium", "m5.large", "g5.xlarge"};
        for (String type : testTypes) {
            InstanceTypeInfo info = instanceTypeService.getInstanceTypeInfo(type);
            if (info != null) {
                System.out.println(info);
            } else {
                System.out.println("未找到实例类型: " + type);
            }
        }
    }
}
