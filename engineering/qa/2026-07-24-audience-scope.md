# QA：BASE-03 参与范围、冻结名单与统一人员选择

日期：2026-07-24

OpenSpec：`openspec/changes/2026-07-24-implement-audience-scope`

## 范围

- 表：`sts_common_audience` / `sts_common_audience_rule` / `sts_common_audience_member`
- API：`/common/audiences/**`、`/common/person-picker`
- 课程接入：`CourseEnrollmentServiceImpl` 在 `audienceId` 非空时校验 `contains`
- 不实现 BASE-04、前端页面、分享/消息表

## 测试命令与结果

零 DB：

```bash
cd backend && mvn -pl sts-service-admin -am test \
  -Dtest=AudienceResolveServiceImplTest,AudienceControllerTest,CourseEnrollmentServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：18 tests，BUILD SUCCESS。

带 DB（Testcontainers MySQL 8.0.45）：

```bash
cd backend && mvn -pl sts-service-admin -am -Pintegration-test test \
  -Dtest=AudienceMapperIT \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：4 tests，BUILD SUCCESS。

## 未运行项

- 完整 admin 全量 IT 回归（本轮仅跑受影响用例）
- 前端参与范围管理页（本 Change 非目标）
- 正式环境空库 migration 手工演练（脚本已按评审 DDL 落库）

## 结论

BASE-03 后端能力验收通过，可提交。
