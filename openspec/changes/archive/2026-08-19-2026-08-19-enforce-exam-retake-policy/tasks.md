# Tasks：补考策略护栏

## Task 1：完成考试补考策略护栏

- 状态：已实现
- Owner：lims
- 输入：
  - 既有 `sts_exam_policy` / `sts_exam_candidate` / `sts_exam_attempt` 模型
  - `POST /api/exam/candidates/{examCandidateId}/retake`
  - 本 Change proposal / design / spec-delta
- 输出：
  - `ExamAttemptServiceImpl` 按策略校验并开补考或恢复进行中尝试
  - 零 DB 测试覆盖启用、范围、窗口、次数、考试状态和恢复
  - `docs/api/exam.openapi.json` 补考规则、范围枚举和错误码
  - `docs/architecture/exam-retake-collaboration.md` 给前端的协作说明
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：
  - 修改 `docs/api/exam.openapi.json`
  - 不新增路径；复用 `ExamAttemptController.retake`
  - 复用权限 `exam:attempt:start`
  - 新增业务码 `7054`、`7055`、`7056`
- 数据影响：
  - 无数据库结构、migration 或生产数据变化
- 验收标准：
  1. 未启用补考返回 `7051`，且不创建尝试。
  2. 成绩未正式发布或考生及格时，默认/FAILED 范围返回 `7055`。
  3. `FAILED_OR_ABSENT` 允许已发布不及格或未参加；仅 `ABSENT` 范围拒绝不及格考生。
  4. 当前时间早于补考开始或晚于补考结束返回 `7054`。
  5. 非作废补考次数达到上限返回 `7052`；首考次数不单独导致超限。
  6. `ENDED` 且窗口内、范围命中的考生可开补考；`CANCELLED` 返回 `7056`。
  7. 进行中补考或相同幂等令牌恢复原尝试，不新增次数。
  8. 补考尝试时长快照等于原考试 `durationSeconds`；接口仍只有考生 ID 与幂等令牌。
  9. OpenAPI 按 `ExamAttemptController` 写明规则、范围枚举、错误码和 example。
- 测试方式：
  - Red：先增加 Service 护栏测试，观察范围、窗口、次数和 `ENDED` 可补考失败。
  - Green：以最少实现使目标测试通过。
  - 零 DB：在 `backend/` 执行受影响的 `ExamAttemptServiceImplTest`、`ExamAttemptListControllerTest`。
  - 不跑无关全量前端构建；无 schema 变化，不强制 IT。
- 实施检查项：
  - [x] 人工确认本 Change（对话确认：按照既有设计完善）
  - [x] TDD：补考策略护栏
  - [x] OpenAPI 规则、枚举、错误码与 example
  - [x] 双轴 Review
  - [x] 更新本 Task 状态、进度表、QA / Retro
