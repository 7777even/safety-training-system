# training-api-contract Specification

## ADDED Requirements

### Requirement: 培训计划评价表单绑定在保存时拒绝重复用途和跨机构表单

草稿计划整体替换评价表单绑定时，系统 MUST 保证请求内每个 `usageType` 最多一条，并且每个 `formVersionId`
对应的表单主体属于当前计划机构且启用。`forms` 为空数组时 MUST 解除当前全部有效绑定。系统 MUST NOT 在本次将 `formType` 与
`usageType` 强制一一对应。

#### Scenario: 同一请求重复评价用途

- **GIVEN** 当前培训计划为草稿
- **WHEN** 整体绑定请求中出现两条相同 `usageType`
- **THEN** 系统返回 HTTP 409 和业务码 `8133`
- **AND** 计划版本与既有绑定保持不变

#### Scenario: 拒绝其他机构或已禁用表单

- **GIVEN** 评价表单版本已发布
- **AND** 表单主体不属于当前计划机构或未启用
- **WHEN** 草稿计划保存该绑定
- **THEN** 系统返回 HTTP 400 和业务码 `8134`
- **AND** 既有绑定保持不变

#### Scenario: 空数组解除全部绑定

- **GIVEN** 草稿计划已有评价表单绑定
- **WHEN** 使用当前版本提交 `forms: []`
- **THEN** 系统按主键值逻辑删除全部有效绑定
- **AND** 随后查询详情时 `evaluationForms` 为空数组
