# Retro：学员考试查询与答案契约

## 做得好的地方

- 在实现前先以零 DB 测试表达本人隔离、答案恢复和成绩裁剪。
- 复用既有 `answerDetailMode` 与 `isScoreVisible`，没有新增数据库字段或自定义考试规则。
- 查询按 Candidate、Exam、Attempt、答案和答案项批量读取，避免按题逐条查询。

## 问题与原因

- 当前执行环境没有 Docker socket，Testcontainers MySQL IT 无法启动，真实 SQL 和逻辑删除语义尚未在本次环境验证。

## 改进方案

- 在具备 Docker 的开发或 CI 环境中补充 `ExamLearnerService` 的专用 IT，覆盖 Candidate/Attempt/Answer/AnswerItem
  关联、分页筛选和跨机构本人隔离。
- CI 固定提供 Docker/Testcontainers 能力，避免后端持久化变更只停留在零 DB 层。

# 2026-07-30 补充

- 原实现只裁剪了答案得分和正确性，没有把已冻结的正确答案及解析映射到学员 VO，导致规格描述与运行响应不一致。
- 修复后将“答案详情可见”和“解析可见”拆成两层判断，避免 `isAnalysisVisible=0` 时随正确答案一起泄露解析。
- 后续 Review 学员读模型时，应逐项核对 Entity 已冻结的敏感字段是否存在显式映射与显式清空，而不只检查 OpenAPI 描述。
