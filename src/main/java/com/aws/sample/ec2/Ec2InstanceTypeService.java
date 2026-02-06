package com.aws.sample.ec2;

import java.util.ArrayList;
import java.util.List;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.ec2.model.InstanceTypeInfo;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceTypesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceTypesResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.InstanceType;

/**
 * EC2 实例类型服务
 * 提供获取实例类型列表及其详细规格（CPU、内存、GPU、网络）的功能
 */
public class Ec2InstanceTypeService implements AutoCloseable {

    private final Ec2Client ec2Client;

    public Ec2InstanceTypeService(AwsConfig config) {
        this.ec2Client = Ec2Client.builder()
                .region(config.getRegion())
                .credentialsProvider(config.getCredentialsProvider())
                .build();
    }

    /**
     * 获取指定实例类型的详细信息
     * @param instanceType 实例类型名称，如 "t3.medium"、"p4d.24xlarge"
     * @return 实例类型详细信息，如果不存在返回 null
     */
    public InstanceTypeInfo getInstanceTypeInfo(String instanceType) {
        DescribeInstanceTypesRequest request = DescribeInstanceTypesRequest.builder()
                .instanceTypes(InstanceType.fromValue(instanceType))
                .build();

        DescribeInstanceTypesResponse response = ec2Client.describeInstanceTypes(request);
        if (!response.instanceTypes().isEmpty()) {
            return new InstanceTypeInfo(response.instanceTypes().get(0));
        }
        return null;
    }

    /**
     * 批量获取多个实例类型的详细信息
     * @param instanceTypes 实例类型名称列表
     * @return 实例类型详细信息列表
     */
    public List<InstanceTypeInfo> getInstanceTypeInfoBatch(List<String> instanceTypes) {
        List<InstanceType> types = instanceTypes.stream()
                .map(InstanceType::fromValue)
                .toList();

        DescribeInstanceTypesRequest request = DescribeInstanceTypesRequest.builder()
                .instanceTypes(types)
                .build();

        DescribeInstanceTypesResponse response = ec2Client.describeInstanceTypes(request);
        return response.instanceTypes().stream()
                .map(InstanceTypeInfo::new)
                .toList();
    }

    /**
     * 获取实例类型列表
     * @param family 实例系列（可选），如 "t3"、"m5"、"g5"，为 null 时返回所有类型
     * @return 实例类型信息列表
     */
    public List<InstanceTypeInfo> listInstanceTypes(String family) {
        if (family == null || family.isEmpty()) {
            return listAllInstanceTypes();
        }
        return listInstanceTypesByFamily(family);
    }

    /**
     * 获取所有可用的实例类型列表
     * 注意：此方法会返回大量数据，建议使用过滤条件
     * @return 所有实例类型信息列表
     */
    public List<InstanceTypeInfo> listAllInstanceTypes() {
        List<InstanceTypeInfo> allTypes = new ArrayList<>();
        String nextToken = null;

        do {
            DescribeInstanceTypesRequest.Builder requestBuilder = DescribeInstanceTypesRequest.builder()
                    .maxResults(100);
            if (nextToken != null) {
                requestBuilder.nextToken(nextToken);
            }

            DescribeInstanceTypesResponse response = ec2Client.describeInstanceTypes(requestBuilder.build());
            response.instanceTypes().stream()
                    .map(InstanceTypeInfo::new)
                    .forEach(allTypes::add);
            nextToken = response.nextToken();
        } while (nextToken != null);

        System.out.println("共获取 " + allTypes.size() + " 种实例类型");
        return allTypes;
    }

    /**
     * 按实例系列筛选实例类型
     * @param family 实例系列，如 "t3"、"m5"、"p4d"、"g5"
     * @return 该系列的所有实例类型
     */
    public List<InstanceTypeInfo> listInstanceTypesByFamily(String family) {
        List<InstanceTypeInfo> result = new ArrayList<>();
        String nextToken = null;

        do {
            DescribeInstanceTypesRequest.Builder requestBuilder = DescribeInstanceTypesRequest.builder()
                    .filters(Filter.builder()
                            .name("instance-type")
                            .values(family + ".*")
                            .build())
                    .maxResults(100);
            if (nextToken != null) {
                requestBuilder.nextToken(nextToken);
            }

            DescribeInstanceTypesResponse response = ec2Client.describeInstanceTypes(requestBuilder.build());
            response.instanceTypes().stream()
                    .map(InstanceTypeInfo::new)
                    .forEach(result::add);
            nextToken = response.nextToken();
        } while (nextToken != null);

        System.out.println("系列 " + family + " 共有 " + result.size() + " 种实例类型");
        return result;
    }

