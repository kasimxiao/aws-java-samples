package com.aws.sample;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.aws.sample.common.AwsConfig;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;
import software.amazon.awssdk.services.ecr.model.ListImagesRequest;
import software.amazon.awssdk.services.ecr.model.ListImagesResponse;

/**
 * 查询法兰克福区域（eu-central-1）SageMaker 常用基础镜像清单
 *
 * 通过 ECR ListImages API 查询 DLC 账户下已知仓库的镜像标签，
 * 提取常用版本信息，生成 Markdown 格式清单。
 */
public class SageMakerBaseImageListTest {

    private static final String REGION = "eu-central-1";
    private static final String DLC_ACCOUNT = "763104351884";
    private static final String ECR_DOMAIN = "dkr.ecr." + REGION + ".amazonaws.com";

    /** SageMaker 常用基础镜像仓库列表 */
    private static final String[][] REPOS = {
        // 分类, 仓库名, 说明
        {"PyTorch", "pytorch-training", "PyTorch 训练"},
        {"PyTorch", "pytorch-inference", "PyTorch 推理"},
        {"PyTorch", "pytorch-training-neuronx", "PyTorch 训练 (Neuron/Inferentia)"},
        {"PyTorch", "pytorch-inference-neuronx", "PyTorch 推理 (Neuron/Inferentia)"},
        {"PyTorch", "pytorch-inference-graviton", "PyTorch 推理 (Graviton/ARM)"},
        {"TensorFlow", "tensorflow-training", "TensorFlow 训练"},
        {"TensorFlow", "tensorflow-inference", "TensorFlow 推理"},
        {"TensorFlow", "tensorflow-inference-graviton", "TensorFlow 推理 (Graviton/ARM)"},
        {"HuggingFace", "huggingface-pytorch-training", "HuggingFace PyTorch 训练"},
        {"HuggingFace", "huggingface-pytorch-inference", "HuggingFace PyTorch 推理"},
        {"HuggingFace", "huggingface-pytorch-tgi-inference", "HuggingFace TGI 推理"},
        {"HuggingFace", "huggingface-pytorch-inference-neuronx", "HuggingFace Neuron 推理"},
        {"HuggingFace", "huggingface-tensorflow-training", "HuggingFace TensorFlow 训练"},
        {"HuggingFace", "huggingface-tensorflow-inference", "HuggingFace TensorFlow 推理"},
        {"DJL / LMI", "djl-inference", "DJL 大模型推理"},
        {"MXNet", "mxnet-training", "MXNet 训练"},
        {"MXNet", "mxnet-inference", "MXNet 推理"},
        {"AutoGluon", "autogluon-training", "AutoGluon 训练"},
        {"AutoGluon", "autogluon-inference", "AutoGluon 推理"},
        {"其他", "sagemaker-data-wrangler-container", "Data Wrangler"},
        {"其他", "sagemaker-debugger-rules", "Debugger Rules"},
        {"其他", "sagemaker-clarify-processing", "Clarify 公平性分析"},
        {"其他", "sagemaker-spark-processing", "Spark 数据处理"},
        {"其他", "sagemaker-sparkml-serving", "SparkML 推理"},
    };

