package com.aws.sample.sagemaker;

import com.aws.sample.common.AwsConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * SageMaker 镜像服务类
 * 提供获取 SageMaker 训练和推理基础镜像 URI 的功能
 * 
 * 镜像 URI 格式: {account}.dkr.ecr.{region}.amazonaws.com/{repository}:{tag}
 */
public class SageMakerImageService {

    private final AwsConfig config;
    
    // 各区域的 SageMaker 镜像账户 ID
    private static final Map<String, String> REGION_ACCOUNT_MAP = new HashMap<>();
    
    // 深度学习容器镜像账户（DLC）
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


    /**
     * 获取当前区域
     */
    private String getRegion() {
        return config.getRegion().id();
    }

    /**
     * 获取 ECR 域名后缀
     */
    private String getEcrDomain() {
        String region = getRegion();
        if (region.startsWith("cn-")) {
            return "dkr.ecr." + region + ".amazonaws.com.cn";
        }
        return "dkr.ecr." + region + ".amazonaws.com";
    }

    // ==================== PyTorch 镜像 ====================

    /**
     * 获取 PyTorch 训练镜像 URI
     * @param version PyTorch 版本，如 "2.0.1", "1.13.1"
     * @param pythonVersion Python 版本，如 "py310", "py39"
     * @param useGpu 是否使用 GPU
     */
    public String getPyTorchTrainingImage(String version, String pythonVersion, boolean useGpu) {
        String account = DLC_ACCOUNT_MAP.getOrDefault(getRegion(), "763104351884");
        String device = useGpu ? "gpu" : "cpu";
        String tag = version + "-" + device + "-" + pythonVersion;
        return String.format("%s.%s/pytorch-training:%s", account, getEcrDomain(), tag);
    }

    /**
     * 获取 PyTorch 推理镜像 URI
     */
    public String getPyTorchInferenceImage(String version, String pythonVersion, boolean useGpu) {
        String account = DLC_ACCOUNT_MAP.getOrDefault(getRegion(), "763104351884");
        String device = useGpu ? "gpu" : "cpu";
        String tag = version + "-" + device + "-" + pythonVersion;
        return String.format("%s.%s/pytorch-inference:%s", account, getEcrDomain(), tag);
    }

    // ==================== TensorFlow 镜像 ====================

    /**
     * 获取 TensorFlow 训练镜像 URI
     */
    public String getTensorFlowTrainingImage(String version, String pythonVersion, boolean useGpu) {
        String account = DLC_ACCOUNT_MAP.getOrDefault(getRegion(), "763104351884");
        String device = useGpu ? "gpu" : "cpu";
        String tag = version + "-" + device + "-" + pythonVersion;
        return String.format("%s.%s/tensorflow-training:%s", account, getEcrDomain(), tag);
    }

    /**
     * 获取 TensorFlow 推理镜像 URI
     */
    public String getTensorFlowInferenceImage(String version, String pythonVersion, boolean useGpu) {
        String account = DLC_ACCOUNT_MAP.getOrDefault(getRegion(), "763104351884");
        String device = useGpu ? "gpu" : "cpu";
        String tag = version + "-" + device + "-" + pythonVersion;
        return String.format("%s.%s/tensorflow-inference:%s", account, getEcrDomain(), tag);
    }

    // ==================== HuggingFace 镜像 ====================

    /**
     * 获取 HuggingFace PyTorch 训练镜像 URI
     */
    public String getHuggingFaceTrainingImage(String transformersVersion, String pytorchVersion, 
                                               String pythonVersion, boolean useGpu) {
        String account = DLC_ACCOUNT_MAP.getOrDefault(getRegion(), "763104351884");
        String device = useGpu ? "gpu" : "cpu";
        String tag = transformersVersion + "-transformers" + transformersVersion + "-" + device + "-" + pythonVersion;
        return String.format("%s.%s/huggingface-pytorch-training:%s", account, getEcrDomain(), tag);
    }

    /**
     * 获取 HuggingFace 推理镜像 URI
     */
    public String getHuggingFaceInferenceImage(String transformersVersion, String pytorchVersion,
                                                String pythonVersion, boolean useGpu) {
        String account = DLC_ACCOUNT_MAP.getOrDefault(getRegion(), "763104351884");
        String device = useGpu ? "gpu" : "cpu";
        String tag = pytorchVersion + "-transformers" + transformersVersion + "-" + device + "-" + pythonVersion;
        return String.format("%s.%s/huggingface-pytorch-inference:%s", account, getEcrDomain(), tag);
    }


