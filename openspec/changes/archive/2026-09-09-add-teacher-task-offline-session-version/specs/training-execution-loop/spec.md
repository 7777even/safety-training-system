# training-execution-loop Delta

## MODIFIED Requirements

### Requirement: 仅进行中场次可录分

系统 MUST 仅在场次 `IN_PROGRESS` 时接受线下成绩写入。其它状态 MUST 返回 409 / `8203`。教师任务 `availableActions` MUST
按状态给出 `START`/`CANCEL`（已安排）、`RECORD_SCORE`/`COMPLETE`/`CANCEL`（进行中），其它状态为空。教师任务列表和详情 MUST
返回场次乐观锁 `version`，供 `start`/`complete`/`cancel` 回传。

#### Scenario: 未开始不可录分

- **GIVEN** 场次为 `SCHEDULED`
- **WHEN** 教师提交成绩
- **THEN** 返回 409 / `8203` 且不写成绩、不刷新投影

#### Scenario: 教师任务返回场次版本

- **GIVEN** 当前用户是该场次教师，场次乐观锁版本为 4
- **WHEN** 查询教师任务列表或详情
- **THEN** 返回 `version` 为 4

### Requirement: 教师任务返回应到名单与成绩

系统 MUST 在教师场次任务详情中返回该计划全部参训人作为应到名单，并附带该场已评分成绩（无则为空）及对应内容投影
`resultStatus`。名单 MUST 来自计划参训人。计数 MUST 使用应到人数、已评分数、未评分数。列表与详情 MUST 返回场次乐观锁
`version`。权限仍为 `training:teacher:task` 且 MUST 校验本人场次教师归属。

#### Scenario: 教师打开场次看到应到人

- **GIVEN** 当前用户是该场次教师，计划有两名参训人且其中一人已评分
- **WHEN** 查询教师任务详情
- **THEN** 返回两名参训人
- **AND** 已评分者带分数，未评分者成绩为空
- **AND** `scoredCount` 为 1，`pendingScoreCount` 为 1
