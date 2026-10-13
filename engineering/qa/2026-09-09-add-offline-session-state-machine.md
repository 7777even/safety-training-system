# QA：线下场次受控状态机

## 范围

OpenSpec Change：`add-offline-session-state-machine` Task 1.1～2.1。

覆盖 `start`/`complete`/`cancel`、PUT 不改状态、创建固定 DRAFT、仅 `IN_PROGRESS` 录分、教师 `availableActions`。无 DDL。未改前端。

## 自动化验证

在 `backend/` 执行：

```text
mvn -pl sts-service-admin -am test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=TrainingOfflineSessionServiceImplTest,TrainingExecutionControllerTest,TrainingOfflineScoreServiceImplTest,TrainingWorkflowServiceImplTest
```

→ 40 项通过，0 失败。

- `docs/api/training.openapi.json` JSON 解析通过。
- `git diff --check` 通过。

未跑 MySQL IT（无 schema 变化；录分 IT 走 Mapper 直插，不经状态门禁）。

## 覆盖场景

1. SCHEDULED start → IN_PROGRESS。
2. 非法 start 409/8202。
3. 非教师 403/8131；管理员可跨教师。
4. PUT 不改 sessionStatus；创建固定 DRAFT。
5. SCHEDULED 录分 409/8203；IN_PROGRESS 可录分。
6. 教师动作 SCHEDULED=START/CANCEL，IN_PROGRESS=RECORD_SCORE/COMPLETE/CANCEL。

## Review

规格符合性：通过。
代码质量：通过。未改 `frontend/`。
