# 前端直传 S3 方案：CORS 配置 + STS 临时凭证

## 概述

本方案解决前端 JavaScript 通过 AWS SDK 直接操作 S3（put、get、list、delete）时的两个核心问题：

1. **跨域问题** — S3 默认不允许浏览器跨域请求，需要配置 CORS
2. **凭证安全** — 前端不能使用长期 AKSK，需要通过 STS AssumeRole 获取临时凭证

### 整体架构

```
┌──────────────┐     1. 请求临时凭证      ┌──────────────────┐
│              │ ──────────────────────►  │                  │
│   前端 JS    │                          │   后端 Java 服务  │
│  (浏览器)    │  ◄──────────────────────  │  (StsService)    │
│              │     2. 返回临时凭证       │                  │
└──────┬───────┘                          └────────┬─────────┘
       │                                           │
       │ 3. 使用临时凭证                            │ STS AssumeRole
       │    直接操作 S3                              │
       ▼                                           ▼
┌──────────────┐                          ┌──────────────────┐
│              │                          │                  │
│   S3 存储桶   │                          │    AWS STS       │
│  (已配置CORS) │                          │                  │
│              │                          │                  │
└──────────────┘                          └──────────────────┘
```

## 一、S3 CORS 跨域配置

### 1.1 为什么需要 CORS

浏览器的同源策略会阻止前端 JS 直接请求 S3 域名。必须在 S3 桶上配置 CORS 规则，声明允许哪些域名、哪些 HTTP 方法可以访问。

### 1.2 快速配置（推荐）

使用 `S3Service.putBucketCorsForOrigins()` 一键配置，默认允许 GET/PUT/POST/DELETE/HEAD：

```java
AwsConfig config = new AwsConfig();
try (S3Service s3Service = new S3Service(config)) {
    // 为已有桶配置 CORS
    s3Service.putBucketCorsForOrigins("my-bucket", List.of(
        "http://localhost:3000",    // 本地开发
        "http://localhost:5173",    // Vite 开发服务器
        "https://app.example.com"   // 生产环境
    ));
}
```

默认 CORS 规则包含：
- 允许方法：GET、PUT、POST、DELETE、HEAD
- 允许头：`*`（所有请求头）
- 暴露头：ETag、x-amz-request-id、x-amz-id-2、Content-Length、Content-Type
- 预检缓存：3600 秒

### 1.3 一键创建桶 + CORS

创建新桶时直接配置 CORS：

```java
s3Service.createBucketWithCors(
    "my-new-bucket",
    List.of("http://localhost:3000", "https://app.example.com"),
    Map.of("project", "my-project", "env", "dev")  // 标签，可为 null
);
```

### 1.4 自定义 CORS 规则

需要更精细控制时，使用 `putBucketCors()` 传入自定义规则：

```java
// 只读规则（仅允许 GET）
CORSRule readOnlyRule = CORSRule.builder()
    .allowedOrigins("https://readonly.example.com")
    .allowedMethods("GET", "HEAD")
    .allowedHeaders("*")
    .maxAgeSeconds(600)
    .build();

// 完全访问规则
CORSRule fullAccessRule = CORSRule.builder()
    .allowedOrigins("https://admin.example.com")
    .allowedMethods("GET", "PUT", "POST", "DELETE", "HEAD")
    .allowedHeaders("*")
    .exposeHeaders("ETag", "x-amz-request-id", "Content-Length", "Content-Type")
    .maxAgeSeconds(3600)
    .build();

s3Service.putBucketCors("my-bucket", List.of(readOnlyRule, fullAccessRule));
```

### 1.5 查询和删除 CORS

```java
// 查询当前 CORS 配置
List<CORSRule> rules = s3Service.getBucketCors("my-bucket");

// 删除 CORS 配置
s3Service.deleteBucketCors("my-bucket");
```


## 二、STS 临时凭证

### 2.1 前置条件：创建 IAM 角色

后端通过 STS AssumeRole 获取临时凭证，需要先创建一个 IAM 角色。

#### 步骤 1：创建 IAM 角色的信任策略

信任策略决定谁可以扮演这个角色。将 `ACCOUNT_ID` 替换为你的 AWS 账户 ID：

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::ACCOUNT_ID:root"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
```

> 生产环境建议将 Principal 限制为具体的 IAM 用户或角色，而非 root。

#### 步骤 2：为角色附加 S3 权限策略

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::my-bucket",
        "arn:aws:s3:::my-bucket/*"
      ]
    }
  ]
}
```

