# Tasks：考试资源名称回显

## T1 查询响应名称字段 [x]

- **Owner**：后端
- **输入**：考试的 `paperVersionId` 或 `questionBankId`
- **输出**：`ExamVO` 返回 `paperName`、`paperVersionNo`、`questionBankName`
- **后端影响**：Exam VO、组装服务、定向单测
- **API 依赖**：`GET /api/exam/exams`、`GET /api/exam/exams/{examId}`
- **数据影响**：无
- **验收**：绑定试卷返回名称与版本号；绑定题库返回名称；无绑定或关联缺失返回空名称，不暴露内部 ID
- **测试**：`ExamServiceImplTest`

## T2 契约与 PC 展示 [x]

- **Owner**：前端
- **输入**：扩展后的 `ExamVO`
- **输出**：列表与详情使用名称展示资源，列表补齐类型和开放时间
- **前端影响**：考试 API 类型、列表、信息面板、定向测试
- **API 依赖**：T1 返回字段
- **数据影响**：无
- **验收**：列表不显示资源 ID；详情不以 ID 作为资源名称回退
- **测试**：PC 定向 Node 测试、类型检查、lint

## 验证记录

- `mvn -pl sts-service-admin test "-Dtest=ExamServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"`：45/45 通过。
- `node --test --test-name-pattern "考试列表和详情通过 Contract 名称" tests/exam-management.test.mjs`：通过。
- `pnpm typecheck`、改动路径 ESLint、`git diff --check`：通过。
