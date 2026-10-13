# Spec Delta：开课与考试各是一套核心能力

## 变更类型

- 修改既有能力：`training-execution-loop`、`training-course-offering-lifecycle`、`learner-exam-reading`、
  `learner-course-reading`、`course-assessment-source-separation`
- 新增说明规格：`training-origin-admin-listing`（列表即能力对象列表，暴露 `planContentId`）

## 该 Delta 要求

1. 培训生成调用开课/考试的创建、发布、人员接口，不得插入已发布对象。
2. 开课发布按「已有版本则用已有，否则取当前」一条规则。
3. 考试发布按考试字段物化执行，一条路径。
4. 管理端开课/考试列表包含培训生成对象。
5. 学员三个入口读同一事实、不同来源条件。
