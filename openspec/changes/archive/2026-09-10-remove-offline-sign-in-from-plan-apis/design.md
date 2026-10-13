# Design：线下培训执行链路移除签到

## Context

`simplify-offline-session-to-score` 已确认并实现“线下完成只认成绩、删除签到 HTTP”，但后端仍存在三类残留：

1. `TrainingContentExecutionConfig`、发布校验和执行生成仍要求并传播签到窗口及签退要求；
2. 管理端线下场次请求/响应与 `sts_training_offline_session` 仍保存签到字段；
3. `sts_training_attendance` 虽无 Controller，仍由参训人删除保护和教师工作台待办统计读取，相关 Service/Mapper/DTO/VO
   仍可被内部调用。

当前处于开发阶段，人工已确认无需保留历史数据或旧客户端兼容。本次为 API、数据库和跨模块工作台契约变更，按 L4 执行。

## Goals / Non-Goals

**Goals:**

- 线下计划发布、执行生成、场次管理、教师工作台和数据库统一采用“场次状态 + 教师成绩”模型。
- 删除所有可写、可读或可统计的签到字段和签到记录存储，避免残留代码重新形成签到依赖。
- 保证发布预检与正式发布、批量生成与单内容补建使用同一规则。

**Non-Goals:**

- 不改变 `POST /api/training/offline-scores` 的成绩规则与权限。
- 不改变场次 start/complete/cancel 状态机，也不允许 Create/Update 直接推进 `sessionStatus`。
- 不改变学员线下场次详情、结果投影和合格成绩换算获得学时的既有行为。
- 不恢复签到、签退、扫码、考勤导入或缺勤判定。
- 不修改前端代码。

## Decisions

### D1：直接删除而非兼容废弃

从 Java DTO/VO/Entity、OpenAPI Schema、required 列表和示例中直接删除 `signInStartTime`、`signInEndTime`、`requireSignOut`、
`attendanceRule`。当前无需兼容历史请求、响应或 `executionConfigJson`，不增加别名、忽略字段或过渡期双写。

曾考虑保留字段并标记 deprecated，但这会让生成类型与页面继续看见无效能力，且无法解决发布校验和持久化语义分叉，因此不采用。

### D2：硬删除签到数据库资产

新增不可修改的 Admin Flyway migration：

- 从 `sts_training_offline_session` 删除 `sign_in_start_time`、`sign_in_end_time`、`attendance_rule`；
- 删除 `sts_training_attendance` 表。
- 从 `sts_archive_participant` 删除始终写空对象且无对外读取的 `attendance_snapshot_json`。

同步从 `schema-training.sql` 的最新完整结构删除相同资产，并把 `sts.init.schema-version` 更新到新 migration 版本。Entity 与
Mapper 同步删除，禁止保留不可达的字段映射。

曾考虑仅把 `attendance_rule` 改为可空并保留签到表，但人工已确认开发阶段无需数据兼容，保留会继续产生死模型和误用风险，因此不采用。

### D3：线下计划与场次只传递执行必需信息

线下发布校验仅要求 `teacherUserId`、`location`、`startTime`、`endTime`、`passScore`
，统一错误提示为“线下内容必须配置教师、地点、时间和及格线”。正式发布继续复用同一校验器。

批量 `generate-executions` 与单内容 `provision` 继续共用 `TrainingExecutionProvisioner`
，创建线下场次时只写计划/阶段/内容关联、名称、教师、地点、起止时间、初始场次状态和启用状态。

线下场次 Create/Update 请求保留名称、教师、地点、起止时间、启用状态及适用的计划/阶段/内容关联。生命周期 `sessionStatus`
继续由服务端初始值和 start/complete/cancel 接口控制，不因本次清理开放直接修改。

### D4：删除签到表的全部生产引用

删除签到 Service、DTO、VO、Entity、Mapper 及测试。参训人删除保护不再查询签到记录，仍由线下成绩、奖惩、课程报名、考试人员和正式计算事实保护。

教师工作台删除 `pendingAttendanceConfirmCount` 及对应查询；`offlineSessionCount` 保留。同步修改 `OpsTeacherWorkbenchVO` 和
`ops.openapi.json`，避免删除表后启动或查询失败。

人工已确认同步删除 `training:attendance:list/edit/confirm` 三个无 Controller 对应的权限点。初始化 `menus.json`
与当前菜单树快照同时删除对应按钮项；migration 先删除角色菜单关联，再删除既有菜单记录。

### D5：契约清理覆盖间接残留

除指定 Schema 外，同时清理 `training.openapi.json` 中的签到错误码、取消计划“保留签到”描述、`OFFLINE_ATTENDANCE`
来源类型描述和所有签到示例。学员线下详情保持当前无签到字段的契约，不做无关修改。

## Risks / Trade-offs

- [遗漏 Mapper 或工作台引用导致运行期查不存在表] → 使用全仓静态搜索，并覆盖 Spring 上下文/工作台定向测试。
- [只改预检而正式发布仍失败] → 在同一 validator 测试中分别覆盖预检结果与 publish 调用链。
- [批量生成与单内容补建行为分叉] → 两条入口均断言生成的场次没有签到属性，并保留 Provisioner 单一实现。
- [DDL 与 init 快照不一致] → 增加真实 MySQL migration/Mapper 集成测试并校验 init schema version。
- [删除工作台字段影响旧前端] → 作为已确认的开发期 BREAKING 变更，由 `ops.openapi.json` 作为前端唯一契约重新生成或调整类型。
- [删除菜单时遗留角色关联] → migration 先按权限定位并删除 `us_sys_role_menu` 关联，再删除 `us_sys_menu`
  记录，初始化菜单一致性测试覆盖无悬空权限。

## Migration Plan

1. 先提交失败测试，证明无签到配置可发布、生成，且工作台不再公开签到统计。
2. 同步修改业务代码与两个 OpenAPI 契约。
3. 新增单向 migration 删除场次签到列、签到表、空签到归档快照列、角色菜单关联和菜单记录，同步 init SQL、菜单种子、schema
   version 与持久化模型。
4. 运行受影响零 DB 测试、真实 MySQL migration/Mapper IT、OpenAPI 校验与 `git diff --check`。
5. 开发环境若需回滚代码，使用后续 migration 重建空表/空列；不恢复已删除的开发数据。

## Open Questions

无。人工已确认开发阶段无需历史数据与旧契约兼容，可直接删除字段和表。
