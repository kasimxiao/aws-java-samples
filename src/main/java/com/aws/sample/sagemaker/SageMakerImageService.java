package com.aws.sample.sagemaker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.aws.sample.common.AwsConfig;

import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;
import software.amazon.awssdk.services.ecr.model.ListImagesRequest;
import software.amazon.awssdk.services.ecr.model.ListImagesResponse;

/**
 * SageMaker 镜像服务
 *
 * 提供获取 SageMaker 训练和推理镜像 URI 的功能，支持多区域和多框架。
 * 支持通过 ECR API 动态查询可用镜像版本。
 *
 * 镜像 URI 格式: {account}.dkr.ecr.{region}.amazonaws.com/{repository}:{tag}
 *
 * 支持的框架: PyTorch、TensorFlow、HuggingFace、XGBoost、Scikit-learn、MXNet
 *
 * 使用示例:
 * <pre>
 * SageMakerImageService imageService = new SageMakerImageService(config);
 * // 统一查询接口
 * String image = imageService.getImageUri("pytorch", "training", "2.0.1", "py310", true);
 * // 动态查询可用版本
 * List&lt;String&gt; versions = imageService.listFrameworkVersions("pytorch", "training");
 * </pre>
 */
public class SageMakerImageService implements AutoCloseable {

    private final AwsConfig config;
    private EcrClient ecrClient;

    /** 各区域的 SageMaker 内置算法镜像 ECR 账户 ID */
    private static final Map<String, String> REGION_ACCOUNT_MAP = new HashMap<>();

    /** 各区域的深度学习容器（DLC）镜像 ECR 账户 ID */
    private static final Map<String, String> DLC_ACCOUNT_MAP = new HashMap<>();

    static {
        // SageMaker 内置算法镜像账户
        REGION_ACCOUNT_MAP.put("us-east-1", "811284229777");
        REGION_ACCOUNT_MAP.put("us-east-2", "825641698319");
        REGION_ACCOUNT_MAP.put("us-west-1", "632365934929");
        REGION_ACCOUNT_MAP.put("us-west-2", "433757028032");
        REGION_ACCOUNT_MAP.put("eu-west-1", "685385470294");
        REGION_ACCOUNT_MAP.put("eu-west-2", "644912444149");
        REGION_ACCOUNT_MAP.put("eu-central-1", "813361260812");
        REGION_ACCOUNT_MAP.put("ap-northeast-1", "501404015308");
        REGION_ACCOUNT_MAP.put("ap-northeast-2", "306986355934");
        REGION_ACCOUNT_MAP.put("ap-southeast-1", "475088953585");
        REGION_ACCOUNT_MAP.put("ap-southeast-2", "544295431143");
        REGION_ACCOUNT_MAP.put("ap-south-1", "991648021394");
        REGION_ACCOUNT_MAP.put("cn-north-1", "390948362332");
        REGION_ACCOUNT_MAP.put("cn-northwest-1", "387376663083");

        // 深度学习容器（DLC）镜像账户
        DLC_ACCOUNT_MAP.put("us-east-1", "763104351884");
        DLC_ACCOUNT_MAP.put("us-east-2", "763104351884");
        DLC_ACCOUNT_MAP.put("us-west-1", "763104351884");
        DLC_ACCOUNT_MAP.put("us-west-2", "763104351884");
        DLC_ACCOUNT_MAP.put("eu-west-1", "763104351884");
        DLC_ACCOUNT_MAP.put("eu-west-2", "763104351884");
        DLC_ACCOUNT_MAP.put("eu-central-1", "763104351884");
        DLC_ACCOUNT_MAP.put("ap-northeast-1", "763104351884");
        DLC_ACCOUNT_MAP.put("ap-northeast-2", "763104351884");
        DLC_ACCOUNT_MAP.put("ap-southeast-1", "763104351884");
        DLC_ACCOUNT_MAP.put("ap-southeast-2", "763104351884");
        DLC_ACCOUNT_MAP.put("ap-south-1", "763104351884");
        DLC_ACCOUNT_MAP.put("cn-north-1", "727897471807");
        DLC_ACCOUNT_MAP.put("cn-northwest-1", "727897471807");
    }

    public SageMakerImageService(AwsConfig config) {
        this.config = config;
    }

    /** 获取 ECR 客户端（懒加载，仅动态查询时创建） */
    private EcrClient getEcrClient() {
        if (ecrClient == null) {
            ecrClient = EcrClient.builder()
                    .region(config.getRegion())
                    .credentialsProvider(config.getCredentialsProvider())
                    .build();
        }
        return ecrClient;
    }

    @Override
    public void close() {
        if (ecrClient != null) {
            ecrClient.close();
            ecrClient = null;
        }
    }

    private String getRegion() {
        return config.getRegion().id();
    }

    /** 获取 ECR 域名，中国区域使用 .amazonaws.com.cn 后缀 */
    private String getEcrDomain() {
        String region = getRegion();
        String suffix = region.startsWith("cn-") ? ".amazonaws.com.cn" : ".amazonaws.com";
        return "dkr.ecr." + region + suffix;
    }

    // ==================== 镜像 URI 查询 ====================

