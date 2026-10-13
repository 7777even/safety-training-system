# course-assessment-source-separation Specification Delta

## ADDED Requirements

### Requirement: 课程测验交卷必须形成内容项参与事实

系统 MUST 在课程测验交卷且作答进入 `PENDING_REVIEW` 时，按
`assessmentExecutionId + courseEnrollmentId + courseItemId` 幂等更新该报名的内容项进度：内容项
`progressStatus` 转 `LEARNING`、作答次数记 1、`firstStartTime` 与 `lastStudyTime` 取交卷时间。
该阶段 MUST NOT 写入 `course_assessment_result`、MUST NOT 记录任何分数或及格结论、MUST NOT 把内容项
置为 `COMPLETED`，课程进度百分比、累计学习时长与学分 MUST NOT 因交卷发生变化。重复交卷回调
MUST NOT 重复累加作答次数。

成绩生效时，同一内容项 MUST 由生效结果转 `COMPLETED`，作答次数 MUST NOT 因交卷与生效两次投影而
重复累加。

#### Scenario: 课程测验交卷后内容项出现待批阅进度

- **GIVEN** 学员在课程测验中完成作答并交卷，且存在主观题待批阅
- **WHEN** 交卷完成
- **THEN** 该报名的测验内容项进度为 `LEARNING`
- **AND** 作答次数为 1，`firstStartTime` 与 `lastStudyTime` 均为交卷时间
- **AND** 管理端学员学习进度与学员端任务视图都能看到该内容项的作答事实

#### Scenario: 交卷不得提前产生成绩事实

- **GIVEN** 课程测验交卷后尚待教师批阅
- **WHEN** 查询该报名的内容项进度与考核结果
- **THEN** 不产生 `course_assessment_result` 记录
- **AND** 不返回总分、题目得分、标准答案或解析
- **AND** 课程进度百分比与学分与交卷前一致

#### Scenario: 成绩生效后转为已完成且作答次数不重复

- **GIVEN** 课程测验交卷已投影内容项参与事实，作答次数为 1
- **WHEN** 教师完成批阅使课程测验成绩生效
- **THEN** 同一内容项 `progressStatus` 转 `COMPLETED`
- **AND** 作答次数仍为 1
- **AND** 内容项完成参与课程完成度与学分计算

#### Scenario: 重复交卷回调保持幂等

- **GIVEN** 同一报名的同一内容项已存在 `LEARNING` 进度记录
- **WHEN** 再次收到同一交卷事实
- **THEN** 系统只刷新最近学习时间
- **AND** 不新增进度记录、不重复累加作答次数、不改变内容项完成状态

### Requirement: 学员课程目录必须按考核状态展示

学员端课程目录（课程详情页与课程学习页）MUST 以作答与批阅状态展示课程测验内容项：已交卷待批阅
MUST 显示为等待批阅，结果已生效 MUST 显示成绩结论；学员已交卷时 MUST NOT 仍显示「待学习」或
「待参加」。同一学员在课程详情页与课程学习页 MUST 得到一致的考核状态文案。

#### Scenario: 已交卷待批阅不再显示待学习

- **GIVEN** 学员已交卷且课程测验成绩尚未生效
- **WHEN** 打开课程详情页或课程学习页的课程目录
- **THEN** 该内容项显示为已交卷、等待批阅
- **AND** 不显示「待学习」或「待参加」

#### Scenario: 结果生效后目录显示成绩结论

- **GIVEN** 课程测验成绩已生效
- **WHEN** 打开课程目录
- **THEN** 该内容项显示结果已生效与最终分数
- **AND** 提供查看结果入口