    public static void main(String[] args) throws Exception {
        System.out.println("===== 查询法兰克福区域 SageMaker 基础镜像 =====\n");

        AwsConfig config = new AwsConfig();
        EcrClient ecrClient = EcrClient.builder()
                .region(Region.of(REGION))
                .credentialsProvider(config.getCredentialsProvider())
                .build();

        StringBuilder md = new StringBuilder();
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        md.append("# 法兰克福区域（eu-central-1）SageMaker 基础镜像清单\n\n");
        md.append("> 生成时间: ").append(now).append("\n");
        md.append("> 数据来源: ECR ListImages API（DLC 账户 `").append(DLC_ACCOUNT).append("`）\n");
        md.append("> 镜像 URI 格式: `").append(DLC_ACCOUNT).append(".").append(ECR_DOMAIN).append("/{仓库名}:{标签}`\n\n");

        // 按分类组织
        String currentCategory = "";
        for (String[] repo : REPOS) {
            String category = repo[0];
            String repoName = repo[1];
            String desc = repo[2];

            if (!category.equals(currentCategory)) {
                currentCategory = category;
                md.append("## ").append(category).append("\n\n");
            }

            System.out.println("查询: " + repoName);
            List<String> tags = listImageTags(ecrClient, DLC_ACCOUNT, repoName);

            if (tags.isEmpty()) {
                System.out.println("  -> 无结果，跳过");
                continue;
            }

            // 提取版本信息
            List<VersionInfo> versions = extractVersionInfo(tags);
            int totalTags = tags.size();

            md.append("### ").append(desc).append("\n\n");
            md.append("仓库: `").append(repoName).append("` | 标签总数: ").append(totalTags).append("\n\n");

            if (!versions.isEmpty()) {
                md.append("| 版本 | Python | GPU | CPU | 示例标签 |\n");
                md.append("|------|--------|-----|-----|--------|\n");
                for (VersionInfo v : versions) {
                    md.append("| ").append(v.version)
                      .append(" | ").append(String.join(", ", v.pythonVersions))
                      .append(" | ").append(v.hasGpu ? "✅" : "❌")
                      .append(" | ").append(v.hasCpu ? "✅" : "❌")
                      .append(" | `").append(v.sampleTag).append("` |\n");
                }
            } else {
                // 无法解析的标签，列出最新 10 个
                md.append("最新标签:\n\n```\n");
                tags.stream().sorted(Comparator.reverseOrder()).limit(10)
                    .forEach(t -> md.append(t).append("\n"));
                if (totalTags > 10) md.append("... 共 ").append(totalTags).append(" 个\n");
                md.append("```\n");
            }
            md.append("\n");
        }

        ecrClient.close();

        String outputPath = "docs/frankfurt-sagemaker-base-images.md";
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.print(md);
        }
        System.out.println("\n清单已生成: " + outputPath);
    }

    /** 查询 ECR 仓库的所有镜像标签 */
    private static List<String> listImageTags(EcrClient client, String registryId, String repoName) {
        List<String> tags = new ArrayList<>();
        try {
            String nextToken = null;
            do {
                ListImagesRequest.Builder builder = ListImagesRequest.builder()
                        .registryId(registryId)
                        .repositoryName(repoName);
                if (nextToken != null) builder.nextToken(nextToken);
                ListImagesResponse response = client.listImages(builder.build());
                for (ImageIdentifier img : response.imageIds()) {
                    if (img.imageTag() != null) tags.add(img.imageTag());
                }
                nextToken = response.nextToken();
            } while (nextToken != null);
            System.out.println("  -> " + tags.size() + " 个标签");
        } catch (Exception e) {
            System.out.println("  -> 失败: " + e.getMessage());
        }
        return tags;
    }

    /** 从标签提取版本信息，按版本号降序，只保留主要版本 */
    private static List<VersionInfo> extractVersionInfo(List<String> tags) {
        // 匹配: {version}-{gpu|cpu}-{pyXXX}
        Pattern p = Pattern.compile("^(\\d+\\.\\d+\\.?\\d*)-(gpu|cpu)-(py\\d+)");
        Map<String, VersionInfo> map = new TreeMap<>(Comparator.reverseOrder());

        for (String tag : tags) {
            Matcher m = p.matcher(tag);
            if (m.find()) {
                String ver = m.group(1);
                String device = m.group(2);
                String pyVer = m.group(3);

                VersionInfo info = map.computeIfAbsent(ver, k -> new VersionInfo(k));
                info.pythonVersions.add(pyVer);
                if ("gpu".equals(device)) info.hasGpu = true;
                if ("cpu".equals(device)) info.hasCpu = true;
                // 优先选 gpu + sagemaker 标签
                if (info.sampleTag == null || (tag.contains("gpu") && tag.contains("sagemaker"))) {
                    info.sampleTag = tag;
                }
            }
        }

        // 只保留前 10 个主要版本
        return map.values().stream().limit(10).collect(Collectors.toList());
    }

    static class VersionInfo {
        String version;
        TreeSet<String> pythonVersions = new TreeSet<>();
        boolean hasGpu = false;
        boolean hasCpu = false;
        String sampleTag;

        VersionInfo(String version) { this.version = version; }
    }
}
