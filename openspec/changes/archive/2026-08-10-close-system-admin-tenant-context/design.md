# 设计

## 用户与机构主数据

用户创建/编辑表单使用标签和岗位查询接口生成选择项，提交 `memberType`、`email`、`jobTitle`、`entryDate`、`tagIds`、
`positionIds`、`primaryPositionId`。主岗位只能从已选择岗位中选择。

标签、岗位、部门、角色页面通过 `system:tenant:list` 判断是否允许选择租户；普通租户使用当前会话的 `organizationId`，不提交跨租户筛选。

## 监管视角

监管汇总路由要求当前用户 `orgType=REGULATOR` 且拥有 `regulator:aggregate:view`。目标租户只读视角继续由请求拦截器在
GET/HEAD/OPTIONS 请求中追加 `X-Target-Tenant-Id`，退出或会话清理时清除。

## 错误提示

请求层集中维护 4061～4065 的租户视角提示，页面只负责展示错误对象消息。

## 验收数据

验收数据写入 `docs/requirement/superadmin-validation-data.md`，仅作为通过 superAdmin 界面录入的样例，不绕过 API 直接写库。
