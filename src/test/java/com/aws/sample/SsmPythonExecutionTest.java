package com.aws.sample;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.aws.sample.cloudwatch.CloudWatchLogService;
import com.aws.sample.common.AwsConfig;
import com.aws.sample.common.model.CommandResult;
import com.aws.sample.ssm.SsmService;

/**
 * SSM 执行 Python 脚本并集成 CloudWatch Logs 日志采集
 *
 * <h2>功能总结</h2>
 * 通过 AWS Systems Manager (SSM) 在 EC2 实例上远程执行 Python 脚本，
 * 利用 SSM 的 CloudWatchOutputConfig 将脚本的 stdout/stderr 自动转发到 CloudWatch Logs，
 * 实现「远程执行 + 日志集中采集 + 执行状态查询」的完整链路。
 *
 * <h2>实现架构</h2>
 * <pre>
 * 本地 Java 代码
 *   │
 *   ├─ SsmService.executeCommandWithLogs()     → SSM SendCommand API（带 CloudWatchOutputConfig）
 *   │     └─ SSM Agent 在 EC2 上执行 python3 -c '...'
 *   │           ├─ stdout → CloudWatch Logs 日志组 /aws/ssm/python-execution
 *   │           └─ stderr → CloudWatch Logs 日志组 /aws/ssm/python-execution
 *   │
 *   ├─ SsmService.getCommandResult()           → SSM GetCommandInvocation API（轮询等待完成）
 *   │
 *   └─ CloudWatchLogService.getLogs()           → CloudWatch Logs GetLogEvents API（查询日志）
 * </pre>
 *
 * <h2>涉及的服务类</h2>
 * <ul>
 *   <li>{@link com.aws.sample.ssm.SsmService} — SSM 命令执行，核心方法:
 *       <ul>
 *         <li>executeCommandWithLogs() — 发送命令并启用 CloudWatch 日志输出</li>
 *         <li>executeScriptWithLogs() — 带执行包装（开始/结束标记、退出码捕获）</li>
 *         <li>executeScriptAndWait() — 同步执行并等待结果</li>
 *         <li>getCommandResult() — 轮询获取命令执行状态（每 2 秒一次，最多 30 次）</li>
 *       </ul>
 *   </li>
 *   <li>{@link com.aws.sample.cloudwatch.CloudWatchLogService} — CloudWatch Logs 查询，核心方法:
 *       <ul>
 *         <li>ensureLogGroupExists() — 确保日志组存在（SSM 不会自动创建）</li>
 *         <li>getLogs() / printLogs() — 获取/打印日志（合并所有日志流）</li>
 *         <li>filterLogs() — 按关键字和时间范围过滤日志</li>
 *         <li>printSsmScriptDiagnostics() — SSM 脚本执行诊断报告</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <h2>实测耗时（Python 脚本执行约 5 秒的场景）</h2>
 * <ul>
 *   <li>发送 SSM 命令: ~1 秒</li>
 *   <li>getCommandResult 返回 Success: ~10 秒（脚本执行 5 秒 + SSM 轮询间隔）</li>
 *   <li>CloudWatch Logs 首次可查到日志: 命令完成后 ~5-7 秒</li>
 *   <li>端到端总耗时: ~20-30 秒</li>
 * </ul>
 *
 * <h2>前置条件</h2>
 * <ol>
 *   <li>EC2 实例已安装 SSM Agent（版本 >= 3.2.582.0）且在线</li>
 *   <li>EC2 实例已安装 Python3</li>
 *   <li>建议提前创建日志组（调用 ensureLogGroupExists）。SSM 会自动创建日志组，但自动创建时日志写入延迟约 46 秒，
 *       提前创建仅需约 7 秒。差距约 6 倍。</li>
 *   <li>SSM Agent 使用的 IAM 角色必须有 CloudWatch Logs 写入权限（见下方注意事项）</li>
 * </ol>
 *
 * <h2>注意事项（踩坑记录）</h2>
 *
 * <h3>1. IAM 权限 — DHMC 角色问题（关键）</h3>
 * <p>
 * 如果账号启用了 SSM Default Host Management Configuration (DHMC)，SSM Agent 使用的不是
 * EC2 实例配置文件（Instance Profile）的角色，而是 DHMC 创建的服务角色
 * （如 EpoxyAWSSystemsManagerDefaultEC2InstanceManagementRole）。
 * 该角色默认只有 ssm、ssmmessages、ec2messages 权限，没有 CloudWatch Logs 权限。
 * </p>
 * <p>
 * 解决方案：给 DHMC 角色添加 CloudWatch Logs 内联策略:
 * </p>
 * <pre>
 * aws iam put-role-policy \
 *   --role-name EpoxyAWSSystemsManagerDefaultEC2InstanceManagementRole \
 *   --policy-name CloudWatchLogsAccess \
 *   --policy-document '{
 *     "Version": "2012-10-17",
 *     "Statement": [{
 *       "Effect": "Allow",
 *       "Action": [
 *         "logs:CreateLogGroup",
 *         "logs:CreateLogStream",
 *         "logs:DescribeLogStreams",
 *         "logs:PutLogEvents"
 *       ],
 *       "Resource": "arn:aws:logs:eu-central-1:671067840733:log-group:/aws/ssm/*"
 *     }]
 *   }'
 * </pre>
 * <p>
 * 判断是否受 DHMC 影响：查看 SSM Agent 日志（/var/log/amazon/ssm/amazon-ssm-agent.log），
 * 如果 assumed-role 不是你期望的实例配置文件角色，说明 DHMC 生效了。
 * </p>
 *
 * <h3>2. 日志组名称前缀</h3>
 * <p>
 * 建议日志组以 /aws/ssm/ 开头。某些 SSM 默认策略仅允许此前缀下的 CloudWatch Logs 操作。
 * 使用其他前缀可能导致权限不足。
 * </p>
 *
 * <h3>3. CloudWatch Logs API 限制</h3>
 * <p>
 * DescribeLogStreams API 带 logStreamNamePrefix 参数时，不能同时使用 OrderBy=LastEventTime。
 * 代码中已处理此限制（见 CloudWatchLogService.getLogStreams）。
 * </p>
 *
 * <h3>4. 日志写入延迟</h3>
 * <p>
 * SSM 命令完成后，CloudWatch Logs 并非立即可查。实测延迟约 5-7 秒。
 * 查询日志时建议加入重试或等待机制，不要在命令完成后立即查询。
 * </p>
 *
 * <h3>5. SSM stdout 截断</h3>
 * <p>
 * SSM GetCommandInvocation 返回的 standardOutputContent 最大 24000 字符。
 * 超长输出会被截断，完整日志需通过 CloudWatch Logs 查看。
 * 这也是使用 CloudWatchOutputConfig 的核心价值之一。
 * </p>
 *
 * <h3>6. Python 脚本中的单引号</h3>
 * <p>
 * 内联 Python 脚本通过 python3 -c '...' 执行，脚本内容不能包含单引号。
 * 如果需要单引号，使用双引号替代或将脚本写入临时文件后执行。
 * </p>
 */
