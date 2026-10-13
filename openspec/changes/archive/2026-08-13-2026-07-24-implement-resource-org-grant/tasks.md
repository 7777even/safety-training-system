# Tasks：跨机构资源授权与监管关系（BASE-04）

## Task 1：监管关系与资源授权表

- 状态：已完成
- Owner：AI
- 输入：已评审 DDL；BASE-02/03
- 输出：migration；schema 同步；Entity/Mapper；IT
- 验收标准：唯一约束、软删复用、有效期字段落库
- [x] migration `V20260724150000__create_regulatory_relation_and_resource_grant.sql`
- [x] `schema-org.sql` / `schema-common.sql` 同步
- [x] Entity/Mapper + Mapper IT

## Task 2：监管关系 CRUD + 资源授权 CRUD/校验 + 课程钩子

- 状态：已完成
- Owner：AI
- 输入：Task 1
- 输出：API、OpenAPI、权限、零 DB + IT、课程跨机构 USE 校验
- 验收标准：等级包含、有效期、本机构不拦、无授权跨机构详情失败
- [x] 监管关系 CRUD `/org/regulatory-relations`
- [x] 资源授权 CRUD + check `/common/resource-grants`
- [x] ResourceGrantService MANAGE⊃USE⊃VIEW；课程详情跨机构 USE 钩子
- [x] 包扫描、menus.json、`docs/api/resource-grant.openapi.json`
- [x] 零 DB 与 Mapper IT（35 + 4）

## Task 3：进度与 QA

- 状态：已完成
- Owner：AI
- 输出：进度表 BASE-04/M0；QA；tasks 勾选
- [x] QA `engineering/qa/2026-07-24-resource-org-grant.md`
- [x] 进度表 BASE-04 已完成；M0 已完成；COURSE-06/07/08 备注前端另开
