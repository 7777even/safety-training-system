# Design：开考类型由服务端派生

## Context

`ExamAttemptServiceImpl.start` 把 `StartAttemptRequest.attemptType` 写入新答卷。唯一约束是 `(考生, token)` 与
`(考生, attempt_no)`。前端每次进入换新 token 并默认 `FIRST`，即可无限首考。课程测验已有终态复用；补考只在
`attemptType=RETAKE` 或 `/retake` 时校验。`retake()` 内部调用 `start()`。

## Goals / Non-Goals

**Goals:**

- 请求不得决定作答类型。
- 正式考试与培训考试/题库：有效终态后普通进入只回看。
- 补考仅专用接口。

**Non-Goals:**

- 练习重置作废已提交卷。
- 学时、投影、前端页面。

## Decisions

### D1：`start(request)` 永不按请求创建 RETAKE

`start()` 固定 `retake=false`。`retake()` 走内部 `startInternal(request, true)`。HTTP 的 `attemptType` 反序列化忽略。

### D2：普通进入状态机

1. 同 token 非空 → 返回该答卷。
2. 有 `CREATED`/`ANSWERING` → 返回该答卷（含练习，不再因 PRACTICE 跳过）。
3. 若本次是补考内部调用 → `assertCanCreateRetake` 后插入 `RETAKE`。
4. 否则若存在非 `VOID` 终态（`SUBMITTED`/`PENDING_REVIEW`/`PENDING_PUBLISH`/`PUBLISHED`/`RESULT_EFFECTIVE`）：

- 可重复练习（`COURSE_BANK_PRACTICE`、`STANDALONE_PRACTICE`）→ 插入 `PRACTICE`。
- 其余（`STANDALONE_EXAM`、`TRAINING_EXAM`、`TRAINING_PRACTICE`、`COURSE_QUIZ`）→ 返回最新终态，不 insert。

5. 否则插入：可重复练习为 `PRACTICE`，其余为 `FIRST`。

### D3：契约字段保留但无效

OpenAPI 仍可出现 `attemptType`，description 写明服务端忽略；补考用 `/retake`。培训 enter / exam-attempts 同样。无新错误码。

### D4：课程考核不再在请求上声明类型

`CourseAssessmentAttemptService` 不再 `setAttemptType`；由 `start()` 按 `sourceType`/`assessmentKind` 派生。

## Risks / Trade-offs

- 历史已存在多份 `FIRST` 不会自动合并；新进入复用最新终态。
- 独立练习交卷后仍可再开 PRACTICE，与课程题库刷题一致；培训题库按正式结果不可再开 FIRST。

## Migration Plan

无 DDL。部署后新进入即受护栏。回滚即恢复信任请求类型。

## Open Questions

无。
