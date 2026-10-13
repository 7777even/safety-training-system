# Design：对齐学员端线下场次状态

## 状态语义

- `resultStatus`：该学员对该内容的个人完成结果。线下培训未评分时保持 `PENDING`；合格后完成，不合格后未通过。
- `sessionStatus`：线下场次当前执行状态。教师开始后为 `IN_PROGRESS`，结束为 `COMPLETED`，取消为 `CANCELLED`。
- 两个字段不得互相覆盖或推导：场次进行中不代表学员已完成，学员已完成也不回写场次状态。

## 契约与实现

- `TrainingParticipantResultItemVO` 新增可空 `sessionStatus`，仅 `executionType=OFFLINE` 时返回对应线下场次状态；其他内容类型返回 `null`。
- 后端读取结果投影时按 `executionId` 对应的线下场次填充该字段；没有执行实例时返回 `null`。
- PC Web 仅在线下内容卡上优先使用 `result.sessionStatus` 展示场次状态；卡片仍可显示个人成绩和获得学时，完成统计继续只使用 `resultStatus`。
- 学员线下场次详情保留既有 `sessionStatus` 和 `resultStatus`，界面分别标注“场次状态”“完成状态”。

## 兼容性与取舍

- 不新增数据表或落库字段，`sessionStatus` 是读取时的当前事实。
- 不以学员详情页逐项调用线下场次接口实现状态，避免列表内容数量增加时产生 N+1 HTTP 请求。
