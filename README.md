# HttpMate

<p align="center">
  <img src="docs/images/logo.png" alt="HttpMate Logo" width="128" height="128" />
</p>

![Build](https://github.com/EachFly/HttpMate/workflows/Build/badge.svg)

<!-- Plugin description -->
## 📝 项目简介 (Project Overview)

**HttpMate** 是一个 IntelliJ IDEA 插件，用于在 Java/Kotlin 项目中提升 REST API 开发效率。  
它提供以下高频开发辅助功能：

- 快速检索并跳转 REST 接口定义（类似 IDE 内搜索即跳转体验）
- 基于类结构一键生成 JSON / Mock JSON
- 基于控制器方法自动生成 Markdown 接口文档
- 代码行统计：右键目录递归分析代码行、注释行、空行占比，含可视化图表
- 命名风格切换：选中文本一键在 camelCase / snake_case / PascalCase / SCREAMING_SNAKE / kebab-case 间转换

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
6. **代码行统计**
   - 右键任意目录或 Package 递归分析所有源文件的代码行、注释行、空行数及百分比
   - 支持 25+ 种文件后缀，结果含汇总面板、彩色条形图和可排序明细表
7. **命名风格切换**
   - 快捷键：`Ctrl + Shift + Alt + U`
   - 支持 camelCase、snake_case、SCREAMING_SNAKE_CASE、PascalCase、kebab-case 互转
   - 智能分词，可正确处理缩写词（如 `XMLParser` → `xml`, `parser`）
<!-- Plugin description end -->

## 🛠️ 技术栈 (Tech Stack)

| 类别 | 技术/库 | 版本 |
| --- | --- | --- |
| 语言 | Kotlin (JVM) | `2.4.0` |
| 运行时 | Java Toolchain | `21` |
| 构建工具 | Gradle Wrapper | `9.6.0` |
| IntelliJ 插件构建 | `org.jetbrains.intellij.platform` | `2.16.0` |
| 目标 IDE 平台 | IntelliJ IDEA Community (`IC`) | `2024.3.6` |
| 测试 | JUnit | `4.13.2` |
| 覆盖率 | Kover | `0.9.8` |
| 代码检查 | Qodana Gradle Plugin | `2026.1.3` |

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
pluginVersion = 1.2.9
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

### 4) 代码行统计

在项目视图中右键某个目录或 Package：

- `HttpMate -> Code Line Statistics`

弹出的统计窗口包含：
- 汇总面板（总行数、代码行、注释行、空行）
- 彩色比例条形图
- 按文件后缀分组的可排序明细表
- 按文件维度的详情表

### 5) 命名风格切换

选中编辑器中的文本，按 `Ctrl + Shift + Alt + U`（或右键 `HttpMate -> Toggle Case`），在弹出菜单中选择目标风格：

- `camelCase`
- `snake_case`
- `SCREAMING_SNAKE_CASE`
- `PascalCase`
- `kebab-case`

## 📂 项目结构 (Project Structure)

```text
HttpMate/
├─ src/
│  ├─ main/
│  │  ├─ kotlin/com/github/jeraxxxxxxx/httpmate/
│  │  │  ├─ HttpMateBundle.kt  # 国际化资源绑定
│  │  │  ├─ actions/      # 插件动作入口（搜索、JSON生成、文档生成、代码统计、命名切换）
│  │  │  ├─ services/     # 接口扫描与项目级服务
│  │  │  ├─ doc/          # Markdown 文档生成
│  │  │  ├─ generator/    # JSON / Mock JSON 生成器
│  │  │  ├─ ui/           # 搜索对话框与图标
│  │  │  ├─ model/        # 数据模型
│  │  │  └─ constants/    # 注解与通用常量
│  │  └─ resources/
│  │     ├─ META-INF/plugin.xml      # 插件声明与动作注册
│  │     ├─ icons/                    # SVG 图标资源
│  │     └─ messages/MyBundle.properties
│  └─ test/
│     ├─ kotlin/com/.../             # 单元测试
│     └─ testData/                   # 测试数据文件
├─ build.gradle.kts
├─ gradle.properties
└─ CHANGELOG.md
```

## 发布草稿说明 (Release Drafts)

构建流程会创建或更新当前版本的自动 Release 草稿，读取对应 Changelog，并将目标更新为实际构建的提交 SHA。
不修改版本号时，新构建仍可刷新同版本草稿的说明和插件包；只有草稿已发布后才需要升级版本号。
已发布的正式版／预发行版、手工创建的草稿及没有生成区块标记的旧草稿会保留并提示跳过。
自动更新仅替换 `<!-- httpmate:changelog:start -->` 与 `<!-- httpmate:changelog:end -->` 之间的内容，
保留区块外的手工说明，并同步构建 SHA 标记。请不要删除这些标记。

草稿附件采用“原包名 + 提交编号 + 内容摘要”的唯一名称；先上传新 ZIP，再更新说明中的当前构建附件清单。
旧 ZIP 保留为历史构建，不使用 `--clobber` 或删除后重传，避免上传失败导致旧包丢失。
写入前会重新检查草稿状态及人工可编辑的信息；发现发布或编辑冲突则停止，可能留下未被清单引用的新附件，
但不会自动回滚、删除附件或将正式版改回草稿。GitHub API 不提供原子的“仅在仍为草稿时更新”操作，
最后一次检查与写入之间仍有并发窗口；请等 Build 完成后再点击 Publish release，不要在更新期间编辑或发布草稿。
如果同名 Git 标签已存在但指向其他提交，构建会拒绝更新草稿，不会自动移动标签。

正式版和从草稿发布的预发行版均通过 `release.published` 触发插件发布。
Release 标签必须与检出代码的 `pluginVersion` 一致；自动草稿的构建 SHA 标记也会在上传 Marketplace 前校验，
请保留该标记。手工／历史 Release 缺少标记时会提示无法验证构建来源。附件上传不删除旧文件：相同名称、相同摘要的
附件会复用，不同内容或无法验证摘要的附件会报错并保持原文件不变。

发布脚本的本地回归测试：`node --test .github/scripts/*.test.mjs`（只模拟调用，不连接 GitHub）。

文档校验规则支持参数上的 `@Validated` 分组（含分组继承）及字段/getter 约束；组序列、方法级校验或
级联分组转换等无法静态确定的情形使用“条件必填”提示，不将其误写为无条件必填。
JAX-RS 子资源按可静态确定的返回类型拼接路径，动态返回子类型的路由无法完整推断。

## 🤝 贡献与许可 (Contributing & License)

欢迎提交 Issue 与 PR，一般流程如下：

1. Fork 仓库并创建分支
2. 完成功能或修复并补充测试
3. 本地运行 `./gradlew check` 与 `./gradlew verifyPlugin`
4. 提交 PR 并说明变更动机与影响范围

许可证（License）：本项目基于 [MIT License](LICENSE) 开源。
