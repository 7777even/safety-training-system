# Spec Delta：PC 培训执行闭环契约同步

## ADDED Requirements

### Requirement：PC 学员读取培训执行结果

PC 学员培训详情 SHALL 使用后端内容结果投影展示完成状态、成绩与获得学时；学员进入考试或题库时不得指定作答类型。

考试或题库练习的结果状态为 `PENDING_REVIEW`、`PENDING_PUBLISH`、`COMPLETED`、`PASSED` 或 `FAILED` 时，PC 学员 SHALL 使用统一进入接口返回的既有 `examAttemptId` 跳转至培训考试结果页；不得再次进入作答页。

### Requirement：PC 教师录入线下成绩

PC 教师任务页 SHALL 从教师任务详情返回的参训名单选择录入对象，并且只调用线下成绩接口保存每人一条成绩，不得调用签到接口。

### Requirement：PC 教师推进线下场次

PC 教师任务页 SHALL 仅按教师任务详情 `availableActions` 展示开始、结束、取消入口，并原样提交详情 `version` 调用对应场次状态接口；操作后 SHALL 重新读取详情。

### Requirement：学时口径可区分

PC 页面 SHALL 将内容 `requiredHours` 与结果获得学时分开命名和展示，并保留零值。
