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
 * 法兰克福区域（eu-central-1）AI/ML 镜像清单生成器
 *
 * 通过 ECR API 动态查询 SageMaker DLC 和内置算法镜像，
 * 按框架分类整理版本信息，生成 Markdown 格式清单。
 */
public class FrankfurtImageListTest {

    /** DLC 镜像 ECR 账户 ID（全球统一） */
    private static final String DLC_ACCOUNT = "763104351884";
    /** 内置算法 ECR 账户 ID（eu-central-1） */
    private static final String BUILTIN_ACCOUNT = "813361260812";
    private static final String REGION = "eu-central-1";
    private static final String ECR_DOMAIN = "dkr.ecr." + REGION + ".amazonaws.com";

    /** 要查询的 DLC 框架仓库（训练 + 推理） */
    private static final String[][] DLC_REPOS = {
        {"pytorch-training", "PyTorch", "训练"},
        {"pytorch-inference", "PyTorch", "推理"},
        {"tensorflow-training", "TensorFlow", "训练"},
        {"tensorflow-inference", "TensorFlow", "推理"},
        {"huggingface-pytorch-training", "HuggingFace PyTorch", "训练"},
        {"huggingface-pytorch-inference", "HuggingFace PyTorch", "推理"},
        {"huggingface-tensorflow-training", "HuggingFace TensorFlow", "训练"},
        {"huggingface-tensorflow-inference", "HuggingFace TensorFlow", "推理"},
        {"mxnet-training", "MXNet", "训练"},
        {"mxnet-inference", "MXNet", "推理"},
        {"djl-inference", "DJL (Deep Java Library)", "推理"},
    };

    /** 要查询的内置算法仓库 */
    private static final String[][] BUILTIN_REPOS = {
        {"sagemaker-xgboost", "XGBoost"},
        {"sagemaker-scikit-learn", "Scikit-learn"},
        {"blazingtext", "BlazingText"},
        {"image-classification", "Image Classification"},
        {"object-detection", "Object Detection"},
        {"semantic-segmentation", "Semantic Segmentation"},
        {"seq2seq", "Seq2Seq"},
        {"ntm", "Neural Topic Model"},
        {"kmeans", "K-Means"},
        {"pca", "PCA"},
        {"linear-learner", "Linear Learner"},
        {"forecasting-deepar", "DeepAR Forecasting"},
        {"randomcutforest", "Random Cut Forest"},
        {"knn", "K-Nearest Neighbors"},
        {"factorization-machines", "Factorization Machines"},
        {"ipinsights", "IP Insights"},
        {"lda", "LDA"},
    };

