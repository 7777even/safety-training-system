# Design：考试与题库退出培训学时

## Context

`TrainingPlanPublishValidator.validateHours` 与监管 `WarningDetectServiceImpl.splitPlanHours` 把 `COURSE`、`EXAM`、
`QUESTION_BANK` 的 `requiredHours` 加为线上安排学时。学员投影对考试/题库写 0 秒。功能项 33 把考试/题库定义为培训考核，不是学时来源。9
月 9 日 `distinguish-planned-and-earned-training-hours` 只把两套数写进契约，没有改公式。

本 Change 改合计与保存约束，使课表学时和可发给学员的内容一致：只有课程和线下。

## Goals / Non-Goals

**Goals:**

- 考试/题库阶段内容不填学时；正数拒绝；读写为 0。
- 发布、预检、监管预警使用同一合计：总学时 = 课程 + 线下，线上 = 课程。
- 契约标明考试/题库 `requiredHours` 不是必填，不得为正。

**Non-Goals:**

- 考试合格发放学时。
- 改课程获得学时公式或线下合格发放规则。
- DDL、改写已发布内容行、前端页面。

## Decisions

### D1：考试/题库不占学时，而不是考完再发

备选：合格后把 `requiredHours` 发给学员。否决：考核不是培训学时，功能项无此要求，尺子会被考试灌满。

### D2：保存拒绝正数，省略则存 0

模板/计划保存阶段内容时，`EXAM` / `QUESTION_BANK` 的 `requiredHours` 为 `null`、省略或 `0` 则落库 `0`；`> 0` 返回
`400/8165`，消息区分考试与题库，不写库。

`apply-template` 从旧模板拷贝时把这两类强制为 `0`，避免草稿带着历史正数。

不删列：同一张内容表，课程和线下仍用 `requiredHours`。

### D3：合计只认课程和线下

```text
planTotalHours  = Σ COURSE.requiredHours + Σ OFFLINE.requiredHours
onlineHours     = Σ COURSE.requiredHours
onlineRatio     = onlineHours / planTotalHours   （总学时为 0 时不因占比阻止，沿用现有空合计处理）
```

`EXAM` / `QUESTION_BANK` / 其他非课程线下类型贡献 0。发布校验、预检、`PLAN_TOTAL_HOURS`、`PLAN_ONLINE_RATIO` 共用该口径。
`ONLINE_TYPES` 改为仅 `COURSE`。

阶段行上的 `requiredHours` 仍可由调用方提交作展示，**发布与预警不读阶段合计**，只读内容行。

草稿发布时若内容行仍留有考试/题库正数学时：合计当 0，并在冻结时把该行写成 `0`，不因此单独失败。新保存路径已禁止正数。

已发布计划不改写内容行；预警按新公式现算，原先靠考试凑满的计划可能变为学时不足。

### D4：获得学时与结业不变

考试/题库 `effectiveDurationSeconds` 仍为 0。课程仍用正式学习秒。线下 `COMPLETED` 仍用该项 `requiredHours`。结束仍不用个人获得学时重算
TOTAL_HOURS。

### D5：契约

`requiredHours`：课程、线下的安排学时，发布与预警用。考试/题库不得为正，返回 `0`。`8165` 写入保存阶段内容的 description 与
example。发布说明改为课程计入线上、线下计入线下。

架构 `training-course-exam-capability.md` 同步一句，取代「考试计入线上安排」。

## Risks / Trade-offs

- [草稿/已发布计划总学时下降] → 不够尺子是课表缺口，须补课程或线下；预检 8121 文案已含当前值与最低学时。
- [旧前端仍提交考试学时] → 保存 8165；OpenAPI 标明，前端另开。
- [监管对历史计划新预警] → 接受为口径修正，不冻结旧合计。
- [阶段展示合计仍含考试] → 后端不读阶段合计；调用方应按内容行重算。

## Migration Plan

无 DDL。部署后保存/发布/预警立即按新口径。回滚即恢复旧 `ONLINE_TYPES` 与保存校验。

## Open Questions

无。
