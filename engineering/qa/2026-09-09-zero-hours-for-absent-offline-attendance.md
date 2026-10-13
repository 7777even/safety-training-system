# QA：缺勤确认后不计线下获得学时

## 范围

OpenSpec Change：`zero-hours-for-absent-offline-attendance` Task 1.1。

覆盖线下投影读取已确认 `attendanceStatus`：仅 `PRESENT`/`LATE` 计入获得学时；已确认 `ABSENT`/`LEAVE` 的
`effectiveDurationSeconds` 为 0、`resultStatus=FAILED`。确认接口不接收学时。无新路径、无 DDL、未改前端。

## 自动化验证

在 `backend/` 执行：

```text
mvn -pl sts-service-admin -am test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=TrainingResultSyncServiceImplTest,TrainingAttendanceServiceImplTest
```

→ 32 项通过（投影 27、签到 5），0 失败。

- `docs/api/training.openapi.json` JSON 解析通过。
- `git diff --check` 通过。

## 覆盖场景

1. 确认 ABSENT：秒数 0、offlineHours 不含该场、FAILED。
2. 确认 LEAVE：同上，不得 COMPLETED。
3. 确认 ABSENT 且已有成绩：仍 FAILED、0 学时。
4. 确认 PRESENT/LATE：场次 7200 秒、offlineHours=2.00、COMPLETED。
5. 要求签退却未签退仍为 0。

## MySQL IT

无 DDL，未跑 Testcontainers IT。

## 真实登录态待验收

前端按 OpenAPI 展示缺勤 0 学时（不在 lims 范围）。存量已确认缺勤若仍带着满学时，部署后对计划做一次 `results/synchronize` 补账。
