# Design：系统管理 OpenAPI 契约校准

## 系统设计

OpenAPI 是前端调用的机器可读契约，Controller 是当前 HTTP 行为的实现依据。本次只将文档收敛到已实现行为，不修改后端路由。

## API 设计

### 基础路径

服务配置 `server.servlet.context-path=/api`，因此所有公开接口以 `/api` 开头。契约中不得出现 `/api/v1`；除非未来通过
OpenSpec 明确引入版本化路由并同步修改 Controller 或网关配置。

### 响应与分页

- 正常响应为 `Result<T>`：`code`、`message`、`data`。
- 分页接口的 `data` 为 `PageResult<T>`：`list`、`total`。
- 分页查询参数为 `pageNum`、`pageSize` 与 `query.<field>`；不得将业务筛选字段写为无前缀参数。
- 失败响应遵循 `docs/architecture/api-standard.md` 与 `global-exception.md`：参数错误 400/1001、未登录 401/1002、无权限
  403/1003、未知异常 500/9999；业务异常使用其实际 HTTP 状态和业务码。

### 资源覆盖

契约按 Controller 的资源分组覆盖认证、用户、角色、部门、菜单、字典类型/数据/目录、配置、缓存、操作日志、登录日志、在线用户和文件管理。每个操作必须声明路径参数、鉴权权限、请求与成功响应模型及示例。

## 权限设计

接口权限严格复用 Controller 上的 `@SaCheckPermission` 值；未标注该注解的接口在契约中明确标识为“暂不在 Controller
层声明权限”，不自行推导权限。

## 数据模型

模型由 Controller 的请求 DTO、响应 VO 与公共响应类型生成。缓存清理模型与 `CacheDomain`、`ClearScope` 当前枚举和值保持一致；不得描述
DTO 中不存在的 `key` 字段或不可用缓存域。

## 技术方案

1. 从 Controller 映射、DTO/VO、公共响应包装和枚举提取契约。
2. 用 JSON 语法与 OpenAPI 3.0 结构检查验证路径参数、引用和 schema。
3. 用 Controller 清单逐项核对路径、方法、权限、参数和响应模型。
4. 同步 API 协作指南中的契约入口说明；若仓库存在对应 Mock，则同步 Mock。

## 风险和取舍

- 当前 OpenAPI 覆盖面小于已实现 Controller，补全后文件规模会增加；取舍是完整性优先，避免前端遗漏接口。
- 部分 Controller 未声明权限注解；本次不创建权限模型，按实现如实记录。
- 暂定：是否将全部 52 个当前 Controller 操作一次性纳入同一 OpenAPI 文件，待确认后执行；默认建议全部覆盖，以符合该文件“系统管理模块
  API 契约”的定位。
