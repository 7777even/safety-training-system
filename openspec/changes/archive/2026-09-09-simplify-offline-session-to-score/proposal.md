# Proposal：线下场次收口为录成绩

线下完成依赖签到，但生成场次不建名单、教师任务没有人。产品确认按功能项最小集合执行：公示地点、成绩进入系统；签到口径全部去掉。表保留，签到
HTTP 删除。

## Why

教师无法按人录完成结果。继续做签到会超出功能项最小集合。

## What Changes

- 线下完成与获得学时只读已评分成绩。**BREAKING**
- 教师任务返回参训名单与分数。
- 一场一人一条成绩。
- 删除签到 HTTP。**BREAKING**
- 学员 GET 去掉出勤字段。**BREAKING**
- `sts_training_attendance` 留表。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `training-execution-loop`：线下以成绩为准；删除签到接口；教师名单来自参训人。

## Impact

- 后端：投影、成绩 upsert、教师任务、学员 GET、ExecutionController；零 DB；IT 混合计划。
- 契约：删除 attendance 路径；教师/学员 VO。
- 文档：`docs/architecture/offline-session-minimum.md`、开课考试能力说明、路线图阶段 5 第 6～7 条。
- 仅后端；前端以 OpenAPI 为准另开。

## 功能项追溯

- 33：加线下、公示地点、成绩写入；签到延期。
- 34：计划可设线下。
- 37：本场成绩可见。
- 48、56：学员看见地点与本人分数。

## 目标

1. 教师能按参训人录一条成绩并完成该项。
2. 签到 HTTP 404，完成不依赖出勤。
3. 学员 GET 只有通知和本人成绩。

## 非目标范围

签到能力、删表、Excel 导入、图表、附件、奖惩、前端、场次状态机。

## 前后端影响

仅后端；前端以 OpenAPI 为准另开。
