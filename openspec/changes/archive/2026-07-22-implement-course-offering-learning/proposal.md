## Why

课程发布版本（步骤 4）已交付。开课、报名与学习事实是学员侧闭环的前置；没有开课绑定不可变版本，学习进度无法落到稳定内容上。按
`course-module-task-plan` 步骤 5 继续推进。

## What Changes

- 新增表：`sts_course_offering`、`sts_course_enrollment`、`sts_course_item_progress`、`sts_course_study_session`、
  `sts_course_evaluation`。
- 管理端：开课 CRUD、发布开课（冻结 `course_version_id`）、结束/取消；指派报名与开放自助报名；学习详情只读汇总。
- 学员侧 API：自助报名/退选（选修规则）、学习会话上报（幂等令牌）、内容项进度聚合、课程完成判定、评价提交。
- 发布课程切换内容模式守卫：存在有效开课引用时拒绝（补齐步骤 3 遗留）。
- 同步 OpenAPI 与权限点；前端不在本协作范围。

## Capabilities

### New Capabilities

- `course-offering`: 开课管理与发布（绑定课程版本）。
- `course-enrollment-learning`: 报名、学习会话、进度、完成判定与评价。

### Modified Capabilities

- `course-publish-version` / 草稿编排：开课发布后 `course_version_id` 不可变；存在有效开课时禁止课程 `content-mode` 切换。

## Impact

- 后端 `com.sts.course.*`、migration、`docs/api/`、`menus.json`。
- 依赖：已发布课程版本；默认机构方案 A；`audience` 公共能力未交付前，`audience_id` 可空且不解析动态范围。
- 非目标：培训计划驱动开课、完整数据范围六类、素材转码、考试类内容项。
