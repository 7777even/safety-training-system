# Spec Delta：M2 题库、试卷与考试前端 Mock 优先闭环

## ADDED Requirements

### Requirement: Contract Mock 与真实请求切换

系统 MUST 在 M2 前端 API 模块中保留与 OpenAPI 一致的 Mock 与真实请求分支。默认 MUST 使用 Mock；当
`VITE_USE_API_MOCK=false` 时，MUST 使用相同入参和响应类型调用真实接口。Mock MUST NOT 被作为真实联调或验收证据。

#### Scenario: 后端接口尚不可用

- **WHEN** `VITE_USE_API_MOCK` 未设置或不等于 `false`
- **THEN** 页面调用契约一致的本地 Mock，并能展示成功、失败和版本冲突反馈

#### Scenario: 后端接口可联调

- **WHEN** 设置 `VITE_USE_API_MOCK=false`
- **THEN** 页面不改变业务逻辑，直接调用已保留的真实 OpenAPI 请求分支

### Requirement: 独立的管理端编辑与编排页面

系统 MUST 将题目编辑、试卷组卷、试卷版本查看、考试详情和人员配置提供为独立路由页面。复杂工作流 MUST NOT 使用 Dialog 或
Drawer 承载；轻量确认可以使用确认弹窗。

### Requirement: 后台授权内部路由

系统 MUST 将后台列表页之外的新增、编辑、详情、版本和人员配置页面注册为不参与菜单导航的内部路由。系统 MUST
仅在用户已拥有来源列表菜单及该页面对应按钮权限时注册内部路由；未满足条件时 MUST NOT 注册。内部路由 MUST
继承来源菜单标识，以维持现有路由守卫的授权判断；后端仍 MUST 独立校验所有资源接口权限。

#### Scenario: 已授权用户直达新增页面

- **WHEN** 用户已拥有题库中心菜单和 `question:question:add`
- **THEN** 用户可直接访问题目新增页，且侧栏中不出现“新增题目”菜单

#### Scenario: 用户缺少内部页面权限

- **WHEN** 用户没有试卷管理的 `paper:paper:edit`
- **THEN** 编辑既有试卷的内部路由不被注册，访问该地址时按既有守卫回退到可访问菜单页

### Requirement: 双端考试作答与结果体验

系统 MUST 为 PC 和移动端分别提供考试任务、作答和结果页面。PC 作答页 MUST 提供考试状态、计时、提交入口、答题卡和题目工作区；移动端
MUST 使用适合触控和窄屏的独立布局。结果页 MUST 只在后端返回已发布成绩时展示分数和解析。

## MODIFIED Requirements

### Requirement: M2 前端验收

M2 前端开发阶段 MAY 使用 Contract Mock 完成页面和自动化测试；最终验收 MUST 在 `VITE_USE_API_MOCK=false`
下覆盖建题、组卷、考试编排、作答、批阅、成绩发布和课程回接的真实请求链路。

## REMOVED Requirements

无。
