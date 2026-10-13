# Tasks：同步课程考核与独立考试前端

> 状态：已于 2026-08-26 获得人工确认。
>
> 测试 seams：PC/移动 API 函数与学员/管理端页面交互；用户在确认方案时已确认以契约读取测试和页面交互测试作为验收边界。

## Task 1：完成课程内容资源直连与前端契约同步

- 状态：已完成（2026-08-26；规格符合性 Review：通过；代码质量 Review：通过）
- Owner：Codex
- 输入：`course.openapi.json`、`question.openapi.json`、`paper.openapi.json`。
- 输出：课程内容仅支持题库和试卷版本资源；PC/移动/shared 类型、API 与 Mock 不再包含课程 `examId` 或旧 `/exam-attempts`。
- 前后端影响：PC Web、移动端与 shared；不改后端。
- API 依赖：课程内容、课程考核 Attempt、题库与试卷查询接口。
- 数据影响：无。
- 验收标准：管理端不展示独立考试/专项练习资源；课程学员接口使用 `/attempts` 并接收课程考核响应。
- 测试方式：先新增失败的契约读取/API 行为测试，再更新实现；运行受影响 workspace 的 typecheck 与目标测试。

## Task 2：完成开课内课程测验管理

- 状态：已完成（2026-08-26；规格符合性 Review：通过；代码质量 Review：通过）
- Owner：Codex
- 输入：Task 1、课程测验阅卷人/批阅/结果契约。
- 输出：具体开课内可配置课程测验阅卷人、处理待批阅题目和查看自动生效结果。
- 前后端影响：仅 PC Web；不改后端。
- API 依赖：课程 assessment reviewers、review tasks、answers review、results。
- 数据影响：无。
- 验收标准：题库练习不显示阅卷操作；课程测验不显示人工发布成绩入口。
- 测试方式：页面交互与 API 读取测试、PC typecheck。

## Task 3：完成 PC 与移动学员课程考核闭环及独立考试同步

- 状态：已完成（2026-08-26；规格符合性 Review：通过；代码质量 Review：通过）
- Owner：Codex
- 输入：Task 1、最新 course/exam OpenAPI。
- 输出：两端学员可完成课程题库练习和课程测验；独立考试请求和来源摘要与契约一致。
- 前后端影响：PC Web、移动端与 shared；不改后端。
- API 依赖：课程考核 Attempt、独立考试 task/attempt/review 契约。
- 数据影响：无。
- 验收标准：课程自动生效与独立考试人工发布文案隔离；已移除请求字段不再发送。
- 测试方式：PC/移动页面交互、接口读取测试、两端 typecheck/lint。
