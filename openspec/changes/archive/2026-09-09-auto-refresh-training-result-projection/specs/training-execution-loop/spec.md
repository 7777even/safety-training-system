## ADDED Requirements

### Requirement: 来源事实写入后自动刷新对应逐内容投影

系统 MUST 在培训绑定的课程学习状态变化、培训考试/题库开考或交卷或成绩发布、线下签到确认、线下成绩保存之后，于同一事务按
`trainingParticipantId + planContentId` 幂等刷新唯一逐内容投影，并重建该参训人聚合字段。系统 MUST NOT
要求管理员先调用计划级结果同步，学员或查询才能看到更新后的内容项状态。无培训参训人的独立开课或独立考试 MUST NOT
刷新培训投影。已冻结参训人投影 MUST NOT 被此次刷新覆盖。学员与结果查询 MUST 继续只读投影，MUST NOT 在读路径现场同步。计划级
`synchronize` MUST 仍可用于全量补账；计划结束 MUST 仍先全量同步再冻结。系统 MUST NOT 因单条内容完成而写入参训人最终
PASSED/FAILED。

#### Scenario: 学员交卷后内容项不再保持待开始

- **GIVEN** 培训考试或题库考生已生成 PENDING 投影
- **WHEN** 学员交卷且来源进入已提交或待发布
- **THEN** 对应投影 `resultStatus` 为 `IN_PROGRESS` 或 `PENDING_PUBLISH`
- **AND** 查询学员参训详情或结果不必先调用计划级结果同步
- **AND** 未发布成绩仍不计入 `examScore`

#### Scenario: 课程学习状态变化后投影跟随

- **GIVEN** 培训报名且投影为 PENDING
- **WHEN** 学习状态首次变为 LEARNING
- **THEN** 对应投影为 `IN_PROGRESS`
- **WHEN** 学习状态变为 COMPLETED
- **THEN** 对应投影为 `COMPLETED` 且 `onlineHours` 按课程学习秒数重建
- **AND** 仅学习秒数变化而状态不变时 MUST NOT 为刷新投影而额外写入

#### Scenario: 教师确认签到或保存成绩后投影跟随

- **GIVEN** 线下内容已有 PENDING 投影
- **WHEN** 场次教师确认该参训人签到或保存已评分成绩
- **THEN** 系统在同一事务刷新该内容项投影及该参训人线下聚合
- **AND** 查询结果不必先调用计划级结果同步

#### Scenario: 独立考试不刷新培训投影

- **GIVEN** 考生没有 `trainingParticipantId`
- **WHEN** 学员交卷或发布成绩
- **THEN** 系统不调用培训投影刷新

#### Scenario: 已冻结参训人忽略自动刷新

- **GIVEN** 参训人已填写 `resultFinalizedTime`
- **WHEN** 来源事实再次变化并触发刷新
- **THEN** 系统不覆盖投影行和最终快照

## MODIFIED Requirements

### Requirement: 培训结果由正式来源同步到逐内容投影

系统 MUST 按 trainingParticipantId + planContentId 读取课程、考试和线下正式事实，并幂等更新唯一的逐内容结果投影。投影 MUST
保存 executionId、来源记录、学时、分数，并分别保存来源域 `sourceStatus` 和培训域 `resultStatus`；正式事实仍是业务真相源。未发布考试成绩
MUST 不展示、不计分；已发布考试结果 MUST 按 Candidate 的 PASSED/FAILED 正式结论映射。成绩聚合枚举和算法 MUST 与 OpenAPI 的
AVERAGE/WEIGHTED_AVERAGE 一致。最小版本中 EXAM/QUESTION_BANK MUST NOT 重复计入培训学时。最终快照 schemaVersion=2 MUST
保留两个状态；快照 MUST 包含 finalizedTime，并与投影及参训人的归档时间保持一致。培训绑定来源事实写入后 MUST
自动执行上述投影更新；计划级同步保留为全量补账，MUST NOT 再作为看见正式结果的唯一途径。

#### Scenario: 同步正式结果

- **GIVEN** 课程完成、考试成绩发布或线下事实确认
- **WHEN** 系统同步培训结果
- **THEN** 系统更新唯一逐内容投影及参训人聚合字段，并保留可追溯的正式来源

#### Scenario: 考试成绩未发布

- **GIVEN** Candidate 已交卷但成绩未发布
- **WHEN** 同步或查询培训结果
- **THEN** 系统显示待发布且不计入 examScore

#### Scenario: 投影区分来源状态与培训结论

- **GIVEN** 来源考试 Candidate 状态与培训统一结论使用不同枚举
- **WHEN** 系统同步逐内容投影或冻结最终快照
- **THEN** `sourceStatus` 保存来源记录的原始状态
- **AND** `resultStatus` 保存培训统一结论
- **AND** schemaVersion=2 的最终快照同时保留这两个字段

#### Scenario: 计划级同步仍可全量补账

- **GIVEN** 历史投影落后于来源事实
- **WHEN** 管理员调用计划级结果同步
- **THEN** 系统按每名未冻结参训人、每个内容项刷新投影并重建聚合
- **AND** 已冻结参训人不得覆盖
