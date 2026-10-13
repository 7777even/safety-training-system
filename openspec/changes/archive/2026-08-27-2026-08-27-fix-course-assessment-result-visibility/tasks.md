# Tasks：课程考核学员作答详情可见性

> Owner / 执行身份：`lims`。仅后端；前端以 OpenAPI 为准另开。

## Task 1：完成课程考核学员作答详情的自动生效可见性

- 状态：已完成（2026-08-27；规格符合性 Review：通过；代码质量 Review：通过）
- Owner：`lims`
- 输入：已确认课程自动生效规则；当前 `GET .../attempts/{attemptId}` 误用独立考试发布裁剪；功能项 22、48～49。
- 输出：课程题库练习和课程测验学员详情按 `RESULT_EFFECTIVE` 返回 `EFFECTIVE` 及成绩、标准答案、题目得分和解析；未生效时仍隐藏；独立考试读取不变；OpenAPI
  与架构说明同步。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：修改 `docs/api/course.openapi.json` 的课程作答详情 description、example 和字段说明；不改路径、权限码和错误码。
- 数据影响：无。
- 验收标准：
  1. 课程题库练习或纯客观课程测验结果生效后，学员详情返回 `scorePublishStatus=EFFECTIVE`、`scoreVisible=true`
     ，以及总分、题目得分、标准答案和解析。
  2. 课程测验待主观题批阅时返回 `UNPUBLISHED` / `scoreVisible=false`，可见本人答案，不可见标准答案、得分和解析。
  3. 不因缺少 `examId` 或 `ExamPolicy` 而把已生效课程结果当成未发布。
  4. 独立考试未发布/已发布及 `answerDetailMode` 行为保持不变。
  5. 学员详情不返回 `reviewComment`。
- 测试方式：`ExamLearnerServiceImpl` 零 DB TDD；受影响模块
  `mvn -pl sts-service-admin -am test -Dtest=ExamLearnerServiceImplTest`；不跑 IT。
