# QA：培训计划初培 / 复审性质与学时判定

## 范围

OpenSpec Change `2026-09-01-add-training-plan-nature` Task 1～2。仅后端、数据库、OpenAPI 和长期文档；前端以 OpenAPI 为准另开。

## 验收口径

- 新建、修改计划必须选择 `INITIAL` 或 `REVIEW`；历史计划迁移为只读兼容值 `UNSPECIFIED`。
- 培训类型必须配置正数初培和复审学时。计划发布按培训性质选择对应学时，并与全部启用 `TOTAL_HOURS` 规则取最大值。
- 发布冻结 `trainingNature`、`applicableRequiredHours` 和 v2 类型快照；来源配置后续变化不重算历史计划。
- `PLAN_TOTAL_HOURS` 预警对新计划使用 `max(applicableRequiredHours, conditionThreshold)`；历史计划沿用标准或条件回退。
- 预警证据记录培训性质、计划总学时、冻结值、条件值、最终阈值和来源。

## TDD 与实现证据

1. 先增加 DTO、发布校验和服务测试，观察因 `trainingNature`、8129 和冻结字段尚不存在而编译失败。
2. 最小实现 DTO/VO/Entity、发布校验、冻结快照和数据库结构后，目标零 DB 32 项通过。
3. 先增加预警冻结值与 legacy 证据测试，观察证据字段缺失和旧阈值算法失败；实现后 `WarningDetectServiceImplTest` 5 项通过。
4. `WarningDetectServiceIT` 真实落库验证初培计划冻结 80 学时、内容 60 学时、条件 40 学时，最终按 80 触发并保存
   `PLAN_FROZEN` 证据。
5. Flyway 升级测试验证旧计划回填 `UNSPECIFIED`，init 从空库创建最终结构并登记新 baseline。

## 验证结果

- 完整 Admin 零 DB：`mvn -pl sts-service-admin -am test` → 859 项，0 失败，0 错误，1 跳过。
- 完整 Admin MySQL IT：`mvn -pl sts-service-admin -am -Pintegration-test test` → 210 项，0 失败，0 错误，0 跳过。
- Admin-init IT：
  `mvn -pl sts-service-admin-init -am -Pintegration-test -Dtest=AdminInitFlywayIT -Dsurefire.failIfNoSpecifiedTests=false test` →
  1 项通过，baseline `20260901103000`。
- OpenAPI JSON、OpenSpec strict、`git diff --check`：见最终验收记录，均要求通过。

## Review

- 规格符合性：通过。功能项 5 的初培/复审学时被计划明确选择并冻结；功能项 2 的监管预警读取同一冻结事实。
- 代码质量：通过。未新增权限或路径；历史记录不猜测性质；在线占比和个人结论口径未改变；migration 与 init 最终结构同步。
- 需人工决策：无。前端适配不属于 lims 当前 Change。
