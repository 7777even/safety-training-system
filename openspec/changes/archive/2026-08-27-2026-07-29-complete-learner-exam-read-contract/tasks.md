# Tasks：补全学员考试查询与答案契约

前后端影响：仅后端；前端以 OpenAPI 为准另开。Owner：lims

> 当前状态：已确认实施；MySQL IT 受执行环境 Docker 缺失阻塞。

## Task 1：完成学员本人考试任务与作答恢复能力

- 状态：已实现，MySQL IT 环境阻塞
- Owner：lims
- 输入：
  - 已完成的 `2026-07-24-implement-question-paper-exam`
  - `sts_exam_candidate`、`sts_exam_attempt`、`sts_exam_answer`、`sts_exam_answer_item` 既有数据事实
  - 本 Change proposal / design / spec-delta
- 输出：
  - 学员本人考试任务分页查询
  - 学员本人 Attempt 详情及暂存答案恢复
  - `ExamLearnerTaskVO`、`ExamAnswerVO`、`ExamAnswerItemVO`
  - 成绩发布状态和服务端字段可见性裁剪
  - 本人执行链校验与对应权限
  - `docs/api/exam.openapi.json`
  - 零 DB 测试、相关 Mapper IT、QA / Retro 和进度记录
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：
  - 修改 `docs/api/exam.openapi.json`
  - 新增 `GET /api/exam/learner/tasks`
  - 新增 `GET /api/exam/learner/attempts/{examAttemptId}`
  - 补全 `ExamAttemptVO`、`ExamAttemptQuestionVO`、`ExamAnswerVO`、`ExamAnswerItemVO`
- 数据影响：
  - 仅读取既有 `sts_exam_exam`、`sts_exam_candidate`、`sts_exam_attempt`、`sts_exam_attempt_question`、
    `sts_exam_attempt_option`、`sts_exam_answer`、`sts_exam_answer_item`
  - 无数据库结构、migration 或生产数据变化
- 验收标准：
  1. 当前登录学员可分页查询本人的考试任务，支持约定筛选，并得到稳定的任务状态、成绩发布状态和可用动作。
  2. 接口不接受调用方传入 `userId`；跨机构但属于本人的 Candidate 可见，其他用户的任务不可见。
  3. 当前登录学员可读取本人的 Attempt，刷新或重新进入后可恢复文本、选项和填空答案项。
  4. 非本人 Attempt 返回 HTTP 403；不存在返回 HTTP 404，并使用 exam 业务错误码。
  5. 未发布时不得向学员返回 Attempt 汇总成绩、答案级评分、正确性、正确答案、正确选项标记或题目解析。
  6. 已发布时返回正式成绩、及格状态和发布时间；分项成绩、正确答案和解析仅按已确认的 `answerDetailMode` 策略开放，未明确时默认隐藏。
  7. `ExamAnswerVO` / `ExamAnswerItemVO` 覆盖实体中对外有意义的答案、答案项、评分和时间字段，每个字段具有中文
     description、类型、可空性和 example。
  8. OpenAPI 按 Controller 分类，每个新增或修改接口均有 summary、description、参数 example、成功响应 example、错误码和
     `x-permission`。
  9. 查询题目、选项、答案和答案项不存在按题逐条查询的 N+1。
  10. 不修改 `frontend/`，不新增生产依赖，不修改数据库结构和既有判分规则。
- 测试方式：
  - Red：先增加 Controller / Service / Mapper IT，分别观察任务查询、本人校验、答案恢复和字段裁剪失败。
  - Green：以最少实现使目标测试通过。
  - 零 DB：在 `backend/` 执行 `mvn -pl sts-service-admin -am test`，至少覆盖受影响的考试 Controller / Service 测试。
  - 带 DB：在 `backend/` 执行 `mvn -pl sts-service-admin -am -Pintegration-test test`，验证真实分页、关联查询和用户隔离。
  - OpenAPI：JSON 解析、Controller/字段/example/权限对齐检查。
  - 文档：`git diff --check`。
- 实施检查项：
  - [x] TDD：学员任务列表与身份隔离
  - [x] TDD：Attempt 本人访问与暂存答案恢复
  - [x] TDD：成绩发布前后字段可见性
  - [x] TDD：正确答案 / 标准答案按 answerDetailMode 裁剪，解析额外按 isAnalysisVisible 裁剪（2026-07-30 缺陷修复）
  - [ ] Mapper 聚合查询与 MySQL IT（2026-07-29：Docker socket 不可用，Testcontainers 未启动）
  - [x] OpenAPI schema、examples、权限和错误码
  - [x] 双轴 Review：规格符合性与代码质量
  - [x] 更新本 Task 状态、进度表、QA / Retro
