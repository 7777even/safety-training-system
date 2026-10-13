# Tasks：移除线下培训签到残留

> Owner：lims。状态：已完成。一次只实施一个 Task。

## 1. 收口线下计划与场次为成绩执行链路

- [x] 1.1 以 TDD 删除线下计划和场次公开模型中的签到字段

  - **Owner**：lims。
  - **输入**：已确认的 proposal/design、`training-execution-loop` 与 `training-api-contract` delta、现有发布与执行生成实现。
  - **输出**：发布预检和正式发布只校验教师、地点、授课起止时间和及格线；批量生成与单内容补建不再传播签到字段；Create/Update/VO/Entity
    删除签到属性；Training OpenAPI Schema、required、说明、错误码与示例同步。
  - **前后端影响**：仅后端与 `docs/api/training.openapi.json`；前端以 OpenAPI 为准另开。
  - **API 依赖**：`publish-precheck`、`publish`、`generate-executions`、内容 `provision`、线下场次 POST/PUT/GET；成绩、状态流转和学员场次
    GET 行为保持不变。
  - **数据影响**：新增 migration 删除 `sts_training_offline_session.sign_in_start_time`、`sign_in_end_time`、
    `attendance_rule`；同步 init SQL、schema version 与 Entity。
  - **验收标准**：无签到配置的完整线下内容可预检、发布和生成；缺教师/地点/时间/及格线仍以指定提示失败；两种生成入口行为一致；创建与更新契约不接受、查询契约不返回签到字段；状态只能由既有状态接口推进。
  - **测试方式**：Red → Green 更新 `TrainingPlanPublishValidatorTest`、`TrainingPlanServiceImplTest`、
    `TrainingExecutionProvisionerTest`、`TrainingOfflineSessionServiceImplTest`、相关 Controller/OpenAPI 契约测试；运行受影响模块定向测试。

## 2. 删除签到存储和工作台残留

- [x] 2.1 以 TDD 删除签到表、后端死代码与待确认签到统计

  - **Owner**：lims。
  - **输入**：Task 1.1 数据模型、现有签到 Entity/Mapper/Service/DTO/VO、参训人删除保护、教师工作台。
  - **输出**：删除 `sts_training_attendance` 表及全部 Java
    资产；删除空签到归档快照列与写入；参训人删除保护移除签到查询；教师工作台删除待确认签到字段与查询；删除三个签到权限和菜单种子；Ops
    OpenAPI 和培训契约间接残留同步清理。
  - **前后端影响**：仅后端、`docs/api/training.openapi.json`、`docs/api/ops.openapi.json`；前端以 OpenAPI 为准另开。
  - **API 依赖**：教师工作台摘要删除 `pendingAttendanceConfirmCount`；线下成绩、场次状态机和其他工作台计数保持不变。
  - **数据影响**：同一新增 migration 删除 `sts_training_attendance`、`sts_archive_participant.attendance_snapshot_json`
    、相关角色菜单关联和菜单记录；从 init SQL 删除对应资产并同步菜单种子；开发数据无需迁移或保留。
  - **验收标准**：生产代码和 OpenAPI 不再引用签到表、签到字段、签到错误码、签到来源或待确认统计；菜单和角色授权不含三个签到权限；教师工作台仍返回正确场次数；无其他事实的草稿参训人可按既有规则移除。
  - **测试方式**：Red → Green 更新 `TrainingWorkflowServiceImplTest`、`OpsWorkbenchServiceImplTest` 和菜单权限一致性测试，删除签到
    Service 测试；执行真实 MySQL migration/Mapper IT、受影响零 DB 测试、OpenAPI 校验和 `git diff --check`。

## 完成门槛

- 两个 Task 均完成后更新本文件、进度表、QA 和 Retro。
- 独立完成规格符合性与代码质量双轴 Review。
- 使用真实 MySQL 验证 DROP migration 和最新 init SQL；Docker 不可用时如实记录，不能以零 DB 测试替代。
- 不修改 `frontend/`，不顺带改变成绩、场次状态、权限或学时规则。
