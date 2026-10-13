# QA：开考类型由服务端派生

## 范围

OpenSpec Change：`derive-exam-attempt-type-on-start` Task 1.1～2.1。

覆盖通用开考忽略请求 `attemptType`、正式考试/培训考试/培训题库终态复用、仅 `/retake` 创建补考、课程题库可再开 PRACTICE，以及
exam/training OpenAPI 说明。无新路径、无新权限、无 DDL。未改前端。

## 自动化验证

在 `backend/` 执行：

```text
mvn -pl sts-service-admin -am test -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=ExamAttemptServiceImplTest,CourseAssessmentAttemptServiceTest,TrainingWorkflowServiceImplTest,TrainingWorkflowControllerTest
```

→ 73 项通过，0 失败、0 错误、0 跳过。

- `docs/api/exam.openapi.json`、`docs/api/training.openapi.json` JSON 解析通过。
- `git diff --check` 通过。

## 覆盖场景

1. 培训考试、培训题库已有终态后再 start，即使请求 `FIRST` 和新令牌也不 insert。
2. 通用 start 传 `RETAKE` 不创建补考，返回终态答卷。
3. `/retake` 仍创建 `RETAKE`（既有用例）。
4. 课程题库首次派生 `PRACTICE`；无进行中时可再开 PRACTICE。
5. 课程/培训入口不再把客户端类型写入开考请求。

## MySQL IT

无 DDL、无 Mapper/SQL 变更，未跑 Testcontainers IT。

## 真实登录态待验收

需要在非生产环境由前端按 OpenAPI 对接后验证（不在 lims 范围）：

1. 培训考试交卷后再次进入，拿到同一 `examAttemptId`，不出现第二份首考。
2. 前端继续传 `FIRST` 也不再开新卷。
3. 补考仍走 `POST /exam/candidates/{id}/retake`。
