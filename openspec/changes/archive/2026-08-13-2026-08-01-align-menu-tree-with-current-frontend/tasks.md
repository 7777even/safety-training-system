# Tasks：按当前前端菜单重排后端菜单树

前后端影响：仅后端；前端以 OpenAPI 为准另开。Owner：lims

## Task 1：完成菜单树转换与存量数据同步

- 状态：已完成
- Owner：lims
- 输入：
  - `backend/sts-service-admin-init/src/main/resources/data/current-online-menu-tree.json`
  - `backend/sts-service-admin-init/src/main/resources/data/menus.json`
  - 后端 Controller `@SaCheckPermission`
  - `us_sys_menu`、`us_sys_role_menu` 现有结构及菜单 migration
- 输出：
  - 按前端展示树重排的 `menus.json`
  - 同步已有数据库的正式 migration
  - 菜单转换、权限一致性和 migration 自动化测试
  - QA / Retro 与进度记录
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：无 API Contract 变化
- 数据影响：更新、增加或删除 `us_sys_menu` 记录并同步必要的 `us_sys_role_menu` 关系；不改变表结构
- 验收标准：
  1. D/M/C 节点的层级、名称、路径、组件、图标和排序与前端输入一致。
  2. 输出为初始化程序可直接消费的扁平结构，ID 唯一、父节点完整且根 `moduleId` 正确。
  3. 全部 Controller 权限均存在，菜单中不存在无后端能力的权限。
  4. 可匹配节点复用既有稳定 ID，自定义角色的既有 F 权限授权不因重排丢失。
  5. migration 后的存量菜单与新库 `menus.json` 语义一致，默认 `admin` 获得完整目录。
  6. 不修改前端、Controller、OpenAPI、权限模型或数据库结构。
  7. 规格符合性和代码质量双轴 Review 均为“通过”。
- 测试方式：
  - 按 TDD 先增加/调整菜单转换与一致性失败测试。
  - 运行 `sts-service-admin-init` 目标单测和受影响的 Admin 零 DB 测试。
  - 运行相关 MySQL migration IT；Docker 不可用时如实报告。
  - 执行 OpenSpec 严格校验和 `git diff --check`。

- [x] 固化前端树转换与稳定 ID 约束测试（Red）
- [x] 转换并更新 `menus.json`（Green）
- [x] 新增正式 migration，同步存量菜单及角色关系
- [x] 运行目标测试与验收检查；MySQL IT 因 Docker socket 不可用未执行
- [x] 完成双轴 Review，更新 Task、QA / Retro 和进度记录
