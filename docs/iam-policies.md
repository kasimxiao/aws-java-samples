# IAM 权限配置文档

本文档列出执行项目代码所需的 IAM 权限。

## 一、调用方 IAM Policy

执行代码的用户或角色需要以下权限：

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "EC2Permissions",
            "Effect": "Allow",
            "Action": [
                "ec2:RunInstances",
                "ec2:StartInstances",
                "ec2:StopInstances",
                "ec2:RebootInstances",
                "ec2:TerminateInstances",
                "ec2:DescribeInstances",
                "ec2:CreateTags",
                "ec2:DeleteTags",
                "ec2:DescribeTags",
                "ec2:CreateImage",
                "ec2:CopyImage",
                "ec2:DeregisterImage",
                "ec2:DescribeImages",
                "ec2:ModifyImageAttribute",
                "ec2:DeleteSnapshot",
                "ec2:AssociateIamInstanceProfile",
                "ec2:DisassociateIamInstanceProfile",
                "ec2:DescribeIamInstanceProfileAssociations"
            ],
            "Resource": "*"
        },
        {
            "Sid": "SSMPermissions",
            "Effect": "Allow",
            "Action": [
                "ssm:SendCommand",
                "ssm:GetCommandInvocation"
            ],
            "Resource": "*"
        },
        {
            "Sid": "IAMPermissions",
            "Effect": "Allow",
            "Action": [
                "iam:CreateInstanceProfile",
                "iam:AddRoleToInstanceProfile",
                "iam:PassRole"
            ],
            "Resource": "*"
        },
        {
            "Sid": "ECRPermissions",
            "Effect": "Allow",
            "Action": [
                "ecr:CreateRepository",
                "ecr:DeleteRepository",
                "ecr:DescribeRepositories",
                "ecr:ListImages",
                "ecr:DescribeImages",
                "ecr:BatchDeleteImage",
                "ecr:GetAuthorizationToken",
                "ecr:PutLifecyclePolicy",
                "ecr:GetLifecyclePolicy"
            ],
            "Resource": "*"
        }
    ]
}
```

## 二、EC2 实例 IAM Policy（Instance Profile）

EC2 实例需要以下权限：

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "SSMAgentPermissions",
            "Effect": "Allow",
            "Action": [
                "ssm:UpdateInstanceInformation",
                "ssmmessages:CreateControlChannel",
                "ssmmessages:CreateDataChannel",
                "ssmmessages:OpenControlChannel",
                "ssmmessages:OpenDataChannel",
                "ec2messages:AcknowledgeMessage",
                "ec2messages:DeleteMessage",
                "ec2messages:FailMessage",
                "ec2messages:GetEndpoint",
                "ec2messages:GetMessages",
                "ec2messages:SendReply"
            ],
            "Resource": "*"
        },
        {
            "Sid": "S3MountPermissions",
            "Effect": "Allow",
            "Action": [
                "s3:GetObject",
                "s3:PutObject",
                "s3:ListBucket",
                "s3:DeleteObject"
            ],
            "Resource": [
                "arn:aws:s3:::YOUR-BUCKET-NAME",
                "arn:aws:s3:::YOUR-BUCKET-NAME/*"
            ]
        },
        {
            "Sid": "CloudWatchLogs",
            "Effect": "Allow",
            "Action": [
                "logs:CreateLogGroup",
                "logs:CreateLogStream",
                "logs:PutLogEvents"
            ],
            "Resource": "*"
        }
    ]
}
```

## 三、权限说明

| 服务 | 权限 | 用途 |
|------|------|------|
| EC2 | RunInstances | 创建实例 |
| EC2 | Start/Stop/Reboot/TerminateInstances | 实例生命周期管理 |
| EC2 | CreateImage, CopyImage, DeregisterImage | AMI 镜像管理 |
| EC2 | CreateTags, DeleteTags, DescribeTags | 标签管理 |
| EC2 | ModifyImageAttribute | 修改 AMI 共享权限 |
| EC2 | DeleteSnapshot | 删除 EBS 快照 |
| SSM | SendCommand, GetCommandInvocation | 远程执行命令 |
| IAM | CreateInstanceProfile, AddRoleToInstanceProfile | 创建实例配置文件 |
| IAM | PassRole | 将角色传递给 EC2 |
| ECR | CreateRepository, DeleteRepository | 仓库管理 |
| ECR | GetAuthorizationToken | Docker 登录认证 |
| ECR | BatchDeleteImage, ListImages | 镜像管理 |
