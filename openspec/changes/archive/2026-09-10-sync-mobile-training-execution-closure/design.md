# Design：移动端培训执行闭环契约同步

## 契约边界

- 培训上下文统一进入仅上传 `clientAttemptToken`、`platform=MOBILE` 和可选终端信息；不得传 `attemptType`。
- 线下内容进入返回 `offlineSessionId` 后，移动端仅调用 `GET /api/training/learner/offline-sessions/{offlineSessionId}`。不得调用管理端场次接口，也不得以查询参数作为成绩或状态事实。
- 内容完成状态、分数和获得学时只读取学员详情 `results` 或学员线下场次详情；不由前端推导，不触发结果同步。

## 交互

- 详情页在页面显示或从课程、答题页返回时重新加载，处理 `PENDING_REVIEW`、`PENDING_PUBLISH` 时引导查看既有答卷结果，不重复创建答卷。
- 内容卡同时显示 `requiredHours`（安排学时）和 `effectiveDurationSeconds`（获得学时）；成绩与获得学时为 `0` 时仍展示。
- 线下页显示场次名称、地点、起止时间、场次状态、本人分数、结果状态和获得学时；不显示签到、教师或其他学员数据。

## 兼容性与风险

- 学员线下详情需要计划处于 `RUNNING` 或 `ENDED`；后端 404/8155 由移动端显示真实错误信息。
- 本 Change 仅处理培训上下文。独立考试页面是否传作答类型，由其所属考试 OpenAPI 另行判断，禁止一并改动。
