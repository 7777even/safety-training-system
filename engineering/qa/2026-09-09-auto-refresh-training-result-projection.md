# QA：来源事实写入时自动刷新培训内容结果投影

## 范围

OpenSpec Change：`auto-refresh-training-result-projection` Task 1.1～3.1。

覆盖单行 `synchronizeParticipantContent`、课程学习状态变化刷新、培训考试开考/交卷/成绩发布同步事件、线下确认签到与保存成绩刷新，以及
`docs/api/training.openapi.json` 将计划级同步标为补账。无新路径、无新权限码、无 DDL。未改前端。

## 自动化验证

在 `backend/` 执行：

```text
mvn -pl sts-service-admin -am test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=TrainingResultSyncServiceImplTest,TrainingParticipantContentFactListenerTest,CourseLearningServiceImplTest,CourseLearnerReadingServiceImplTest,ExamAttemptServiceImplTest,TrainingAttendanceServiceImplTest,TrainingOfflineScoreServiceImplTest
```

→ 92 项通过，0 失败、0 错误、0 跳过。

- `docs/api/training.openapi.json` JSON 解析通过。
- `git diff --check` 通过。

## 覆盖场景

1. 单行刷新：未发布考试投影进入待发布；已冻结参训人跳过；内容不属于该计划 400/8120；字段未变不递增 `resultRevision`。
2. 培训报名学习状态变化调用刷新；仅秒数变化且状态不变不调用；独立开课无 `trainingParticipantId` 不调用。
3. 培训考生交卷、成绩发布发布 `TrainingParticipantContentFactChangedEvent`；监听器同步调用 `synchronizeParticipantContent`
   ；无 `trainingParticipantId` 不发事件。
4. 教师确认签到、保存线下成绩调用 `refreshParticipantContent`。
5. 计划级同步说明为全量补账；学员详情/结果 GET 只读已刷新投影。

## MySQL IT

无 DDL、无 Entity/Mapper/SQL 变更，未跑 Testcontainers IT。不得以零 DB 代替落库验证；本 Change 复用既有 `upsertProjection`
写路径，增量风险在写路径挂钩。

## 真实登录态待验收

需要在非生产环境由前端按 OpenAPI 对接后验证（不在 lims 范围）：

1. 培训考试交卷后，学员参训详情对应内容项不再保持待开始。
2. 课程开始学习后内容项进入进行中；完成课程后进入已完成。
3. 教师确认签到或保存线下成绩后，结果查询无需先调计划级同步。
4. 存量已交卷仍 PENDING 的行，管理员执行一次 `POST /training/plans/{id}/results/synchronize` 补账。
