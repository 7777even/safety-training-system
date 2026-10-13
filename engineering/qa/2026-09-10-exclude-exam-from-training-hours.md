# QA：考试与题库退出培训学时

## 范围

OpenSpec Change：`exclude-exam-from-training-hours` Task 1.1、2.1。

考试/题库阶段不填学时（正数 400/8165，落库 0）；发布与监管预警总学时只加课程+线下，线上只加课程；草稿发布冻结将考试/题库学时写成
0。无 DDL、未改前端。

## 自动化验证

在 `backend/` 执行：

```text
mvn -pl sts-service-admin -am test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=TrainingPlanPublishValidatorTest,WarningDetectServiceImplTest,TrainingPlanServiceImplTest,TrainingTemplateServiceImplTest,TrainingResultSyncServiceImplTest
```

→ 90 项通过，0 失败（发布校验 19、预警 6、计划 30、投影 24、模板 11）。

- `docs/api/training.openapi.json`、`docs/api/regulation.openapi.json` JSON 解析通过。
- `git diff --check` 通过。

## 覆盖场景

1. 考试/题库省略或 0 可保存；正数 400/8165 且不写库；课程正数学时仍可保存。
2. apply-template 将模板考试学时拷成 0。
3. 考试学时不能凑满类型总学时；考试不抬高线上占比。
4. 草稿发布冻结后考试 `requiredHours` 为 0。
5. 预警 `planTotalHours` 不含考试学时。
6. 学员考试获得学时仍为 0；线下合格仍发安排学时。

## MySQL IT

无 DDL，未跑 Testcontainers IT。`TrainingMixedPlanLifecycleIT` 现有课程 4 + 线下 4 仍够类型 8 学时，未在本次重跑。

## 双轴 Review

1. 规格符合性：通过。考试/题库不填、不计入学时；发布与预警同一合计。
2. 代码质量：通过。无前端、无 DDL；合计收口到 `TrainingArrangedHours`。

## 真实登录态待验收

前端按 OpenAPI 隐藏考试/题库学时框，并不再拿考试学时凑总学时（不在 lims 范围）。