    // ==================== XGBoost 镜像 ====================

    /**
     * 获取 XGBoost 镜像 URI（训练和推理通用）
     */
    public String getXGBoostImage(String version) {
        String account = REGION_ACCOUNT_MAP.getOrDefault(getRegion(), "811284229777");
        return String.format("%s.%s/sagemaker-xgboost:%s", account, getEcrDomain(), version);
    }

    // ==================== Scikit-learn 镜像 ====================

    /**
     * 获取 Scikit-learn 镜像 URI
     */
    public String getSklearnImage(String version, String pythonVersion) {
        String account = REGION_ACCOUNT_MAP.getOrDefault(getRegion(), "811284229777");
        String tag = version + "-" + pythonVersion;
        return String.format("%s.%s/sagemaker-scikit-learn:%s", account, getEcrDomain(), tag);
    }

    // ==================== MXNet 镜像 ====================

    /**
     * 获取 MXNet 训练镜像 URI
     */
    public String getMXNetTrainingImage(String version, String pythonVersion, boolean useGpu) {
        String account = DLC_ACCOUNT_MAP.getOrDefault(getRegion(), "763104351884");
        String device = useGpu ? "gpu" : "cpu";
        String tag = version + "-" + device + "-" + pythonVersion;
        return String.format("%s.%s/mxnet-training:%s", account, getEcrDomain(), tag);
    }

    /**
     * 获取 MXNet 推理镜像 URI
     */
    public String getMXNetInferenceImage(String version, String pythonVersion, boolean useGpu) {
        String account = DLC_ACCOUNT_MAP.getOrDefault(getRegion(), "763104351884");
        String device = useGpu ? "gpu" : "cpu";
        String tag = version + "-" + device + "-" + pythonVersion;
        return String.format("%s.%s/mxnet-inference:%s", account, getEcrDomain(), tag);
    }

    // ==================== 通用方法 ====================

    /**
     * 获取自定义深度学习容器镜像 URI
     * @param framework 框架名称，如 "pytorch", "tensorflow", "huggingface-pytorch"
     * @param jobType 任务类型，"training" 或 "inference"
     * @param tag 镜像标签
     */
    public String getDLCImage(String framework, String jobType, String tag) {
        String account = DLC_ACCOUNT_MAP.getOrDefault(getRegion(), "763104351884");
        String repository = framework + "-" + jobType;
        return String.format("%s.%s/%s:%s", account, getEcrDomain(), repository, tag);
    }

    /**
     * 获取 SageMaker 内置算法镜像 URI
     * @param algorithm 算法名称，如 "xgboost", "linear-learner", "kmeans"
     * @param tag 镜像标签/版本
     */
    public String getBuiltInAlgorithmImage(String algorithm, String tag) {
        String account = REGION_ACCOUNT_MAP.getOrDefault(getRegion(), "811284229777");
        String repository = "sagemaker-" + algorithm;
        return String.format("%s.%s/%s:%s", account, getEcrDomain(), repository, tag);
    }

    /**
     * 获取当前区域的 DLC 镜像账户 ID
     */
    public String getDLCAccountId() {
        return DLC_ACCOUNT_MAP.getOrDefault(getRegion(), "763104351884");
    }

    /**
     * 获取当前区域的内置算法镜像账户 ID
     */
    public String getBuiltInAlgorithmAccountId() {
        return REGION_ACCOUNT_MAP.getOrDefault(getRegion(), "811284229777");
    }

    /**
     * 打印常用镜像信息
     */
    public void printAvailableImages() {
        System.out.println("==================== SageMaker 镜像信息 ====================");
        System.out.println("区域: " + getRegion());
        System.out.println("DLC 账户: " + getDLCAccountId());
        System.out.println("内置算法账户: " + getBuiltInAlgorithmAccountId());
        System.out.println("\n示例镜像 URI:");
        System.out.println("PyTorch 训练 (GPU): " + getPyTorchTrainingImage("2.0.1", "py310", true));
        System.out.println("PyTorch 推理 (CPU): " + getPyTorchInferenceImage("2.0.1", "py310", false));
        System.out.println("TensorFlow 训练: " + getTensorFlowTrainingImage("2.13.0", "py310", true));
        System.out.println("XGBoost: " + getXGBoostImage("1.7-1"));
        System.out.println("============================================================");
    }
}