    /**
     * 获取所有支持 GPU 的实例类型
     * @return GPU 实例类型列表
     */
    public List<InstanceTypeInfo> listGpuInstanceTypes() {
        List<InstanceTypeInfo> result = new ArrayList<>();
        String nextToken = null;

        do {
            DescribeInstanceTypesRequest.Builder requestBuilder = DescribeInstanceTypesRequest.builder()
                    .filters(Filter.builder()
                            .name("processor-info.supported-architecture")
                            .values("x86_64", "arm64")
                            .build())
                    .maxResults(100);
            if (nextToken != null) {
                requestBuilder.nextToken(nextToken);
            }

            DescribeInstanceTypesResponse response = ec2Client.describeInstanceTypes(requestBuilder.build());
            response.instanceTypes().stream()
                    .filter(info -> info.gpuInfo() != null 
                            && info.gpuInfo().gpus() != null 
                            && !info.gpuInfo().gpus().isEmpty())
                    .map(InstanceTypeInfo::new)
                    .forEach(result::add);
            nextToken = response.nextToken();
        } while (nextToken != null);

        System.out.println("共有 " + result.size() + " 种 GPU 实例类型");
        return result;
    }

    /**
     * 按 vCPU 数量范围筛选实例类型
     * @param minVCpus 最小 vCPU 数量
     * @param maxVCpus 最大 vCPU 数量
     * @return 符合条件的实例类型列表
     */
    public List<InstanceTypeInfo> listInstanceTypesByVCpuRange(int minVCpus, int maxVCpus) {
        List<InstanceTypeInfo> result = new ArrayList<>();
        String nextToken = null;

        do {
            DescribeInstanceTypesRequest.Builder requestBuilder = DescribeInstanceTypesRequest.builder()
                    .filters(
                            Filter.builder()
                                    .name("vcpu-info.default-vcpus")
                                    .values(String.valueOf(minVCpus))
                                    .build()
                    )
                    .maxResults(100);
            if (nextToken != null) {
                requestBuilder.nextToken(nextToken);
            }

            DescribeInstanceTypesResponse response = ec2Client.describeInstanceTypes(requestBuilder.build());
            response.instanceTypes().stream()
                    .filter(info -> {
                        int vcpus = info.vCpuInfo() != null && info.vCpuInfo().defaultVCpus() != null 
                                ? info.vCpuInfo().defaultVCpus() : 0;
                        return vcpus >= minVCpus && vcpus <= maxVCpus;
                    })
                    .map(InstanceTypeInfo::new)
                    .forEach(result::add);
            nextToken = response.nextToken();
        } while (nextToken != null);

        return result;
    }

    /**
     * 按内存大小范围筛选实例类型
     * @param minMemoryGiB 最小内存（GiB）
     * @param maxMemoryGiB 最大内存（GiB）
     * @return 符合条件的实例类型列表
     */
    public List<InstanceTypeInfo> listInstanceTypesByMemoryRange(double minMemoryGiB, double maxMemoryGiB) {
        List<InstanceTypeInfo> result = new ArrayList<>();
        String nextToken = null;
        long minMemoryMiB = (long) (minMemoryGiB * 1024);
        long maxMemoryMiB = (long) (maxMemoryGiB * 1024);

        do {
            DescribeInstanceTypesRequest.Builder requestBuilder = DescribeInstanceTypesRequest.builder()
                    .maxResults(100);
            if (nextToken != null) {
                requestBuilder.nextToken(nextToken);
            }

            DescribeInstanceTypesResponse response = ec2Client.describeInstanceTypes(requestBuilder.build());
            response.instanceTypes().stream()
                    .filter(info -> {
                        long memory = info.memoryInfo() != null && info.memoryInfo().sizeInMiB() != null 
                                ? info.memoryInfo().sizeInMiB() : 0;
                        return memory >= minMemoryMiB && memory <= maxMemoryMiB;
                    })
                    .map(InstanceTypeInfo::new)
                    .forEach(result::add);
            nextToken = response.nextToken();
        } while (nextToken != null);

        return result;
    }

