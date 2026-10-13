# QA：M3 培训执行闭环 Task 4

## 范围

OpenSpec Change `2026-07-29-complete-m3-training-execution-loop` Task 4。仅后端与 OpenAPI。对照 Design 第 14 节修正 Task
1～3 实现偏差。

## 已确认规则

- QUESTION_BANK 首次 Attempt 按冻结 drawRules 过滤、抽取、赋分，持久化随机种子；候选不足失败并定位规则；同令牌不重新抽题。
- 及格线必须在 0～抽题总分之间。
- 计划专属 EXAM 只读取已校验 hash 的发布快照，禁止回退来源当前策略。
- 已发布 Candidate PASSED/FAILED 映射 COMPLETED/FAILED；未发布不计分。
- 线下终态以确认出勤为前提；孤立成绩不能完成内容。
- 教师处理接口权限为 `training:teacher:task`，不能用接口准入权限绕过场次教师归属。
- 聚合只认 `AVERAGE` / `WEIGHTED_AVERAGE`。
- 最终快照含 `finalizedTime`，与投影和参训人归档时间一致。

## 验证结果

- TDD：先增加抽题、快照建考、FAILED 映射、线下孤立成绩、教师归属、加权 95、finalizedTime 失败测试并观察失败，再最小实现。
- 目标零
  DB：75/75（QuestionBankDrawEngine、ExamAttempt、ResultSync、Adapter、Provisioner、PublishValidator、Attendance、ExecutionController）通过；回归
  TrainingPlanLifecycle/Workflow/Execution/Plan 36/36 通过。
- 带 DB IT：通过。
  - 定向：`FlywayMigrationIT` 3、`CourseOfferingMapperIT` 3、`ExamMapperIT` 3、`TrainingPlanMapperIT` 6、`TrainingTypeSeedIT`
    2，合计 17/17。
  - 完整 Admin MySQL IT：205/205。
  - Admin-init MySQL IT：`AdminInitFlywayIT` 1/1（baseline `20260831170000`）。
  - `FlywayMigrationIT` 复合唯一索引断言已用 `COUNT(DISTINCT index_name)` 在真库验证。

- `docs/api/training.openapi.json` JSON 可解析；教师签到确认与线下成绩 `x-permission` 改为 `training:teacher:task`。
- `git diff --check` 通过。无数据库结构变化。

## Review

- 规格符合性：通过。Design 第 14.1～14.8 七个最小推演均有自动化覆盖；旧课程题库练习无 drawRules 时仍抽取全部已发布题目。
- 代码质量：通过。抽题算法抽到 `QuestionBankDrawEngine`；培训域仍经 Port 创建考试；未改 `frontend/`；未改已有 V
  文件。需人工决策：管理员代办权限仍不开放。
