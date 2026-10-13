# Spec Delta：training-execution-loop

## MODIFIED Requirements

### Requirement: 来源事实写入后自动刷新对应逐内容投影

系统 MUST 在培训绑定的课程学习状态变化、培训考试/题库开考或交卷或成绩发布、线下成绩保存之后，于同一事务按
`trainingParticipantId + planContentId` 幂等刷新唯一逐内容投影，并重建该参训人聚合字段。培训绑定课程在完成时，系统 MUST
将课程完成规则范围内已生效 `COURSE_QUIZ` 的当前正式分按算术平均写为课程报名正式成绩，保留两位小数；`QUESTION_BANK`
不参与，0 分 MUST 作为有效成绩参与计算。没有纳入测验、纳入测验未全部生效或课程未完成时课程正式成绩 MUST 保持为空。
系统 MUST NOT 要求管理员先调用计划级结果同步，学员或查询才能看到更新后的内容项状态。无培训参训人的独立开课或独立考试 MUST
NOT 刷新培训投影，独立课程 MUST NOT 因本规则写入课程汇总正式成绩。已冻结参训人投影 MUST NOT 被此次刷新覆盖。学员与结果查询
MUST 继续只读投影，MUST NOT 在读路径现场同步。计划级 `synchronize` MUST 仍可用于全量补账，并在同步投影前只为未冻结参训人
补齐空的培训课程成绩；计划结束 MUST 仍先全量同步再冻结。系统 MUST NOT 因单条内容完成而写入参训人最终 PASSED/FAILED。线下签到
MUST NOT 再作为看见线下完成结果的必要写入。

#### Scenario: 学员交卷后内容项不再保持待开始

- **GIVEN** 培训考试或题库考生已生成 PENDING 投影
- **WHEN** 学员交卷且来源进入已提交或待发布
- **THEN** 对应投影 `resultStatus` 为 `IN_PROGRESS` 或 `PENDING_PUBLISH`
- **AND** 查询学员参训详情或结果不必先调用计划级结果同步
- **AND** 未发布成绩仍不计入 `examScore`

#### Scenario: 培训课程的多个章节测验均已生效

- **GIVEN** 培训绑定课程按 `ALL_REQUIRED_ITEMS` 完成，两个必修课程测验正式分为 80 和 90
- **WHEN** 最后一个必修内容项完成
- **THEN** 课程正式成绩为 85.00，培训课程内容投影读取 85.00

#### Scenario: 0 分课程测验与题库练习

- **GIVEN** 培训绑定课程已完成，两个必修课程测验正式分为 0 和 90，另有题库练习分为 100
- **WHEN** 系统计算课程正式成绩
- **THEN** 课程正式成绩为 45.00
- **AND** 题库练习分不参与平均

#### Scenario: 课程学习状态变化后投影跟随

- **GIVEN** 培训报名且投影为 PENDING
- **WHEN** 学习状态首次变为 LEARNING
- **THEN** 对应投影为 `IN_PROGRESS`
- **WHEN** 学习状态变为 COMPLETED
- **THEN** 对应投影为 `COMPLETED` 且 `onlineHours` 按课程学习秒数重建
- **AND** 仅学习秒数变化而状态不变时 MUST NOT 为刷新投影而额外写入

#### Scenario: 教师保存成绩后投影跟随

- **GIVEN** 线下内容已有 PENDING 投影
- **WHEN** 场次教师保存该参训人已评分成绩
- **THEN** 系统在同一事务刷新该内容项投影及该参训人线下聚合
- **AND** 查询结果不必先调用计划级结果同步
- **AND** 无须先确认签到

#### Scenario: 独立课程完成

- **GIVEN** 独立开课报名完成并有课程测验成绩
- **WHEN** 系统重算课程报名或执行培训结果同步
- **THEN** 系统不得写入课程汇总正式成绩

#### Scenario: 独立考试不刷新培训投影

- **GIVEN** 考生没有 `trainingParticipantId`
- **WHEN** 学员交卷或发布成绩
- **THEN** 系统不调用培训投影刷新

#### Scenario: 已冻结参训人忽略自动刷新

- **GIVEN** 参训人已填写 `resultFinalizedTime`
- **WHEN** 管理员调用计划级结果同步
- **THEN** 系统不补齐其课程成绩，不覆盖投影行和最终快照
