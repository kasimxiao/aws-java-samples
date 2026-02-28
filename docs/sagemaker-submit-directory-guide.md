# SageMaker Submit Directory 使用说明

## 概述

通过 `submit_directory` 机制，可以将训练脚本和 `requirements.txt` 打包上传到 S3，
SageMaker 预置容器启动时会自动解压代码包、安装依赖、运行训练脚本。

## 工作原理

```
sourcedir.tar.gz（上传到 S3）
├── train.py              # 训练入口脚本
├── requirements.txt      # Python 依赖列表
└── utils/                # 其他辅助模块（可选）
```

SageMaker 预置容器启动后：
1. 从 S3 下载 `sourcedir.tar.gz`
2. 解压到 `/opt/ml/code/`
3. 检测到 `requirements.txt` → 自动执行 `pip install -r requirements.txt`
4. 运行 `sagemaker_program` 指定的入口脚本

## 关键超参数

| 超参数 | 说明 | 示例 |
|--------|------|------|
| `sagemaker_submit_directory` | S3 上代码包路径 | `s3://bucket/code/sourcedir.tar.gz` |
| `sagemaker_program` | 入口脚本文件名 | `train.py` |

## 使用方式

### 方式一：通过 TrainingJobConfig（推荐）

```java
TrainingJobConfig jobConfig = TrainingJobConfig.builder()
        .jobName("my-training-job")
        .roleArn("arn:aws:iam::123456789012:role/SageMakerExecutionRole")
        .trainingImage("镜像 URI")
        .instanceType("ml.m5.xlarge")
        .s3TrainDataUri("s3://bucket/train/")
        .s3SubmitDirectory("s3://bucket/code/sourcedir.tar.gz")
        .entryPoint("train.py")
        .s3OutputPath("s3://bucket/output/")
        .hyperParameter("epochs", "10")
        .build();

trainingService.createTrainingJob(jobConfig);
```

`SageMakerTrainingService` 会自动将 `s3SubmitDirectory` 和 `entryPoint` 转换为
`sagemaker_submit_directory` 和 `sagemaker_program` 超参数。

### 方式二：直接设置超参数

```java
Map<String, String> hp = Map.of(
    "sagemaker_submit_directory", "s3://bucket/code/sourcedir.tar.gz",
    "sagemaker_program", "train.py",
    "epochs", "10"
);
```

## SageMaker 容器内目录结构

```
/opt/ml/
├── code/                    # submit_directory 解压位置
│   ├── train.py
│   └── requirements.txt
├── input/
│   ├── data/
│   │   └── training/       # 训练数据（从 S3 下载）
│   └── config/             # 训练配置
├── model/                   # 模型输出目录（训练完成后上传到 S3）
└── output/                  # 其他输出
```

## 注意事项

- `sourcedir.tar.gz` 必须是 `.tar.gz` 格式
- `requirements.txt` 必须放在 tar 包根目录
- 预置容器（PyTorch、TensorFlow、HuggingFace 等）才支持自动安装 `requirements.txt`
- 自定义容器需要自行实现依赖安装逻辑
- 依赖安装发生在训练开始前，大量依赖会增加启动时间
