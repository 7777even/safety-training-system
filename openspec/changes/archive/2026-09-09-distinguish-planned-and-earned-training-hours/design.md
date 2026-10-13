# Design：区分培训安排学时与获得学时

## Context

发布校验 `TrainingPlanPublishValidator.validateHours` 把 COURSE/EXAM/QUESTION_BANK 的 `requiredHours` 加为线上安排学时。投影
`readDraft` 对考试/题库写 `durationSeconds=0`，`toResults` 只把课程秒计入 `onlineHours`、线下确认秒计入 `offlineHours`。M3
规定结束不再用个人实学时重算 TOTAL_HOURS。

## Goals / Non-Goals

**Goals:** 契约与注释钉死两套口径；计算保持不变。

**Non-Goals:** 考试合格计学时、题库时限折学时、缺勤折算、前端展示。

## Decisions

### D1：不改公式

发布仍按内容 `requiredHours`；获得学时仍课程实学 + 线下确认。考试/题库投影秒数为 0。

### D2：契约分层命名

- `requiredHours`：安排学时。
- `effectiveDurationSeconds`：该项获得秒数。
- `onlineHours` / `offlineHours`：该人获得小时（秒/3600）。
- QUESTION_BANK `durationSeconds`：作答时限。

### D3：结束门禁

`end` 说明继续：TOTAL_HOURS/线上占比是发布时的编排规则，不用个人获得学时重算。

## Risks / Trade-offs

页面若仍拿获得学时去对安排学时，数字仍会不等。这是展示要对齐契约，不是再改后端公式。

## Migration Plan

无 DDL。部署后仅文档生效。

## Open Questions

无。
