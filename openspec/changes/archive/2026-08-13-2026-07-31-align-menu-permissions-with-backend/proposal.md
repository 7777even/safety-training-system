# Proposal：对齐菜单权限与后端真实授权点

## 背景

`sts-service-admin` 的 Controller 使用 `@SaCheckPermission` 声明后端真实功能权限，`sts-service-admin-init` 的
`data/menus.json` 负责初始化菜单和按钮权限。审计发现后端共有 223 个注解权限点，菜单配置仅覆盖其中 195 个，缺少
28 个；同时菜单中存在 `sys:file:download`、`sys:operlog:export` 两个当前没有对应后端接口或权限注解的占位权限。

权限配置漂移会导致普通管理员即使具备对应业务职责，也无法通过标准 RBAC 获得部分后端能力；无真实接口的按钮权限则会误导权限配置人员。

## 目标

1. 使菜单按钮权限完整覆盖当前后端全部 `@SaCheckPermission` 权限点。
2. 移除没有真实后端能力支撑的占位菜单权限。
3. 同步空库初始化配置和既有数据库 migration，保证新旧环境权限语义一致。
4. 增加自动一致性测试，阻止后续 Controller 权限与菜单种子再次漂移。

## 用户价值

平台管理员可为普通管理员准确分配培训、问卷、评价、档案和证书能力，不再因菜单种子漏项造成“接口已实现但角色无法授权”；角色配置中也不再展示无对应后端能力的权限项。

## 功能范围

- 补齐 28 个已存在后端权限点对应的 `F` 类型菜单按钮。
- 将新增按钮挂载到现有培训计划、问卷管理、评价记录、培训档案和证书管理菜单下。
- 删除 `sys:file:download`、`sys:operlog:export` 两个无真实后端接口的占位按钮权限。
- 更新 `menus.json`，并增加正式 migration 同步存量数据库及默认 `admin` 角色授权。
- 增加菜单结构、权限覆盖、重复权限白名单和无效权限检测。

## 非目标范围

- 任何 `frontend/` 页面、Mock、联调或 UI 改动。
- 新增、删除或修改 Controller API、OpenAPI Contract。
- 新建角色、改变 `superAdmin` 特权旁路或改变数据权限范围。
- 为 `sys:file:download`、`sys:operlog:export` 新增后端接口。
- 修改其他业务规则或数据库表结构。

## 前后端影响

仅后端；前端以 OpenAPI 为准另开。
