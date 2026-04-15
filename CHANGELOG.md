# HttpMate Changelog

## [1.1.0] - 2026-04-15

### Added / 新增

- **Code Line Statistics / 代码行统计**:
  - Added "Code Line Statistics" action under the HttpMate context menu in Project view.
  - 在 Project 视图的 HttpMate 右键菜单中新增了「Code Line Statistics」功能。
  - Right-click any directory or package to recursively analyze all source files.
  - 右键任意目录或 Package 即可递归分析其下所有源文件。
  - Counts total lines, code lines, comment lines (`//` and `/* */`), and blank lines with percentages.
  - 统计总行数、代码行数、注释行数（支持 `//` 和 `/* */`）、空行数及各部分百分比。
  - Results displayed in a custom dialog with summary panel, color-coded stacked bar chart, and sortable detail table grouped by file extension.
  - 结果以自定义弹窗展示，包含汇总面板、彩色比例条形图和按文件后缀分组的可排序明细表格。
  - Supports 25+ file extensions including `.java`, `.kt`, `.xml`, `.json`, `.py`, `.go`, `.rs`, etc.
  - 支持 25+ 种文件后缀，包括 `.java`、`.kt`、`.xml`、`.json`、`.py`、`.go`、`.rs` 等。
  - Background execution with progress bar and cancellation support.
  - 后台执行，带进度条和取消支持。

## [1.0.0] - 2026-03-22

### Fixed

- Refined action context resolution so class-level JSON generation also works for Java record files.
- Stabilized class-level API documentation generation on the first right-click invocation by committing documents before resolving PSI and reloading PSI from smart pointers in background tasks.
- Improved REST annotation detection to recognize annotations even when only short names are immediately available during PSI resolution.
- Replaced deprecated `Query.iterator()` usage in REST API scanning.
- Replaced deprecated `ReadAction.compute(ThrowableComputable)` calls with supported synchronous read actions.

### Improved

- Reworked REST API scanning to avoid one long read action by collecting candidates first and resolving them in batches.
- Removed PSI access from search result rendering hot paths by snapshotting display data up front.
- Moved JSON and API documentation generation off the EDT into background tasks.
- Cleaned up document generation output, restored readable section titles, and reduced field expansion noise to class-local instance fields.
- Added regression tests for REST scanning, Markdown generation, record-class context resolution, and method-priority action resolution.

## [0.0.16] - 2026-03-19

### Improved / 优化

- **Search Navigation / 搜索导航**:
  - Enhanced REST API search with fuzzy subsequence matching (e.g., typing `apiinfo` to match `/api/user/info`)
  - 优化 REST API 搜索功能，支持模糊子序列匹配（如输入 `apiinfo` 即可匹配 `/api/user/info`）
  - Added precise highlighting for fuzzy-matched characters in search results
  - 在搜索结果中支持对模糊匹配字符的精确高亮显示

## [0.0.15] - 2026-03-19

### Fixed / 修复

- **Thread Safety / 线程安全**:
  - Moved REST API scanning from EDT to background thread with progress indicator
  - 将 REST API 扫描从 EDT 移至后台线程并显示进度条
  - Fixed `PsiElement` memory leak by using `SmartPsiElementPointer` in `RestApiItem`
  - 使用 `SmartPsiElementPointer` 修复 `RestApiItem` 中的 `PsiElement` 内存泄漏
  - Moved file I/O operations in `GenerateDocAction` to pooled thread
  - 将 `GenerateDocAction` 中的文件 I/O 操作移至线程池

### Improved / 优化

- **Concurrency / 并发安全**:
  - Made `HttpMateProjectService` statistics thread-safe with `AtomicInteger` and `ConcurrentHashMap`
  - 使用 `AtomicInteger` 和 `ConcurrentHashMap` 使统计信息线程安全
  - Added `ReadAction` wrapper in search filter for safe PSI access
  - 在搜索过滤中添加 `ReadAction` 包装以安全访问 PSI

- **Performance / 性能**:
  - Cached `MethodIcon` instances in `RestApiIcons` to reduce GC pressure
  - 缓存 `MethodIcon` 实例以减少 GC 压力

- **Consistency / 一致性**:
  - Replaced hardcoded recursion depth in `DefaultJsonGenerator` with `AppConstants`
  - 将 `DefaultJsonGenerator` 中的硬编码递归深度替换为 `AppConstants`
  - Added `getActionUpdateThread()` override to `RestApiSearchAction`
  - 为 `RestApiSearchAction` 添加了 `getActionUpdateThread()` 重写

## [0.0.14] - 2026-03-18

### Dependencies / 依赖升级

- Upgraded `org.jetbrains.intellij.platform` from `2.12.0` to `2.13.1`
- Upgraded `org.jetbrains.kotlin.jvm` from `2.3.10` to `2.3.20`
- Upgraded `org.jetbrains.qodana` from `2025.3.1` to `2025.3.2`
- Upgraded `JetBrains/qodana-action` from `2025.3.1` to `2025.3.2`

## [0.0.13] - 2026-01-27

### Improved / 优化

- **Menu Structure / 菜单结构**:
  - Grouped all HttpMate features under a single "HttpMate" submenu in context menus
  - 将所有 HttpMate 功能聚合到右键菜单的 "HttpMate" 二级菜单中
  - Menu now appears at the top of the context menu (was at the bottom)
  - 菜单现在显示在右键菜单顶部（之前在底部）
  - Added HttpMate logo icon to the menu group
  - 为菜单组添加了 HttpMate logo 图标

- **Code Quality / 代码质量**:
  - Added KDoc documentation to public methods in `RestApiScanner`, `DocGenerator`, and `GenerateDocAction`
  - 为 `RestApiScanner`, `DocGenerator` 和 `GenerateDocAction` 的公共方法添加了 KDoc 文档
  - Refactored `scanFallback()` into smaller helper methods for better maintainability
  - 将 `scanFallback()` 重构为更小的辅助方法以提高可维护性
  - Extracted regex patterns to class-level properties
  - 将正则表达式模式提取为类级属性
  - Created centralized `Constants.kt` for REST annotations and app constants
  - 创建了集中管理 REST 注解和应用常量的 `Constants.kt`

## [0.0.12] - 2025-12-25

### Fixed / 修复

- **Compatibility / 兼容性**:
  - Fixed deprecated `Query.iterator()` usage in `RestApiScanner.scan()`
  - 修复了 `RestApiScanner.scan()` 中使用已弃用的 `Query.iterator()` 方法
  - Replaced with recommended `findAll()` method for better IntelliJ Platform compatibility
  - 替换为推荐的 `findAll()` 方法以提高 IntelliJ Platform 兼容性

- **Tests / 测试**:
  - Updated unit tests to use refactored `HttpMateProjectService`
  - 更新单元测试以使用重构后的 `HttpMateProjectService`
  - Replaced obsolete test methods with actual service functionality tests
  - 用实际服务功能测试替换了过时的测试方法

## [0.0.11] - 2025-12-17

### Dependencies / 依赖升级

- Upgraded Kotlin JVM plugin from 2.2.21 to 2.3.0
- Upgraded Kover plugin from 0.9.3 to 0.9.4
- Upgraded Qodana plugin from 2025.2.3 to 2025.2.4
- Upgraded GitHub Actions upload-artifact from v5 to v6
- Upgraded JetBrains/qodana-action from 2025.2.3 to 2025.2.4

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
