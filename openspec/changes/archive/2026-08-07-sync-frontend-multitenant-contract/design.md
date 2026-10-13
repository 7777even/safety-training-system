# 设计

## 契约来源

以以下已确认契约为唯一来源：

- `docs/api/system-admin.openapi.json`
- `docs/api/org.openapi.json`
- `docs/api/regulator.openapi.json`
- `docs/api/asset.openapi.json`
- 其他业务域 OpenAPI 中已删除的 `organizationId` 请求字段

## 前端边界

- `frontend/shared` 仅保存 PC 与移动端共同使用且来自 OpenAPI 的登录请求、用户会话和响应类型。
- PC Web 负责后台租户管理、系统管理、监管聚合和目标租户只读视角。
- 移动端只同步登录和会话恢复，不复制 PC 管理页面。
- 普通企业租户不显示监管菜单和目标租户切换能力。

## 主要行为

### 登录

登录表单必须提交 `orgCode`、`userCode` 和 SHA-256 密码摘要。`orgCode` 在前端 trim，后端负责大小写归一化。1002 统一显示后端防枚举错误信息。

### 租户上下文

普通业务请求不再由前端主动传入租户 `organizationId`。监管只读请求可通过 `X-Target-Tenant-Id` 选择企业租户，写请求不得携带该视角。

### 监管聚合

新增只读 API `/api/regulator/aggregates/tenants`，按租户展示后端返回的课程、培训计划和参训人员统计，不在前端重新计算。

### 清理

删除 `/api/org/members`、`/api/org/regulatory-relations` 调用及其专属页面、Mock 和路由引用。用户成员字段归入 `/api/system/users`；监管关系页面替换为监管聚合入口。

### 素材

播放格式只接受 `hls`、`mp3`、`pdf`、`excel`、`image`、`original`。外链与富文本不调用 play-info，5006 和 6011 使用明确中文提示。
