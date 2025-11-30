# API 文档生成功能指南

HttpMate 插件提供了强大的 API 文档自动生成功能,支持 Spring Boot 和 JAX-RS 框架。

---

## 🚀 快速开始

### 使用方式

#### 方式一: 生成单个方法的文档

1. 将光标放在 Controller 方法内
2. 右键点击 → 选择 **"Http-Mate Generate API Doc"**
3. 文档将生成在 `项目根目录/http-mate/docs/ClassName_methodName.md`

#### 方式二: 生成整个类的文档

1. 将光标放在 Controller 类名上
2. 右键点击 → 选择 **"Http-Mate Generate API Doc"**
3. 文档将生成在 `项目根目录/http-mate/docs/ClassName.md`
4. 包含该类所有带 REST 注解的公共方法

---

## ✨ 功能特点

### 1. 递归解析自定义类型

当参数类型是自定义类(如 `BaseQuery`)时,会自动展开显示其所有字段。

**示例代码**:

```java
@PostMapping("/query")
public Result query(BaseQuery query) {
    // ...
}
```

其中 `BaseQuery` 定义为:

```java
public class BaseQuery {
    private Integer pageNum;
    private Integer pageSize;
    private String keyword;
}
```

**生成的文档**:

| 参数名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| query | BaseQuery | 是 | (展开如下) |
| └─ pageNum | Integer | 否 | |
| └─ pageSize | Integer | 否 | |
| └─ keyword | String | 否 | |

### 2. 类级别批量生成

选中 Controller 类名,一键生成该类所有接口的文档。

**示例代码**:

```java
@RestController
@RequestMapping("/api/user")
public class UserController {
    
    @GetMapping("/{id}")
    public Result getUserById(@PathVariable Long id) {
        // ...
    }
    
    @PostMapping
    public Result createUser(@RequestBody UserDTO userDTO) {
        // ...
    }
    
    @PutMapping("/{id}")
    public Result updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        // ...
    }
}
```

**生成的文档结构**:

```markdown
# UserController 接口文档

**基础路径**: `/api/user`

---

## UserController - getUserById

### 接口信息
| 属性 | 值 |
| --- | --- |
| 接口名称 | getUserById |
| 请求方式 | GET |
| 接口路径 | `/api/user/{id}` |

### 请求参数
...

### 响应参数
...

### 请求示例
...

### 响应示例
...

---

## UserController - createUser

### 接口信息
...

(其他方法...)
```

### 3. 自动生成 Mock JSON 示例

利用内置的 `MockJsonGenerator`,为请求体和响应体自动生成带有模拟数据的 JSON 示例。

**示例**:

```json
{
  "pageNum": 1,
  "pageSize": 10,
  "keyword": "example_string_123"
}
```

---

## 📋 支持的注解

### Spring Boot

- `@GetMapping`
- `@PostMapping`
- `@PutMapping`
- `@DeleteMapping`
- `@PatchMapping`
- `@RequestMapping`

### JAX-RS

- `@GET`
- `@POST`
- `@PUT`
- `@DELETE`
- `@PATCH`
- `@Path`

---

## 📂 文档输出

### 输出位置

所有生成的文档保存在: **`项目根目录/http-mate/docs/`**

### 文件命名规则

- **单个方法**: `ClassName_methodName.md`
- **整个类**: `ClassName.md`

---

## 🎯 完整示例

### 输入代码

```java
@RestController
@RequestMapping("/api/order")
public class OrderController {
    
    @PostMapping("/create")
    public Result<OrderVO> createOrder(@RequestBody OrderCreateDTO dto) {
        // ...
    }
}
```

### 生成的文档

````markdown
# OrderController - createOrder

## 接口信息

| 属性 | 值 |
| --- | --- |
| 接口名称 | createOrder |
| 请求方式 | POST |
| 接口路径 | `/api/order/create` |

## 请求参数

| 参数名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| dto | OrderCreateDTO | 是 | (展开如下) |
| └─ productId | Long | 否 | |
| └─ quantity | Integer | 否 | |
| └─ userId | Long | 否 | |

## 响应参数

| 参数名称 | 类型 | 说明 |
| --- | --- | --- |
| orderId | Long | |
| orderNo | String | |
| createTime | LocalDateTime | |

## 请求示例

```json
{
  "productId": 123,
  "quantity": 1,
  "userId": 456
}
```

## 响应示例

```json
{
  "orderId": 789,
  "orderNo": "ORDER_20231130_001",
  "createTime": "2023-11-30T12:00:00"
}
```
````

---

## 💡 使用技巧

1. **批量生成**: 对于大型 Controller,使用类级别生成可以一次性生成所有接口文档
2. **增量更新**: 修改接口后,重新生成文档会覆盖原文件,保持文档与代码同步
3. **自定义类型**: 充分利用递归解析功能,复杂的嵌套对象也能清晰展示
4. **版本管理**: 建议将 `http-mate/docs/` 目录加入版本控制,方便团队协作

---

## 🔧 高级配置

### 自定义输出目录

目前输出目录固定为 `http-mate/docs/`,如需自定义,可以修改插件源码中的 `GenerateDocAction.kt`。

### 扩展支持的注解

如需支持其他框架的注解,可以在 `DocGenerator.kt` 和 `GenerateDocAction.kt` 中添加相应的注解识别逻辑。

---

## 📞 反馈与支持

如有问题或建议,欢迎在 [GitHub Issues](https://github.com/EachFly/HttpMate/issues) 提出。
