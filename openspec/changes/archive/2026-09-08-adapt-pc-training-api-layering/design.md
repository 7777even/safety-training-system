# Design：PC Web 培训接口分层适配

## 契约与职责

- 创建计划携带 `sourceTemplateId` 时，由 `POST /api/training/plans` 在服务端复制课表；PC Web 仅重载计划详情。
- 已创建草稿改模板时，PC Web 在确认覆盖后调用 `POST /api/training/plans/{trainingPlanId}/apply-template`，请求为 `version` 和非空 `sourceTemplateId`。普通计划更新只修改来源字段，不复制或清空课表。
- 标准规则是发布检查口径，不生成模板/计划阶段；模板只提供可复用课表，`executionConfig` 是可选默认值。计划阶段才校验本期执行配置。
- 课程与考试独立对象是否来自培训，仅由 `planContentId` 是否存在区分。培训学员进入内容只调用 training learner enter，并按返回的报名、作答或场次 ID 跳转。

## 错误与展示

- 8156 定位至计划“阶段与内容”；8157 显示模板不可复制原因；8158 显示仅草稿可复制。
- 学员详情仅使用 `contents`、`results` 和 `plan.publishStatus`。实际成绩仅在 `score != null` 时展示，保留零分。

## 兼容性

- 不回填或迁移存量数据；服务端字段缺失或与 OpenAPI 不一致时停止前端推断，记录为契约阻塞。
- 现有门户页面样式改动保留，接口适配不引入新的 Mock 或后端字段。
