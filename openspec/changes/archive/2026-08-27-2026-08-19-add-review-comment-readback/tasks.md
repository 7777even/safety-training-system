# Tasks：批阅意见读取与教师批阅界面优化

## Task 1：批阅意见的受控读取与回显

- 状态：已完成（2026-08-19）
- Owner：AI
- 输入：既有 `ExamReviewRecord`、`ExamAnswerVO`、`GET /api/exam/attempts/{examAttemptId}`、
  `GET /api/exam/learner/attempts/{examAttemptId}` 及本 Change。
- 输出：管理端作答详情返回每题最近一次批阅意见；学员详情明确裁剪该字段；教师页面保存后刷新可回显。
- 前后端影响：后端、OpenAPI、PC Web；不修改数据库。
- API 依赖：既有管理端和学员端作答详情接口、既有单题批阅接口。
- 数据影响：仅读取 `sts_exam_review_record`；不修改历史记录或数据库结构。
- 验收标准：
  1. 教师保存“要点完整”后，刷新或重新进入批阅页仍看到“要点完整”。
  2. 多次批阅同一答案时只读取最近一次记录的意见。
  3. 学员作答详情的 `reviewComment` 恒为 `null`，无论成绩是否已发布。
  4. 查询不产生按题 N+1 批阅记录读取。
- 测试方式：先增加零 DB Service 失败测试和 PC Web 定向失败测试；实现后运行受影响后端测试、PC Web
  定向测试/typecheck/lint、OpenAPI JSON 校验和 `git diff --check`。如 Docker 可用，补充 Mapper IT。

## Task 2：主观题批阅紧凑布局

- 状态：已完成（2026-08-19）
- Owner：AI
- 输入：Task 1 的稳定作答详情数据、既有后台详情页视觉规范。
- 输出：作答、参考答案、评分状态、已给分、得分输入、批阅意见和保存操作的紧凑布局。
- 前后端影响：仅 PC Web；后端和 OpenAPI 不变。
- API 依赖：Task 1 的作答详情响应。
- 数据影响：无。
- 验收标准：题目卡片不再使用过宽的空白行；得分与意见字段具有明确中文标签；意见可回显且不影响评分保存。
- 测试方式：PC Web 定向页面回归、typecheck、受影响 lint 和 `git diff --check`。

## 完成记录

- 先新增管理端作答详情返回最近批阅意见的零 DB 失败测试，随后以批量答案和批阅记录查询装配 `reviewComment`；学员读取路径显式置空该字段。
- PC Web 在加载详情时以服务端 `reviewComment` 初始化意见输入框，Mock 同步保存该字段；主观题描述标签宽度和卡片内边距收紧。
- 已通过后端 `ExamAttemptServiceImplTest` 6/6、PC Web 定向测试 3/3、受影响 ESLint、PC Web typecheck、OpenAPI JSON 解析与
  `git diff --check`；未运行 MySQL IT（本次仅新增服务层批量映射，未提供 Docker 验证）。
