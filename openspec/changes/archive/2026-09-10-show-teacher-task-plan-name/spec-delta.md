# Spec Delta：教师任务列表显示所属培训

## MODIFIED Requirements

### Requirement：教师查询线下场次任务

`GET /api/training/teacher-tasks` SHALL 在每条教师任务摘要中返回 `trainingPlanId` 和可空 `trainingPlanName`。PC Web 教师任务列表 SHALL 显示所属培训名称；当名称不可获得时 SHALL 显示明确的降级文案，且不得隐藏该任务。
