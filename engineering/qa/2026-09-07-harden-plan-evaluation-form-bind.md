# QA：培训计划评价表单绑定保存时对齐既有约束

## 范围

OpenSpec Change `2026-09-07-harden-plan-evaluation-form-bind` Task 1。仅后端与 `docs/api/training.openapi.json`。

## 验收口径

- 请求内重复 `usageType`：409/`8133`，不写库。
- 跨机构或禁用表单：400/`8134`，不写库。
- `forms: []` 解除全部绑定；契约提供空数组 example。
- 未发布版本仍为 8110。不做 formVersionId 全局去重，不做 formType 匹配。

## TDD 与实现证据

1. 先增加重复用途、跨机构、禁用表单用例。实现前均因先走乐观锁而失败为 8104。
2. 将用途去重和机构/启停校验移到 `updateById` 之前后，定向零 DB 通过。

## 验证结果

- 受影响零 DB：
  `mvn -pl sts-service-admin -am test -Dtest=TrainingPlanServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
  → 20 项，0 失败，0 错误，0 跳过。
- 带 DB IT：未运行。无 Entity/Mapper/SQL/schema 变更。
- OpenAPI：JSON 解析通过；`8133`/`8134`、空数组 description 与 `unbindAll` example 已核对。
- `git diff --check`：通过。

## Review

- 规格符合性：通过。
- 代码质量：通过。插入仍捕获唯一索引转为 8133。
- 需人工决策：无。
