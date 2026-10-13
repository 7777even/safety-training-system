# Design：PC 培训执行闭环契约同步

## 契约边界

- 学员统一进入培训内容仅上传客户端尝试标识及终端信息；作答类型由服务端派生。
- 学员线下场次仅调用 `GET /api/training/learner/offline-sessions/{offlineSessionId}`，不得调用管理端场次或签到接口。
- 教师任务详情返回场次信息、乐观锁 `version`、参训名单、已评分信息和可执行动作；教师只在 `availableActions` 包含相应值时调用状态接口或线下成绩接口。

## 展示与交互

- 培训详情在重新进入路由时重新读取详情，直接展示后端投影状态；不在前端推断完成态或触发批量同步。
- `requiredHours` 标记为“安排学时”；`effectiveDurationSeconds`、`onlineHours`、`offlineHours` 标记为“获得学时”。数值 0 为真实值，必须显示。
- 教师名单每位参训人至多一个成绩。录入时固定提交 `scoreName=线下培训成绩`、`scoreSource=MANUAL`，教师填写得分与满分并由前端校验区间。
- 教师开始、结束或取消场次时，原样提交详情中的 `version`；成功或失败后均以重新读取详情为准，版本冲突由后端返回实际原因。

## 兼容性

- 页面不再引用已删除的 `/api/training/attendance*`。
- 不修改 `docs/api/training.openapi.json`；PC 类型与请求映射以其当前字段、错误码和权限为准。
