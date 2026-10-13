# Proposal：校准系统管理 API 契约

## 背景

`docs/api/system-admin.openapi.json` 是前端开发的接口依据，但其路径、请求参数和部分模型与现有 `sts-service-admin`
Controller 不一致。特别是契约使用 `/api/v1` 前缀，而服务实际公开前缀为 `/api`，会导致前端请求无法路由到后端。

## 目标

将系统管理 OpenAPI 契约校准为现有 Controller 的真实 HTTP 行为，并明确 `/api` 为唯一 API 基础路径，使前端可以据此生成调用与
Mock。

## 用户价值

- 前端可使用唯一、可执行的 API 契约开发，不再依赖代码猜测路径和字段。
- 前后端对分页、响应包装、鉴权和错误响应形成一致约定。
- 减少联调阶段因接口文档漂移造成的返工和阻塞。

## 功能范围

- 统一 OpenAPI 路径为 `/api` 前缀，并记录服务地址约定。
- 为现有系统管理与认证 Controller 补全或校准 URL、方法、参数、请求体、响应体、权限、错误码和示例。
- 以现有后端 DTO、VO、`Result<T>` 和 `PageResult<T>` 为契约依据，校准模型字段与分页参数。
- 保留机器可读 JSON 契约在 `docs/api/`。

## 非目标范围

- 不修改 Controller、Service、DTO、VO、权限模型或数据库结构。
- 不新增业务功能，不改变既有接口的实际运行行为。
- 不创建或修改前端业务页面；Mock 调整仅在已有 Mock 资产存在时同步。
