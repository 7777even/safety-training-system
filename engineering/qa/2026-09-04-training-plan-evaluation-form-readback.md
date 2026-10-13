# QA：培训计划评价表单绑定读回闭环

## 范围

OpenSpec Change `2026-09-04-complete-training-plan-evaluation-form-readback` Task 1。仅后端与 OpenAPI；未修改数据库结构和前端。

## TDD 与缺陷复现

- Red：新增计划详情测试后，因 `TrainingPlanVO` 没有 `evaluationForms` 且 Service 无评价表单摘要装配能力而编译失败。
- 既有真库故障：默认 Mapper delete 把逻辑删除值保持为 0，同用途再次插入触发 `uk_plan_usage_deleted`。
- Green：统一使用 `deleted=plan_evaluation_form_id`，并从计划详情读回当前有效绑定摘要。
- 真库失败测试首次在测试类外层事务中观察到未提交版本号；改为 `NOT_SUPPORTED` 后由 Service 建立真实独立事务，确认 8110
  异常会回滚计划版本及绑定。

## 验证结果

- 目标零 DB：`TrainingPlanServiceImplTest` 17/17 通过。
- Admin 全量零 DB：863 个用例，0 失败、0 错误、1 个既有环境型跳过。
- 目标 MySQL IT：`TrainingPlanServiceImplIT` 2/2 通过。
- 真库覆盖：无绑定空数组、保存后读回、摘要字段、同用途重复替换、唯一约束、旧绑定与计划删除的主键值软删除、未发布版本失败回滚。
- OpenAPI：JSON 解析、本地 `$ref`、详情 500/8132、`evaluationForms` schema、必填摘要字段和成功 example 断言通过。
- OpenSpec strict、`git diff --check` 通过。

## Review

- 规格符合性：通过。复用计划详情和既有版本详情接口，没有新增路径、权限或数据库结构。
- 代码质量：通过。计划乐观锁、版本校验、绑定替换处于同一事务；租户插件继续约束计划、绑定、版本和表单查询；最多四种用途，摘要关联查询规模受控。
