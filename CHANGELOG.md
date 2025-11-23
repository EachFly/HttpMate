<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# HttpMate Changelog

## [0.0.1] - 2025-11-23

### Added / 新增

- **REST API Search / REST API 搜索**:
  - Quickly search for REST APIs using `Alt + |` (or `Ctrl + Alt + H`).
  - 使用 `Alt + |` (或 `Ctrl + Alt + H`) 快速搜索 REST API。
- **Framework Support / 框架支持**:
  - Full support for Spring Boot (`@RequestMapping`, `@GetMapping`, etc.) and JAX-RS (`@Path`, `@GET`, etc.).
  - 全面支持 Spring Boot 和 JAX-RS 注解。
- **Fallback Scanning / 后备扫描**:
  - Implemented a regex-based fallback scanner to find APIs even when the project has compilation errors.
  - 实现了基于正则的后备扫描机制，即使项目编译报错也能找到 API。
- **Visual Enhancements / 视觉增强**:
  - Added colored icons to distinguish different HTTP methods (e.g., Blue for GET, Green for POST).
  - 添加了彩色图标以区分不同的 HTTP 方法（如 GET 为蓝色，POST 为绿色）。
- **Navigation / 导航**:
  - Press `Enter` to navigate directly to the API definition.
  - 按 `Enter` 键直接跳转到 API 定义处。

### Fixed / 修复

- **Runtime Errors / 运行时错误**:
  - Fixed `SecurityException: setContextClassLoader` by optimizing JVM arguments.
  - 通过优化 JVM 参数修复了 `SecurityException` 报错。
- **Encoding / 编码**:
  - Fixed console character encoding issues (garbled text).
  - 修复了控制台输出乱码问题。
- **Shortcuts / 快捷键**:
  - Fixed shortcut conflicts and invalid keymap definitions in `plugin.xml`.
  - 修复了快捷键冲突和 `plugin.xml` 中的无效定义。

## [Unreleased]

### Added

- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
