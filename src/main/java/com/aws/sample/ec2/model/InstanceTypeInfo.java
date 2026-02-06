package com.aws.sample.ec2.model;

import java.util.List;

import software.amazon.awssdk.services.ec2.model.GpuDeviceInfo;

/**
 * EC2 实例类型信息模型
 * 包含 CPU、内存、GPU、网络等详细规格
 */
public class InstanceTypeInfo {

    private final String instanceType;
    private final int vCpuCount;
    private final int defaultVCpus;
    private final long memorySizeMiB;
    private final double memorySizeGiB;
    private final boolean gpuSupported;
    private final List<GpuInfo> gpuInfoList;
    private final String networkPerformance;
    private final int maxNetworkInterfaces;
    private final int ipv4AddressesPerInterface;
    private final boolean ebsOptimizedSupported;
    private final String hypervisor;
    private final String processorArchitecture;
    private final List<String> supportedUsageClasses;
    private final boolean bareMetal;
    private final boolean burstablePerformanceSupported;
    private final boolean dedicatedHostsSupported;
    private final boolean freeTierEligible;

    public InstanceTypeInfo(software.amazon.awssdk.services.ec2.model.InstanceTypeInfo awsInfo) {
        this.instanceType = awsInfo.instanceTypeAsString();
        
        // CPU 信息
        if (awsInfo.vCpuInfo() != null) {
            this.vCpuCount = awsInfo.vCpuInfo().defaultVCpus() != null 
                    ? awsInfo.vCpuInfo().defaultVCpus() : 0;
            this.defaultVCpus = this.vCpuCount;
        } else {
            this.vCpuCount = 0;
            this.defaultVCpus = 0;
        }
        
        // 内存信息
        if (awsInfo.memoryInfo() != null && awsInfo.memoryInfo().sizeInMiB() != null) {
            this.memorySizeMiB = awsInfo.memoryInfo().sizeInMiB();
            this.memorySizeGiB = this.memorySizeMiB / 1024.0;
        } else {
            this.memorySizeMiB = 0;
            this.memorySizeGiB = 0;
        }
        
        // GPU 信息
        if (awsInfo.gpuInfo() != null && awsInfo.gpuInfo().gpus() != null && !awsInfo.gpuInfo().gpus().isEmpty()) {
            this.gpuSupported = true;
            this.gpuInfoList = awsInfo.gpuInfo().gpus().stream()
                    .map(GpuInfo::new)
                    .toList();
        } else {
            this.gpuSupported = false;
            this.gpuInfoList = List.of();
        }
        
        // 网络信息
        if (awsInfo.networkInfo() != null) {
            this.networkPerformance = awsInfo.networkInfo().networkPerformance();
            this.maxNetworkInterfaces = awsInfo.networkInfo().maximumNetworkInterfaces() != null 
                    ? awsInfo.networkInfo().maximumNetworkInterfaces() : 0;
            this.ipv4AddressesPerInterface = awsInfo.networkInfo().ipv4AddressesPerInterface() != null 
                    ? awsInfo.networkInfo().ipv4AddressesPerInterface() : 0;
        } else {
            this.networkPerformance = "未知";
            this.maxNetworkInterfaces = 0;
            this.ipv4AddressesPerInterface = 0;
        }
        
        // EBS 优化
        this.ebsOptimizedSupported = awsInfo.ebsInfo() != null 
                && awsInfo.ebsInfo().ebsOptimizedSupport() != null
                && !"unsupported".equalsIgnoreCase(awsInfo.ebsInfo().ebsOptimizedSupportAsString());
        
        // 其他信息
        this.hypervisor = awsInfo.hypervisorAsString();
        this.processorArchitecture = awsInfo.processorInfo() != null 
                && awsInfo.processorInfo().supportedArchitectures() != null
                && !awsInfo.processorInfo().supportedArchitectures().isEmpty()
                ? awsInfo.processorInfo().supportedArchitecturesAsStrings().get(0) : "未知";
        this.supportedUsageClasses = awsInfo.supportedUsageClassesAsStrings();
        this.bareMetal = awsInfo.bareMetal() != null && awsInfo.bareMetal();
        this.burstablePerformanceSupported = awsInfo.burstablePerformanceSupported() != null 
                && awsInfo.burstablePerformanceSupported();
        this.dedicatedHostsSupported = awsInfo.dedicatedHostsSupported() != null 
                && awsInfo.dedicatedHostsSupported();
        this.freeTierEligible = awsInfo.freeTierEligible() != null && awsInfo.freeTierEligible();
    }

