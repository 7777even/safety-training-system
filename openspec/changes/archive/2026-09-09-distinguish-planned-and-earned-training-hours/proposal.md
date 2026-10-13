# Proposal：区分培训安排学时与获得学时

计划发布按内容 `requiredHours` 校验类型总学时和线上占比（考试/题库计入线上安排），学员结果里考试/题库获得学时为
0。两套数都是后端口径，但契约都叫「学时」，前端按获得学时去对安排学时，会误判为计算错误。

## Why

M3 已规定考试/题库不计入个人培训获得学时，结束门禁也不用个人 `onlineHours` 去套 `TOTAL_HOURS`
。需要在契约上把「安排学时」和「获得学时」分开，而不是改公式把考试作答时长加进获得学时。

## What Changes

- OpenAPI 标明：`requiredHours` 是安排学时，只用于发布校验；`effectiveDurationSeconds` / `onlineHours` / `offlineHours`
  是获得学时。
- 考试/题库获得学时为 0；题库 `durationSeconds` 是作答时限，不是学时。
- 不改发布合计、投影累计、结束门禁的计算。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `training-execution-loop`：明确安排学时与获得学时不得混用。

## Impact

- 契约：`docs/api/training.openapi.json` 字段与发布/结果接口说明。
- 后端：发布校验与投影处补充口径注释，不改公式。
- 仅后端；前端以 OpenAPI 为准另开。

## 功能项追溯

- 功能项 5：类型学时与计划编排对比。
- 功能项 34：计划内容安排学时。

## 目标

1. 调用方能区分安排学时和获得学时。
2. 考试/题库完成后获得学时仍为 0，发布仍用其 `requiredHours` 做编排校验。

## 非目标范围

- 不改计算公式，不把考试/题库计入 `onlineHours`。
- 不把个人获得学时当作结束时的 TOTAL_HOURS 门槛。
- 不改缺勤折算、前端页面。

## 前后端影响

仅后端；前端以 OpenAPI 为准另开。
