## MODIFIED Requirements

### Requirement: 执行人与教师身份来自登录态

系统 MUST 从受信任登录态派生发布人、评分人和当前教师身份，不得使用客户端传入值冒充当前执行人。

#### Scenario: 查询本人教师任务

- **GIVEN** 当前登录用户是教师 A
- **WHEN** 用户查询教师任务
- **THEN** 系统只返回教师 A 被分配的任务
- **AND** 客户端不能通过提交教师 B 的用户 ID 查看教师 B 的任务

#### Scenario: 记录发布和确认审计

- **GIVEN** 当前登录用户执行发布或线下评分
- **WHEN** 操作成功
- **THEN** 对应审计用户 ID 取自当前登录态
- **AND** 客户端提交的同名身份字段不得覆盖该值

## ADDED Requirements

### Requirement: 线下培训公开契约不得暴露签到语义

培训与运维 OpenAPI MUST 删除签到窗口、签退要求、签到规则、签到记录错误码、签到来源类型和待确认签到统计。系统 MUST 删除
`training:attendance:list/edit/confirm` 权限及对应菜单种子。线下场次生命周期状态与启用状态 MUST
保持既有契约，创建或更新场次不得绕过专用状态流转接口。

#### Scenario: 前端读取线下相关 OpenAPI

- **WHEN** 前端读取培训内容配置、线下场次创建/更新/查询和教师工作台 Schema
- **THEN** 所有请求、响应、required 列表、字段说明和示例均不包含签到语义
- **AND** 场次名称、教师、地点、起止时间、状态及计划/阶段/内容关联仍按适用接口保留

#### Scenario: 初始化权限菜单

- **WHEN** 系统校验或初始化菜单权限
- **THEN** 菜单和角色授权均不包含 `training:attendance:list/edit/confirm`
- **AND** 其他线下场次、教师任务和成绩权限保持不变
