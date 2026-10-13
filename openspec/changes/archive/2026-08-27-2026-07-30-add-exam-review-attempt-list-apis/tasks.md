# Tasks：考试批阅任务与作答成绩分页接口

## Task 1：完成教师批阅任务与考试作答成绩分页能力

- 状态：已实现，MySQL IT 环境阻塞
- Owner：lims
- 输入：
  - 已完成的 `2026-07-24-implement-question-paper-exam`
  - `2026-07-29-complete-learner-exam-read-contract` 的成绩发布状态定义
  - 既有 Exam、Reviewer、Candidate、Attempt、AttemptQuestion、Answer、ReviewRecord 事实
  - 本 Change proposal / design / spec-delta
- 输出：
  - 当前教师批阅任务分页接口
  - 管理端考试作答成绩分页接口
  - 查询 DTO、列表 VO、Service 和 Mapper 聚合查询
  - `docs/api/exam.openapi.json`
  - 零 DB 测试、相关 Mapper IT、QA / Retro 和进度记录
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：
  - 修改 `docs/api/exam.openapi.json`
  - 新增 `GET /api/exam/review-tasks`
  - 新增 `GET /api/exam/attempts`
  - 复用 `ExamAttemptController`
  - 复用权限 `exam:review`、`exam:attempt:list`
- 数据影响：
  - 仅读取既有考试、批阅人、考生、作答、题目快照、答案和批阅记录表
  - 无数据库结构、migration 或生产数据变化
- 验收标准：
  1. 当前登录教师可分页查询本人被分配考试中的主观题批阅任务，未分配给本人的考试不可见。
  2. 教师批阅任务支持考试、批阅状态、学员关键字和提交时间范围筛选；默认待阅优先并稳定分页。
  3. 考试管理人员可在既有机构数据范围内分页查询作答记录，越权机构数据不可见。
  4. 作答记录支持考试、Attempt 状态、批阅状态、成绩发布状态、学员关键字和提交时间范围筛选。
  5. 两个列表均返回 `examId`、`examAttemptId`、`examCandidateId`、必要学员展示信息、提交时间、批阅状态和成绩发布状态。
  6. 批阅任务同时返回 `examAnswerId`、题目快照、学员答案及最近批阅摘要，能够直接调用既有单题批阅接口。
  7. 作答记录返回 Attempt 次数/类型/状态、时间、内部成绩、是否及格和正式发布时间；未发布状态不得被误标为已发布。
  8. 学员信息优先读取 Candidate 冻结快照，不返回身份证号、手机号等非必要敏感字段。
  9. Mapper 使用真实数据库分页，不进行全量内存分页；列表查询不存在逐项加载的 N+1。
  10. OpenAPI 按 `ExamAttemptController` 分类，每个参数和字段有中文注释、类型、可空性及 example；成功响应包含完整分页
      example、权限和错误响应。
  11. 不修改前端、不新增生产依赖、不修改数据库结构、判分规则、批阅追加规则或学员成绩可见性。
- 测试方式：
  - Red：先增加 Controller / Service / Mapper IT，分别观察身份限制、筛选、状态派生、真实分页和机构隔离失败。
  - Green：以最少实现使目标测试通过。
  - 零 DB：在 `backend/` 执行 `mvn -pl sts-service-admin -am test`，覆盖参数绑定、权限下的 Service 委托、当前批阅人限制、状态派生和敏感字段边界。
  - 带 DB：在 `backend/` 执行 `mvn -pl sts-service-admin -am -Pintegration-test test`，验证真实
    join、分页、排序、批阅人隔离、机构数据范围和最新批阅记录。
  - OpenAPI：JSON 解析、Controller tag、字段、参数、example、权限和错误响应对齐检查。
  - 文档：`git diff --check`。
- 实施检查项：
  - [x] 人工确认本 Change（2026-07-30）
  - [x] TDD：教师批阅任务分页与批阅人隔离
  - [x] TDD：作答成绩分页与机构数据范围
  - [x] TDD：批阅/发布状态派生及筛选
  - [ ] Mapper 聚合查询与 MySQL IT（2026-07-30：Docker 环境不可用，Testcontainers 未启动）
  - [x] OpenAPI schema、examples、权限和错误码
  - [x] 双轴 Review：规格符合性与代码质量
  - [x] 更新本 Task 状态、进度表、QA / Retro
