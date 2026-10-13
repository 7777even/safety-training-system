# Tasks：开课、报名与学习事实

> 阻塞项已按 design 推荐默认关闭。前端不在范围。一次最多连续 3 个 Task。

## Task 1：开课管理与版本冻结

- 状态：已实施
- Owner：AI
- 输入：design D3；五表中的 `sts_course_offering` migration（可与 Task2/3 同 migration 一次建齐）
- 输出：开课 CRUD、发布/结束/取消；冻结 `course_version_id`；content-mode 开课守卫；权限与 OpenAPI 开课部分
- 验收标准：
  - [x] 发布开课写入不可变 `course_version_id`
  - [x] 课程无发布版本时拒绝开课发布
  - [x] 存在 DRAFT/PUBLISHED 开课时 content-mode 切换失败
  - [x] 单测覆盖发布冻结与守卫
- 测试方式：`CourseOfferingServiceImplTest` + 相关回归

## Task 2：报名（指派 / 自助 / 退选）

- 状态：已实施
- Owner：AI
- 输入：Task 1；design D2
- 输出：`sts_course_enrollment` 能力；管理端指派/移除；学员自助报名/选修退选；唯一约束
- 验收标准：
  - [x] 同开课同用户唯一
  - [x] OPEN+自助允许 SELF；REQUIRED 禁止自助退选；ELECTIVE 未完成可退选
- 测试方式：`CourseEnrollmentServiceImplTest`

## Task 3：学习会话、进度、完成与评价

- 状态：已实施
- Owner：AI
- 输入：Task 2；design D1/D4/D5；版本快照 JSON
- 输出：会话幂等上报、item_progress、报名汇总、完成判定、评价、管理端学习详情 API、OpenAPI 全文
- 验收标准：
  - [x] client_trace_id 幂等不双计
  - [x] ALL_REQUIRED_ITEMS 完成判定
  - [x] 评价一报名一条
- 测试方式：`CourseLearningServiceImplTest` 等
