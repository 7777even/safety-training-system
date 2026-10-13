# Proposal：修复 Servlet 异步分派租户上下文丢失

## 背景

登录成功后，目标企业已经写入 Sa-Token TokenSession。普通 `REQUEST` 分派进入
`TenantContextFilter` 时会把该企业绑定到 `TenantContext`，并在请求结束后清理 ThreadLocal。

素材媒体使用 `StreamingResponseBody`，Servlet 会对同一个请求执行 `ASYNC` 二次分派。Sa-Token
上下文过滤器和鉴权拦截器已经支持该分派，但 `TenantContextFilter` 继承
`OncePerRequestFilter` 后仍使用“跳过 ASYNC 分派”的默认行为。二次分派因此可以继续执行，
却没有重新从 TokenSession 绑定租户；一旦响应处理、转换或扩展逻辑访问
`sts_asset_transcode_job` 等租户表，就会被 `TenantLineHandlerImpl` 拒绝。

## 目标

- 每次已认证的 Servlet `ASYNC` 分派都从 TokenSession 重新绑定登录租户。
- `superAdmin` 与普通用户使用同一规则，不恢复任何全局租户旁路。
- 每次分派结束或异常后清理 ThreadLocal，避免容器线程复用导致串租户。
- 保持 `ERROR` 分派只负责呈现原始异常，不引入二次数据库访问或覆盖第一现场。

## 用户价值

- 素材上传后的转码任务查询、媒体预览和 Range 流式响应不再随机出现“租户上下文缺失”。
- 登录企业仍是整个请求生命周期的数据租户，异步二次分派不会扩大或丢失数据边界。

## 功能范围

- 调整 `TenantContextFilter` 的 Servlet 分派策略，使其处理 `REQUEST` 与 `ASYNC`。
- 增加零 DB 测试，覆盖 ASYNC 重新绑定、请求结束清理和租户身份一致性。
- 回归 Sa-Token ASYNC 二次鉴权策略及素材转码任务查询。

## 非目标范围

- 不修改登录参数、Token 格式、外部 API Contract 或错误码。
- 不修改数据库结构、转码调度线程模型或转码供应商。
- 不修改前端；前端以现有 OpenAPI 为准。
- 不让 `ERROR` 分派进入业务租户查询，也不允许无登录态异步分派访问租户表。
