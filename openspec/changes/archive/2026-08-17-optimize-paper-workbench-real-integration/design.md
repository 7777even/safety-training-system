# Design：优化试卷工作台并完成真实接口联调

## 1. 依据与约束

- API Contract：`docs/api/paper.openapi.json`。
- 既有 M2 规格：`openspec/changes/2026-07-27-implement-m2-frontend-question-exam/`。
- 课程工作台视觉与交互模板：`frontend/pc-web/src/views/admin/business-center/course/detail/CourseWorkbench.vue`。
- 现有试卷入口：`/admin/business-center/exams/paper/editor` 与 `/admin/business-center/exams/paper/editor/:paperId`。

不新增接口、字段、权限或数据库变更。真实环境返回的 Contract 不一致属于阻塞项，不在前端自行兼容未定义字段。

## 2. 工作台信息架构

```
基础信息 ──保存成功──> 组卷配置 ──预览成功──> 发布
     │                       │                 │
     └────未保存时禁用────────┴─────────────────┘
```

工作台保持一个 URL 上下文：

- 新建时使用编辑器入口；基础信息创建成功后替换为带 `paperId` 的编辑器地址并进入组卷配置。
- 已有试卷根据 `paperId` 读取基础信息、版本列表和当前版本详情；三个阶段均在同一页面切换，不新增可见菜单。
- 发布成功后仍跳转到既有版本只读页，确保已发布快照不可编辑。

## 3. 组件边界

- 工作台壳层负责标题、阶段导航、阶段可访问性、试卷 ID 和返回列表。
- 基础信息面板只负责 `PaperCreateRequest` / `PaperUpdateRequest` 表单与保存。
- 组卷配置面板只负责 `PaperComposeRequest`、题库选题、抽题规则、保存草稿和预览。
- 发布阶段只展示当前配置的发布前置条件和预览结果，并调用既有发布接口；不复制组卷编辑字段。

如果现有组件规模允许，可仅在当前编辑器文件中形成上述逻辑分区；不为单次页面改造创建通用抽象。

## 4. 真实接口联调

联调时保持 `VITE_USE_API_MOCK=false`，按以下顺序执行：

1. 以具备 `paper:paper:add`、`paper:paper:edit`、`paper:paper:list` 等权限的账号登录。
2. 创建固定、随机或自定义试卷基础信息，确认返回真实 `paperId`。
3. 保存有效的组卷草稿，预览并核对总分、及格分、题目或抽题规则。
4. 发布版本，确认返回真实 `paperVersionId`，并从版本只读页回读冻结版本。
5. 验证无权限、参数错误、草稿不满足发布条件等错误均显示服务端真实中文消息。

不得使用生产数据或生产环境；若指定的开发/测试服务不可访问、没有登录会话或无测试数据，记录为未执行的阻塞项，不伪造验收结论。

## 5. 风险与取舍

- 将三个阶段做成同页切换会增加局部状态管理，但避免路由碎片化，且与课程创建体验一致。
- 当前远程开发代理不可达时，无法完成真实联调；可先完成前端 TDD 和目标构建，但 Task 不得标记完成。
- 当前工作区存在未提交的试卷列表页修改，本 Change 不触碰该文件，避免覆盖协作中的变更。
