# Spec Delta：课程测验交卷事实可见性

## 变更类型

- 修改既有能力：`course-assessment-source-separation`（新增交卷参与事实 Requirement）
- 不新增能力规格，不删除或重命名既有 Requirement。

## 机器可读 Delta

正式 Requirements 与 Scenarios 位于：

- `specs/course-assessment-source-separation/spec.md`

该 Delta 新增以下要求：

1. 课程测验交卷 MUST 幂等形成内容项参与事实（`LEARNING` / 作答次数 1 / 时间取交卷时间）。
2. 交卷 MUST NOT 产生任何成绩事实，MUST NOT 把内容项标记为已完成，MUST NOT 改变进度百分比与学分。
3. 成绩生效后同一内容项 MUST 转 `COMPLETED`，作答次数 MUST NOT 因两次投影重复累加。
4. 学员端课程目录 MUST 按作答与批阅状态显示，已交卷 MUST NOT 仍显示「待学习 / 待参加」。
5. 成绩未生效时 MUST 只展示本人已提交答案，MUST NOT 展示判分、标准答案或解析。
