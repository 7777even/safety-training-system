# Tasks：培训计划评价表单绑定保存时对齐既有约束

> 状态：已于 2026-09-07 确认并完成 Task 1。Owner：lims。

## Task 1：完成评价表单绑定保存时的用途去重、机构校验与空数组契约

- **状态**：已完成（2026-09-07）
- **Owner**：lims
- **输入**：本 Change 的 proposal、design、spec；现行 `bindEvaluationForms` 与发布预检口径；功能项 34、40。
- **输出**：绑定保存校验、`8133`/`8134`、OpenAPI 空数组说明、零 DB 测试。
- **前后端影响**：仅后端；前端以 OpenAPI 为准另开。
- **API 依赖**：保持 `PUT /api/training/plans/{trainingPlanId}/evaluation-forms` 与权限；新增 `8133`、`8134`。
- **数据影响**：无。
- **验收标准**：
  1. 同一请求重复 `usageType` 返回 409/`8133`，不更新计划、不删除绑定。
  2. 已发布但跨机构或禁用的表单返回 400/`8134`，不改变既有绑定。
  3. `forms: []` 仍解除全部绑定；版本冲突仍为 8104 且不进表单校验写路径。
  4. 未发布版本仍为 400/`8110`。
  5. OpenAPI 登记错误码，并写明空数组语义且提供 `forms: []` example。
- **测试方式**：
  1. TDD 扩展 `TrainingPlanServiceImplTest`，观察失败后再实现。
  2. 运行受影响零 DB 测试。不改 Entity/Mapper/SQL，不跑 IT。
  3. 校验 OpenAPI JSON；双轴 Review 后更新本文件、进度表、QA / Retro。

## 完成门槛

1. 规格符合性 Review：通过。未做 formVersionId 全局去重，也未做 formType/usageType 匹配。
2. 代码质量 Review：通过。校验在乐观锁更新之前；无表结构/前端改动。
