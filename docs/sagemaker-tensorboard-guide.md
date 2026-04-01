# SageMaker TensorBoard 使用指南

## 概述

SageMaker 训练任务原生支持 TensorBoard，通过 `TensorBoardOutputConfig` 将训练容器内的 TensorBoard 日志自动同步到 S3。你可以在 SageMaker Studio 中实时查看，也可以下载到本地用 TensorBoard 打开。

## Java 端配置

在 `TrainingJobConfig` 中设置 `tensorBoardS3OutputPath` 即可启用：

```java
TrainingJobConfig jobConfig = TrainingJobConfig.builder()
    .jobName("my-tb-training")
    .roleArn("arn:aws:iam::123456789012:role/SageMakerRole")
    .trainingImage("镜像URI")
    .s3TrainDataUri("s3://bucket/train/")
    .s3OutputPath("s3://bucket/output/")
    .tensorBoardS3OutputPath("s3://bucket/tensorboard/")
    .build();

SageMakerTrainingService service = new SageMakerTrainingService(new AwsConfig());
service.createTrainingJob(jobConfig);
```

### 参数说明

| 参数 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `tensorBoardS3OutputPath` | 是（启用 TensorBoard 时） | 无 | TensorBoard 日志的 S3 存储路径 |
| `tensorBoardLocalPath` | 否 | `/opt/ml/output/tensorboard` | 容器内 TensorBoard 日志的本地路径 |

> 大多数场景不需要设置 `tensorBoardLocalPath`，保持默认即可。只有当训练脚本将日志写到非默认路径时才需要自定义。

## 训练脚本端配置

训练脚本需要将 TensorBoard 日志写入容器内的对应路径（默认 `/opt/ml/output/tensorboard`）。

### PyTorch 示例

```python
from torch.utils.tensorboard import SummaryWriter

writer = SummaryWriter('/opt/ml/output/tensorboard')

for epoch in range(num_epochs):
    # ... 训练逻辑 ...
    writer.add_scalar('loss/train', train_loss, epoch)
    writer.add_scalar('loss/val', val_loss, epoch)
    writer.add_scalar('accuracy', accuracy, epoch)

writer.close()
```

### TensorFlow/Keras 示例

```python
import tensorflow as tf

tensorboard_callback = tf.keras.callbacks.TensorBoard(
    log_dir='/opt/ml/output/tensorboard',
    histogram_freq=1
)

model.fit(
    train_data,
    epochs=num_epochs,
    callbacks=[tensorboard_callback]
)
```

## 查看 TensorBoard 日志

### 方式一：SageMaker Studio（推荐）

在 SageMaker Studio 中可以直接打开 TensorBoard 应用，指向训练任务输出的 S3 路径即可实时查看。

### 方式二：本地查看

```bash
# 1. 从 S3 下载日志
aws s3 sync s3://bucket/tensorboard/ ./tensorboard-logs/

# 2. 启动本地 TensorBoard
tensorboard --logdir=./tensorboard-logs/

# 3. 浏览器打开 http://localhost:6006
```

## 自定义本地路径

如果训练脚本将日志写到了非默认路径，需要同步配置：

```java
TrainingJobConfig jobConfig = TrainingJobConfig.builder()
    .jobName("my-tb-training")
    .roleArn("arn:aws:iam::123456789012:role/SageMakerRole")
    .trainingImage("镜像URI")
    .s3TrainDataUri("s3://bucket/train/")
    .s3OutputPath("s3://bucket/output/")
    .tensorBoardS3OutputPath("s3://bucket/tensorboard/")
    .tensorBoardLocalPath("/opt/ml/output/logs")  // 与训练脚本中的路径一致
    .build();
```

对应训练脚本：

```python
writer = SummaryWriter('/opt/ml/output/logs')
```

## IAM 权限

SageMaker 执行角色需要对 TensorBoard S3 输出路径有写入权限：

```json
{
    "Effect": "Allow",
    "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:ListBucket"
    ],
    "Resource": [
        "arn:aws:s3:::bucket/tensorboard/*",
        "arn:aws:s3:::bucket"
    ]
}
```

## 参考文档

- [TensorBoardOutputConfig API](https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_TensorBoardOutputConfig.html)
- [CreateTrainingJob API](https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_CreateTrainingJob.html)
