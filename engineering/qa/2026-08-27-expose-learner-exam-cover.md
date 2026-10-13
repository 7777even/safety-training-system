# QA：学员按考试 ID 受控读取封面

日期：2026-08-27  
OpenSpec：`openspec/changes/2026-08-27-expose-learner-exam-cover/`  
Owner：lims

## 验收范围

- 新增 `GET /api/exam/learner/exams/{examId}/cover`，按考试 ID 读取当前学员可见独立考试封面。
- 可见性与任务列表一致：已发布/进行中/已结束且当前用户是 Candidate。
- 不可见返回 404 / `7001`；无封面或文件不可读返回 404 / `7071`。
- 默认内联，`download=true` 附件下载；成功响应为原始字节，不包 Result。
- 不改任务列表字段，不改库表，不改封面上传。

## TDD 证据

1. 零 DB 先覆盖 `openCover` 成功、不可见 7001、草稿 7001、无封面 7071。
2. Controller 验证 inline/attachment 与路径绑定。
3. WebSlice 验证原始字节成功响应，以及 7001/7071 使用统一错误外壳。

## 自动化结果

| 验证                                                                                                   | 结果          |
|------------------------------------------------------------------------------------------------------|-------------|
| 受影响零 DB：`ExamLearnerServiceImplTest`、`ExamLearnerControllerTest`、`ExamLearnerControllerWebSliceTest` | 18 个通过，0 失败 |
| `python3 -c "import json; json.load(open('docs/api/exam.openapi.json'))"`                            | 通过          |
| `git diff --check`（本 Change 后端与 OpenAPI）                                                             | 通过          |

## Review

- 规格符合性：通过。只实现封面 GET；任务列表未增加 `coverFileId`；授权边界是 `examId`。
- 代码质量：通过。可见性复用学员任务口径；无封面与文件不可读收口为 7071；前端不在本协作范围。

## 未执行项

- 未跑全量零 DB 与 IT：本 Change 无 schema/SQL/持久化变化。
- 未实施前端页面、Mock 或联调，前端以 `docs/api/exam.openapi.json` 为准另开。
- 未操作生产环境或生产数据。