    public static void main(String[] args) throws Exception {
        System.out.println("===== 法兰克福区域 AI/ML 镜像清单生成 =====");
        System.out.println("区域: " + REGION);
        System.out.println("开始查询...\n");

        AwsConfig config = new AwsConfig();
        EcrClient ecrClient = EcrClient.builder()
                .region(Region.of(REGION))
                .credentialsProvider(config.getCredentialsProvider())
                .build();

        StringBuilder md = new StringBuilder();
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        md.append("# 法兰克福区域（eu-central-1）AI/ML 镜像清单\n\n");
        md.append("> 生成时间: ").append(now).append("\n");
        md.append("> 数据来源: ECR API 动态查询\n\n");

        // ===== 1. DLC 深度学习容器镜像 =====
        md.append("## 一、深度学习容器（DLC）镜像\n\n");
        md.append("ECR 账户: `").append(DLC_ACCOUNT).append("`\n\n");

        for (String[] repo : DLC_REPOS) {
            String repoName = repo[0];
            String framework = repo[1];
            String jobType = repo[2];

            System.out.println("查询 DLC 仓库: " + repoName);
            List<String> tags = listImageTags(ecrClient, DLC_ACCOUNT, repoName);

            if (tags.isEmpty()) {
                System.out.println("  -> 无结果或无权限，跳过");
                continue;
            }

            md.append("### ").append(framework).append("（").append(jobType).append("）\n\n");
            md.append("仓库: `").append(repoName).append("`\n\n");
            md.append("镜像 URI 前缀: `").append(DLC_ACCOUNT).append(".").append(ECR_DOMAIN)
              .append("/").append(repoName).append("`\n\n");

            // 解析版本信息
            Map<String, VersionDetail> versionMap = parseDLCTags(tags);
            if (!versionMap.isEmpty()) {
                md.append("| 框架版本 | Python 版本 | GPU | CPU | 示例标签 |\n");
                md.append("|---------|-----------|-----|-----|--------|\n");
                for (var entry : versionMap.entrySet()) {
                    VersionDetail d = entry.getValue();
                    md.append("| ").append(entry.getKey())
                      .append(" | ").append(String.join(", ", d.pythonVersions))
                      .append(" | ").append(d.hasGpu ? "✅" : "❌")
                      .append(" | ").append(d.hasCpu ? "✅" : "❌")
                      .append(" | `").append(d.sampleTag).append("` |\n");
                }
            } else {
                // 无法解析的标签直接列出前 20 个
                md.append("可用标签（前 20 个）:\n\n");
                md.append("```\n");
                tags.stream().sorted().limit(20).forEach(t -> md.append(t).append("\n"));
                if (tags.size() > 20) md.append("... 共 ").append(tags.size()).append(" 个标签\n");
                md.append("```\n");
            }
            md.append("\n");
        }

        // ===== 2. 内置算法镜像 =====
        md.append("## 二、SageMaker 内置算法镜像\n\n");
        md.append("ECR 账户: `").append(BUILTIN_ACCOUNT).append("`\n\n");
        md.append("| 算法 | 仓库名 | 可用版本 | 镜像 URI 示例 |\n");
        md.append("|------|-------|---------|-------------|\n");

        for (String[] repo : BUILTIN_REPOS) {
            String repoName = repo[0];
            String algorithm = repo[1];

            System.out.println("查询内置算法仓库: " + repoName);
            List<String> tags = listImageTags(ecrClient, BUILTIN_ACCOUNT, repoName);

            if (tags.isEmpty()) {
                System.out.println("  -> 无结果或无权限，跳过");
                continue;
            }

            List<String> sorted = tags.stream().sorted().collect(Collectors.toList());
            String versions = sorted.stream().limit(8).collect(Collectors.joining(", "));
            if (sorted.size() > 8) versions += " ... 共 " + sorted.size() + " 个";
            String latestTag = sorted.get(sorted.size() - 1);
            String uri = BUILTIN_ACCOUNT + "." + ECR_DOMAIN + "/" + repoName + ":" + latestTag;

            md.append("| ").append(algorithm)
              .append(" | `").append(repoName).append("`")
              .append(" | ").append(versions)
              .append(" | `").append(uri).append("` |\n");
        }

        md.append("\n## 三、镜像 URI 格式说明\n\n");
        md.append("```\n");
        md.append("{account_id}.dkr.ecr.eu-central-1.amazonaws.com/{repository}:{tag}\n");
        md.append("```\n\n");
        md.append("- DLC 镜像标签格式: `{版本}-{gpu|cpu}-{python版本}`，例如 `2.1.0-gpu-py310`\n");
        md.append("- 内置算法标签格式: `{版本}` 或 `latest`\n\n");
        md.append("## 四、常用镜像速查\n\n");
        md.append("| 用途 | 镜像 URI |\n");
        md.append("|------|----------|\n");
        md.append("| PyTorch 2.1 训练 (GPU) | `763104351884.dkr.ecr.eu-central-1.amazonaws.com/pytorch-training:2.1.0-gpu-py310` |\n");
        md.append("| PyTorch 2.1 推理 (CPU) | `763104351884.dkr.ecr.eu-central-1.amazonaws.com/pytorch-inference:2.1.0-cpu-py310` |\n");
        md.append("| TensorFlow 2.13 训练 | `763104351884.dkr.ecr.eu-central-1.amazonaws.com/tensorflow-training:2.13.0-gpu-py310` |\n");
        md.append("| HuggingFace 推理 | `763104351884.dkr.ecr.eu-central-1.amazonaws.com/huggingface-pytorch-inference:2.1.0-transformers4.36.0-gpu-py310` |\n");
        md.append("| XGBoost | `813361260812.dkr.ecr.eu-central-1.amazonaws.com/sagemaker-xgboost:1.7-1` |\n");
        md.append("| Scikit-learn | `813361260812.dkr.ecr.eu-central-1.amazonaws.com/sagemaker-scikit-learn:1.2-1` |\n");

        ecrClient.close();

        // 写入文件
        String outputPath = "docs/frankfurt-ai-ml-images.md";
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.print(md);
        }
        System.out.println("\n清单已生成: " + outputPath);
        System.out.println("===== 完成 =====");
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
            System.out.println("  -> 查询失败: " + e.getMessage());
        }
        return tags;
    }

    /** 解析 DLC 镜像标签，提取版本、Python 版本、GPU/CPU 支持信息 */
    private static Map<String, VersionDetail> parseDLCTags(List<String> tags) {
        // 格式: {version}-{gpu|cpu}-{pyXXX}
        Pattern p = Pattern.compile("^([\\d]+\\.[\\d]+\\.?[\\d]*)-(gpu|cpu)-(py\\d+)");
        Map<String, VersionDetail> map = new TreeMap<>(Comparator.reverseOrder());

        for (String tag : tags) {
            Matcher m = p.matcher(tag);
            if (m.find()) {
                String version = m.group(1);
                String device = m.group(2);
                String pyVer = m.group(3);

                VersionDetail d = map.computeIfAbsent(version, k -> new VersionDetail());
                d.pythonVersions.add(pyVer);
                if ("gpu".equals(device)) d.hasGpu = true;
                if ("cpu".equals(device)) d.hasCpu = true;
                if (d.sampleTag == null || tag.contains("gpu")) d.sampleTag = tag;
            }
        }
        return map;
    }

    /** 版本详情 */
    static class VersionDetail {
        TreeSet<String> pythonVersions = new TreeSet<>();
        boolean hasGpu = false;
        boolean hasCpu = false;
        String sampleTag;
    }
}