#### 步骤 3：使用 IamService 创建（可选）

也可以通过代码创建：

```java
try (IamService iamService = new IamService(config)) {
    // 创建角色
    String roleArn = iamService.createRole(
        "S3AccessRole",
        trustPolicyJson,
        "前端 S3 直传用角色"
    );

    // 创建并附加权限策略
    String policyArn = iamService.createPolicy(
        "S3AccessPolicy",
        s3PolicyJson,
        "S3 读写权限"
    );
    iamService.attachRolePolicy("S3AccessRole", policyArn);
}
```

### 2.2 获取临时凭证

#### 基本用法（默认 1 小时有效期）

```java
AwsConfig config = new AwsConfig();
try (StsService stsService = new StsService(config)) {
    Credentials credentials = stsService.assumeRole(
        "arn:aws:iam::123456789012:role/S3AccessRole",
        "frontend-session"
    );

    // 将以下三个值返回给前端
    String accessKeyId = credentials.accessKeyId();
    String secretAccessKey = credentials.secretAccessKey();
    String sessionToken = credentials.sessionToken();
    // 过期时间
    Instant expiration = credentials.expiration();
}
```

#### 指定有效期

```java
// 15 分钟有效期（范围：900 ~ 43200 秒）
Credentials credentials = stsService.assumeRole(
    "arn:aws:iam::123456789012:role/S3AccessRole",
    "short-session",
    900
);
```

#### 限定桶访问（推荐）

通过内联策略进一步收窄权限，临时凭证只能操作指定的桶：

```java
Credentials credentials = stsService.assumeRoleForS3Bucket(
    "arn:aws:iam::123456789012:role/S3AccessRole",
    "bucket-session",
    "my-bucket"
);
```

#### 限定桶 + 前缀（多租户场景）

每个用户只能访问自己的前缀路径，实现租户隔离：

```java
String userId = "user123";
Credentials credentials = stsService.assumeRoleForS3Bucket(
    "arn:aws:iam::123456789012:role/S3AccessRole",
    "tenant-" + userId,
    "my-bucket",
    userId + "/"   // 只能访问 my-bucket/user123/* 下的对象
);
```

### 2.3 通过 Ec2Manager 调用

所有功能也可以通过统一入口 `Ec2Manager` 调用：

```java
try (Ec2Manager manager = new Ec2Manager()) {
    // CORS 配置
    manager.putBucketCorsForOrigins("my-bucket", List.of("http://localhost:3000"));
    manager.getBucketCors("my-bucket");
    manager.deleteBucketCors("my-bucket");
    manager.createBucketWithCors("new-bucket", List.of("https://app.example.com"), null);

    // STS 临时凭证
    Credentials creds = manager.assumeRole(roleArn, "session-name");
    Credentials scopedCreds = manager.assumeRoleForS3Bucket(roleArn, "session", "my-bucket");
    Credentials tenantCreds = manager.assumeRoleForS3Bucket(roleArn, "session", "my-bucket", "user123/");
}
```


## 三、前端 JavaScript 使用示例

### 3.1 安装依赖

```bash
npm install @aws-sdk/client-s3
```

### 3.2 初始化 S3 客户端

```javascript
import { S3Client } from '@aws-sdk/client-s3';

// 从后端接口获取临时凭证
const response = await fetch('/api/sts/credentials');
const { accessKeyId, secretAccessKey, sessionToken, expiration } = await response.json();

const s3Client = new S3Client({
  region: 'eu-central-1',
  credentials: {
    accessKeyId,
    secretAccessKey,
    sessionToken
  }
});
```

### 3.3 上传文件（PutObject）

```javascript
import { PutObjectCommand } from '@aws-sdk/client-s3';

async function uploadFile(file) {
  const command = new PutObjectCommand({
    Bucket: 'my-bucket',
    Key: `uploads/${file.name}`,
    Body: file,
    ContentType: file.type
  });

  const result = await s3Client.send(command);
  console.log('上传成功:', result);
}
```

### 3.4 列出对象（ListObjects）

