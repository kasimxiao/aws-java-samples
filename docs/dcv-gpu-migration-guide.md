# DCV 远程桌面 — GPU 实例类型迁移指南

当使用已有 AMI 更换 EC2 GPU 实例类型（如 g4dn → g6）时，由于 GPU 硬件不同，DCV 远程桌面可能无法正常连接（一直卡在 "Connecting" 状态）。本文档记录排查和修复流程。

## 问题背景

| 实例类型 | GPU 型号 | PCI BusID（示例） |
|---------|---------|------------------|
| g4dn    | Tesla T4 | PCI:0:30:0 |
| g6      | NVIDIA L4 | PCI:49:0:0 |
| g5      | A10G | 因实例而异 |
| p4d     | A100 | 因实例而异 |

AMI 中的 `/etc/X11/xorg.conf` 会写死原始 GPU 的 `BoardName` 和 `BusID`，迁移后这些值不再匹配，导致 Xorg 无法启动，DCV 没有可用的显示输出。

## 症状表现

- 浏览器打开 DCV URL 后一直显示 "Connecting"
- `dcv list-sessions` 正常，`dcv list-connections` 也能看到连接
- 安全组、端口、DCV 服务状态均正常
- GDM 日志中反复出现 `Session never registered, failing`

## 排查步骤

### 1. 检查 Xorg 是否运行

```bash
ps aux | grep Xorg | grep -v grep
```

如果没有 Xorg 进程，说明显示服务未启动。

### 2. 检查 GPU 识别和驱动状态

```bash
# 查看 GPU 硬件
lspci | grep -i nvidia

# 查看驱动版本（用户空间）
nvidia-smi

# 查看内核模块版本
cat /proc/driver/nvidia/version
```

确认两个版本一致。如果 `nvidia-smi` 报 `Driver/library version mismatch`，说明需要重启加载新内核模块。

### 3. 检查 Xorg 驱动模块

```bash
ls /usr/lib/xorg/modules/drivers/ | grep nvidia
```

如果没有 `nvidia_drv.so`，说明缺少 Xorg 显示驱动模块。

### 4. 检查 xorg.conf

```bash
cat /etc/X11/xorg.conf
```

关注 `Device` 段中的 `BoardName` 和 `BusID` 是否与当前 GPU 匹配。

## 修复步骤

### 步骤一：安装完整的 NVIDIA 显示驱动

headless/server 版驱动可能缺少 Xorg 模块，需要补装：

```bash
sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
  nvidia-driver-570-server \
  xserver-xorg-video-nvidia-570-server
```

> 注意：将 `570` 替换为你实际使用的驱动主版本号。可通过 `dpkg -l | grep nvidia-driver` 查看当前版本。

### 步骤二：更新 xorg.conf 中的 PCI BusID

```bash
# 查看新 GPU 的 PCI 地址（十六进制）
lspci -D | grep -i nvidia
# 输出示例：0000:31:00.0 → 十进制为 PCI:49:0:0

# 自动重新生成 xorg.conf
sudo nvidia-xconfig --busid=PCI:49:0:0 --preserve-busid
```

PCI 地址转换规则：`lspci` 输出的十六进制 `31:00.0` → xorg.conf 中的十进制 `PCI:49:0:0`（0x31 = 49）。

### 步骤三：重启实例

驱动更新后内核模块版本可能不匹配，必须重启：

```bash
sudo reboot
```

### 步骤四：验证

重启后依次确认：

```bash
# 1. 驱动版本一致
nvidia-smi
cat /proc/driver/nvidia/version

# 2. Xorg 正常运行
ps aux | grep Xorg

# 3. GPU 显存被使用（说明 Xorg 在用 GPU 渲染）
nvidia-smi  # Memory-Usage 应大于 0

# 4. DCV 会话正常
dcv list-sessions

# 5. GDM 不再循环报错
journalctl -u gdm --no-pager -n 10
```

### 步骤五：重新生成 DCV Presign URL

修复后原有 token 已失效，需要重新生成：

```bash
# 通过项目工具生成
mvn compile test-compile exec:java \
  -Dexec.mainClass="com.aws.sample.DcvPresignTest" \
  -Dexec.classpathScope=test
```

## 快速修复脚本

将以下脚本通过 SSM 发送到实例执行（需根据实际情况修改驱动版本和 BusID）：

```bash
#!/bin/bash
set -e

# 获取 NVIDIA GPU 的 PCI BusID
PCI_HEX=$(lspci | grep -i nvidia | awk '{print $1}' | head -1)
BUS_HEX=$(echo $PCI_HEX | cut -d: -f1)
DEV_HEX=$(echo $PCI_HEX | cut -d: -f2 | cut -d. -f1)
FUNC=$(echo $PCI_HEX | cut -d. -f1 | rev | cut -d: -f1 | rev)
FUNC=$(echo $PCI_HEX | cut -d. -f2)
BUS_DEC=$((16#$BUS_HEX))
DEV_DEC=$((16#$DEV_HEX))

echo "检测到 GPU PCI 地址: $PCI_HEX → PCI:$BUS_DEC:$DEV_DEC:$FUNC"

# 获取当前驱动主版本
DRIVER_VER=$(dpkg -l | grep nvidia-driver | grep "^ii" | head -1 | awk '{print $3}' | cut -d. -f1)
echo "当前驱动版本: $DRIVER_VER"

# 安装 Xorg 驱动模块
sudo apt-get update -y
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
  nvidia-driver-${DRIVER_VER}-server \
  xserver-xorg-video-nvidia-${DRIVER_VER}-server

# 更新 xorg.conf
sudo nvidia-xconfig --busid="PCI:$BUS_DEC:$DEV_DEC:$FUNC" --preserve-busid

echo "修复完成，请重启实例: sudo reboot"
```

## 常见 GPU 实例类型对照

| 实例系列 | GPU | 架构 | 典型用途 |
|---------|-----|------|---------|
| g4dn | Tesla T4 | Turing | 推理、轻量训练 |
| g5 | A10G | Ampere | 图形、推理 |
| g6 | L4 | Ada Lovelace | 推理、视频编码 |
| g6e | L40S | Ada Lovelace | 训练、推理 |
| p4d | A100 | Ampere | 大规模训练 |
| p5 | H100 | Hopper | 大规模训练 |

> 同架构 GPU 之间迁移（如 g5 → g6e）通常只需更新 xorg.conf 的 BusID。跨架构迁移可能还需要更新驱动版本。
