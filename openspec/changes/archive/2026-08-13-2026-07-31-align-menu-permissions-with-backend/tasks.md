# Tasks：对齐菜单权限与后端真实授权点

前后端影响：仅后端；前端以 OpenAPI 为准另开。Owner：lims

## Task 1：完成菜单权限目录与后端授权点一致性

- 状态：已完成
- Owner：lims
- 输入：
  - `backend/sts-service-admin/src/main/java/**` 中的 `@SaCheckPermission`
  - `backend/sts-service-admin-init/src/main/resources/data/menus.json`
  - `backend/sql/schema-system.sql`
  - `docs/architecture/super-admin-bypass.md`
- 输出：
  - 补齐 28 个真实后端权限点的菜单按钮种子
  - 删除 2 个无后端能力支撑的占位权限
  - 同步既有数据库的正式 migration
  - 权限/菜单一致性自动化测试与 QA / Retro 记录
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：无 API Contract 变化；以现有 Controller 权限注解为输入
- 数据影响：
  - `us_sys_menu` 新增 28 个 `F` 类型记录、删除 2 个占位记录
  - `us_sys_role_menu` 为默认 `admin` 角色补充新增权限并移除占位关联
  - 不修改表结构，不自动扩张其他自定义角色权限
- 验收标准：
  1. 后端全部 223 个 `@SaCheckPermission` 权限均至少有一个菜单配置。
  2. `menus.json` 不再包含没有后端能力的 `sys:file:download`、`sys:operlog:export`。
  3. 菜单 ID 唯一、父节点完整、模块归属正确，既有菜单 ID 不变。
  4. migration 与 `menus.json` 对新增/删除权限完全一致，且可安全作用于已有数据库。
  5. 默认 `admin` 获得全部新增菜单权限；`superAdmin` 继续使用硬编码 `*`，不建立角色绑定。
  6. 不修改 Controller、OpenAPI、前端、权限模型或数据库结构。
  7. 规格符合性与代码质量双轴 Review 结论均为“通过”。
- 测试方式：
  - 先增加权限覆盖测试并观察当前 28 个缺失和 2 个无效配置导致失败。
  - 更新种子与 migration 后运行 `sts-service-admin-init` 目标单测。
  - 运行 Admin 模块相关零 DB 测试。
  - 使用 MySQL Testcontainers 验证 migration 后菜单及 `admin` 角色关联；Docker 不可用时如实报告。
  - 执行 `openspec validate 2026-07-31-align-menu-permissions-with-backend --strict` 与 `git diff --check`。

- [x] 固化当前权限审计为失败测试（Red）
- [x] 补齐 `menus.json` 并移除无效占位权限（Green）
- [x] 新增正式 migration，同步存量菜单与默认 `admin` 授权
- [x] 运行目标单测、Admin 零 DB 测试；MySQL IT 因 Docker socket 不可用未执行
- [x] 完成规格符合性与代码质量双轴 Review
- [x] 更新 Task 状态并记录 QA / Retro