    /**
     * 统一查询镜像 URI（推荐使用）
     *
     * @param framework     框架名称: pytorch, tensorflow, huggingface, xgboost, sklearn, mxnet
     * @param jobType       任务类型: training, inference
     * @param version       框架版本（如 "2.0.1"），HuggingFace 格式: "transformersVer:pytorchVer"
     * @param pythonVersion Python 版本（如 "py310"），xgboost 可传 null
     * @param useGpu        是否使用 GPU，xgboost/sklearn 忽略此参数
     * @return 完整的 ECR 镜像 URI
     */
    public String getImageUri(String framework, String jobType, String version,
                              String pythonVersion, boolean useGpu) {
        String device = useGpu ? "gpu" : "cpu";
        return switch (framework.toLowerCase()) {
            case "pytorch" -> buildDLCImage("pytorch-" + jobType,
                    version + "-" + device + "-" + pythonVersion);
            case "tensorflow" -> buildDLCImage("tensorflow-" + jobType,
                    version + "-" + device + "-" + pythonVersion);
            case "huggingface" -> {
                String[] parts = version.split(":");
                String transVer = parts[0];
                String ptVer = parts.length > 1 ? parts[1] : "2.0.0";
                String tag = "training".equals(jobType)
                        ? transVer + "-transformers" + transVer + "-" + device + "-" + pythonVersion
                        : ptVer + "-transformers" + transVer + "-" + device + "-" + pythonVersion;
                yield buildDLCImage("huggingface-pytorch-" + jobType, tag);
            }
            case "xgboost" -> buildBuiltInImage("sagemaker-xgboost", version);
            case "sklearn", "scikit-learn" -> buildBuiltInImage("sagemaker-scikit-learn",
                    version + "-" + (pythonVersion != null ? pythonVersion : "py3"));
            case "mxnet" -> buildDLCImage("mxnet-" + jobType,
                    version + "-" + device + "-" + pythonVersion);
            default -> buildDLCImage(framework + "-" + jobType, version);
        };
    }

    /**
     * 获取 SageMaker 内置算法镜像 URI
     *
     * @param algorithm 算法名称（如 "xgboost"、"linear-learner"、"kmeans"）
     * @param tag       镜像标签/版本（如 "1.7-1"、"latest"）
     * @return 完整的 ECR 镜像 URI
     */
    public String getBuiltInAlgorithmImage(String algorithm, String tag) {
        return buildBuiltInImage("sagemaker-" + algorithm, tag);
    }

    /** 构建 DLC 镜像 URI */
    private String buildDLCImage(String repository, String tag) {
        String account = DLC_ACCOUNT_MAP.getOrDefault(getRegion(), "763104351884");
        return String.format("%s.%s/%s:%s", account, getEcrDomain(), repository, tag);
    }

    /** 构建内置算法镜像 URI */
    private String buildBuiltInImage(String repository, String tag) {
        String account = REGION_ACCOUNT_MAP.getOrDefault(getRegion(), "811284229777");
        return String.format("%s.%s/%s:%s", account, getEcrDomain(), repository, tag);
    }

    // ==================== ECR 动态查询 ====================

