package com.aws.sample;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aws.sample.common.AwsConfig;
import com.aws.sample.sagemaker.SageMakerImageService;
import com.aws.sample.sagemaker.SageMakerTrainingService;
import com.aws.sample.sagemaker.model.TrainingJobConfig;

/**
 * SageMaker 训练服务测试示例
 * 
 * 使用前请确保:
 * 1. 配置好 AWS 凭证
 * 2. 准备好训练数据并上传到 S3
 * 3. 创建好 SageMaker 执行角色
 */
public class SageMakerTrainingTest {

    private AwsConfig config;
    private SageMakerTrainingService trainingService;
    private SageMakerImageService imageService;

    // 配置参数 - 请根据实际情况修改
    private static final String ROLE_ARN = "arn:aws:iam::YOUR_ACCOUNT:role/SageMakerExecutionRole";
    private static final String S3_BUCKET = "your-sagemaker-bucket";
    private static final String S3_PREFIX = "sagemaker";

    @BeforeEach
    void setUp() {
        config = new AwsConfig();
        trainingService = new SageMakerTrainingService(config);
        imageService = new SageMakerImageService(config);
    }

    /**
     * 示例：使用 XGBoost 进行训练
     */
    @Test
    void testXGBoostTraining() {
        String jobName = "xgboost-training-" + System.currentTimeMillis();
        
        // 获取 XGBoost 镜像
        String xgboostImage = imageService.getXGBoostImage("1.7-1");
        System.out.println("XGBoost 镜像: " + xgboostImage);

        // 构建训练配置
        TrainingJobConfig jobConfig = TrainingJobConfig.builder()
                .jobName(jobName)
                .roleArn(ROLE_ARN)
                .trainingImage(xgboostImage)
                .instanceType("ml.m5.xlarge")
                .instanceCount(1)
                .volumeSizeGB(30)
                .maxRuntimeSeconds(3600)
                .s3TrainDataUri("s3://" + S3_BUCKET + "/" + S3_PREFIX + "/train/")
                .s3ValidationDataUri("s3://" + S3_BUCKET + "/" + S3_PREFIX + "/validation/")
                .s3OutputPath("s3://" + S3_BUCKET + "/" + S3_PREFIX + "/output/")
                .inputContentType("text/csv")
                // XGBoost 超参数
                .hyperParameter("objective", "binary:logistic")
                .hyperParameter("num_round", "100")
                .hyperParameter("max_depth", "5")
                .hyperParameter("eta", "0.2")
                .hyperParameter("subsample", "0.8")
                .hyperParameter("colsample_bytree", "0.8")
                .build();

        // 创建训练任务
        // String trainingJobArn = trainingService.createTrainingJob(jobConfig);
        // System.out.println("训练任务 ARN: " + trainingJobArn);

        // 打印配置信息（测试模式）
        System.out.println("==================== XGBoost 训练配置 ====================");
        System.out.println("任务名称: " + jobConfig.getJobName());
        System.out.println("镜像: " + jobConfig.getTrainingImage());
        System.out.println("实例类型: " + jobConfig.getInstanceType());
        System.out.println("训练数据: " + jobConfig.getS3TrainDataUri());
        System.out.println("输出路径: " + jobConfig.getS3OutputPath());
        System.out.println("超参数: " + jobConfig.getHyperParameters());
        System.out.println("========================================================");
    }

    /**
     * 示例：使用 PyTorch 进行深度学习训练
     */
    @Test
    void testPyTorchTraining() {
        String jobName = "pytorch-training-" + System.currentTimeMillis();
        
        // 获取 PyTorch GPU 训练镜像
        String pytorchImage = imageService.getPyTorchTrainingImage("2.0.1", "py310", true);
        System.out.println("PyTorch 镜像: " + pytorchImage);

        TrainingJobConfig jobConfig = TrainingJobConfig.builder()
                .jobName(jobName)
                .roleArn(ROLE_ARN)
                .trainingImage(pytorchImage)
                .instanceType("ml.p3.2xlarge")  // GPU 实例
                .instanceCount(1)
                .volumeSizeGB(100)
                .maxRuntimeSeconds(7200)
                .s3TrainDataUri("s3://" + S3_BUCKET + "/" + S3_PREFIX + "/pytorch/train/")
                .s3OutputPath("s3://" + S3_BUCKET + "/" + S3_PREFIX + "/pytorch/output/")
                // PyTorch 训练超参数
                .hyperParameter("epochs", "10")
                .hyperParameter("batch-size", "32")
                .hyperParameter("learning-rate", "0.001")
                .hyperParameter("model-type", "resnet50")
                .build();

        System.out.println("==================== PyTorch 训练配置 ====================");
        System.out.println("任务名称: " + jobConfig.getJobName());
        System.out.println("镜像: " + jobConfig.getTrainingImage());
        System.out.println("实例类型: " + jobConfig.getInstanceType());
        System.out.println("超参数: " + jobConfig.getHyperParameters());
        System.out.println("=========================================================");
    }