    // Getters
    public String getInstanceType() { return instanceType; }
    public int getVCpuCount() { return vCpuCount; }
    public int getDefaultVCpus() { return defaultVCpus; }
    public long getMemorySizeMiB() { return memorySizeMiB; }
    public double getMemorySizeGiB() { return memorySizeGiB; }
    public boolean isGpuSupported() { return gpuSupported; }
    public List<GpuInfo> getGpuInfoList() { return gpuInfoList; }
    public String getNetworkPerformance() { return networkPerformance; }
    public int getMaxNetworkInterfaces() { return maxNetworkInterfaces; }
    public int getIpv4AddressesPerInterface() { return ipv4AddressesPerInterface; }
    public boolean isEbsOptimizedSupported() { return ebsOptimizedSupported; }
    public String getHypervisor() { return hypervisor; }
    public String getProcessorArchitecture() { return processorArchitecture; }
    public List<String> getSupportedUsageClasses() { return supportedUsageClasses; }
    public boolean isBareMetal() { return bareMetal; }
    public boolean isBurstablePerformanceSupported() { return burstablePerformanceSupported; }
    public boolean isDedicatedHostsSupported() { return dedicatedHostsSupported; }
    public boolean isFreeTierEligible() { return freeTierEligible; }

    /**
     * 获取总 GPU 数量
     */
    public int getTotalGpuCount() {
        return gpuInfoList.stream()
                .mapToInt(GpuInfo::getCount)
                .sum();
    }

    /**
     * 获取总 GPU 内存（MiB）
     */
    public long getTotalGpuMemoryMiB() {
        return gpuInfoList.stream()
                .mapToLong(gpu -> (long) gpu.getCount() * gpu.getMemorySizeMiB())
                .sum();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("实例类型: %s%n", instanceType));
        sb.append(String.format("  CPU: %d vCPU (%s)%n", vCpuCount, processorArchitecture));
        sb.append(String.format("  内存: %.1f GiB (%d MiB)%n", memorySizeGiB, memorySizeMiB));
        if (gpuSupported) {
            sb.append(String.format("  GPU: %d 个 (总内存: %d MiB)%n", getTotalGpuCount(), getTotalGpuMemoryMiB()));
            for (GpuInfo gpu : gpuInfoList) {
                sb.append(String.format("    - %s%n", gpu));
            }
        } else {
            sb.append("  GPU: 不支持\n");
        }
        sb.append(String.format("  网络: %s (最大 %d 网卡)%n", networkPerformance, maxNetworkInterfaces));
        sb.append(String.format("  EBS 优化: %s%n", ebsOptimizedSupported ? "支持" : "不支持"));
        sb.append(String.format("  免费套餐: %s%n", freeTierEligible ? "是" : "否"));
        return sb.toString();
    }

    /**
     * GPU 设备信息
     */
    public static class GpuInfo {
        private final String name;
        private final String manufacturer;
        private final int count;
        private final int memorySizeMiB;

        public GpuInfo(GpuDeviceInfo deviceInfo) {
            this.name = deviceInfo.name();
            this.manufacturer = deviceInfo.manufacturer();
            this.count = deviceInfo.count() != null ? deviceInfo.count() : 0;
            this.memorySizeMiB = deviceInfo.memoryInfo() != null 
                    && deviceInfo.memoryInfo().sizeInMiB() != null 
                    ? deviceInfo.memoryInfo().sizeInMiB() : 0;
        }

        public String getName() { return name; }
        public String getManufacturer() { return manufacturer; }
        public int getCount() { return count; }
        public int getMemorySizeMiB() { return memorySizeMiB; }

        @Override
        public String toString() {
            return String.format("%s %s x%d (%d MiB)", manufacturer, name, count, memorySizeMiB);
        }
    }
}
