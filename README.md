# HttpMate

![Build](https://github.com/EachFly/HttpMate/workflows/Build/badge.svg)

**HttpMate** is a powerful IntelliJ IDEA plugin designed to help developers quickly search and navigate to REST APIs within their projects. It supports Spring Boot and JAX-RS frameworks, offering a seamless experience similar to "Search Everywhere".

**HttpMate** 是一个强大的 IntelliJ IDEA 插件，旨在帮助开发者快速搜索并跳转到项目中的 REST API 定义。它支持 Spring Boot 和 JAX-RS 框架，提供类似 "Search Everywhere" 的流畅体验。

---

## Features / 功能特性

- **Quick Search / 快速搜索**:
  - Press `Alt + |` (or `Ctrl + Alt + H`) to open the search dialog.
  - 按下 `Alt + |` (或 `Ctrl + Alt + H`) 打开搜索对话框。
- **Framework Support / 框架支持**:
  - Supports Spring MVC (`@RequestMapping`, `@GetMapping`, etc.) and JAX-RS (`@Path`, `@GET`, etc.).
  - 支持 Spring MVC (`@RequestMapping`, `@GetMapping` 等) 和 JAX-RS (`@Path`, `@GET` 等)。
- **Robust Scanning / 鲁棒扫描**:
  - **PSI Scanning**: Accurate parsing based on IntelliJ's index.
  - **Fallback Scanning**: Regex-based scanning ensures APIs are found even if the project has compilation errors (red code).
  - **PSI 扫描**: 基于 IntelliJ 索引的精准解析。
  - **后备扫描**: 基于正则的文本扫描，确保即使项目代码爆红（编译错误）也能找到 API。
- **Visual Feedback / 视觉反馈**:
  - Colored icons for different HTTP methods (GET, POST, DELETE, etc.).
  - 为不同的 HTTP 方法（GET, POST, DELETE 等）提供彩色图标区分。
- **Navigation / 快速导航**:
  - Press `Enter` to jump directly to the API definition in the code.
  - 按 `Enter` 键直接跳转到代码中的 API 定义处。

---

<!-- Plugin description -->
## Development & Debugging / 开发与调试

If you want to contribute or debug the plugin locally:
如果您想在本地贡献代码或调试插件：

1. **Clone the repository / 克隆仓库**:

    ```bash
    git clone https://github.com/EachFly/HttpMate.git
    cd HttpMate
    ```

2. **Run the IDE / 运行 IDE**:
    Run the following command to start a sandboxed IntelliJ IDEA instance with the plugin installed:
    运行以下命令以启动安装了该插件的沙盒版 IntelliJ IDEA：

    ```bash
    ./gradlew runIde
    ```

    *Note: This may take some time on the first run as it downloads the IDE distribution.*
    *注意：首次运行可能需要一些时间，因为它会下载 IDE 发行版。*

---

## Packaging & Installation / 打包与安装

To build the plugin and install it into your daily IntelliJ IDEA:
要构建插件并将其安装到您日常使用的 IntelliJ IDEA 中：

1. **Build the Plugin / 构建插件**:

    ```bash
    ./gradlew buildPlugin
    ```

    The plugin distribution file will be generated at:
    插件发行包将生成于：
    `build/distributions/HttpMate-0.0.1.zip`

2. **Install from Disk / 从磁盘安装**:
    - Open IntelliJ IDEA **Settings** (`Ctrl + Alt + S`).
    - Go to **Plugins** -> **Gear Icon** ⚙️ -> **Install Plugin from Disk...**.
    - Select the generated `.zip` file.
    - Restart IDEA.
    - 打开 IntelliJ IDEA **设置** (`Ctrl + Alt + S`)。
    - 进入 **Plugins** -> **齿轮图标** ⚙️ -> **Install Plugin from Disk...**。
    - 选择生成的 `.zip` 文件。
    - 重启 IDEA。

<!-- Plugin description end -->

---

## Requirements / 环境要求

- JDK 17 or later
- IntelliJ IDEA 2023.1 or later

## License / 许可证

Licensed under the Apache License, Version 2.0.
