# Spec Delta：课程考试内容项与成绩回接

## ADDED Requirements

### Requirement: 课程考试资源引用

系统 MUST 仅允许课程机构引用存在、启用、已发布且对该机构具备 USE 授权的考试资源。EXAM_LIKE 内容项 MUST 有 `examId`，ASSET
内容项 MUST 有 `assetId`，服务端 MUST 校验而非信任前端 ID。

#### Scenario: 引用跨机构已授权考试

- **GIVEN** 考试已发布、启用且考试所属机构向课程机构授予 EXAM USE
- **WHEN** 管理员创建或更新考试类内容项
- **THEN** 系统保存该 examId

#### Scenario: 拒绝不可用考试

- **GIVEN** 考试不存在、未发布、停用或无有效 USE 授权
- **WHEN** 管理员引用该考试
- **THEN** 系统拒绝请求且不保存内容项引用

### Requirement: 课程上下文考试执行

系统 MUST 从当前登录学员、课程报名和冻结内容项创建课程上下文 Candidate；MUST NOT 接受调用方传入 userId 或任意课程上下文。

#### Scenario: 同一考试在不同课程中隔离

- **GIVEN** 同一考试被两个课程内容项引用
- **WHEN** 学员分别从两个课程进入考试
- **THEN** 系统创建独立的课程上下文 Candidate 和 Attempt

## MODIFIED Requirements

### Requirement: 课程考试类内容项

课程内容项类型 EXAM / QUESTION_BANK / PRACTICE MUST 可创建，并且 MUST 直接关联可用考试。参与事实存在后，受锁定规则保护的内容项
MUST NOT 修改关联考试。

#### Scenario: 参与后拒绝换考试

- **GIVEN** 内容项已有学习进度、课程 Candidate 或已被开课版本使用
- **WHEN** 管理员修改其 examId
- **THEN** 系统拒绝修改

### Requirement: 课程完成态读取正式成绩

课程完成态 MUST 只读取同一 courseEnrollmentId + courseItemId 上下文中正式发布且通过的考试结果；成绩发布 MUST
幂等回写课程内容项进度并重新计算报名完成态。

#### Scenario: 成绩发布完成课程考试项

- **GIVEN** 课程上下文 Candidate 的 Attempt 已正式发布且通过
- **WHEN** 成绩发布事务完成或重试
- **THEN** 对应课程内容项进度完成且课程报名被重新计算
- **AND** 重试不产生重复或跨课程回写

## REMOVED Requirements

无。
