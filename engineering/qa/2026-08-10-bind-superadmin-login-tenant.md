# superAdmin 登录租户绑定与轻量企业开户 QA（2026-08-10）

## 范围

OpenSpec Change：`2026-08-10-bind-superadmin-login-tenant` Task 1～3，lims 纯后端范围；未修改前端。

## 验收结果

- 普通用户仅能使用账号所属企业编码登录；`superAdmin` 使用唯一平台凭证可选择任一启用企业或监管机构登录。
- 所有成功会话均写入所选 `organizationId` 和 `orgType`；`superAdmin` 仅保留 `*` 功能权限，不再绕过当前请求的租户隔离。
- 缺少租户信息的旧会话返回 401；`superAdmin` 携带 `X-Target-Tenant-Id` 返回 403。
- 文件、素材等普通业务的查询和新增统一使用登录租户，`organization_id` 由租户上下文自动过滤或填充。
- 企业开户仅创建企业主数据、一个无部门管理员、管理员角色、角色菜单和本地登录凭证，不创建默认部门或业务数据。
- `us_sys_user.dept_id` 通过 migration 调整为可空；普通用户创建流程仍要求有效部门。

## 自动化验证

- `mvn -pl sts-service-admin -am test`：556/556 通过。
- `mvn -pl sts-service-admin -am -Pintegration-test test`：198/198 通过，覆盖真实 MySQL、租户 SQL、开户落库、可空部门
  migration 和 MinIO 相关回归。
- `jq empty docs/api/system-admin.openapi.json docs/api/org.openapi.json`：通过。
- `openspec validate 2026-08-10-bind-superadmin-login-tenant --strict`：通过。
- `git diff --check`：通过。

## Review

- 规格符合性：通过。实现严格覆盖已确认 Task 1～3，API 外形未变化，数据库变化有正式 migration 与契约说明。
- 代码质量：通过。平台主体读取仅在最小代码段显式跨租户；普通 HTTP 请求无 `superAdmin` 全局旁路；未发现越界重构或新增依赖。

## 部署注意

- 部署代码前先执行 `V20260810151000__allow_user_without_department.sql`。
- 已登录的旧 `superAdmin` Token 没有目标租户信息，升级后必须重新登录。
