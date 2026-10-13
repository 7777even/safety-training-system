# QA：BASE-04 跨机构资源授权与监管关系

日期：2026-07-24

OpenSpec：`openspec/changes/2026-07-24-implement-resource-org-grant`

## 范围

- 表：`sts_org_regulatory_relation`、`sts_common_resource_org_grant`
- API：`/org/regulatory-relations/**`、`/common/resource-grants/**`（含 check）
- 服务：`ResourceGrantService` 等级包含 MANAGE⊃USE⊃VIEW；有效期 + `status=1`；COURSE owner 校验
- 课程接入：`CourseServiceImpl.getCourse` 跨机构须 USE
- 未实现：`sts_common_resource_admin_permission`、前端页面

## 测试命令与结果

零 DB：

```bash
cd backend && mvn -pl sts-service-admin -am test \
  -Dtest=ResourceGrantServiceImplTest,ResourceGrantControllerTest,RegulatoryRelationServiceImplTest,RegulatoryRelationControllerTest,CourseServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：31 tests，BUILD SUCCESS。

带 DB（Testcontainers MySQL 8.0.45）：

```bash
cd backend && mvn -pl sts-service-admin -am -Pintegration-test test \
  -Dtest=RegulatoryRelationMapperIT,ResourceOrgGrantMapperIT \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：4 tests，BUILD SUCCESS。

## 未运行项

- 完整 admin 全量 IT 回归（本轮仅跑受影响用例）
- 前端监管关系 / 资源授权管理页（本 Change 非目标）
- 正式环境空库 migration 手工演练（脚本已按评审 DDL 落库）
- `sts_common_resource_admin_permission`（本期跳过）

## 结论

BASE-04 后端能力验收通过；BASE-03/04 均完成后进度表 M0 可关闭。
