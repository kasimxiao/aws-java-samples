#!/bin/bash
# 根据租户 ID 生成 SageMaker 执行角色策略
# 用法: ./generate-tenant-sagemaker-policy.sh <tenant_id> [output_file]

set -e

TENANT_ID=$1
OUTPUT_FILE=${2:-"sagemaker-execution-policy-${TENANT_ID}.json"}

if [ -z "$TENANT_ID" ]; then
    echo "错误: 请提供租户 ID"
    echo "用法: $0 <tenant_id> [output_file]"
    exit 1
fi

if [[ ! "$TENANT_ID" =~ ^[a-zA-Z0-9-]+$ ]]; then
    echo "错误: 租户 ID 只能包含字母、数字和连字符"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TEMPLATE_FILE="${SCRIPT_DIR}/sagemaker-execution-tenant-policy-template.json"

if [ ! -f "$TEMPLATE_FILE" ]; then
    echo "错误: 模板文件不存在: $TEMPLATE_FILE"
    exit 1
fi

sed "s/\${TENANT_ID}/${TENANT_ID}/g" "$TEMPLATE_FILE" > "$OUTPUT_FILE"

echo "已生成 SageMaker 执行策略: $OUTPUT_FILE"
echo "租户 ID: $TENANT_ID"
echo ""
echo "部署命令:"
echo "  aws iam create-policy --policy-name sagemaker-execution-policy-${TENANT_ID} --policy-document file://${OUTPUT_FILE}"
