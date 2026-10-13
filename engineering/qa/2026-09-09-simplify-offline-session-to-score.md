# QA：线下场次收口为录成绩

## 范围

OpenSpec Change：`simplify-offline-session-to-score` Task 1～3。

线下完成与获得学时只读已评分成绩；签到 HTTP 删除；`sts_training_attendance` 留表。教师任务返回参训名单。一场一人一行。学员
GET
去掉出勤字段。无 DDL、未改前端。

## 自动化验证

在 `backend/` 执行：

```text
mvn -pl sts-service-admin -am test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=TrainingResultSyncServiceImplTest,TrainingExecutionControllerTest,TrainingWorkflowServiceImplTest,TrainingOfflineScoreServiceImplTest,TrainingTeacherTaskServiceImplTest,TrainingWorkflowControllerTest,TrainingWorkflowControllerWebSliceTest
```

→ 62 项通过，0 失败。

```text
mvn -pl sts-service-admin -am -Pintegration-test test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=TrainingMixedPlanLifecycleIT
```

→ 2 项通过，0 失败。

- `docs/api/training.openapi.json` JSON 解析通过。
- `git diff --check` 通过。

## 覆盖场景

1. 无分 PENDING 0 秒；合格 COMPLETED 且秒数=requiredHours×3600；低于及格线 FAILED 0 秒。
2. 教师详情返回两名参训人、已评 1、待评 1，动作仅 RECORD_SCORE。
3. 不同 scoreName 再次提交更新原行。
4. 学员 GET 返回成绩与投影，无出勤字段。
5. 混合计划 IT 只写成绩即可使线下项 COMPLETED。

## MySQL IT

`TrainingMixedPlanLifecycleIT` 2 通过。无 DDL。

## 真实登录态待验收

前端改调教师名单与成绩接口、停调签到路径（不在 lims 范围）。
