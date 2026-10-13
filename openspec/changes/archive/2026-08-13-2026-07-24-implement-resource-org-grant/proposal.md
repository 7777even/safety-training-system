## Why

BASE-02/03 补齐机构成员与参与范围后，课程跨机构复用仍缺监管关系与资源授权。阶段 1.1 要求最小跨机构资源授权模型，否则无法支撑后续题库/试卷/培训模板的
VIEW/USE/MANAGE。

依据已评审：`sts_org_regulatory_relation`、`sts_common_resource_org_grant`（可选本期最小资源管理员权限表）。

## What Changes

- 落地监管关系 CRUD（REGULATES/MANAGES、有效期、启停）。
- 落地资源机构授权 CRUD（VIEW/USE/MANAGE、有效期、启停）；校验授权方为资源所属机构。
- 提供授权校验服务：`ResourceGrantService.canAccess(ownerOrg, resourceType, resourceId, granteeOrg, requiredLevel)`
  ；等级包含关系 MANAGE⊃USE⊃VIEW。
- 课程域最小接入：查询/引用课程时，若非本机构资源，须有效 USE 及以上授权（或监管关系+授权，按 design 明确）。
- OpenAPI、权限点、schema、测试与进度回填。

## Capabilities

### New Capabilities

- `org-resource-grant`：监管关系与跨机构资源授权。

## Non-goals

- 完整资源管理员权限 UI（`sts_common_resource_admin_permission` 可落表但管理 API 可简化或同 Change 最小 CRUD）。
- 分享链接/消息/积分。
- 题库试卷培训标准的全量授权业务（服务可复用，业务接入后续）。

## Impact

- migration、`schema-org.sql`/`schema-common.sql` 扩展
- `com.sts.org` 监管关系；`com.sts.audience` 或 `com.sts.grant` 资源授权
- `docs/api` 扩展；课程可见性校验挂钩
