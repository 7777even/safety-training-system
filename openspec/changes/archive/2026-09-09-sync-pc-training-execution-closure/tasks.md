# Tasks：PC 培训执行闭环契约同步

> Owner：fxw11。状态：代码与定向自动验证完成；真实登录态验收待执行。

## 1. 学员培训结果与线下场次契约适配

- [X] **输入**：Training OpenAPI 学员详情、统一进入、学员线下场次接口。
- [X] **输出**：学员详情展示真实投影、安排/获得学时；考试不传作答类型；线下页展示本人场次与成绩。
- [X] **前后端影响**：仅 PC Web；后端以 OpenAPI 为准。
- [X] **API 依赖**：learner detail、enter、learner offline session。
- [X] **数据影响**：无。
- [X] **验收标准**：不再传 `attemptType`；不调用管理端线下场次或签到接口；零分和零获得学时不丢失。
- [X] **测试方式**：学员 API/页面 Node 回归与 PC Web typecheck。

## 2. 教师线下成绩闭环适配

- [X] **输入**：Training OpenAPI 教师任务、教师任务详情、线下成绩接口。
- [X] **输出**：教师可按后端名单逐人录入或更新唯一线下成绩。
- [X] **前后端影响**：仅 PC Web；后端以 OpenAPI 为准。
- [X] **API 依赖**：teacher tasks、teacher task detail、offline scores。
- [X] **数据影响**：无。
- [X] **验收标准**：页面及 API 无签到引用；成绩保存后刷新名单及统计。
- [X] **测试方式**：教师 API/页面 Node 回归与 PC Web typecheck。

## 3. 管理侧结果学时语义同步

- [X] **输入**：Training OpenAPI 参训人结果与获得学时字段。
- [X] **输出**：计划执行结果区分安排学时和获得学时。
- [X] **前后端影响**：仅 PC Web；后端以 OpenAPI 为准。
- [X] **API 依赖**：计划参训人、参训人结果。
- [X] **数据影响**：无。
- [X] **验收标准**：页面不将获得学时表述为计划安排学时。
- [X] **测试方式**：页面静态回归、PC Web typecheck。

## 待人工验收

- [X] 使用学员、教师真实登录态验证课程/考试/题库交卷后的投影刷新，以及线下场次开始、结束、取消、合格/不合格成绩和获得学时。

## 4. 教师线下场次状态操作

- [X] **输入**：教师任务详情的 `availableActions`、`version` 与线下场次状态接口。
- [X] **输出**：教师按后端授权开始、结束或取消场次；每次操作后刷新详情。
- [X] **前后端影响**：仅 PC Web；后端以 OpenAPI 为准。
- [X] **API 依赖**：offline session start、complete、cancel。
- [X] **数据影响**：无。
- [X] **验收标准**：请求携带详情返回的 `version`；页面不自行判断可执行状态；录分仅由 `RECORD_SCORE` 控制。
- [X] **测试方式**：教师 API/页面 Node 回归与 PC Web typecheck。

## 5. 学员培训终态答卷只读查看

- [X] **输入**：培训学员内容结果投影与统一进入接口返回的既有 `examAttemptId`。
- [X] **输出**：考试、题库练习处于待阅卷、待发布、已完成、已通过或未通过时，进入培训结果页；仅作答中的答卷进入作答页。
- [X] **前后端影响**：仅 PC Web；不修改后端契约。
- [X] **API 依赖**：learner detail、enter、既有考试结果查询。
- [X] **数据影响**：无。
- [X] **验收标准**：已完成并提交的培训考试点击“查看答卷”进入 `/portal/training/attempt/{examAttemptId}/result`，可由既有结果页按后端可见范围展示本人答案与解析。
- [X] **测试方式**：学员培训详情 Node 回归与 PC Web typecheck。
