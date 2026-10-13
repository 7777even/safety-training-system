# Tasks：考试从试卷版本带入作答规则

## 1. 完成考试从试卷版本带入作答规则并允许覆盖

- 状态：已实现
- Owner：lims
- 输入：
  - 本 Change `proposal.md` / `design.md` / `specs/exam-paper-rule-defaults/spec.md`
  - `ExamCreateRequest` / `ExamUpdateRequest` / `ExamServiceImpl`
  - `docs/api/exam.openapi.json`
  - 既有 `sts_paper_version` / `sts_paper_paper` / `sts_exam_exam`
- 输出：
  - 创建与草稿更新按 design 带出、覆盖、换绑重填、未换绑保留
  - 有试卷时及格分按试卷总分 × 比例计算
  - 创建时封面可带出；分类/编码/名称不带出
  - `ExamServiceImplTest` 覆盖规格场景
  - `docs/api/exam.openapi.json` 更新 required、字段注释、省略语义和 example
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：
  - 修改 `POST /api/exam/exams`、`PUT /api/exam/exams/{examId}`
  - 不新增路径或权限码；复用 `exam:exam:add` / `exam:exam:edit`
  - 无试卷缺规则字段：`1001`；试卷版本不存在：`7005`
- 数据影响：
  - 无数据库结构、migration 或生产数据回填
  - 考试规则字段保持 `NOT NULL`，带出结果物化写入
- 验收标准：
  1. 绑卷且省略时长/及格比例/题序/多选计分时，考试行等于试卷版本对应值。
  2. 绑卷同时提交不同时长时，时长为请求值，其余省略字段为试卷版本值。
  3. 草稿换绑且省略规则字段时，按新版本重填；换绑且提交时长时，时长为覆盖值。
  4. 未换绑的更新省略时长时，保留考试原覆盖值。
  5. 无试卷且省略时长时返回 `1001`，不创建考试。
  6. 绑卷时忽略请求 `passScore`，按 `totalScore × passRatio` 四舍五入到 2 位写入；无试卷时保留手填及格分。
  7. 创建且请求无封面时带出试卷 `coverFileId`；不写入试卷分类/编码/名称。
  8. 试卷版本不存在返回 `7005`，不落库。
  9. 开考仍冻考试时长/及格分，总分仍来自试卷版本。
  10. OpenAPI 按 `ExamController` 写明省略语义、及格分计算、错误码和 example。
- 测试方式：
  - Red：先在 `ExamServiceImplTest` 增加带出、覆盖、换绑、保留、无试卷 `1001`、版本 `7005`、及格分计算失败用例。
  - Green：以最少实现使目标测试通过。
  - 零 DB：在 `backend/` 执行 `mvn -pl sts-service-admin -am test -Dtest=ExamServiceImplTest,ExamAttemptServiceImplTest`
  - 无 schema 变化，不强制 IT；不跑前端构建。
- 实施检查项：

- [x] 1.1 取得人工确认后再改代码
- [x] 1.2 TDD：绑卷带出、覆盖、换绑重填、未换绑保留、无试卷必填、版本不存在
- [x] 1.3 TDD：有试卷时计算及格分；创建带出封面且不带分类
- [x] 1.4 实现创建/更新带出与覆盖，物化写入考试行
- [x] 1.5 同步 `docs/api/exam.openapi.json` 的 required、注释、example
- [x] 1.6 回归开考快照仍读考试规则；双轴 Review；更新本 Task 状态
