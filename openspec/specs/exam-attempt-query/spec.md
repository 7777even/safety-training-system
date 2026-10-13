# exam-attempt-query Specification

## Purpose

教师批阅任务与考试作答成绩分页基于既有分配关系和机构数据范围；查询关联真实机构表。管理端作答详情回显最近批阅意见；正式成绩发布后锁定人工批阅。机器可读契约见
`docs/api/exam.openapi.json`。

## Requirements

### Requirement: 考试查询必须关联真实机构表

系统 SHALL 在教师批阅任务和考试作答记录查询中，通过 `sts_org_organization` 获取考生机构名称。

#### Scenario: 查询批阅任务

- **WHEN** 教师查询本人批阅任务
- **THEN** 查询可在真实 MySQL schema 上执行
- **AND** 返回考生所属机构名称

#### Scenario: 查询考试作答记录

- **WHEN** 管理员按数据范围查询考试作答记录
- **THEN** 查询可在真实 MySQL schema 上执行
- **AND** 既有筛选和排序语义保持不变

### Requirement: 教师本人批阅任务分页

系统 MUST 为当前登录教师提供批阅任务分页查询。任务 MUST 基于既有考试批阅人分配关系和主观题答案生成，且只返回当前教师被分配考试中的任务。

#### Scenario: 查询本人待批阅任务

- **GIVEN** 当前教师被分配为某考试的批阅人
- **AND** 该考试存在已提交且等待人工评分的主观题答案
- **WHEN** 教师查询待批阅任务
- **THEN** 系统分页返回该答案对应的批阅任务
- **AND** 返回考试、Candidate、Attempt、Answer、学员快照、提交时间、批阅状态和成绩发布状态

#### Scenario: 隔离未分配考试

- **GIVEN** 当前用户具有 `exam:review` 权限
- **BUT** 当前用户不是目标考试的批阅人
- **WHEN** 当前用户查询批阅任务
- **THEN** 系统不得返回目标考试的任何批阅任务

### Requirement: 考试作答成绩分页

系统 MUST 为考试管理人员提供作答记录分页，并按既有机构数据范围限制可见数据。列表 MUST 明确区分作答状态、批阅状态和成绩发布状态。

#### Scenario: 查询考试作答记录并筛选

- **GIVEN** 当前用户具有 `exam:attempt:list` 权限并可访问目标机构
- **WHEN** 当前用户按考试、批阅状态和成绩发布状态查询作答记录
- **THEN** 系统分页返回同时满足筛选条件的 Attempt
- **AND** 不可访问机构的 Attempt 及其学员、答案和成绩不得出现

### Requirement: 管理端作答详情回显批阅意见

系统 MUST 为具有 `exam:attempt:list` 权限的内部用户返回作答详情及每道答案最近一次批阅意见。

#### Scenario：教师刷新后回显意见

- **GIVEN** 教师已为一道主观题保存批阅意见
- **WHEN** 教师重新读取该作答详情
- **THEN** 对应 `questions[].answer.reviewComment` 返回最近一次保存的意见

### Requirement: 正式成绩发布后锁定人工批阅

系统 MUST 将已发布正式成绩的作答视为不可修改的评分事实。任何人工批阅或改分请求均不得更新其答案评分、总分、及格结果或作答状态。

#### Scenario：教师尝试批阅已发布作答

- **GIVEN** 某次作答已发布正式成绩
- **WHEN** 教师通过既有人工批阅入口提交评分
- **THEN** 系统拒绝该请求并保持评分数据不变

