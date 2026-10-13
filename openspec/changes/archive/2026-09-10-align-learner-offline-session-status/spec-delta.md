# Spec Delta：对齐学员端线下场次状态

## MODIFIED Requirements

### Requirement：学员查看线下培训状态

学员培训详情的 `OFFLINE` 内容 SHALL 同时保留个人 `resultStatus` 和可空 `sessionStatus`。当线下场次存在时，`sessionStatus` SHALL 表示教师推进的实际场次状态；PC Web SHALL 用其展示线下内容卡的场次状态。

个人 `resultStatus` SHALL 继续按成绩结果确定；场次开始不得使未评分学员变为完成。PC Web 学员线下场次详情 SHALL 分别展示场次状态与个人完成状态。
