# Spec Delta：training-course-offering-lifecycle

## MODIFIED Requirements

### Requirement: 培训课程必须先落下开课再校验阅卷人

系统 MUST 通过开课能力创建或复用该计划内容对应的草稿开课，并在创建时写入计划冻结的课程版本。含主观题的课程测验 MUST
在该开课执行上配置至少一名阅卷人后，才允许发布该开课。开课发布 MUST 使用开课已有版本（冻结版本），MUST NOT 另写一套培训发布。发布成功后系统
MUST 才以 TRAINING 来源调用报名能力。缺阅卷人时系统 MUST 保留草稿开课，MUST NOT 将开课标为已发布，MUST NOT 创建培训来源报名或执行关联。

#### Scenario: 缺阅卷人时开课以草稿保留

- **GIVEN** 已发布培训计划包含一门带主观题测验的课程
- **AND** 对应开课测验执行尚未配置阅卷人
- **WHEN** 管理员生成该内容的执行
- **THEN** 系统创建或保留该开课及测验执行
- **AND** 开课 `publishStatus` 为 `DRAFT`
- **AND** 返回 5068，失败项包含 `offeringId`
- **AND** 不创建 `enrollmentSource=TRAINING` 的报名
- **AND** 不写入 COURSE 执行关联

#### Scenario: 配齐阅卷人后重试完成发布和开放

- **GIVEN** 该计划内容对应草稿开课已存在
- **AND** 主观题测验已配置至少一名阅卷人
- **WHEN** 管理员再次生成该内容执行
- **THEN** 系统调用同一套开课发布
- **AND** 开课仍绑定冻结课程版本
- **AND** 以 TRAINING 来源报名并写入 COURSE 执行关联

#### Scenario: 已有开课仍须经发布校验阅卷人

- **GIVEN** 该计划内容已有开课
- **AND** 主观题测验仍未配置阅卷人
- **WHEN** 管理员再次生成该内容执行
- **THEN** 系统仍返回 5068
- **AND** 不得因开课已存在而跳过发布校验并报名

#### Scenario: 发布使用开课已有版本

- **GIVEN** 开课创建时已写入冻结 `courseVersionId`
- **AND** 课程后来的 `currentVersionId` 已变化
- **WHEN** 系统发布该开课
- **THEN** 开课继续绑定已有版本
- **AND** 独立开课（版本为空）发布时仍取当前版本
