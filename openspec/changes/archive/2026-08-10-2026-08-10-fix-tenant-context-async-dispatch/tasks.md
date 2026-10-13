# Tasks：修复 Servlet 异步分派租户上下文丢失

> 状态：**已完成（2026-08-10）**
>
> Owner：lims
>
> 前后端影响：仅后端；前端以 OpenAPI 为准另开。

## Task 1：完成 ASYNC 分派租户上下文恢复与回归

- 状态：已完成
- Owner：lims
- 输入：TokenSession 登录租户；现有 `TenantContextFilter`、Sa-Token ASYNC 分派策略和素材媒体流式响应
- 输出：REQUEST/ASYNC 分派均绑定会话租户；每次分派结束清理上下文；相关自动化回归
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：无外部 API Contract 变化，不修改 `docs/api/*.openapi.json`
- 数据影响：无数据库结构、索引、migration 或种子变化
- 验收标准：
  - [x] 普通用户的 ASYNC 二次分派从 TokenSession 重新绑定登录租户
  - [x] `superAdmin` 的 ASYNC 二次分派绑定所选登录租户且不进入全局旁路
  - [x] ASYNC 分派访问 `sts_asset_transcode_job` 等租户表时不再因上下文缺失失败
  - [x] REQUEST 与 ASYNC 的正常、异常链路结束后均清理 ThreadLocal
  - [x] 未登录或缺少租户信息的异步分派失败关闭，不允许未隔离访问
  - [x] ERROR 分派策略不变，不覆盖原始异常
  - [x] Sa-Token ASYNC 二次分派不重复鉴权，现有媒体 Range/流式响应行为不变
  - [x] Admin 零 DB 全量测试通过，规格符合性与代码质量 Review 均为通过
- 测试方式：按 Red → Green → Refactor 修改 `TenantContextFilterTest`，必要时补充
  `SaTokenConfigTest` 或素材媒体 Web 测试；运行 `mvn -pl sts-service-admin -am test`；执行
  `openspec validate 2026-08-10-fix-tenant-context-async-dispatch --strict` 与 `git diff --check`

## 完成证据

- Red：新增 3 个 ASYNC 绑定/清理测试后，现有实现出现 1 个失败、2 个错误，均为租户上下文缺失。
- Green：`TenantContextFilterTest` 7/7、`SaTokenConfigTest` 8/8 通过。
- Admin 零 DB：561/561 通过，0 失败、0 错误、0 跳过。
- 无 Mapper/schema/API 变化，按设计不重复执行 MySQL IT。
- `openspec validate --strict` 与 `git diff --check` 通过。
- 规格符合性 Review：通过；代码质量 Review：通过。