```javascript
import { ListObjectsV2Command } from '@aws-sdk/client-s3';

async function listFiles(prefix) {
  const command = new ListObjectsV2Command({
    Bucket: 'my-bucket',
    Prefix: prefix,
    MaxKeys: 100
  });

  const result = await s3Client.send(command);
  return result.Contents || [];
}
```

### 3.5 下载文件（GetObject）

```javascript
import { GetObjectCommand } from '@aws-sdk/client-s3';

async function downloadFile(key) {
  const command = new GetObjectCommand({
    Bucket: 'my-bucket',
    Key: key
  });

  const result = await s3Client.send(command);
  // 转为 Blob 用于下载
  const blob = await result.Body.transformToByteArray();
  const url = URL.createObjectURL(new Blob([blob]));

  const a = document.createElement('a');
  a.href = url;
  a.download = key.split('/').pop();
  a.click();
}
```

### 3.6 删除文件（DeleteObject）

```javascript
import { DeleteObjectCommand } from '@aws-sdk/client-s3';

async function deleteFile(key) {
  const command = new DeleteObjectCommand({
    Bucket: 'my-bucket',
    Key: key
  });

  await s3Client.send(command);
  console.log('删除成功:', key);
}
```

### 3.7 凭证刷新

临时凭证有过期时间，前端需要在过期前刷新：

```javascript
let credentials = null;
let expiration = null;

async function getCredentials() {
  // 提前 5 分钟刷新
  if (!credentials || Date.now() > expiration - 5 * 60 * 1000) {
    const response = await fetch('/api/sts/credentials');
    const data = await response.json();
    credentials = {
      accessKeyId: data.accessKeyId,
      secretAccessKey: data.secretAccessKey,
      sessionToken: data.sessionToken
    };
    expiration = new Date(data.expiration).getTime();

    // 更新 S3 客户端
    s3Client = new S3Client({
      region: 'eu-central-1',
      credentials
    });
  }
  return s3Client;
}
```

## 四、后端接口示例

后端需要提供一个 API 接口，供前端获取临时凭证。以下是伪代码示例：

```java
// 后端 API 接口（以 Spring Boot 为例）
@GetMapping("/api/sts/credentials")
public Map<String, Object> getTemporaryCredentials(@RequestParam String userId) {
    AwsConfig config = new AwsConfig();
    try (StsService stsService = new StsService(config)) {
        // 为该用户生成限定前缀的临时凭证
        Credentials credentials = stsService.assumeRoleForS3Bucket(
            "arn:aws:iam::123456789012:role/S3AccessRole",
            "user-" + userId,
            "my-bucket",
            userId + "/"
        );

        return Map.of(
            "accessKeyId", credentials.accessKeyId(),
            "secretAccessKey", credentials.secretAccessKey(),
            "sessionToken", credentials.sessionToken(),
            "expiration", credentials.expiration().toString(),
            "bucket", "my-bucket",
            "prefix", userId + "/"
        );
    }
}
```

## 五、涉及的文件清单

| 文件 | 说明 |
|------|------|
| `src/main/java/com/aws/sample/s3/S3Service.java` | S3 服务，新增 CORS 配置管理方法 |
| `src/main/java/com/aws/sample/sts/StsService.java` | STS 服务（新建），提供 AssumeRole 临时凭证 |
| `src/main/java/com/aws/sample/Ec2Manager.java` | 门面类，整合 S3 CORS 和 STS 操作 |
| `src/test/java/com/aws/sample/S3CorsTest.java` | S3 CORS 配置测试 |
| `src/test/java/com/aws/sample/StsAssumeRoleTest.java` | STS 临时凭证测试 |

## 六、注意事项

1. **CORS 配置生效时间**：S3 CORS 配置通常在几秒内生效，但浏览器可能缓存预检结果（由 `MaxAgeSeconds` 控制）
2. **临时凭证有效期**：范围 900~43200 秒（15 分钟 ~ 12 小时），建议根据业务场景选择合适的时长
3. **安全建议**：
   - 生产环境的 CORS `allowedOrigins` 不要使用 `*`，应明确指定域名
   - 使用 `assumeRoleForS3Bucket` 限定桶和前缀，遵循最小权限原则
   - 前端不要缓存临时凭证到 localStorage，建议存在内存中
4. **IAM 角色信任策略**：确保只有后端服务有权限调用 `sts:AssumeRole`
5. **多租户隔离**：通过前缀限制实现数据隔离，每个用户只能访问 `bucket/userId/*` 下的对象
