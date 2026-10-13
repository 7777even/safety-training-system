# Design：补考策略护栏

## 系统设计

补考不是新考试。`POST /api/exam/candidates/{examCandidateId}/retake` 只携带考生 ID 和幂等令牌；窗口、范围、次数来自该考试的
`sts_exam_policy`。每次补考新增 `sts_exam_attempt`，`attempt_type=RETAKE`，`attempt_no` 递增，作答时长沿用考试
`duration_seconds`。迟到禁考不适用于补考。

## 数据模型

复用既有表，无 migration。

- `sts_exam_policy.is_retake_enabled` / `retake_scope` / `retake_start_time` / `retake_end_time` / `retake_max_count` /
  `retake_score_mode`
- `sts_exam_candidate.candidate_status` / `result_published_time` / `attempt_count` / `final_score`
- `sts_exam_attempt.attempt_type` / `attempt_no` / `attempt_status`

`retake_scope` 取值：`FAILED`、`FAILED_OR_ABSENT`、`ABSENT`。启用补考但范围为空白时按 `FAILED`。

## API 设计

不新增路径。完善 `POST /api/exam/candidates/{examCandidateId}/retake`：

- 权限仍为 `exam:attempt:start`
- 参数仍为路径 `examCandidateId` 与查询 `clientAttemptToken`
- 成功仍返回新的或可恢复的 `ExamAttemptVO`
- 新增业务码：`7054` 不在补考开放时间，`7055` 不在补考范围，`7056` 考试状态不允许补考
- 既有 `7051` 未启用、`7052` 超次数、`7011` 考生不存在保持不变

## 权限设计

不新增权限码。策略护栏在 Service 执行，不依赖前端按钮。

## 技术方案

开新补考前按序校验：

1. 考生存在。
2. 策略 `is_retake_enabled=1`，否则 `7051`。
3. 考试 `publish_status` 为 `PUBLISHED` / `RUNNING` / `ENDED`，否则 `7056`。`DRAFT`、`CANCELLED` 不可补考。
4. 若已有相同 `clientAttemptToken` 或进行中（`CREATED`/`ANSWERING`）尝试，直接恢复，不再占用新次数。
5. 当前时间落在补考窗口内：有 `retake_start_time` 则不得早于开始，有 `retake_end_time` 则不得晚于结束；两端都空则不额外限制窗口。窗口按闭区间。正考
   `open_start_time`/`open_end_time` 和 `late_forbid_seconds` 不约束补考。
6. 考生状态命中范围：
  - `FAILED`：必须 `candidate_status=FAILED` 且 `result_published_time` 非空。
  - `ABSENT`：必须 `ABSENT`。
  - `FAILED_OR_ABSENT`：`FAILED`（含已发布）或 `ABSENT`。
  - 及格、待批阅、待发布、作答中等均 `7055`。
7. 非作废补考尝试数 `>= retake_max_count` 则 `7052`。`retake_max_count=0` 表示不可再开新补考。

通用 `start` 在 `attemptType=RETAKE` 时走同一护栏，且不得为补考新建考生。

发布成绩后的正式分仍按既有 `retake_score_mode`（`HIGHEST`/`LATEST`）回写考生。学员答题详情
`answer_detail_mode=RETAKE_ENDED` 在启用补考且配置了 `retake_end_time` 时，以补考结束时间为准。

## 风险和取舍

- 不按人排期：窗口是整场考试策略，保持原模型。
- 补考换卷/换时长需要新字段，本次不扩展。
- 进行中补考允许在窗口结束后继续答完，截止时间仍是尝试上的 `deadline_time`。
