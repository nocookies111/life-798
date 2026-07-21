# WaterWidget

WaterWidget 是一个面向慧生活798用户的第三方 Android 设备控制客户端，提供多账户管理、热水/冷水设备切换、用水消费统计、桌面小部件、快捷设置磁贴与设备二维码添加功能。

> 非官方项目，与慧生活798服务提供方无关。请仅使用你本人有权访问的账户和设备，并遵守相关服务规则。

## 功能

- 热水 / 冷水一键控制，以及常用设备快捷切换
- 短信登录、账户管理和设备控制登录信息管理
- 扫描二维码或手动添加设备编号
- 每日签到与完整积分任务（最高约 330 积分）、积分明细，以及今日 / 本月 / 本年消费与预计饮水量统计
- 桌面小部件、热水 / 冷水快捷设置磁贴
- 浅色、深色和跟随系统的显示模式


## 运行环境

- Android 8.0（API 26）及以上
- 仅提供 `arm64-v8a` 安装包，适用于现代 64 位 Android 设备

## 构建

项目采用 Gradle Kotlin DSL 与 JDK 17。源码通过 Gradle `sourceSets` 直接使用仓库根目录的 `src/main` 和 `src/test`。

### 配置敏感参数

项目中的 API 地址、签名盐值等敏感信息通过 `secrets.properties` 注入，**不会提交到版本控制**。构建前需手动创建：

1. 复制项目根目录的 `secrets.properties.example` 为 `secrets.properties`
2. 将其中的占位值替换为实际值

```bash
cp secrets.properties.example secrets.properties
# 然后编辑 secrets.properties 填入实际的 API_GATEWAY / SIGN_SALT / API_CID
```

> **说明**：未配置 `secrets.properties` 时项目仍可正常编译，但构建出的 APK 因缺少必要参数无法连接服务端。

### 构建命令

```bash
# JVM 单元测试
gradle testDebugUnitTest

# Debug APK
gradle assembleDebug

# 正式 Release（需设置签名环境变量）
gradle assembleRelease
```

Debug APK 默认输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 隐私与数据

应用的账户和设备配置保存在本机。请不要将设备控制登录信息、账户数据或二维码内容分享给他人。

## 免责声明

本项目仅供学习交流与技术研究之用，**严禁用于任何商业用途或非法用途**。

- 本项目所涉及的所有接口与协议均基于公开通信协议的分析与学习，仅供技术学习与学术研究参考。
- 请于下载后 **24 小时内删除**本项目的全部内容。如果你在 24 小时后仍保留本项目，则视为你自愿承担由此产生的一切法律责任。
- 使用本项目所造成的任何直接或间接损失，开发者不承担任何责任。
- 未经授权访问他人账户、设备或服务可能违反相关法律法规，请务必仅使用你本人合法拥有的账户和设备，并遵守相关服务条款。
- 本项目不提供任何形式的担保，亦不保证其内容的完整性、准确性和时效性。

如果你不同意上述声明，请立即删除本项目并停止使用。

## 许可证

本项目采用 [MIT License](LICENSE)。
