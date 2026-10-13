# Spec Delta：学员考试查询与答案可见性

## ADDED Requirements

### Requirement: 学员本人考试任务

系统 MUST 基于当前登录用户和 `sts_exam_candidate` 事实分页返回该用户本人的考试任务；接口 MUST NOT 接受调用方指定 `userId`
。跨机构任务只要 Candidate 属于当前用户就 MUST 可见，其他用户任务 MUST NOT 可见。

#### Scenario: 查询本人跨机构考试任务

- **GIVEN** 当前用户在主办机构发布的考试中存在 Candidate
- **WHEN** 用户查询学员考试任务
- **THEN** 系统返回该任务及其考试、上下文、当前 Attempt 和发布状态
- **AND** 不要求该用户属于主办机构成员

#### Scenario: 隔离其他用户任务

- **GIVEN** 数据库中同时存在当前用户和其他用户的 Candidate
- **WHEN** 当前用户查询学员考试任务
- **THEN** 结果只包含当前用户的 Candidate

### Requirement: 学员恢复暂存答案

系统 MUST 在学员本人 Attempt 详情中返回已经暂存的答案和答案项，使单选、多选、判断、填空和简答能够在刷新或重新进入后恢复；没有暂存答案的题目
MUST 返回 `answer=null`。

#### Scenario: 恢复已暂存作答

- **GIVEN** 当前用户的 Attempt 已保存文本答案和多个答案项
- **WHEN** 用户重新读取该 Attempt
- **THEN** 对应题目返回 `ExamAnswerVO`
- **AND** 答案文本、答案项、已答状态和最后暂存时间与持久化事实一致

### Requirement: Attempt 本人执行链授权

系统 MUST 在学员读取 Attempt 时验证当前登录用户、Candidate 与 Attempt 属于同一本人执行链。非本人访问 MUST 返回 HTTP
403，资源不存在 MUST 返回 HTTP 404。

#### Scenario: 拒绝读取他人 Attempt

- **GIVEN** Attempt 属于另一用户
- **WHEN** 当前用户按 Attempt ID 请求详情
- **THEN** 系统返回 HTTP 403
- **AND** 不返回题目、答案或成绩数据

### Requirement: 完整答案契约

系统 MUST 在 OpenAPI 中提供 `ExamAnswerVO` 和 `ExamAnswerItemVO`，覆盖答案内容、结构化答案项、作答状态、评分状态、得分、正确性和时间字段；每个字段
MUST 有中文说明、类型、可空性和 example。

#### Scenario: OpenAPI 表达完整答案

- **GIVEN** 前端读取 `docs/api/exam.openapi.json`
- **WHEN** 解析 `ExamAnswerVO` 与 `ExamAnswerItemVO`
- **THEN** 每个对外字段都有中文说明、类型、可空性和 example
- **AND** Attempt 题目通过明确引用关联暂存答案

## MODIFIED Requirements

### Requirement: 判分与成绩发布

客观题 MUST 自动判分；主观题 MUST 进入待人工批阅；批阅记录 MUST 只追加。成绩只有在 Attempt 状态为 `PUBLISHED`
且成绩发布时间存在时才视为已发布。

学员侧在成绩未发布前 MUST NOT 获得 Attempt 汇总成绩、答案级得分、正确性、正确答案、正确选项标记或题目解析。成绩发布后 MUST
可获得正式最终成绩、及格状态和发布时间；分项成绩、正确答案与解析还 MUST 满足 `answerDetailMode`：`SUBMITTED` 为已发布，
`PASSED` 为已发布且及格，`EXAM_ENDED` 为已发布且考试开放期结束，`RETAKE_ENDED` 为已发布且已无可用补考机会，`NEVER`
为始终隐藏。策略值未知或依赖事实不完整时 MUST 默认隐藏。

#### Scenario: 隐藏未发布成绩

- **GIVEN** Attempt 已交卷或已完成批阅但尚未发布
- **WHEN** 学员读取任务或 Attempt 详情
- **THEN** 返回 `scorePublishStatus=UNPUBLISHED` 和 `scoreVisible=false`
- **AND** 所有成绩、正确性、正确答案及解析字段为空或不返回

#### Scenario: 返回已发布正式结果

- **GIVEN** Attempt 状态为 `PUBLISHED` 且存在成绩发布时间
- **WHEN** 本人学员读取任务或 Attempt 详情
- **THEN** 返回 `scorePublishStatus=PUBLISHED`、`scoreVisible=true`
- **AND** 返回正式最终成绩、及格状态和成绩发布时间
- **AND** 其他答案详情仅按已确认策略开放

## REMOVED Requirements

无。
