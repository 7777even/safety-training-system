# Spec Delta：课程考核学员作答详情可见性

## ADDED Requirements

### Requirement: 课程考核学员详情必须按自动生效裁剪成绩和答案

系统 MUST 在学员读取课程题库练习或课程测验作答详情时，按课程自动生效事实裁剪成绩、标准答案和解析，MUST NOT 套用独立考试
`PUBLISHED + scorePublishedTime` 或 `ExamPolicy.answerDetailMode`。

当 `attemptStatus=RESULT_EFFECTIVE` 且 `resultEffectiveTime` 非空时，系统 MUST 返回
`scorePublishStatus=EFFECTIVE` 和 `scoreVisible=true`，并返回总分、合格标记、分项分、对错数、题目得分、正确性、标准答案、正确选项标记和题目解析。

当结果尚未生效时，系统 MUST 返回 `scorePublishStatus=UNPUBLISHED` 和 `scoreVisible=false`，MUST
保留本人已提交答案，MUST NOT 返回成绩、标准答案、正确选项标记或解析。

学员详情 MUST NOT 返回内部批阅意见。课程本期 MUST NOT 要求存在 `Exam` 或 `ExamPolicy` 才返回已生效的答案详情。

#### Scenario: 课程题库练习完成后回看

- **GIVEN** 课程题库练习作答状态为 `RESULT_EFFECTIVE` 且存在 `resultEffectiveTime`
- **WHEN** 本人学员读取该课程内容项作答详情
- **THEN** 返回 `scorePublishStatus=EFFECTIVE` 和 `scoreVisible=true`
- **AND** 返回总分、题目得分、标准答案和解析

#### Scenario: 纯客观题课程测验完成后回看

- **GIVEN** 课程测验无待批阅主观题且结果已自动生效
- **WHEN** 本人学员读取该作答详情
- **THEN** 返回 `scorePublishStatus=EFFECTIVE` 和 `scoreVisible=true`
- **AND** 不因缺少 `examId` 或 `ExamPolicy` 而裁剪标准答案和解析

#### Scenario: 课程测验仍待主观题批阅

- **GIVEN** 课程测验已交卷但状态为 `PENDING_REVIEW`
- **WHEN** 本人学员读取该作答详情
- **THEN** 返回 `scorePublishStatus=UNPUBLISHED` 和 `scoreVisible=false`
- **AND** 可见本人已提交答案
- **AND** 不返回总分、题目得分、标准答案或解析

## MODIFIED Requirements

### Requirement: 判分与成绩发布

客观题 MUST 自动判分；主观题 MUST 进入待人工批阅；批阅记录 MUST 只追加。

独立考试学员读取时，成绩只有在 Attempt 状态为 `PUBLISHED` 且成绩发布时间存在时才视为已发布。独立考试学员在成绩未发布前
MUST
NOT 获得 Attempt 汇总成绩、答案级得分、正确性、正确答案、正确选项标记或题目解析。成绩发布后 MUST
可获得正式最终成绩、及格状态和发布时间；分项成绩、正确答案与解析还
MUST 满足 `answerDetailMode`：`SUBMITTED` 为已发布，`PASSED` 为已发布且及格，`EXAM_ENDED` 为已发布且考试开放期结束，
`RETAKE_ENDED` 为已发布且已无可用补考机会，`NEVER` 为始终隐藏。策略值未知或依赖事实不完整时 MUST 默认隐藏。

上述独立考试发布裁剪 MUST NOT 应用于 `COURSE_QUIZ` 或 `COURSE_BANK_PRACTICE` 学员详情。

学员本人作答详情 MUST NOT 暴露内部批阅意见。

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

## REMOVED Requirements

无。
