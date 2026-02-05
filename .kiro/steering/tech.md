# 技术栈

## 构建系统

- Maven 3.x
- Java 17

## 核心依赖

- AWS SDK for Java 2.x (v2.29.0)
  - ec2、iam、ssm、sagemaker、ecr、sts、bedrockruntime
- SLF4J 2.0.9（日志）
- JUnit 5.10.0（测试）

## 常用命令

```bash
# 编译
mvn compile

# 运行测试
mvn test

# 运行单个测试
mvn test -Dtest=CreateEc2Test

# 打包（生成可执行 JAR）
mvn package

# 清理并打包
mvn clean package

# 跳过测试打包
mvn package -DskipTests
```

## 配置管理

- 配置文件：`src/main/resources/application.properties`
- 支持环境变量覆盖 AWS 凭证
- 支持 IAM Role 自动获取凭证
