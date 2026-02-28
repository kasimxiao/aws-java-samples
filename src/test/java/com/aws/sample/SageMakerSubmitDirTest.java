package com.aws.sample;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.aws.sample.sagemaker.SageMakerTrainingService;
import com.aws.sample.sagemaker.model.TrainingJobConfig;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetLogEventsRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sagemaker.model.TrainingJobStatus;

/**
 * SageMaker submit_directory 端到端测试
 *
 * 验证通过 sourcedir.tar.gz 传入训练脚本和 requirements.txt，
 * SageMaker 预置容器能自动安装依赖并运行训练脚本。
 *
 * 测试流程:
 * 1. 生成 train.py + requirements.txt → 打包 tar.gz → 上传 S3
 * 2. 通过 SageMakerTrainingService 提交训练作业
 * 3. 等待作业完成
 * 4. 查看 CloudWatch 日志，确认依赖安装成功
 */
public class SageMakerSubmitDirTest {

    private static final Region REGION = Region.US_EAST_1;
    private static final String ROLE_ARN = "arn:aws:iam::671067840733:role/SageMakerExecutionRole-tenant-001";
    private static final String S3_BUCKET = "wongxiao-tenant-001-sagemaker-bucket";
    private static final String S3_PREFIX = "sagemaker/submitdir-test";
    private static final String TRAINING_IMAGE = "671067840733.dkr.ecr.us-east-1.amazonaws.com/tenant-001-training:latest";
    private static final String TRAIN_DATA_URI = "s3://" + S3_BUCKET + "/training-data/20251208_104924/train.csv";

    private static SageMakerTrainingService trainingService;
    private static S3Client s3;

    @BeforeAll
    static void setUp() {
        trainingService = new SageMakerTrainingService(REGION);
        s3 = S3Client.builder().region(REGION).build();
    }

    /**
     * 端到端测试：提交带 requirements.txt 的训练作业，等待完成后查看日志
     */
    @Test
    void testSubmitDirectoryWithRequirements() throws IOException, InterruptedException {
        String jobName = "submitdir-test-" + System.currentTimeMillis();
        String s3CodeKey = S3_PREFIX + "/code/sourcedir.tar.gz";
        String s3CodeUri = "s3://" + S3_BUCKET + "/" + s3CodeKey;

        // 1. 生成代码包并上传
        File tarGz = buildSourceTarGz();
        s3.putObject(PutObjectRequest.builder().bucket(S3_BUCKET).key(s3CodeKey).build(), tarGz.toPath());
        System.out.println("代码包已上传: " + s3CodeUri);

        // 2. 通过 Service 提交训练作业
        TrainingJobConfig jobConfig = TrainingJobConfig.builder()
                .jobName(jobName)
                .roleArn(ROLE_ARN)
                .trainingImage(TRAINING_IMAGE)
                .instanceType("ml.m5.xlarge")
                .instanceCount(1)
                .volumeSizeGB(30)
                .maxRuntimeSeconds(300)
                .s3TrainDataUri(TRAIN_DATA_URI)
                .s3SubmitDirectory(s3CodeUri)
                .entryPoint("train.py")
                .s3OutputPath("s3://" + S3_BUCKET + "/" + S3_PREFIX + "/output/")
                .build();

        String arn = trainingService.createTrainingJob(jobConfig);
        System.out.println("作业 ARN: " + arn);

        // 3. 等待完成
        System.out.println("等待作业完成（约 3-5 分钟）...");
        TrainingJobStatus status = trainingService.waitForTrainingJob(jobName, 10);
        System.out.println("最终状态: " + status);

        // 4. 打印详情和日志
        trainingService.printTrainingJobDetails(jobName);
        printTrainingLogs(jobName);

        tarGz.delete();
    }

    /**
     * 查询已有作业的详情和日志（无需重新提交）
     */
    @Test
    void testQueryExistingJobLogs() {
        String jobName = "e2e-reqtxt-1772251480762";
        trainingService.printTrainingJobDetails(jobName);
        printTrainingLogs(jobName);
    }

    // ===== 工具方法 =====

    private void printTrainingLogs(String jobName) {
        System.out.println("\n===== CloudWatch 训练日志 =====");
        try (CloudWatchLogsClient logs = CloudWatchLogsClient.builder().region(REGION).build()) {
            var streams = logs.describeLogStreams(DescribeLogStreamsRequest.builder()
                    .logGroupName("/aws/sagemaker/TrainingJobs")
                    .logStreamNamePrefix(jobName).build());
            if (streams.logStreams().isEmpty()) {
                System.out.println("未找到日志流");
                return;
            }
            for (var stream : streams.logStreams()) {
                System.out.println("--- " + stream.logStreamName() + " ---");
                logs.getLogEvents(GetLogEventsRequest.builder()
                        .logGroupName("/aws/sagemaker/TrainingJobs")
                        .logStreamName(stream.logStreamName())
                        .startFromHead(true).limit(500).build()
                ).events().forEach(e -> System.out.println(e.message()));
            }
        }
    }

    private File buildSourceTarGz() throws IOException {
        Path dir = Files.createTempDirectory("sourcedir");

        try (FileWriter fw = new FileWriter(dir.resolve("train.py").toFile())) {
            fw.write("import sys, os\n");
            fw.write("print('===== 训练脚本启动 =====')\n");
            fw.write("print(f'Python: {sys.version}')\n");
            fw.write("print(f'/opt/ml/code: {os.listdir(\"/opt/ml/code\")}')\n");
            fw.write("try:\n    import pandas; print(f'pandas {pandas.__version__} - 安装成功')\n");
            fw.write("except: print('pandas 未安装 - requirements.txt 未生效')\n");
            fw.write("try:\n    import sklearn; print(f'sklearn {sklearn.__version__} - 安装成功')\n");
            fw.write("except: print('sklearn 未安装')\n");
            fw.write("print('===== 训练脚本结束 =====')\n");
        }
        try (FileWriter fw = new FileWriter(dir.resolve("requirements.txt").toFile())) {
            fw.write("pandas>=1.5.0\nscikit-learn>=1.2.0\n");
        }

        File tarGz = Files.createTempFile("src-", ".tar.gz").toFile();
        ProcessBuilder pb = new ProcessBuilder("tar", "-czf", tarGz.getAbsolutePath(), "-C", dir.toString(), ".");
        pb.inheritIO();
        try { pb.start().waitFor(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        dir.resolve("train.py").toFile().delete();
        dir.resolve("requirements.txt").toFile().delete();
        dir.toFile().delete();
        return tarGz;
    }
}
