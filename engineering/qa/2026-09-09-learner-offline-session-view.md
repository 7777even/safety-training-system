# QA：学员只读本人线下场次

## 范围

OpenSpec Change：`learner-offline-session-view` Task 1.1。

覆盖 `GET /api/training/learner/offline-sessions/{offlineSessionId}`：权限 `training:learner:task`
，按登录用户定位本人参训记录；RUNNING/ENDED 可读场次通知、本人出勤、已评分成绩与投影。不可见 404/8155。enter 仍只返回跳转 ID。无
DDL、未改前端、未改管理端场次权限。

## 自动化验证

在 `backend/` 执行：

```text
mvn -pl sts-service-admin -am test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=TrainingWorkflowServiceImplTest,TrainingWorkflowControllerTest,TrainingWorkflowControllerWebSliceTest
```

→ 33 项通过（Service 25、Controller 5、WebSlice 3），0 失败。

- `docs/api/training.openapi.json` JSON 解析通过。
- `git diff --check` 通过。

## 覆盖场景

1. 本人 RUNNING：返回场次、确认出勤、成绩、投影 COMPLETED/7200 秒。
2. ENDED 可读；无出勤时 confirmed=false、PENDING、0 秒。
3. 场次不存在、未参训、PUBLISHED 计划均为 8155。
4. Controller 把登录 ID 传给 Service；WebSlice 8155 用 Result 外壳。

## MySQL IT

无 DDL，未跑 Testcontainers IT。

## 真实登录态待验收

前端改调本 GET（不在 lims 范围）。须用学员账号，不要用超管。
