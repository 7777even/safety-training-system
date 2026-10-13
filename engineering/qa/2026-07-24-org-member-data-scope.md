# QA：BASE-02 机构成员与角色数据范围

日期：2026-07-24

OpenSpec：`openspec/changes/2026-07-24-implement-org-member-data-scope`

## 范围

- 系统表扩展：`us_sys_dept` / `us_sys_role` / `us_sys_role_dept`
- 机构表：`sts_org_member`、`sts_org_tag`、`sts_org_member_tag`、`sts_org_position`、`sts_org_member_position`
- API：`/org/**`；角色/部门请求体扩展；`DataScopeService`
- 不改素材/课程业务表与既有课程 API 语义

## 测试命令与结果

零 DB：

```bash
cd backend && mvn -pl sts-service-admin -am test \
  -Dtest=OrgTagServiceImplTest,DataScopeServiceImplTest,SysRoleServiceImplTest,SysDeptServiceImplTest,SysRoleControllerTest,SysDeptControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：42 tests，BUILD SUCCESS。

带 DB（Testcontainers MySQL 8.0.45）：

```bash
cd backend && mvn -pl sts-service-admin -am -Pintegration-test test \
  -Dtest=OrgTagMapperIT,RbacPermissionQueryIT \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：5 tests，BUILD SUCCESS。

## 未运行项

- 完整 admin 全量 IT 回归（本轮仅跑受影响用例）
- 前端管理页面（本 Change 非目标）
- 本机空库 `sts` 未预置系统表；正式环境需按 migration 顺序执行

## 结论

BASE-02 后端能力验收通过，可提交。
