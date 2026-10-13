# Spec Delta：系统管理 API 契约

## MODIFIED

### 系统管理 HTTP API 基础路径

系统管理服务公开 HTTP API 的基础路径为 `/api`。OpenAPI 中的每个路径必须以 `/api` 开头；`/api/v1` 不是当前服务的有效路径。

### 系统管理 API 契约完整性

`docs/api/system-admin.openapi.json` 必须成为现有认证与系统管理 Controller 的机器可读契约。每个已公开操作必须声明：

- URL 与 HTTP Method；
- 请求参数、请求体、必填性和示例；
- `Result<T>` 成功响应与适用的 `PageResult<T>` 分页结构；
- 错误码和 HTTP 状态；
- Controller 实际声明的权限要求。

分页筛选字段必须使用 `query.<field>` 参数名。契约中的枚举和值必须与当前后端 DTO 和枚举定义一致。