public class SsmPythonExecutionTest {

    /** 法兰克福区域 EC2 实例 */
    private static final String INSTANCE_ID = "i-08c36423b32ad8eb4";
    /** SSM 脚本执行日志组（必须以 /aws/ssm/ 开头，SSM Agent 默认 IAM 策略仅允许此前缀） */
    private static final String LOG_GROUP = "/aws/ssm/python-execution";

    private static AwsConfig config;
    private static SsmService ssmService;
    private static CloudWatchLogService logService;

    @BeforeAll
    static void setUp() {
        config = new AwsConfig();
        ssmService = new SsmService(config);
        logService = new CloudWatchLogService(config);
        // 提前创建日志组（非必须，SSM 会自动创建，但提前创建可显著减少日志写入延迟：~7秒 vs ~46秒）
        logService.ensureLogGroupExists(LOG_GROUP);
    }

    @AfterAll
    static void tearDown() {
        if (ssmService != null) ssmService.close();
        if (logService != null) logService.close();
    }

    /**
     * 测试：通过 SSM 执行 Python 脚本，日志输出到 CloudWatch Logs
     *
     * Python 脚本使用 logging 模块将日志写入 stdout，
     * SSM 的 CloudWatchOutputConfig 会自动将 stdout/stderr 转发到 CloudWatch Logs。
     */
    @Test
    void testExecutePythonWithCloudWatchLogs() {
        // 构建内联 Python 脚本
        String pythonScript = buildPythonScript();

        // 通过 SSM 执行 Python 脚本，日志输出到 CloudWatch Logs
        List<String> commands = List.of(
                "python3 -c '" + pythonScript + "'"
        );

        System.out.println("========== 发送 SSM 命令 ==========");
        long sendStart = System.currentTimeMillis();
        String commandId = ssmService.executeCommandWithLogs(
                INSTANCE_ID, commands, LOG_GROUP, 120);
        long sendEnd = System.currentTimeMillis();
        System.out.println("命令 ID: " + commandId);
        System.out.println("【计时】发送命令耗时: " + (sendEnd - sendStart) + " ms");

        // 等待命令执行完成，获取执行状态
        System.out.println("\n========== 等待命令执行完成 ==========");
        long waitStart = System.currentTimeMillis();
        CommandResult result = ssmService.getCommandResult(commandId, INSTANCE_ID);
        long waitEnd = System.currentTimeMillis();
        System.out.println("执行状态: " + result.getStatus());
        System.out.println("是否成功: " + result.isSuccess());
        System.out.println("【计时】getCommandResult 耗时: " + (waitEnd - waitStart) + " ms（从发送到 Success）");

        // 打印 SSM 返回的 stdout/stderr
        System.out.println("\n========== SSM 标准输出 ==========");
        System.out.println(result.getStandardOutput());

        if (result.getStandardError() != null && !result.getStandardError().isEmpty()) {
            System.out.println("\n========== SSM 标准错误 ==========");
            System.out.println(result.getStandardError());
        }

        // 等待 CloudWatch Logs 写入（实测延迟约 5-7 秒，这里等 10 秒留余量）
        System.out.println("\n========== 等待 CloudWatch Logs 写入 ==========");
        try { Thread.sleep(10000); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 打印完整日志
        System.out.println("\n========== CloudWatch Logs 完整日志 ==========");
        logService.printLogs(LOG_GROUP, commandId, 200);

        // 耗时汇总
        long totalMs = System.currentTimeMillis() - sendStart;
        System.out.println("\n========== 耗时汇总 ==========");
        System.out.println("发送命令: " + (sendEnd - sendStart) + " ms");
        System.out.println("等待 SSM 返回 Success: " + (waitEnd - waitStart) + " ms");
        System.out.println("总耗时（含日志等待）: " + totalMs + " ms");
    }

    /**
     * 测试：使用 executeScriptWithLogs 简化方式执行 Python 脚本
     *
     * 使用 SsmService 封装的 executeScriptAndWait 方法，
     * 自动添加脚本执行包装（开始/结束标记、退出码捕获）。
     */
    @Test
    void testExecutePythonScriptAndWait() {
        Instant startTime = Instant.now();

        // 将 Python 脚本写入临时文件后执行
        String pythonCode = buildPythonScript();
        String executeCommand = "python3 -c '" + pythonCode + "'";

        System.out.println("========== 执行 Python 脚本并等待完成 ==========");
        CommandResult result = ssmService.executeScriptAndWait(
                INSTANCE_ID, executeCommand, LOG_GROUP, 120);

        System.out.println("\n执行状态: " + result.getStatus());
        System.out.println("是否成功: " + result.isSuccess());
        System.out.println("\n标准输出:\n" + result.getStandardOutput());

        if (result.getStandardError() != null && !result.getStandardError().isEmpty()) {
            System.out.println("标准错误:\n" + result.getStandardError());
        }

        // 等待日志写入（实测延迟约 5-7 秒）
        try { Thread.sleep(10000); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 查询 CloudWatch Logs
        Instant endTime = Instant.now();
        System.out.println("\n========== CloudWatch Logs 日志 ==========");
        logService.printLogs(LOG_GROUP, null, 200);

        // 过滤 ERROR 日志
        System.out.println("\n========== ERROR 日志过滤 ==========");
        var errorLogs = logService.filterLogs(LOG_GROUP, null, "ERROR", startTime, endTime, 50);
        logService.printFilteredEvents(errorLogs);
    }

    /**
     * 测试：Python 脚本执行失败的场景
     *
     * 脚本先正常输出几行日志，然后故意抛出异常（exit code != 0），
     * 验证 getCommandResult 返回 Failed 状态，以及 CloudWatch Logs 中能看到错误信息。
     */
    @Test
    void testExecutePythonWithError() {
        String pythonScript = String.join("\n",
                "import logging, sys, platform",
                "logging.basicConfig(level=logging.INFO, format=\"%(asctime)s [%(levelname)s] %(message)s\",",
                "    handlers=[logging.StreamHandler(sys.stdout)])",
                "logger = logging.getLogger(\"error-test\")",
                "",
                "logger.info(\"脚本开始执行\")",
                "logger.info(f\"Python 版本: {platform.python_version()}\")",
                "logger.info(\"开始处理数据...\")",
                "",
                "# 故意触发异常",
                "data = [1, 2, 3]",
                "logger.error(f\"即将访问越界索引: data[{len(data) + 10}]\")",
                "result = data[999]  # IndexError"
        );

        List<String> commands = List.of("python3 -c '" + pythonScript + "'");

        System.out.println("========== 发送会失败的 SSM 命令 ==========");
        String commandId = ssmService.executeCommandWithLogs(INSTANCE_ID, commands, LOG_GROUP, 60);
        System.out.println("命令 ID: " + commandId);

        // 等待命令执行完成
        System.out.println("\n========== 等待命令执行完成 ==========");
        CommandResult result = ssmService.getCommandResult(commandId, INSTANCE_ID);
        System.out.println("执行状态: " + result.getStatus());
        System.out.println("是否成功: " + result.isSuccess());

        System.out.println("\n========== SSM 标准输出 ==========");
        System.out.println(result.getStandardOutput());

        System.out.println("\n========== SSM 标准错误 ==========");
        System.out.println(result.getStandardError());

        // 等待日志写入后查看 CloudWatch Logs
        try { Thread.sleep(10000); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\n========== CloudWatch Logs 完整日志 ==========");
        logService.printLogs(LOG_GROUP, commandId, 200);
    }

    // ===== 工具方法 =====

    /**
     * 构建测试用 Python 脚本
     *
     * 脚本功能:
     * - 使用 logging 模块输出结构化日志到 stdout
     * - 模拟数据处理流程（生成随机数据、计算统计值）
     * - 包含 INFO、WARNING 级别日志
     * - SSM CloudWatchOutputConfig 自动将 stdout/stderr 转发到 CloudWatch Logs
     */
    private String buildPythonScript() {
        return String.join("\n",
                "import logging",
                "import sys",
                "import time",
                "import random",
                "import platform",
                "",
                "logging.basicConfig(",
                "    level=logging.INFO,",
                "    format=\"%(asctime)s [%(levelname)s] %(message)s\",",
                "    handlers=[logging.StreamHandler(sys.stdout)]",
                ")",
                "logger = logging.getLogger(\"python-ssm-test\")",
                "",
                "logger.info(\"===== Python 脚本开始执行 =====\")",
                "logger.info(f\"Python 版本: {platform.python_version()}\")",
                "logger.info(f\"操作系统: {platform.system()} {platform.release()}\")",
                "logger.info(f\"主机名: {platform.node()}\")",
                "",
                "logger.info(\"开始数据处理...\")",
                "data = [random.gauss(0, 1) for _ in range(1000)]",
                "mean_val = sum(data) / len(data)",
                "max_val = max(data)",
                "min_val = min(data)",
                "logger.info(f\"数据生成完成: 样本数={len(data)}, 均值={mean_val:.4f}, 最大值={max_val:.4f}, 最小值={min_val:.4f}\")",
                "",
                "for step in range(1, 6):",
                "    time.sleep(1)",
                "    progress = step * 20",
                "    logger.info(f\"处理步骤 {step}/5 完成，进度: {progress}%\")",
                "    if step == 3:",
                "        logger.warning(\"步骤 3 处理时间较长，请注意性能\")",
                "",
                "logger.info(\"===== 处理结果汇总 =====\")",
                "logger.info(f\"总处理步骤: 5\")",
                "logger.info(f\"数据样本数: {len(data)}\")",
                "logger.info(f\"处理状态: 成功\")",
                "logger.info(\"===== Python 脚本执行完毕 =====\")"
        );
    }
}
