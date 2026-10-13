# Tasks：学员按考试 ID 受控读取封面

> 状态：已完成。

## Task 1：完成学员考试封面受控读取

- **状态**：已完成
- **Owner**：lims
- **输入**：本 Change；`docs/api/exam.openapi.json`；课程封面受控读取既有模式。
- **输出**：`GET /api/exam/learner/exams/{examId}/cover`；OpenAPI、零 DB 测试、QA。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：新增封面 GET；不改任务列表路径、权限和字段。
- **数据影响**：无 schema/migration 变化。
- **验收标准**：
  1. 本人可见已发布考试可内联或附件下载封面。
  2. 不可见考试返回 7001；无封面返回 7071。
  3. 不接受请求传入 `fileInfoId`。
  4. 任务列表不新增 `coverFileId`。
- **测试方式**：Red → Green；受影响零 DB；更新 OpenAPI。
