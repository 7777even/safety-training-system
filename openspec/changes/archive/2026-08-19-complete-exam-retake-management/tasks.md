# Tasks：完善考试补考管理与成绩状态展示

> 状态：Task 1～3 已完成

## Task 1：完善考试补考策略配置

- 状态：已完成

- Owner：Codex
- 输入：既有 `ExamPolicyRequest`、考试创建/更新接口和 OpenAPI 补考策略字段。
- 输出：考试工作台可配置完整补考范围、开放窗口、次数、成绩取值和记录可见性，并采用真实用户可理解的文案。
- 前后端影响：仅 PC Web；前端以 OpenAPI 为准，不修改后端、接口、权限或数据结构。
- API 依赖：`POST /api/exam/exams`、`PUT /api/exam/exams/{examId}` 的 `policy` 字段。
- 数据影响：保存既有考试策略字段，不新增数据。
- 验收标准：补考范围含三种契约值；未填写范围按“不及格”处理；补考窗口和次数说明准确，不将补考表述为新建考试。
- 测试方式：先新增策略字典与表单默认值的自动化断言，观察失败后完成实现；执行目标前端测试、类型检查和轻量自审。

## Task 2：在成绩处置中开具考生补考

- 状态：已完成

- Owner：Codex
- 输入：成绩处置页面、考试详情策略、作答记录中的考生 ID、既有补考接口。
- 输出：从考试情况和成绩列表均可进入携带考生 ID 的成绩处置页；具备权限且补考已启用时可发起开补考；同一次操作的失败重试使用同一令牌。
- 前后端影响：仅 PC Web；前端以 OpenAPI 为准，不修改后端、接口、权限或数据结构。
- API 依赖：`GET /api/exam/exams/{examId}`、`POST /api/exam/candidates/{examCandidateId}/retake`。
- 数据影响：由后端创建一次新的补考作答；前端不直接修改正式成绩。
- 验收标准：请求只携带 `examCandidateId` 与 `clientAttemptToken`；不提交考试时间、作答时长、试卷、范围、次数或成绩；业务失败显示接口返回的消息；成功后反馈已开通并刷新。
- 测试方式：先增加请求参数、幂等令牌复用、入口路由参数的自动化断言，观察失败后实现；执行目标前端测试、类型检查和轻量自审。

## Task 3：修复成绩处置及发布流水的枚举翻译

- 状态：已完成

- Owner：Codex
- 输入：成绩处置页面、成绩列表、`ExamAttemptRecordVO` 和成绩发布流水契约。
- 输出：作答状态、发布方式、发布结果及相关筛选条件均显示中文业务文案。
- 前后端影响：仅 PC Web；前端以 OpenAPI 为准，不修改后端、接口、权限或数据结构。
- API 依赖：`GET /api/exam/attempts/{examAttemptId}`、`GET /api/exam/attempts/{examAttemptId}/score-publish-logs`、`GET /api/exam/attempts`。
- 数据影响：无。
- 验收标准：截图中的 `PUBLISHED`、`SINGLE`、`FAILED` 不再直接出现在用户可见位置；未知值使用中文兜底文案。
- 测试方式：先增加枚举映射与页面引用的自动化断言，观察失败后实现；执行目标前端测试、类型检查和轻量自审。
