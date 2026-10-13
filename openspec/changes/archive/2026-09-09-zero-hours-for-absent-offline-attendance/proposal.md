# Proposal：缺勤确认后不计线下获得学时

教师确认缺勤或请假后，投影仍按整场时长写入获得学时，并把该项标成完成。确认只表示出勤记录作数，不等于算出勤学时。

## Why

线下投影只看 `confirmedBy`，不读 `attendanceStatus`。确认 `ABSENT`/`LEAVE` 后 `effectiveDurationSeconds` 仍是场次秒数，并进入
`offlineHours`；无及格线时还写成 `COMPLETED`。这与「获得学时来自有效出勤」冲突，也与「以确认出勤为完成前提」冲突。

## What Changes

- 投影读取已确认出勤状态：仅 `PRESENT`/`LATE` 计入获得学时（现有签退规则不变）。
- 已确认 `ABSENT`/`LEAVE`：`effectiveDurationSeconds` 为 0，该项 `resultStatus` 为 `FAILED`，不得 `COMPLETED`。
- 学时仍由后端从出勤事实派生，确认接口不接收学时。
- 不发明迟到折扣或请假比例折算。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `training-execution-loop`：线下获得学时与完成判定必须读取出勤状态，确认缺勤/请假不得计满学时。

## Impact

- 后端：`TrainingResultSyncServiceImpl` 线下草稿与有效秒数；零 DB。
- 契约：`docs/api/training.openapi.json` 标明 ABSENT/LEAVE 确认后获得学时为 0。
- 无新路径、无 DDL。仅后端；前端以 OpenAPI 为准另开。

## 功能项追溯

- 功能项 37：线下培训结果纳入参训结果。
- 功能项 5：学时必须来自有效出勤事实。

## 目标

1. 确认缺勤或请假后该项获得学时为 0，且不进入 `offlineHours`。
2. 确认缺勤或请假后该项不得为 `COMPLETED`。
3. 正常出勤/迟到确认后仍按现有有效时长计学时。

## 非目标范围

- 不改签到写入、确认接口入参，不让前端传学时。
- 不做迟到折扣、请假部分学时或综合成绩缺勤否决公式。
- 不改应到名单、场次状态机、监管下钻。
- 不改前端。

## 前后端影响

仅后端；前端以 OpenAPI 为准另开。
