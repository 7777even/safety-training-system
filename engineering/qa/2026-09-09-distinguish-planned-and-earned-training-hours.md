# QA：区分培训安排学时与获得学时

## 范围

OpenSpec Change：`distinguish-planned-and-earned-training-hours` Task 1.1。

覆盖 `docs/api/training.openapi.json` 将 `requiredHours` 标明为安排学时，将 `effectiveDurationSeconds`/`onlineHours`/
`offlineHours` 标明为获得学时；考试/题库获得学时为 0；题库 `durationSeconds` 为作答时限。发布校验与投影计算公式未改。无新路径、无
DDL、未改前端。

## 自动化验证

在 `backend/` 执行：

```text
mvn -pl sts-service-admin -am test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=TrainingResultSyncServiceImplTest,TrainingPlanPublishValidatorTest
```

→ 40 项通过（发布校验 18、结果投影 22），0 失败。证明学时公式未改。

- `docs/api/training.openapi.json` JSON 解析通过。
- `git diff --check` 通过。

## 覆盖场景

1. 发布按内容安排学时合计总学时与线上占比（既有用例仍绿）。
2. 课程学时计入 onlineHours，考试不计入（既有用例仍绿）。
3. 契约写明考试/题库获得学时为 0，题库时限不是学时。

## MySQL IT

无 DDL，未跑 Testcontainers IT。

## 真实登录态待验收

前端按 OpenAPI 将安排学时与获得学时分开展示（不在 lims 范围）。
