## MODIFIED Requirements

### Requirement: 判分与成绩发布

正式考试和课程测验中的客观题 MUST 自动判分，主观题 MUST 按其已确认业务流程进入人工批阅，批阅记录 MUST 只追加。培训计划
`QUESTION_BANK` 题库练习 MUST 使用不同的完成型练习策略：可自动判断题 MUST 产生正确/错误反馈；不可自动判断题 MUST 只记录作答并标记
`NOT_EVALUATED`，MUST NOT 进入人工批阅、成绩发布、错题统计或正确率分母。

独立考试学员读取时，成绩只有在 Attempt 状态为 `PUBLISHED` 且成绩发布时间存在时才视为已发布。独立考试学员在成绩未发布前
MUST NOT 获得 Attempt 汇总成绩、答案级得分、正确性、正确答案、正确选项标记或题目解析。成绩发布后 MUST
可获得正式最终成绩、及格状态和发布时间；分项成绩、正确答案与解析还 MUST 满足 `answerDetailMode`：`SUBMITTED` 为已发布，
`PASSED` 为已发布且及格，`EXAM_ENDED` 为已发布且考试开放期结束，`RETAKE_ENDED` 为已发布且已无可用补考机会，`NEVER`
为始终隐藏。策略值未知或依赖事实不完整时 MUST 默认隐藏。

上述独立考试发布裁剪 MUST NOT 应用于 `COURSE_QUIZ`、`COURSE_BANK_PRACTICE` 或培训计划题库练习学员详情。学员本人作答详情
MUST NOT 暴露内部批阅意见。

#### Scenario: 培训题库练习混合可判断与不可判断题

- **GIVEN** 培训题库练习包含 8 道 `AUTO` 题和 2 道 `RECORD_ONLY` 简答题
- **WHEN** 学员全部作答并提交
- **THEN** `AUTO` 题分别返回正确或错误
- **AND** 简答题返回 `NOT_EVALUATED` 且 `isCorrect=null`
- **AND** 正确率只使用 8 道可判断题计算
- **AND** Attempt 不进入 `PENDING_REVIEW` 或成绩发布流程

#### Scenario: 培训题库练习全部为不可判断题

- **GIVEN** 培训题库练习全部为 `RECORD_ONLY` 题
- **WHEN** 学员全部作答并提交
- **THEN** 本轮练习完成
- **AND** `evaluatedCount=0`、`unassessedCount` 等于题目数、`accuracyRate=null`
- **AND** 系统不得返回零分、不及格或零正确率

#### Scenario: 培训题库练习存在未答题

- **GIVEN** 培训题库练习仍有至少一道抽中题目未作答
- **WHEN** 学员提交本轮练习
- **THEN** 系统拒绝完成并返回可定位的未答信息
- **AND** Attempt 保持可继续作答状态

#### Scenario: 返回已完成练习反馈

- **GIVEN** 培训题库练习已经完成
- **WHEN** 本人学员读取 Attempt 详情
- **THEN** 返回题目总数、已答数、可判断数、正确数、错误数、不判定数和可空正确率
- **AND** 汇总分数与通过状态为空
- **AND** `RECORD_ONLY` 题可返回参考答案和解析但不返回对错结论

#### Scenario: 隐藏未发布独立考试成绩

- **GIVEN** 独立考试 Attempt 已交卷或已完成批阅但尚未发布
- **WHEN** 学员读取任务或 Attempt 详情
- **THEN** 返回 `scorePublishStatus=UNPUBLISHED` 和 `scoreVisible=false`
- **AND** 所有成绩、正确性、正确答案及解析字段为空或不返回

#### Scenario: 返回已发布独立考试正式结果

- **GIVEN** 独立考试 Attempt 状态为 `PUBLISHED` 且存在成绩发布时间
- **WHEN** 本人学员读取任务或 Attempt 详情
- **THEN** 返回 `scorePublishStatus=PUBLISHED`、`scoreVisible=true`
- **AND** 返回正式最终成绩、及格状态和成绩发布时间
- **AND** 其他答案详情仅按已确认策略开放

#### Scenario: 成绩已发布仍裁剪意见

- **GIVEN** 作答已完成批阅且成绩已发布或课程结果已生效
- **WHEN** 学员读取自己的作答详情
- **THEN** `questions[].answer.reviewComment` 为 `null`
- **AND** 不返回内部批阅意见文本

## ADDED Requirements

### Requirement: 培训题库练习结果必须表达不判定

培训题库练习答案契约 MUST 区分未作答、自动判断正确、自动判断错误和已作答但不判定。不可判断题 MUST 使用稳定状态
`NOT_EVALUATED`，其 `isCorrect`、自动得分、人工得分和最终得分 MUST 为空；调用方不得通过零值猜测该题答错。

#### Scenario: 简答题只记录作答

- **GIVEN** 一道冻结为 `RECORD_ONLY` 的简答题
- **WHEN** 学员填写非空答案并完成练习
- **THEN** 答案保存为已作答和 `NOT_EVALUATED`
- **AND** 不生成批阅任务、错题记录或任何分数

