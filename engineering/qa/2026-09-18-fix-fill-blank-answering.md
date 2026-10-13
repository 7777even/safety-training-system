# QA：填空题按空位作答

## 范围

OpenSpec Change：`2026-09-18-fix-fill-blank-answering` Task 1～4。

- 后端：`ExamLearnerServiceImpl`、`ExamAttemptServiceImpl`、`ExamAttemptQuestionVO`。
- 契约：`docs/api/exam.openapi.json` 的 `ExamAttemptQuestionVO.blankCount`。
- 前端：PC 作答页、移动端独立考试作答页、移动端课程考核作答页。

不变：题目答案配置、判分规则、权限、数据库结构与接口路径。

## 验收结论

1. 后端仅从冻结快照派生 `blankCount`；学员未获准查看答案时仍不返回正确答案 JSON。
2. PC、移动独立考试、移动课程考核均按空位显示“第 N 空”输入框，无任何分隔符要求。
3. 两端统一暂存 `BLANK` items，键和排序均为 `1..blankCount`；未填满的多空题保持未答。

## 自动化验证

- `mvn -pl sts-service-admin -Dtest=ExamLearnerServiceImplTest test`：23/23 通过，覆盖两空 `blankCount=2` 与未发布答案快照脱敏。
- `node --test frontend/pc-web/tests/fill-blank-answering.test.mjs`：4/4 通过。
- PC Web、移动端 `vue-tsc --noEmit`：通过。
- PC 作答页 ESLint：通过；移动独立考试页 ESLint：通过。
- `openspec validate 2026-09-18-fix-fill-blank-answering --strict`：通过。
- OpenAPI JSON 解析与 `git diff --check`：通过。

## 未完成的真实验收

未提供可用的非生产学员登录态与含多空题的冻结作答，故未做真实 PC/H5 浏览器接口验收；自动化验证不等同于真实端到端验收。

## 既有问题

移动课程考核页的定向 ESLint 仍被提交前已有的 `Event` 全局声明（第 42 行）阻断；本次新增输入框的两处样式告警已消除。
