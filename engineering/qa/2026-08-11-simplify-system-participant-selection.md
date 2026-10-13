# QA：全系统参与人员选择简化

## 范围

- 公共 Audience 后端、接口、菜单权限和三张表退役。
- 统一人员选择仅作为临时输入，部门当次展开为当前机构启用用户。
- 课程、考试、培训、问卷最终保存具体人员事实。
- 课程报名收敛为 ASSIGNED 与 OPEN；OPEN 支持 ALL 或 SELECTED 具体白名单。

## 验证结果

- 零 DB 全量：`mvn -pl sts-service-admin -am test`，560/560 通过。
- MySQL IT 全量：`mvn -pl sts-service-admin -am -Pintegration-test test`，189/189 通过，包含退役 migration 专项。
- 退役 migration 专项验证列、索引、菜单、角色关联和三张 Audience 表删除顺序，以及课程具体白名单表创建。
- OpenAPI：course、exam、training、survey、person-selection 五份 JSON 均通过 `jq empty`。
- 残余检查：生产代码、当前 schema 与 OpenAPI 不存在活动 Audience API、字段、菜单或权限引用；无表可维护的 `schema-common.sql`
  已删除。
- OpenSpec：strict validate 通过。
- 差异检查：`git diff --check` 通过。

## 数据与发布边界

- 未连接、修改或删除现有业务数据库及原数据。
- migration 未在用户数据库执行，只在 Testcontainers 临时数据库验证。
- 系统未上线且无历史数据，不提供 Audience 数据转换或兼容读取；用户后续按当前 schema 自行初始化。
- 回退依赖数据库初始化前备份及代码整体回退，不支持新旧模型并行。

## Review

- 规格符合性：通过。实现覆盖已确认 Task 1～3，未实现前端。
- 代码质量：通过。租户校验集中在人员选择解析；业务表只保存具体人员；未保留 Audience 空壳、双写或兼容分支。
