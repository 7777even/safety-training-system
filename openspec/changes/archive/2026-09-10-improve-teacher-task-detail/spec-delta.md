# Spec Delta：优化教师任务详情的名单与录分提示

## MODIFIED Requirements

### Requirement：教师读取线下场次名单并录入成绩

教师任务详情 SHALL 返回本场应到参训人的 `trainingParticipantId`、`userId`、可空 `userName`、既有成绩和结果状态。PC 教师任务页 SHALL 优先使用 `userName` 展示学员；姓名缺失时 SHALL 明确提示未获取姓名并保留用户 ID 辅助识别。

PC 教师任务页 SHALL 仅在详情 `availableActions` 包含 `RECORD_SCORE` 时显示录入或修改成绩入口。场次未开始、已结束、已取消或后端未授权录分时，页面 SHALL 向教师展示相应中文原因，不得保留无说明的空操作栏。

教师没有查看或处理该场次权限时，PC Web SHALL 展示权限提示，不得把访问拒绝表述为场次未开始。

### Requirement：教师线下培训说明

PC 教师任务详情 SHALL 以面向教师的业务文案说明：教师录入参训人员成绩是本期线下培训的完成依据。页面不得在该说明中出现签到、接口、开发或实现细节。