    /**
     * 示例：使用 HuggingFace 进行 NLP 模型训练
     */
    @Test
    void testHuggingFaceTraining() {
        String jobName = "huggingface-training-" + System.currentTimeMillis();
        
        // 获取 HuggingFace 训练镜像
        String hfImage = imageService.getHuggingFaceTrainingImage("4.28.1", "2.0.1", "py310", true);
        System.out.println("HuggingFace 镜像: " + hfImage);

        TrainingJobConfig jobConfig = TrainingJobConfig.builder()
                .jobName(jobName)
                .roleArn(ROLE_ARN)
                .trainingImage(hfImage)
                .instanceType("ml.p3.8xlarge")  // 多 GPU 实例
                .instanceCount(1)
                .volumeSizeGB(200)
                .maxRuntimeSeconds(14400)
                .s3TrainDataUri("s3://" + S3_BUCKET + "/" + S3_PREFIX + "/huggingface/train/")
                .s3OutputPath("s3://" + S3_BUCKET + "/" + S3_PREFIX + "/huggingface/output/")
                // HuggingFace 训练超参数
                .hyperParameter("model_name", "bert-base-uncased")
                .hyperParameter("task", "text-classification")
                .hyperParameter("epochs", "3")
                .hyperParameter("train_batch_size", "16")
                .hyperParameter("eval_batch_size", "32")
                .hyperParameter("learning_rate", "2e-5")
                .hyperParameter("warmup_steps", "500")
                .build();

        System.out.println("==================== HuggingFace 训练配置 ====================");
        System.out.println("任务名称: " + jobConfig.getJobName());
        System.out.println("镜像: " + jobConfig.getTrainingImage());
        System.out.println("实例类型: " + jobConfig.getInstanceType());
        System.out.println("超参数: " + jobConfig.getHyperParameters());
        System.out.println("=============================================================");
    }

    /**
     * 示例：分布式训练配置
     */
    @Test
    void testDistributedTraining() {
        String jobName = "distributed-training-" + System.currentTimeMillis();
        
        String pytorchImage = imageService.getPyTorchTrainingImage("2.0.1", "py310", true);

        TrainingJobConfig jobConfig = TrainingJobConfig.builder()
                .jobName(jobName)
                .roleArn(ROLE_ARN)
                .trainingImage(pytorchImage)
                .instanceType("ml.p3.16xlarge")  // 8 GPU 实例
                .instanceCount(2)  // 2 个实例 = 16 GPU
                .volumeSizeGB(500)
                .maxRuntimeSeconds(86400)
                .s3TrainDataUri("s3://" + S3_BUCKET + "/" + S3_PREFIX + "/distributed/train/")
                .s3OutputPath("s3://" + S3_BUCKET + "/" + S3_PREFIX + "/distributed/output/")
                // 分布式训练超参数
                .hyperParameter("sagemaker_distributed_dataparallel_enabled", "true")
                .hyperParameter("sagemaker_instance_type", "ml.p3.16xlarge")
                .hyperParameter("epochs", "50")
                .hyperParameter("batch-size", "256")
                .hyperParameter("learning-rate", "0.01")
                .build();

        System.out.println("==================== 分布式训练配置 ====================");
        System.out.println("任务名称: " + jobConfig.getJobName());
        System.out.println("实例类型: " + jobConfig.getInstanceType());
        System.out.println("实例数量: " + jobConfig.getInstanceCount());
        System.out.println("总 GPU 数: " + (jobConfig.getInstanceCount() * 8));
        System.out.println("超参数: " + jobConfig.getHyperParameters());
        System.out.println("========================================================");
    }

    /**
     * 示例：列出训练任务
     */
    @Test
    void testListTrainingJobs() {
        var jobs = trainingService.listTrainingJobs(null, 10);
        System.out.println("==================== 训练任务列表 ====================");
        for (var job : jobs) {
            System.out.println("任务名称: " + job.trainingJobName());
            System.out.println("  状态: " + job.trainingJobStatus());
            System.out.println("  创建时间: " + job.creationTime());
            System.out.println();
        }
        System.out.println("====================================================");
    }
}
