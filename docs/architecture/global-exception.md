# 全局异常与 HTTP 状态契约（Spring Boot HTTP 模块）

> 本文由模板契约改写入本仓库知识库，已对齐 `sts-*` 模块与 `com.sts` 包名；已剔除 IoT/设备/驱动相关表述。

> **分类**：**架构功能点** — 与业务模块无关。  
> **联动**：`api-standard.md`（`Result<T>` 体结构）；本文件规定 **HTTP Status** 与异常映射。  

## 设计原则

1. **以 HTTP Status 表达错误语义**，使用 Spring MVC 标准能力（`@RestControllerAdvice`、`ResponseEntity`、
   `ResponseStatusException`）。
2. **禁止**「一律 `HTTP 200` + 仅靠 body `code` 区分成败」；禁止把 `403` 映射成 `200` + `code=1003`。
3. 成功/失败 body 均为 **`Result<T>`**（定义在 **`sts-common`**，admin 与 driver 共用）；客户端**优先看 HTTP Status**，body
   `code` 为业务细分码（见 `api-standard.md` 分段）。

## 模块范围

| 模块                       | HTTP 用途                           | 全局 Advice 扫描包             | 鉴权                                |
|--------------------------|-----------------------------------|---------------------------|-----------------------------------|
| **（已移除驱动模块）/\*** | 驱动**运维/管理** API（连接测试、采集状态、配置热更新等） | `com.sts.driver` | 无北向 RBAC；可按需内网 Token / 网关鉴权       |

`module-boundary.md`）。

## 共享类型（sts-common）

```text
com.sts.common
├── api.Result<T>                    # 与 api-standard.md 一致
└── exception.BusinessException      # 须携带 HttpStatus + int code + message
```

```java
// 推荐：Spring 标准

// 或统一业务异常（driver 业务码 4000–4999）
throw new

BusinessException(HttpStatus.SERVICE_UNAVAILABLE, 4001,"驱动未连接");
```

## GlobalExceptionHandler 约定

### 方式 A（推荐）：`（已移除驱动模块）` 提供统一 Advice


```java

@RestControllerAdvice(basePackages = {
        "com.sts.admin",
        "com.sts.driver"
})
public class HttpApiExceptionHandler {
    // 见下方「标准映射」
}
```

各 Boot 应用通过 `@Import` 或组件扫描引入，**禁止**在各协议模块重复拷贝 Handler 逻辑。

### 方式 B：分模块 Advice

- `AdminGlobalExceptionHandler` → `basePackages = "com.sts.admin"`
- `DriverGlobalExceptionHandler` → `basePackages = "com.sts.driver"`

两类的 **`ResponseEntity<Result<T>>` 映射表必须一致**（与下表相同）。

### 标准映射（admin / driver 共用）

| 场景               | HTTP Status    | body `Result.code`（示例）                      |
|------------------|----------------|---------------------------------------------|
| 成功               | **200**        | `0`                                         |
| 参数校验失败           | **400**        | `1001`                                      |
| 请求体不可读           | **400**        | `1001`                                      |
| 未登录（仅 admin 常见）  | **401**        | `1002`                                      |
| 权限不足（仅 admin 常见） | **403**        | `1003`                                      |
| 资源不存在            | **404**        | 模块业务码                                       |
| 冲突               | **409**        | 模块业务码                                       |
| 可预期业务错误          | **4xx**（与语义一致） | `2000–2999`（admin）/ **`4000–4999`（driver）** |
| 未捕获异常            | **500**        | `9999`                                      |

> 400/503）。

## RBAC 与 Sa-Token（仅 admin）

- `@RequiresPermissions("sys:user:delete")`；失败 **HTTP 403** + `Result.fail(1003, ...)`。
- driver **不使用** 北向菜单权限串；不得复用 `sys:*` 权限模型。

## 强制规则

1. **禁止** Controller / Service 内 `try-catch` 后自行拼装非 `Result` JSON。
2. **禁止** engine 模块引入本 Advice。
4. admin 的 CUD 须 `@Log`（见 `audit-log.md`）。

## 实现示例（统一 Handler）

```java

@RestControllerAdvice(basePackages = {
        "com.sts.admin",
        "com.sts.driver"
})
public class HttpApiExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(Result.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(1001, msg));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Result.fail(1003, "权限不足"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(9999, "系统内部错误"));
    }
}
```

```java
// admin
throw new BusinessException(HttpStatus.CONFLICT, 2003,"用户名已被注册");

// driver
throw new

```
