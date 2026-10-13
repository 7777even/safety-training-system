# Spec Delta：training-api-contract

## ADDED Requirements

### Requirement: 创建计划可服务端复制已发布模板课表

系统 MUST 在 `POST /api/training/plans` 于同一事务内，当 `sourceTemplateId` 非空且模板已发布、属当前机构、与计划
`trainingTypeId` 一致时，复制模板阶段与内容。模板不合格 MUST 返回 400/`8157` 且不留下计划行。`trainingStandardId` 与
`sourceTemplateId` MUST 可空。

#### Scenario：带合格模板创建

- **WHEN** 调用方创建计划并传入已发布且同类型的模板 ID
- **THEN** 返回新计划 ID
- **AND** 再次查询计划详情可见与模板对应的阶段和内容引用

#### Scenario：模板类型不一致

- **WHEN** 调用方创建计划传入其他培训类型的模板
- **THEN** 系统返回 400/`8157`
- **AND** 不创建计划

### Requirement: 草稿计划提供 apply-template 覆盖复制

系统 MUST 提供 `POST /api/training/plans/{trainingPlanId}/apply-template`，权限 `training:plan:edit`，请求含计划 `version`
与 `sourceTemplateId`。仅草稿可调用，否则 409/`8158`。成功 MUST 覆盖来源模板 ID 并重建阶段内容。契约 MUST 登记该 operation 的
summary、description、example 与错误码。

#### Scenario：草稿覆盖复制

- **GIVEN** 草稿计划 version 匹配
- **WHEN** 调用方提交合格已发布同类型模板
- **THEN** 计划来源模板更新
- **AND** 阶段内容被模板课表覆盖

#### Scenario：已发布计划拒绝复制

- **GIVEN** 计划不是草稿
- **WHEN** 调用 apply-template
- **THEN** 系统返回 409/`8158`
- **AND** 阶段内容不变

### Requirement: 模板执行配置在契约中为可选默认值

`TrainingTemplateContentRequest.executionConfig` MUST 标明可选；模板阶段替换与模板发布 MUST NOT
因缺少题库或线下执行配置而失败。计划内容执行配置仍在计划发布时校验。

#### Scenario：模板发布无执行配置

- **GIVEN** 草稿模板含课程内容且 executionConfig 为空
- **WHEN** 发布该模板
- **THEN** 发布成功
- **AND** 不得返回 8117

## MODIFIED Requirements

无。本 Change 不改写既有「完整机器可读契约」条文，仅新增上述 operation 与字段语义，并同步 `docs/api/training.openapi.json`。
