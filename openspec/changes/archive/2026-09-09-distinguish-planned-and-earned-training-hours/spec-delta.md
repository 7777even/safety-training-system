# Spec Delta：区分培训安排学时与获得学时

## Modified Capabilities

### training-execution-loop

计划内容 `requiredHours` 是安排学时，发布时用于总学时和线上占比；COURSE/EXAM/QUESTION_BANK 计入线上安排。学员
`effectiveDurationSeconds`、`onlineHours`、`offlineHours` 是获得学时：课程为正式学习秒，线下为确认出勤有效秒，考试/题库为
0。二者 MUST NOT 混用。题库执行配置 `durationSeconds` MUST 仅表示作答时限。结束门禁 MUST NOT 用个人获得学时重算
TOTAL_HOURS。
