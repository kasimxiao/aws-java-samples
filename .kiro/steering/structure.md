# 项目结构

```
src/main/java/com/aws/sample/
├── Ec2Manager.java          # 统一入口，整合所有服务
├── common/
│   ├── AwsConfig.java       # 配置管理，加载 properties
│   └── model/               # 通用数据模型
├── ec2/
│   ├── Ec2Service.java      # EC2 实例和 AMI 操作
│   └── model/               # EC2 相关模型
├── iam/
│   └── IamService.java      # IAM 角色管理
├── ssm/
│   └── SsmService.java      # SSM 命令执行
├── dcv/
│   └── DcvService.java      # DCV 远程桌面
├── ecr/
│   └── EcrService.java      # ECR 容器镜像
└── sagemaker/
    ├── SageMakerDeploymentService.java   # 模型部署
    ├── SageMakerTrainingService.java     # 模型训练
    ├── SageMakerMonitoringService.java   # 模型监控
    ├── SageMakerImageService.java        # 镜像管理
    └── model/                            # SageMaker 配置模型
```

## 架构模式

- 门面模式：`Ec2Manager` 作为统一入口
- 服务分层：每个 AWS 服务独立封装
- 配置驱动：`AwsConfig` 集中管理配置
- Builder 模式：配置类使用 Builder 构建

## 测试目录

```
src/test/java/com/aws/sample/
├── CreateEc2Test.java       # EC2 创建测试
├── CreateAmiTest.java       # AMI 创建测试
├── ListInstancesTest.java   # 实例列表测试
├── DcvPresignTest.java      # DCV URL 测试
├── DcvChangePasswordTest.java
└── SageMaker*Test.java      # SageMaker 相关测试
```
