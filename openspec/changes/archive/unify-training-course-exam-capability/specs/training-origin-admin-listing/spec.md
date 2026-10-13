# training-origin-admin-listing Specification

## ADDED Requirements

### Requirement: 开课与考试列表包含培训生成的对象

管理端开课分页与考试分页 MUST 返回全部开课和考试，包括培训生成的对象。列表项与详情 MUST 暴露 `planContentId`；非培训对象该字段
MUST 为 null。系统 MAY 提供按 `planContentId` 过滤。

#### Scenario: 默认开课列表含培训开课

- **GIVEN** 机构内同时存在独立开课和培训生成的开课
- **WHEN** 管理员查询开课列表且不带排除条件
- **THEN** 两类开课都返回
- **AND** 培训开课记录包含非空 `planContentId`

#### Scenario: 考试详情返回计划绑定

- **GIVEN** 某考试 `planContentId` 非空
- **WHEN** 管理员读取该考试详情
- **THEN** 响应包含该 `planContentId`

#### Scenario: 可按计划内容过滤

- **GIVEN** 存在培训生成的开课
- **WHEN** 管理员按该 `planContentId` 查询开课
- **THEN** 结果只包含该开课
