# 归档记录

- 归档日期：2026-08-27
- 归档原因：已被 `2026-08-25-separate-course-assessment-from-exam` 替代，不能按原规格关闭。
- 规格同步：未写入 `openspec/specs/`。课程 `EXAM/PRACTICE + examId` 回接已废弃，主规格以
  `course-assessment-source-separation` 为准。

## 任务状态

Task 1～2 当时标记“已实现，MySQL IT 环境阻塞”，实现的是课程内容项绑定独立考试并回写正式成绩。后续确认课程理论题库/测验必须直接引用题库或试卷版本，独立考试不得作为课程资源模板。

## 后续

不要按本 Change 恢复 `examId` 课程项路径。缺口只允许在课程考核分域规格上另开 Change。
