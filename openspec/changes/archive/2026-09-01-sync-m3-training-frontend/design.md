# Design：M3 培训模块前端同步

## 1. 契约与数据边界

- 唯一接口来源为 `docs/api/training.openapi.json`；前端不补造字段、状态或错误码。
- 所属机构由后端登录态派生。前端不得提交或筛选 `organizationId`，仅可将详情返回的机构信息作为只读展示。
- `trainingNature` 新建和编辑仅可选 `INITIAL`（初培）或 `REVIEW`（复审）；`UNSPECIFIED` 只用于历史计划只读显示。
- `applicableRequiredHours` 在草稿为空时显示“发布后计算”，发布后按后端冻结值显示。
- `compositeScore`、`officialCalculationRunId` 仅按当前后端结果读取；前端不得将其解释为 M3 自行计算结果。

## 2. 页面与路由

| 页面 | 路由 | 导航方式 |
| --- | --- | --- |
| 培训计划列表 | `/admin/business-center/training/plan` | 新增后台菜单 |
| 教师任务列表 | `/admin/business-center/training/teacher-tasks` | 新增后台菜单 |
| 计划工作台 | `/admin/business-center/training/plan/create`、`/admin/business-center/training/plan/:trainingPlanId` | 内部路由 |
| 教师场次详情 | `/admin/business-center/training/teacher-tasks/:offlineSessionId` | 内部路由 |
| 我的培训 | `/portal/training` | 既有门户菜单替换占位页 |
| 培训详情 | `/portal/training/:trainingParticipantId` | 门户内部路由 |
| 移动培训页 | `pages/training/index`、`detail`、`offline-session` | `pages.json` 路由 |

PC 后台列表复用 `admin-business-page`，工作台复用 `admin-detail-page`、`admin-detail-page__steps` 和 `sticky-actions`；门户遵循课程学习中心的横幅与任务卡片基准。

培训标准、模板、评价表单与计划工作台均由父页面持有路由、接口、权限和版本状态；每个步骤的工作区卡片迁入所属 `components/` 私有组件。组件统一提供 `admin-detail-page__surface`、`admin-detail-page__form-scroll` 与 `sticky-actions`，不在组件内新增 API 或改变已发布只读语义。

## 3. 工作流

1. 管理员维护类型、标准、模板和评价表单后，在计划工作台完成基本信息、阶段内容、评价表单、参训人员和发布预检。
2. 管理员按当前 OpenAPI 执行发布、生成执行实例、开始、同步结果、结束或取消；后端状态和错误信息为唯一依据。
3. 教师在本人任务中处理线下场次、签到确认和线下成绩。
4. 学员在 PC/移动端只读取本人任务，统一进入课程、考试/练习或线下场次。

## 4. Mock 与真实接口

培训生产页面统一接入真实接口，删除培训类型、标准、模板、评价表单和候选资源的生产 Mock 分支。测试夹具可保留在测试目录。
