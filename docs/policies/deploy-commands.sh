#!/bin/bash
# 权限部署脚本
# 使用前请设置: export AWS_REGION=cn-north-1 (或其他区域)

POLICY_DIR="$(dirname "$0")"

# ========== 1. 用户权限 (AWS Console 操作) ==========
# 创建策略
aws iam create-policy \
  --policy-name toolchain-user-policy \
  --policy-document file://${POLICY_DIR}/1-user-policy.json

# 附加到用户组
# 提前建好
aws iam attach-group-policy \
  --group-name toolchain-users \
  --policy-arn arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):policy/toolchain-user-policy

# ========== 2. Workload 权限 (应用程序调用 SDK) ==========
aws iam create-policy \
  --policy-name toolchain-workload-policy \
  --policy-document file://${POLICY_DIR}/2-workload-policy.json

# 创建 Workload 角色并附加策略
aws iam create-role \
  --role-name toolchain-workload-role \
  --assume-role-policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}'

aws iam attach-role-policy \
  --role-name toolchain-workload-role \
  --policy-arn arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):policy/toolchain-workload-policy


# ========== 3. EC2 实例权限 (SSM 执行 S3 挂载) ==========
aws iam create-policy \
  --policy-name toolchain-ec2-instance-policy \
  --policy-document file://${POLICY_DIR}/3-ec2-instance-policy.json

# 创建 EC2 实例角色
aws iam create-role \
  --role-name toolchain-ec2-instance-role \
  --assume-role-policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}'

aws iam attach-role-policy \
  --role-name toolchain-ec2-instance-role \
  --policy-arn arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):policy/toolchain-ec2-instance-policy

# 创建实例配置文件
aws iam create-instance-profile --instance-profile-name toolchain-ec2-instance-profile
aws iam add-role-to-instance-profile \
  --instance-profile-name toolchain-ec2-instance-profile \
  --role-name toolchain-ec2-instance-role

# ========== 4. SageMaker 执行权限 (训练和部署任务) ==========
aws iam create-policy \
  --policy-name toolchain-sagemaker-execution-policy \
  --policy-document file://${POLICY_DIR}/4-sagemaker-execution-policy.json

# 创建 SageMaker 执行角色
aws iam create-role \
  --role-name toolchain-sagemaker-execution-role \
  --assume-role-policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"sagemaker.amazonaws.com"},"Action":"sts:AssumeRole"}]}'

aws iam attach-role-policy \
  --role-name toolchain-sagemaker-execution-role \
  --policy-arn arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):policy/toolchain-sagemaker-execution-policy

echo "权限部署完成!"
echo "SageMaker 执行角色 ARN: arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):role/toolchain-sagemaker-execution-role"
echo "EC2 实例配置文件: toolchain-ec2-instance-profile"
