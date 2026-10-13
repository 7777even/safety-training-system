# Design：Servlet 异步分派租户上下文恢复

## 1. 根因

当前请求存在两种独立状态：

1. TokenSession 中持久保存 `organizationId`、`orgType` 和 `superAdmin`；
2. `TenantContext` 使用 ThreadLocal 保存当前执行线程的数据租户。

第一次 `REQUEST` 分派的执行顺序为：

```text
SaTokenContextFilter
  → TenantContextFilter：TokenSession.organizationId → TenantContext
    → MVC / Service / MyBatis
  → finally 清理 TenantContext
```

流式响应启动异步处理后，容器会产生 `ASYNC` 二次分派。Sa-Token 过滤器已经显式注册
`DispatcherType.ASYNC`，鉴权拦截器也会识别同一请求已完成首次鉴权；但
`TenantContextFilter` 使用 `OncePerRequestFilter.shouldNotFilterAsyncDispatch()` 的默认值，
所以不执行 `bindTenant()`。ThreadLocal 已在第一次分派结束时清理，二次分派访问租户表必然失败。

## 2. 修复设计

`TenantContextFilter` MUST 处理 `REQUEST` 和 `ASYNC` 两种分派：

```text
REQUEST → 从 TokenSession 绑定租户 → 执行业务 → finally 清理
ASYNC   → 从 TokenSession 重新绑定租户 → 处理异步结果 → finally 清理
ERROR   → 保持跳过，呈现原始异常
```

实现采用 `OncePerRequestFilter` 提供的 ASYNC 扩展点，使
`shouldNotFilterAsyncDispatch()` 返回 `false`。如果运行期自动注册不能稳定覆盖 ASYNC，
则在同一 Task 内为 `TenantContextFilter` 增加明确的 Filter 注册并限定
`REQUEST`、`ASYNC` 分派；不得注册两次或改变其位于 Sa-Token 上下文过滤器之后的顺序。

每次分派仍必须独立执行现有 `finally { TenantContext.clear(); }`。不得把第一次分派的
ThreadLocal 跨线程直接保留，也不得以 `InheritableThreadLocal` 代替重新从 TokenSession 解析。

## 3. 身份与权限规则

- 普通用户：ASYNC 分派绑定 TokenSession 中的登录租户。
- `superAdmin`：ASYNC 分派同样绑定所选登录租户，只保留 `*` 功能权限。
- 监管只读视角：若同一异步请求携带已验证的目标租户头，则按现有规则重新解析并绑定目标租户；不新增请求头语义。
- 未登录或缺少租户的会话：沿用现有失败关闭规则，不进入租户 Mapper。

## 4. API 与数据影响

- API URL、Method、请求字段、响应字段和错误码均不变化，因此不修改 `docs/api/*.openapi.json`。
- 无数据库结构、索引、数据修复或 seed 变化。
- 无新增生产依赖。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。

## 5. 风险与取舍

### 5.1 重复过滤

风险：错误的 Filter 注册可能让同一分派执行两次。

控制：优先使用 `OncePerRequestFilter` 的标准 ASYNC 扩展点；若需要显式注册，测试注册顺序与
DispatcherType，并确认单次分派只执行一次。

### 5.2 ThreadLocal 泄漏

风险：异步线程来自容器线程池，遗漏清理会造成后续请求串租户。

控制：测试正常和异常链路结束后 `TenantContext.isPresent()` 均为 false。

### 5.3 ERROR 分派覆盖第一现场

风险：错误分派重新绑定并进入业务逻辑可能产生第二个异常。

控制：保持 `shouldNotFilterErrorDispatch()` 的默认跳过行为；Sa-Token 的 ERROR 策略不变。

## 6. TDD 与验证

1. Red：在 `TenantContextFilterTest` 构造 `DispatcherType.ASYNC` 请求，证明当前 Filter 跳过绑定，
   业务链读取不到会话租户。
2. Green：以最小修改启用 ASYNC 分派，证明业务链读到正确租户，且 `superAdmin` 不处于旁路。
3. 回归：覆盖普通用户、`superAdmin`、正常返回、异常返回及结束清理。
4. 运行 Admin 零 DB 全量测试；若实现只改变 Filter 分派行为且不涉及 Mapper/schema，则不新增重复的 MySQL IT。
5. 复用现有素材媒体控制器与 Sa-Token 测试，确认流式响应的 ASYNC 二次分派不重复鉴权且租户上下文存在。
