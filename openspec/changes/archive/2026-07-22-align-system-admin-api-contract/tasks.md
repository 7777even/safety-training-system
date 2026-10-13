# Tasks：校准系统管理 API 契约

## Task 1：完成系统管理 API 的可调用契约

- **Owner**: AI
- **输入**: 已确认的 `/api` 基础路径、现有认证与系统管理 Controller、DTO/VO、`Result`/`PageResult` 规范
- **输出**: 完整的 `docs/api/system-admin.openapi.json`，路径、方法、参数、请求体、响应、权限、错误码和示例均与现有实现一致
- **前后端影响**: 前端可按 JSON 生成请求和 Mock；后端无代码变更
- **API 依赖**: `server.servlet.context-path=/api`、Controller 映射、`docs/architecture/api-standard.md`
- **数据影响**: 无
- **验收标准**:
  - 所有公开路径以 `/api` 开头，不存在 `/api/v1`
  - OpenAPI 覆盖现有认证和系统管理 Controller 操作
  - 每个路径参数均已定义，分页字段使用 `query.<field>`
  - 缓存域和清理范围与当前 DTO、枚举一致
- **测试方式**: JSON/OpenAPI 结构检查；逐项对照 Controller 映射与 DTO/VO 字段
- **状态**: 进行中

## Task 2：完成契约发布前审查记录

- **Owner**: AI
- **输入**: Task 1 产出、Controller 对照表
- **输出**: `engineering/qa/` API 契约核对记录与 `engineering/retro/` 复盘记录
- **前后端影响**: 为前后端联调提供可追溯的核验结论；无运行时影响
- **API 依赖**: Task 1 的 OpenAPI JSON
- **数据影响**: 无
- **验收标准**:
  - 记录路径、方法、参数、响应和权限的核对结论
  - 明确未能从代码确认的事项
- **测试方式**: 文档审阅
- **状态**: 待确认
