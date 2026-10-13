# Spec Delta：移动端课程考核闭环

## ADDED Requirements

### Requirement：移动端课程考核闭环

移动端学员 MUST 能从课程 `QUESTION_BANK` 和 `QUIZ` 内容项开始、继续、提交和回看课程考核；所有作答、结果与进度请求 MUST 使用课程域接口，不得进入独立考试路径。

#### Scenario：移动端完成题库练习

- **GIVEN** 学员具有可学习的课程题库练习内容项
- **WHEN** 学员完成全部冻结题目并提交
- **THEN** 移动端显示自动生效结果
- **AND** 刷新的课程目录将该内容项显示为已完成

#### Scenario：移动端提交待批阅课程测验

- **GIVEN** 课程测验包含待人工批阅题目
- **WHEN** 学员提交课程测验
- **THEN** 移动端显示等待批阅
- **AND** 不显示独立考试人工发布文案或未生效成绩
- **AND** 待结果自动生效后才将课程内容项显示为已完成

### Requirement：课程目录任务状态必须与实际服务端行为一致

课程任务快照的 `examTaskStatus` MUST 使用 `NOT_STARTED`、`IN_PROGRESS`、`WAITING_RESULT` 与 `RESULT_EFFECTIVE`。作答详情的内部 `attemptStatus` 继续使用 `ANSWERING`、`PENDING_REVIEW` 与 `RESULT_EFFECTIVE`；两类状态不得混用。

#### Scenario：课程测验等待批阅

- **GIVEN** 学员已提交包含主观题的课程测验
- **WHEN** 学员读取课程目录任务
- **THEN** 返回 `examTaskStatus=WAITING_RESULT`
- **AND** 移动端进入课程结果等待页而非可编辑作答页
