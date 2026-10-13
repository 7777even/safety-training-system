# Proposal：移除线下培训签到残留

## Why

已确认的线下培训最小闭环以“场次状态 + 教师录入成绩”为完成依据，签到 HTTP
已删除，但计划执行配置、发布校验、线下场次、签到表和教师工作台仍保留签到字段或统计，导致前后端契约继续要求一条已经退出本期业务的链路。当前仍处开发阶段且无需兼容历史数据，应一次性删除这些残留，避免新数据继续固化无效签到语义。

## What Changes

- 线下计划内容发布只校验教师、地点、授课起止时间和及格线，不再要求签到窗口或签退要求。
- 批量生成与单内容补建线下场次时，不再读取或写入签到窗口、签退要求及签到规则。
- **BREAKING**：从线下内容执行配置、线下场次创建/更新请求、管理端场次响应及相关示例中删除签到字段。
- **BREAKING**：从线下场次表删除三个签到列，并删除不再使用的 `sts_training_attendance` 表及对应后端代码。
- **BREAKING**：删除归档参与人中始终为空的 `attendance_snapshot_json` 签到快照列。
- **BREAKING**：教师工作台删除“待确认签到数”，培训与运维 OpenAPI 同步删除相关字段、描述和错误语义。
- **BREAKING**：删除 `training:attendance:list/edit/confirm` 权限及对应初始化菜单种子。
- 场次状态仍由 start/complete/cancel 接口受控推进；线下成绩与结果投影继续仅以教师已评分成绩为事实。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `training-execution-loop`：线下计划、场次和教师工作台彻底移除签到配置、签到存储及签到统计，仅保留场次状态与成绩闭环。
- `training-api-contract`：执行人审计与线下管理契约删除签到确认、签到字段及其错误语义。

## Impact

- 后端：`TrainingPlanPublishValidator`、`TrainingExecutionProvisioner`、线下场次 DTO/VO/Entity/Service、签到
  Entity/Mapper/Service/DTO/VO、`TrainingWorkflowServiceImpl`、`OpsWorkbenchServiceImpl`、菜单权限种子及相关测试。
- API：`docs/api/training.openapi.json`、`docs/api/ops.openapi.json`。
- 数据：新增 Admin migration，删除 `sts_training_offline_session` 的三个签到列、`sts_training_attendance` 表和
  `sts_archive_participant.attendance_snapshot_json`；同步最新 init SQL 与 `sts.init.schema-version`。
- 前后端影响：仅后端实施；前端以更新后的 OpenAPI 为准另开，不修改 `frontend/`。

## 功能项追溯

- 功能项 33 原文语义：“线下培训：添加培训阶段线下培训，支持线下签到、线下培训考核成绩导入系统，公示培训地点。”本期按已确认的
  `simplify-offline-session-to-score` 最小闭环继续保留线下场次、地点公示和成绩录入；签到能力延期，若恢复必须另建并确认独立
  Change。
- 功能项 34 原文语义：“可设置关于培训的全部内容，包含培训基本信息设置、线上培训、线下培训、培训人员及附件资料上传等；根据培训计划可添加培训内容，设置培训详情。”本次保留线下内容编排、执行实例和参训关系，不改变其他培训内容。
