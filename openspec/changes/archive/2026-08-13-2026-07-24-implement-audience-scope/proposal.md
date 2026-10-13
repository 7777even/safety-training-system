## Why

BASE-02 已落地机构成员、标签/岗位与角色数据范围，但开课/直播/报名仍只存 `audience_id` 占位，无参与范围定义、规则、解析与冻结名单。阶段
1 主线硬依赖统一参与范围，否则无法校验“谁可见/谁可参加”。

依据已评审数据模型：`openspec/changes/archive/2026-07-22-design-safety-training-mysql-schema/ddl/01_org_common.sql` 与
`design.md` §6.1。

## What Changes

- 落地 `sts_common_audience`、`sts_common_audience_rule`、`sts_common_audience_member`。
- 提供参与范围 CRUD、规则维护、预览解析、冻结（FROZEN）与重新解析。
- 提供统一人员选择查询（按机构内用户/部门/标签/岗位筛选），供圈人与规则配置复用。
- 课程开课自报名/指派接入：有 `audience_id` 时校验用户是否在包含结果中（EXCLUDE 优先）。
- 同步 OpenAPI、权限点、schema 快照与测试。

## Capabilities

### New Capabilities

- `common-audience-scope`：参与范围模板/绑定、动态/冻结解析、统一人员选择。

### Modified Capabilities

- 课程开课报名：在存在参与范围时按解析结果校验准入。

## Non-goals

- 跨机构资源授权与监管关系（BASE-04）。
- 分享链接、提醒、消息、积分表。
- 完整前端圈人组件页面（可先用 ID + 人员选择 API；COURSE-06 联调时接入）。
- 考试/培训/问卷绑定参与范围的业务实现（表与解析服务可复用，业务绑定后续阶段）。

## Impact

- `database/migration`、`backend/sql/schema-common.sql`（新建）
- `com.sts.common.audience.*`（或 `com.sts.audience.*`）
- 课程报名服务接入校验
- `docs/api/common-audience.openapi.json`
