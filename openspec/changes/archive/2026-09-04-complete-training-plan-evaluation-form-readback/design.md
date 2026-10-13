# Design：培训计划评价表单绑定读回闭环

## 1. 设计判断

培训计划详情已经承担聚合根及阶段/内容的读回。评价表单绑定同属计划编排的子集合，保存后也参与计划发布校验，因此应由同一个详情响应返回。新增独立
GET 会制造两种恢复计划编排状态的方式，并增加一次请求和一致性边界，本 Change 不采用。

## 2. API 设计

保持既有路径与权限：

- `GET /api/training/plans/{trainingPlanId}`，权限 `training:plan:list`。
- `PUT /api/training/plans/{trainingPlanId}/evaluation-forms`，权限 `training:plan:edit`。
- `GET /api/evaluation/forms/versions/{formVersionId}`，权限 `evaluation:form:list`，继续负责完整不可变版本内容。

`TrainingPlanVO` 新增必定返回的数组字段：

```text
evaluationForms: TrainingPlanEvaluationFormVO[]
```

`TrainingPlanEvaluationFormVO` 返回：

| 字段                     | 含义            |
|------------------------|---------------|
| `planEvaluationFormId` | 计划绑定关系 ID     |
| `trainingPlanId`       | 培训计划 ID       |
| `formVersionId`        | 固定的评价表单版本 ID  |
| `evaluationFormId`     | 评价表单主体 ID     |
| `formName`             | 表单名称，用于绑定摘要展示 |
| `versionNo`            | 被绑定版本号        |
| `publishedStatus`      | 被绑定版本发布状态     |
| `usageType`            | 在本计划中的评价用途    |
| `isRequired`           | 是否必填          |
| `status`               | 绑定启用状态        |

未绑定时返回空数组，不返回 `null`。绑定列表按 `usageType`、`planEvaluationFormId` 升序稳定排序。

## 3. 数据读取

计划详情先按既有机构隔离和权限读取计划，再查询当前有效的 `sts_training_plan_evaluation_form`。每条绑定通过
`form_version_id` 读取不可变版本，再通过版本的 `evaluation_form_id` 读取表单名称。

计划详情只暴露已绑定表单的识别摘要，不返回评价项目、评分规则或未绑定表单信息。调用方需要完整版本内容时，使用已有版本详情接口并接受其独立权限校验。

如果绑定指向的版本或表单异常缺失，计划详情不得静默丢弃绑定，也不得返回伪造摘要；返回 HTTP 500 和新增业务码
`8132`（培训计划评价表单绑定数据不完整），服务端日志记录计划 ID 与绑定 ID。该错误表示服务端持久化事实损坏，不使用 404
误导调用方重新选择，也不暴露跨机构表单信息。

## 4. 整体替换与事务

评价表单绑定继续遵守计划聚合根乐观锁：

1. 校验计划存在且为草稿。
2. 使用请求 `version` 更新计划；更新失败返回 409/8104。
3. 在同一事务中校验请求引用的评价表单版本均已发布。
4. 将当前有效绑定更新为 `deleted = plan_evaluation_form_id`。
5. 插入请求中的全量绑定。

任一步失败时事务整体回滚，原绑定和计划版本保持不变。计划删除复用相同绑定软删除方法。

数据库唯一索引 `uk_plan_usage_deleted(training_plan_id, usage_type, deleted)` 保留，用于保证一个计划同一评价用途最多一个有效绑定；本
Change 不改变该业务约束。

## 5. 权限与机构隔离

- 计划详情仍使用 `training:plan:list`，只返回当前调用方可见计划自身的绑定摘要。
- 绑定修改仍使用 `training:plan:edit`，且仅草稿计划可修改。
- Mapper 查询继续由租户插件注入机构过滤。
- 返回绑定摘要不授予 `evaluation:form:list`；完整版本内容仍需该权限。

## 6. 兼容性

- 不新增路径、请求字段或数据库字段。
- `TrainingPlanVO` 增加响应字段，旧调用方可忽略。
- `TrainingPlanEvaluationFormVO` 从孤立 schema 转为真实响应模型，并增加可展示的版本摘要字段。
- OpenAPI example 必须展示空数组和非空绑定两种语义中的至少一个有效示例，不得使用 `[null]`。

## 7. 测试设计

### 零 DB

- 计划详情装配有效绑定及表单/版本摘要，未绑定返回空数组。
- 绑定整体替换在版本冲突时不查询版本、不删除绑定、不插入新绑定。
- 绑定成功时使用正确的主键值软删除路径。
- 异常引用返回 HTTP 500/8132，且不返回残缺的计划详情。

### MySQL IT

- 保存绑定后重新查询计划详情，可以完整读回版本 ID、表单 ID、名称、版本号、用途和状态。
- 再次提交相同用途时旧绑定 `deleted=plan_evaluation_form_id`，只剩一条有效绑定且不触发唯一约束。
- 空列表清空绑定后计划详情返回空数组。
- 真库验证租户过滤和稳定排序。

### Contract

- `training.openapi.json` JSON 解析、Controller operation 对齐和 schema 引用检查通过。
- 计划详情响应 schema 与 Java VO 字段一致，每个新增字段有中文 description 和 example。

## 8. 风险与取舍

- 计划详情增加少量关联查询。单个计划的评价用途数量受唯一约束和固定枚举限制，优先保证聚合读回完整性；不为此新增缓存或独立端点。
- 表单主体后续改名时，草稿和已发布计划详情展示当前名称；绑定的业务事实仍由不可变 `formVersionId` 确定，不以名称参与计算。
- 不把完整版本内容嵌入计划详情，避免响应膨胀和绕过评价表单详情权限。
