# Spec Delta：课程学习进度上报闭环与完成阈值生效

## 变更类型

- 新增能力：`course-learning-progress`
- 不修改既有能力规格，不删除或重命名既有 Requirement。

## 机器可读 Delta

正式 Requirements 与 Scenarios 位于：

- `specs/course-learning-progress/spec.md`

该 Delta 新增以下要求：

1. 客户端 MUST 在每次上报检查点前结算活跃区间；不可见区间与媒体暂停区间 MUST NOT 计入有效学习时长。
2. ASSET 内容项完成阈值 MUST 按「自定义材料规则要求秒数 → 内容项最低学习秒数 → 材料规则默认值」
   三级解析；阈值缺失 MUST NOT 被当作立即完成。
3. QUIZ / QUESTION_BANK MUST NOT 按学习时长自动完成；LIVE MUST NOT 按学习时长自动完成。
4. 内容项进度一旦 COMPLETED，MUST NOT 因阈值变化回退。
5. 「时长模式」MUST NOT 再作为可配置项；字段缺失时服务端 MUST 按 `AUTO` 落库，MUST NOT 拒绝请求。
6. `attemptCount` MUST 描述为学习会话次数；已废弃字段的示例值 MUST 落在自身 enum 范围内。

## 与既有规格的关系

- `learner-course-reading` 约束「谁能读到哪些课程与任务」，本能力约束「读到之后进度如何产生与推进」，
  两者互补且无重叠，无需改动前者。
- `training-execution-loop` 的「安排学时与获得学时不得混用」要求 COURSE 的获得学时取正式学习秒数。
  本能力使该秒数真实产生，属于对该既有要求的上游修复，不改变其口径。