    /**
     * 获取免费套餐可用的实例类型
     * @return 免费套餐实例类型列表
     */
    public List<InstanceTypeInfo> listFreeTierInstanceTypes() {
        List<InstanceTypeInfo> result = new ArrayList<>();
        String nextToken = null;

        do {
            DescribeInstanceTypesRequest.Builder requestBuilder = DescribeInstanceTypesRequest.builder()
                    .filters(Filter.builder()
                            .name("free-tier-eligible")
                            .values("true")
                            .build())
                    .maxResults(100);
            if (nextToken != null) {
                requestBuilder.nextToken(nextToken);
            }

            DescribeInstanceTypesResponse response = ec2Client.describeInstanceTypes(requestBuilder.build());
            response.instanceTypes().stream()
                    .map(InstanceTypeInfo::new)
                    .forEach(result::add);
            nextToken = response.nextToken();
        } while (nextToken != null);

        System.out.println("共有 " + result.size() + " 种免费套餐实例类型");
        return result;
    }

    /**
     * 按处理器架构筛选实例类型
     * @param architecture 架构类型，如 "x86_64"、"arm64"
     * @return 符合条件的实例类型列表
     */
    public List<InstanceTypeInfo> listInstanceTypesByArchitecture(String architecture) {
        List<InstanceTypeInfo> result = new ArrayList<>();
        String nextToken = null;

        do {
            DescribeInstanceTypesRequest.Builder requestBuilder = DescribeInstanceTypesRequest.builder()
                    .filters(Filter.builder()
                            .name("processor-info.supported-architecture")
                            .values(architecture)
                            .build())
                    .maxResults(100);
            if (nextToken != null) {
                requestBuilder.nextToken(nextToken);
            }

            DescribeInstanceTypesResponse response = ec2Client.describeInstanceTypes(requestBuilder.build());
            response.instanceTypes().stream()
                    .map(InstanceTypeInfo::new)
                    .forEach(result::add);
            nextToken = response.nextToken();
        } while (nextToken != null);

        System.out.println("架构 " + architecture + " 共有 " + result.size() + " 种实例类型");
        return result;
    }

    /**
     * 综合条件筛选实例类型
     * @param minVCpus 最小 vCPU（可为 null）
     * @param maxVCpus 最大 vCPU（可为 null）
     * @param minMemoryGiB 最小内存 GiB（可为 null）
     * @param maxMemoryGiB 最大内存 GiB（可为 null）
     * @param requireGpu 是否需要 GPU（可为 null）
     * @param architecture 架构类型（可为 null）
     * @return 符合条件的实例类型列表
     */
    public List<InstanceTypeInfo> findInstanceTypes(
            Integer minVCpus, Integer maxVCpus,
            Double minMemoryGiB, Double maxMemoryGiB,
            Boolean requireGpu, String architecture) {

        List<InstanceTypeInfo> result = new ArrayList<>();
        String nextToken = null;

        // 构建过滤器
        List<Filter> filters = new ArrayList<>();
        if (architecture != null && !architecture.isEmpty()) {
            filters.add(Filter.builder()
                    .name("processor-info.supported-architecture")
                    .values(architecture)
                    .build());
        }

        do {
            DescribeInstanceTypesRequest.Builder requestBuilder = DescribeInstanceTypesRequest.builder()
                    .maxResults(100);
            if (!filters.isEmpty()) {
                requestBuilder.filters(filters);
            }
            if (nextToken != null) {
                requestBuilder.nextToken(nextToken);
            }

            DescribeInstanceTypesResponse response = ec2Client.describeInstanceTypes(requestBuilder.build());
            response.instanceTypes().stream()
                    .filter(info -> {
                        // vCPU 过滤
                        int vcpus = info.vCpuInfo() != null && info.vCpuInfo().defaultVCpus() != null 
                                ? info.vCpuInfo().defaultVCpus() : 0;
                        if (minVCpus != null && vcpus < minVCpus) return false;
                        if (maxVCpus != null && vcpus > maxVCpus) return false;

                        // 内存过滤
                        long memoryMiB = info.memoryInfo() != null && info.memoryInfo().sizeInMiB() != null 
                                ? info.memoryInfo().sizeInMiB() : 0;
                        double memoryGiB = memoryMiB / 1024.0;
                        if (minMemoryGiB != null && memoryGiB < minMemoryGiB) return false;
                        if (maxMemoryGiB != null && memoryGiB > maxMemoryGiB) return false;

                        // GPU 过滤
                        boolean hasGpu = info.gpuInfo() != null 
                                && info.gpuInfo().gpus() != null 
                                && !info.gpuInfo().gpus().isEmpty();
                        if (requireGpu != null && requireGpu && !hasGpu) return false;

                        return true;
                    })
                    .map(InstanceTypeInfo::new)
                    .forEach(result::add);
            nextToken = response.nextToken();
        } while (nextToken != null);

        return result;
    }

    @Override
    public void close() {
        if (ec2Client != null) {
            ec2Client.close();
        }
    }
}
