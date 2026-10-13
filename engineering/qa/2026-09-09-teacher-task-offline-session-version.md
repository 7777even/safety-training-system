# QA：教师任务返回场次乐观锁 version

## 范围

补 `add-offline-session-state-machine` 读侧契约：教师任务列表/详情返回场次 `version`，供
`POST /training/offline-sessions/{id}/start|complete|cancel` 回传。无 DDL。未改前端。

## 自动化验证

在 `backend/` 执行：

```text
mvn -pl sts-service-admin -am test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=TrainingTeacherTaskServiceImplTest,TrainingWorkflowServiceImplTest
```

→ 29 项通过，0 失败。

- `docs/api/training.openapi.json` JSON 解析通过。
- `git diff --check` 通过。

未跑 MySQL IT（无 schema / Mapper / 持久化变化）。

## 覆盖场景

1. 教师任务列表映射场次 `version`。
2. 教师任务详情映射场次 `version`（SCHEDULED / IN_PROGRESS）。

## Review

规格符合性：通过。教师读模型补上写接口已要求的乐观锁令牌。
代码质量：通过。未改 `frontend/`。
