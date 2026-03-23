# HttpMate

<p align="center">
  <img src="docs/images/logo.png" alt="HttpMate Logo" width="128" height="128" />
</p>

![Build](https://github.com/EachFly/HttpMate/workflows/Build/badge.svg)

<!-- Plugin description -->
## 📝 项目简介 (Project Overview)

**HttpMate** 是一个 IntelliJ IDEA 插件，用于在 Java/Kotlin 项目中提升 REST API 开发效率。  
它聚焦三个高频场景：

- 快速检索并跳转 REST 接口定义（类似 IDE 内搜索即跳转体验）
- 基于类结构一键生成 JSON / Mock JSON
- 基于控制器方法自动生成 Markdown 接口文档

插件当前通过 PSI + 注解扫描实现对 **Spring Web** 与 **JAX-RS（javax / jakarta）** 的接口识别，并在 IDE 内完成交互与导航。

## ✨ 主要特性 (Key Features)

1. **REST API 全局搜索与跳转**
   - 快捷键：`Ctrl + \` 或 `Ctrl + Alt + H`
   - 支持按路径/方法模糊子序列匹配，`Enter` 直接跳转到接口定义
2. **注解级接口识别（Spring + JAX-RS）**
   - 支持 `@GetMapping/@PostMapping/.../@RequestMapping`
   - 支持 `@Path/@GET/@POST/...`（`javax.ws.rs` 与 `jakarta.ws.rs`）
3. **JSON 结构生成**
   - 右键类生成结构化 JSON，递归展开字段并处理基础类型、集合、Map、枚举、时间类型
4. **Mock JSON 数据生成**
   - 基于字段类型生成随机示例数据，便于接口联调与文档示例编写
5. **Markdown API 文档生成**
   - 支持按方法或按类批量生成文档，包含接口信息、参数表、响应字段与请求/响应示例
<!-- Plugin description end -->

## 🛠️ 技术栈 (Tech Stack)

| 类别 | 技术/库 | 版本 |
| --- | --- | --- |
| 语言 | Kotlin (JVM) | `2.3.20` |
| 运行时 | Java Toolchain | `21` |
| 构建工具 | Gradle Wrapper | `9.0.0` |
| IntelliJ 插件构建 | `org.jetbrains.intellij.platform` | `2.13.1` |
| 目标 IDE 平台 | IntelliJ IDEA Community (`IC`) | `2024.3.6` |
| 测试 | JUnit | `4.13.2` |
| 覆盖率 | Kover | `0.9.7` |
| 代码检查 | Qodana Gradle Plugin | `2025.3.2` |

> 兼容基线由插件配置控制：`sinceBuild = 243`（IntelliJ 2024.3+）。

## 🚀 快速开始 (Quick Start)

### 环境前置

- JDK `21+`
- IntelliJ IDEA `2024.3+`（用于运行/调试插件）

### 安装指南

```bash
git clone https://github.com/EachFly/HttpMate.git
cd HttpMate
```

### 运行指令

macOS / Linux:

```bash
./gradlew runIde
```

Windows (PowerShell):

```powershell
.\gradlew.bat runIde
```

常用命令：

```bash
# 构建插件包（zip）
./gradlew buildPlugin

# 运行测试与检查
./gradlew check

# 插件结构与兼容性校验
./gradlew verifyPlugin
```

构建产物默认位于：

```text
build/distributions/
```

### 配置说明

当前项目**没有** `.env` 或 `.env.example`，核心配置通过 `gradle.properties` 管理。  
常用配置示例（节选）：

```properties
pluginName = HttpMate
pluginVersion = 1.0.0
platformType = IC
platformVersion = 2024.3.6
pluginSinceBuild = 243
```

插件运行后生成内容默认输出到项目目录：

```text
http-mate/
├─ <ClassName>.json
└─ docs/
   ├─ <ClassName>_<methodName>.md
   └─ <ClassName>.md
```

## 💡 使用示例 (Usage Examples)

### 1) 搜索并跳转 REST API

在 IDE 中按 `Ctrl + \`（或 `Ctrl + Alt + H`），输入例如 `g u s e r`（子序列匹配），选择结果后按 `Enter` 跳转到对应方法。

### 2) 生成 JSON / Mock JSON

在项目视图中右键某个 Java/Kotlin 类：

- `HttpMate -> Generate JSON`
- `HttpMate -> Generate Mock JSON`

生成文件示例（`http-mate/UserDTO.json`）：

```json
{
  "id": 0,
  "name": "",
  "tags": []
}
```

### 3) 生成 API 文档

在 Controller 方法或类上右键：

- `HttpMate -> Generate API Doc`

生成文档示例路径：

```text
http-mate/docs/UserController_getUser.md
http-mate/docs/UserController.md
```

## 📂 项目结构 (Project Structure)

```text
HttpMate/
├─ src/
│  ├─ main/
│  │  ├─ kotlin/com/github/jeraxxxxxxx/httpmate/
│  │  │  ├─ actions/      # 插件动作入口（搜索、JSON生成、文档生成）
│  │  │  ├─ services/     # 接口扫描与项目级服务
│  │  │  ├─ doc/          # Markdown 文档生成
│  │  │  ├─ generator/    # JSON / Mock JSON 生成器
│  │  │  ├─ ui/           # 搜索对话框与图标
│  │  │  ├─ model/        # 数据模型
│  │  │  └─ constants/    # 注解与通用常量
│  │  └─ resources/
│  │     ├─ META-INF/plugin.xml      # 插件声明与动作注册
│  │     └─ messages/MyBundle.properties
│  └─ test/
│     └─ kotlin/.../MyPluginTest.kt  # 核心行为测试
├─ build.gradle.kts
├─ gradle.properties
└─ CHANGELOG.md
```

## 🤝 贡献与许可 (Contributing & License)

欢迎提交 Issue 与 PR，一般流程如下：

1. Fork 仓库并创建分支
2. 完成功能或修复并补充测试
3. 本地运行 `./gradlew check` 与 `./gradlew verifyPlugin`
4. 提交 PR 并说明变更动机与影响范围

许可证（License）：**待补充**（仓库根目录当前未找到 `LICENSE` 文件）。
