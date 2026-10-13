# Tasks：同步草稿考试人员配置前端语义

> 状态：已完成。

## Task 1：支持草稿考试空阅卷人配置

- **状态**：已完成
- **Owner**：前端
- **输入**：`docs/api/exam.openapi.json` 中独立考试批阅人 GET/PUT 语义；后端 Change `2026-08-27-support-draft-exam-participant-configuration`。
- **输出**：草稿人员配置可读取空阅卷人、可保存空数组清空阅卷人；Mock 与 UI 行为一致。
- **前后端影响**：仅前端；复用既有 OpenAPI。
- **API 依赖**：`GET/PUT /api/exam/exams/{examId}/reviewers`、`POST /api/exam/exams/{examId}/candidates/bind-selection`。
- **数据影响**：无数据库影响；前端 Mock 的阅卷人集合可保存空数组。
- **验收标准**：
  1. 草稿考试阅卷人读取返回空数组时，人员配置页面正常显示空态，不出现“先发布”提示。
  2. 已选择考生范围后，未选择阅卷人仍可保存，PUT 请求含 `reviewerUserIds: []`。
  3. Mock 对空数组返回成功并清空已保存阅卷人。
  4. 课程考核阅卷人代码未改动；不新增 7062 的前端吞错或误导提示。
- **测试方式**：先新增会在当前代码失败的定向前端测试，再最小修改使其通过；已运行 `tests/exam-contract-readback.test.mjs`、`tests/exam-workspace-navigation-and-paper-defaults.test.mjs` 的目标用例（3/3）、PC Web typecheck 与目标差异检查。
