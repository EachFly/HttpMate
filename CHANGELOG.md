# HttpMate Changelog

## [0.0.10] - 2025-12-04

### Added / 新增

- **Project Service / 项目服务**:
  - Refactored `MyProjectService` to `HttpMateProjectService` with enhanced functionality.
  - 将 `MyProjectService` 重构为 `HttpMateProjectService`,增强了功能。
  - Added configuration management (output directory, auto-open, etc.).
  - 添加了配置管理(输出目录、自动打开等)。
  - Added generation statistics tracking (total docs, last generation time, file list).
  - 添加了生成统计跟踪(总文档数、最后生成时间、文件列表)。
  - Centralized document output path management.
  - 集中管理文档输出路径。

### Changed / 变更

- **Keyboard Shortcuts / 快捷键**:
  - Changed REST API search shortcut from `Alt + |` to `Ctrl + \`.
  - 将 REST API 搜索快捷键从 `Alt + |` 改为 `Ctrl + \`。
  - `Ctrl + Alt + H` remains as alternative shortcut.
  - `Ctrl + Alt + H` 保持为备用快捷键。

### Improved / 改进

- Updated all documentation (README, plugin description) to reflect new shortcuts.
- 更新了所有文档(README、插件描述)以反映新的快捷键。
- Integrated project service into document generation workflow.
- 将项目服务集成到文档生成工作流中。

## [0.0.9] - 2025-12-01

### Added / 新增

- **Validation Annotation Support / 验证注解支持**:
  - Added "Length" column to request parameters table.
  - 为请求参数表格添加了"长度"列。
  - Extract length constraints from validation annotations (@Size, @Length, @Min, @Max, @DecimalMin, @DecimalMax, @Pattern).
  - 从验证注解中提取长度约束信息（@Size, @Length, @Min, @Max, @DecimalMin, @DecimalMax, @Pattern）。
  - Support both javax.validation and jakarta.validation packages.
  - 同时支持 javax.validation 和 jakarta.validation 包。

- **Nested Type Independent Tables / 嵌套类型独立表格**:
  - Nested custom types now display as independent tables instead of tree structure.
  - 嵌套的自定义类型现在以独立表格形式显示,而不是树形结构。
  - Support up to 3 levels of nesting with recursive processing.
  - 支持最多3层嵌套,采用递归处理。
  - Each nested type has its own table with type name header.
  - 每个嵌套类型都有独立的表格和类型名称标题。

### Fixed / 修复

- **Duplicate Notifications / 重复通知**:
  - Fixed duplicate success notifications when generating class-level documentation.
  - 修复了生成类级别文档时出现两次成功通知的问题。
  - Now shows single notification with method count and file path.
  - 现在只显示一次通知,包含方法数量和文件路径。

### Improved / 改进

- Added `bin/` directory to .gitignore to prevent tracking IDE output.
- 将 `bin/` 目录添加到 .gitignore,防止跟踪 IDE 输出。

## [0.0.8] - 2025-11-30

### Added / 新增

- **API Documentation / 接口文档**:
  - Added "Http-Mate Generate API Doc" action to generate Markdown documentation for Controller methods.
  - 添加了 "Http-Mate Generate API Doc" 功能，可为 Controller 方法生成 Markdown 格式的接口文档。

## [0.0.7] - 2025-11-27

### Added / 新增

- **Plugin Icon / 插件图标**:
  - Added a brand new plugin icon and logo (resized to standard 40x40/80x80).
  - 添加了全新的插件图标和 Logo（已调整为标准的 40x40/80x80 尺寸）。

### Fixed / 修复

- **Metadata / 元数据**:
  - Added project URL to the plugin overview.
  - 在插件概览中添加了项目地址。

## [0.0.6] - 2025-11-26

### Added / 新增

- **Mock JSON Generation / Mock JSON 生成**:
  - Added "Http-Mate Generate Mock JSON" action to generate JSON with random test data (Strings, Numbers, Dates, Collections).
  - 添加了 "Http-Mate Generate Mock JSON" 功能，可生成包含随机测试数据的 JSON 文件。

## [0.0.5] - 2025-11-26

### Improved / 优化

- **Search Performance / 搜索性能**:
  - Limited search results to top 50 items to prevent UI lag with large datasets (e.g., 1000+ APIs).
  - 限制搜索结果仅显示前 50 条，以解决大量数据下的 UI 卡顿问题。
- **User Experience / 用户体验**:
  - Typing in the search list now automatically refocuses to the search field.
  - 在搜索结果列表中输入字符会自动将焦点切回搜索框。

### Fixed / 修复

- **JSON Generation / JSON 生成**:
  - Fixed `BigDecimal` generating as an object. Now generates as a number (e.g., `0.0`).
  - 修复了 `BigDecimal` 生成为对象的问题，现在生成为数字类型（如 `0.0`）。

## [0.0.4] - 2025-11-25

### Added / 新增

- **JSON Generation / JSON 生成**:
  - Added "Http-Mate Generate JSON" action to generate JSON files for Java classes.
  - 添加了 "Http-Mate Generate JSON" 功能，可为 Java 类生成 JSON 文件。
- **API Count Display / API 数量显示**:
  - Added a status label showing found/total API count in the search dialog.
  - 在搜索弹窗中添加了 API 数量统计显示。

### Improved / 优化

- **Search Performance / 搜索性能**:
  - Implemented debounce and asynchronous filtering for smoother search experience.
  - 实现了防抖和异步过滤，搜索体验更流畅。
- **User Experience / 用户体验**:
  - Supported `ESC` key to close the search dialog.
  - 支持使用 `ESC` 键关闭搜索弹窗。

### Fixed / 修复

- **JSON Generation Logic / JSON 生成逻辑**:
  - Fixed primitive types (e.g., Integer) generating as objects.
  - 修复了基本类型生成为对象的问题。
  - Fixed Date/Time formatting to use current time string.
  - 修复了日期时间格式化问题，现在生成当前时间字符串。
- **Search Functionality / 搜索功能**:
  - Fixed search result updates in modal dialogs.
  - 修复了模态弹窗中搜索结果更新的问题。

## [0.0.3] - 2025-11-24

### Added / 新增

- **JSON Generation / JSON 生成**:
  - Added "Http-Mate Generate JSON" action.
  - 添加了 "Http-Mate Generate JSON" 功能。

## [0.0.2] - 2025-11-24

### Added / 新增

- **Search Highlighting / 搜索高亮**:
  - Keywords in search results are now highlighted in **bold blue** for easier identification.
  - 搜索结果中的关键词现在会以**蓝色粗体**高亮显示，便于快速识别。
- **Dialog Size Persistence / 弹窗尺寸记忆**:
  - The plugin now remembers the size of the search dialog after you resize it.
  - 插件现在会记住你调整后的搜索弹窗大小。

### Fixed / 修复

- **JAX-RS Support / JAX-RS 支持**:
  - Fixed an issue where JAX-RS methods were incorrectly identified as "ALL". Now correctly parses `@GET`, `@POST`, etc.
  - 修复了 JAX-RS 方法被错误识别为 "ALL" 的问题。现在能正确解析 `@GET`, `@POST` 等注解。

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
