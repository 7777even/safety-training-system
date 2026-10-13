# Spec Delta：教学任务菜单与初始化权限

## ADDED Requirements

### Requirement：教师可从培训管理进入教学任务

系统 MUST 在培训管理目录提供“教学任务”菜单，路由为 `/admin/business-center/training/teacher-tasks`，组件为既有教师任务列表页面，且菜单权限为 `training:teacher:task`。

#### Scenario：教师获得教学任务权限

- **WHEN** 登录用户具有 `training:teacher:task`
- **THEN** 前端显示并可解析“教学任务”菜单

#### Scenario：教师处理场次

- **WHEN** 教师进入教学任务详情
- **THEN** 场次开始、结束、取消和成绩录入仅由后端返回的 `availableActions` 及本人场次归属决定

### Requirement：门户培训中心具有学员培训列表权限节点

系统 MUST 在门户培训中心提供“培训列表”功能节点，并绑定 `training:learner:task`。

#### Scenario：学员查看培训任务

- **WHEN** 登录学员具有 `training:learner:task`
- **THEN** 可按既有培训中心流程查询本人培训任务