    /**
     * 从 ECR 仓库动态查询镜像标签列表
     *
     * @param registryId     ECR 注册表 ID（账户 ID）
     * @param repositoryName 仓库名称（如 "pytorch-training"）
     * @return 镜像标签列表
     */
    public List<String> listImageTags(String registryId, String repositoryName) {
        List<String> tags = new ArrayList<>();
        try {
            String nextToken = null;
            do {
                ListImagesRequest.Builder builder = ListImagesRequest.builder()
                        .registryId(registryId)
                        .repositoryName(repositoryName);
                if (nextToken != null) {
                    builder.nextToken(nextToken);
                }
                ListImagesResponse response = getEcrClient().listImages(builder.build());
                for (ImageIdentifier image : response.imageIds()) {
                    if (image.imageTag() != null) {
                        tags.add(image.imageTag());
                    }
                }
                nextToken = response.nextToken();
            } while (nextToken != null);
            System.out.println("仓库 " + repositoryName + " 共 " + tags.size() + " 个镜像标签");
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("not authorized")) {
                System.out.println("仓库 " + repositoryName + " 跨账户查询无权限，将使用已知版本");
            } else {
                System.out.println("查询仓库 " + repositoryName + " 失败: " + msg);
            }
        }
        return tags;
    }

    /**
     * 动态查询指定框架的可用版本
     *
     * @param framework 框架名称: pytorch, tensorflow, huggingface, xgboost, sklearn, mxnet
     * @param jobType   任务类型: training, inference（xgboost/sklearn 忽略此参数）
     * @return 可用版本号列表（去重排序）
     */
    public List<String> listFrameworkVersions(String framework, String jobType) {
        String dlcAccount = DLC_ACCOUNT_MAP.getOrDefault(getRegion(), "763104351884");
        String builtInAccount = REGION_ACCOUNT_MAP.getOrDefault(getRegion(), "811284229777");

        return switch (framework.toLowerCase()) {
            case "pytorch", "tensorflow", "mxnet" -> {
                String repo = framework.toLowerCase() + "-" + jobType;
                yield parseDLCVersions(listImageTags(dlcAccount, repo));
            }
            case "huggingface" -> {
                List<String> tags = listImageTags(dlcAccount, "huggingface-pytorch-" + jobType);
                Pattern p = Pattern.compile("transformers([\\d]+\\.[\\d]+\\.?[\\d]*)");
                TreeSet<String> versions = new TreeSet<>();
                for (String tag : tags) {
                    Matcher m = p.matcher(tag);
                    if (m.find()) versions.add(m.group(1));
                }
                yield new ArrayList<>(versions);
            }
            case "xgboost" -> {
                List<String> versions = parseBuiltInVersions(
                        listImageTags(builtInAccount, "sagemaker-xgboost"));
                yield versions.isEmpty()
                        ? List.of("1.0-1", "1.2-1", "1.2-2", "1.3-1", "1.5-1", "1.7-1") : versions;
            }
            case "sklearn", "scikit-learn" -> {
                List<String> versions = parseBuiltInVersions(
                        listImageTags(builtInAccount, "sagemaker-scikit-learn"));
                yield versions.isEmpty()
                        ? List.of("0.20.0", "0.23-1", "1.0-1", "1.2-1") : versions;
            }
            default -> parseDLCVersions(listImageTags(dlcAccount, framework + "-" + jobType));
        };
    }

    /**
     * 动态查询所有支持框架的可用版本
     *
     * @return 框架名称 -> 可用版本列表
     */
    public Map<String, List<String>> listSupportedFrameworks() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String fw : new String[]{"pytorch", "tensorflow", "huggingface", "xgboost", "sklearn", "mxnet"}) {
            result.put(fw, listFrameworkVersions(fw, "training"));
        }
        return result;
    }

    /** 从 DLC 镜像标签解析版本号（格式: version-gpu/cpu-pyXXX） */
    private List<String> parseDLCVersions(List<String> tags) {
        Pattern p = Pattern.compile("^([\\d]+\\.[\\d]+\\.?[\\d]*)-(gpu|cpu)-py\\d+");
        TreeSet<String> versions = new TreeSet<>();
        for (String tag : tags) {
            Matcher m = p.matcher(tag);
            if (m.find()) versions.add(m.group(1));
        }
        return new ArrayList<>(versions);
    }

    /** 从内置算法标签解析版本号（格式: x.y-z） */
    private List<String> parseBuiltInVersions(List<String> tags) {
        Pattern p = Pattern.compile("^[\\d]+\\.[\\d]+-?\\d*$");
        TreeSet<String> versions = new TreeSet<>();
        for (String tag : tags) {
            if (p.matcher(tag).matches()) versions.add(tag);
        }
        return new ArrayList<>(versions);
    }

    // ==================== 信息打印 ====================

    /**
     * 打印常用镜像信息
     */
    public void printAvailableImages() {
        System.out.println("==================== SageMaker 镜像信息 ====================");
        System.out.println("区域: " + getRegion());
        System.out.println("DLC 账户: " + DLC_ACCOUNT_MAP.getOrDefault(getRegion(), "763104351884"));
        System.out.println("内置算法账户: " + REGION_ACCOUNT_MAP.getOrDefault(getRegion(), "811284229777"));
        System.out.println("\n示例镜像 URI:");
        System.out.println("PyTorch 训练 (GPU): " + getImageUri("pytorch", "training", "2.0.1", "py310", true));
        System.out.println("PyTorch 推理 (CPU): " + getImageUri("pytorch", "inference", "2.0.1", "py310", false));
        System.out.println("TensorFlow 训练: " + getImageUri("tensorflow", "training", "2.13.0", "py310", true));
        System.out.println("XGBoost: " + getImageUri("xgboost", "training", "1.7-1", null, false));
        System.out.println("============================================================");
    }

    /**
     * 打印所有支持的框架和可用版本（从 ECR 动态查询）
     */
    public void printSupportedFrameworks() {
        System.out.println("==================== 支持的框架（动态查询）====================");
        System.out.println("区域: " + getRegion());
        System.out.println("任务类型: training / inference");
        System.out.println("设备类型: gpu / cpu\n");

        for (Map.Entry<String, List<String>> entry : listSupportedFrameworks().entrySet()) {
            List<String> versions = entry.getValue();
            String versionStr = versions.isEmpty() ? "（无可用版本）"
                    : versions.stream().limit(10).collect(Collectors.joining(", "))
                    + (versions.size() > 10 ? " ... 共 " + versions.size() + " 个版本" : "");
            System.out.printf("  %-15s 可用版本: %s%n", entry.getKey(), versionStr);
        }

        System.out.println("\n使用示例:");
        System.out.println("  getImageUri(\"pytorch\", \"training\", \"2.0.1\", \"py310\", true)");
        System.out.println("  getImageUri(\"xgboost\", \"training\", \"1.7-1\", null, false)");
        System.out.println("  getImageUri(\"huggingface\", \"training\", \"4.28.1:2.0.0\", \"py310\", true)");
        System.out.println("=============================================================");
    }
}
