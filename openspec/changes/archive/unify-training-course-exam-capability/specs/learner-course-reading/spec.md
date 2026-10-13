# Spec Delta：learner-course-reading

## ADDED Requirements

### Requirement: 我的课程入口只读独立开课报名

「我的课程」MUST 只返回当前登录人来源为非 TRAINING 的报名。培训来源报名 MUST 由「我的培训」入口进入。按报名 ID 学习 MUST
仍允许本人访问培训来源报名，因为学习能力与入口查询条件分离。

#### Scenario: 我的课程不含培训报名

- **GIVEN** 当前用户同时存在独立开课报名和培训来源报名
- **WHEN** 用户查询我的课程
- **THEN** 结果不含 `enrollmentSource=TRAINING` 的报名
- **AND** 独立开课报名仍返回

#### Scenario: 从培训进入后按报名学习

- **GIVEN** 学员已通过培训入口获得 `courseEnrollmentId`
- **WHEN** 学员按该报名 ID 读取学习任务
- **THEN** 系统返回本人学习任务
