# Design：考试真实联调与契约对齐

## 1. 依据与边界

- 接口唯一来源：`docs/api/exam.openapi.json`、`docs/api/person-selection.openapi.json`。
- UI 基准：`docs/design/pc-web-ui-standard.md` 的“详情、编辑与工作台页”。
- 历史 Change：`2026-07-27-implement-m2-frontend-question-exam`、`2026-07-29-complete-learner-exam-read-contract`、`2026-07-30-add-exam-review-attempt-list-apis`。

本 Change 不修改数据库、后端权限语义或未确认的 API Contract。前端的每个请求必须来自当前 OpenAPI；废弃路径不得通过 Mock 或兼容分支保留。

## 2. 表单布局

`ExamDefinitionForm` 保持为创建页和草稿详情页的共享组件，但表单结构调整为：

- `ElForm.admin-detail-form` 内使用 `ElRow :gutter="24"`；常规字段放入 `ElCol :span="12"`；
- 考试须知内容、策略分组与其它长文本/可变内容使用 `admin-detail-form__wide` 独占整行；
- 常规控件保持 320px，数字控件保持 220px；
- 创建、详情和人员配置均采用“标题 → 可滚动表单区 → sticky-actions”的详情卡结构。

这只改变呈现和可读性，不改变创建/更新请求字段、校验或业务默认值。

## 3. 考生和批阅人配置

考生绑定不再使用“已冻结参与范围”或受众 ID。考试人员配置页使用既有 `PersonPickerDialog`（其请求基于
`GET /api/common/person-picker`）选择当前考试所属机构的具体人员，并转换为 `selectionType=USER` 的
`PersonSelectionRequest` 后提交：

```text
POST /api/exam/exams/{examId}/candidates/bind-selection
BindSelectionRequest {
  selection: PersonSelectionRequest,
  contextType: "STANDALONE",
  contextId: 0
}
```

批阅人继续通过 `PUT /api/exam/exams/{examId}/reviewers` 的 `reviewerUserIds` 整体替换。页面先完成选择，再分别发起考生绑定和批阅人替换；任一请求失败时向用户显示真实 Result 消息，并重新读取已绑定考生和批阅人，不在客户端宣称保存成功。

`resultNotifyAudienceId` 按 proposal 的人工决策执行。在决策前不得调用受众接口、不得编造人员选择到该字段的映射。

## 4. 当前契约下的真实闭环

| 阶段 | 角色 | 当前 OpenAPI 操作 | 验证结果 |
| --- | --- | --- | --- |
| 创建草稿 | 考试管理员 | `POST /api/exam/exams` | 返回 `examId`，可读取草稿详情 |
| 配置人员 | 考试管理员 | `POST .../candidates/bind-selection`、`PUT .../reviewers` | 考生分页和批阅人回显正确 |
| 发布考试 | 考试管理员 | `POST /api/exam/exams/{examId}/publish` | 考试状态为 PUBLISHED |
| 开始/作答/交卷 | 学员 | `GET /learner/tasks`、`POST /attempts/start`、`PUT /attempts/{id}/answers`、`POST .../submit` | 仅本人可见并可恢复答案 |
| 教师批阅 | 批阅教师 | `GET /review-tasks`、`POST /answers/{id}/review` | 仅已分配任务可见，主观题得分持久化 |
| 发布成绩 | 成绩发布人 | `POST /attempts/{id}/publish-score` | 正式成绩与发布时间产生 |
| 查看成绩 | 学员 | `GET /learner/tasks`、`GET /learner/attempts/{id}` | 仅已发布的成绩按策略可见 |

新建考试加载试卷版本时，先按每份试卷的 `versionNo` 降序排序，再仅保留第一个 `PUBLISHED` 版本。历史版本仍在
`GET /api/paper/papers/{paperId}/versions` 中保留，供既有考试和试卷版本详情回读；前端不得将其作为常规的新建考试选项。

真实联调运行 `VITE_USE_API_MOCK=false`，以已获授权的非生产会话执行。所有测试数据记录 ID 和清理责任，但不得触及生产环境。

## 5. 风险与取舍

- 人员选择契约只提供当前机构的分页候选人；若真实服务无法按考试所属机构返回数据，属于 Contract/后端行为偏差，停止并提交人工决策。
- 发布成绩依赖教师批阅完成；若真实服务的状态转换或权限与 OpenAPI 不一致，记录 HTTP 状态与 Result 后停止，不在前端规避。
- 关闭 Mock 后的失败是联调证据，不得退回 Mock 宣称流程完成。

## 6. 全模块字段—界面操作审计

| 契约域 | 审计结论 | 整改决策 |
| --- | --- | --- |
| 考试创建/更新 | 必填字段、试卷版本、题库、负责人、开放时间和策略均有对应控件；`paperVersionId` 在编辑阶段锁定符合不可变版本边界 | 保持现状 |
| 考生绑定 `BindSelectionRequest` | 当前页仅支持逐人选择并固定提交 `selectionType=USER`；`ALL`、`DEPT` 与其 `subjectIds` 不可操作 | 改用已有 `PersonSelectionDialog`，让管理员以“全部人员 / 按部门 / 指定人员”三种模式选择；仍固定独立考试的 `contextType=STANDALONE`、`contextId=0` |
| 批阅人替换 | `reviewerUserIds` 与 `assignmentSource=USER` 对应明确的逐人选择 | 保持现状；不把批阅人扩展为未在当前业务页确认的部门/全员分派 |
| 学员开始、暂存、交卷 | `examId`、`userId`、幂等令牌、答案、交卷原因和耗时均在页面或客户端运行时生成 | 保持会话/客户端自动派生字段，不增加无业务价值的手工控件 |
| 教师批阅、成绩发布、补考、练习重置 | 批阅分数/意见、发布确认、补考、重置范围/原因均有对应操作 | 保持现状；真实联调核验权限与状态反馈 |
| 反作弊事件 | 当前有读取列表，但 PC/移动作答端没有调用 `POST .../anticheat-events` 记录事件；策略开关没有可追溯的前端事件入口 | 在作答端以最小事件采集记录失焦/页面隐藏和超时提交，遵从既有策略与 OpenAPI，不实现摄像头、人脸或新防作弊能力 |
| 成绩与解析可见性 | 学员端以 `scoreVisible` 和发布状态控制成绩，但结果页仍固定展示“标准答案/解析”文案，即使服务端因策略裁剪了字段 | 只有 Contract 返回相应字段时才展示对应区块；无字段时不以“未提供”伪装为可见内容 |
| `resultNotifyAudienceId` | 当前 OpenAPI 留有兼容字段但无受众读取/选择契约；前端输入已移除，详情页仍可能显示原始 ID | 不展示原始 ID；保留请求/响应类型兼容，直到有经确认的通知 Contract |

上述整改均不改变现有 API Contract、权限模型或数据库结构。

## 7. 考试详情三步工作台

考试详情使用与试卷管理详情、课程详情一致的顶部步骤栏，步骤固定为：

1. 设置考试信息：承载现有草稿编辑或已发布只读详情；
2. 人员配置：导航至既有 `/assignment` 页面，不在详情页复制人员配置逻辑；
3. 发布考试：仅草稿可进入，复用现有 `POST /api/exam/exams/{examId}/publish` 确认操作；已发布考试显示完成状态且不再触发请求。

步骤栏是页面导航与当前操作状态的可视化，不替代后端发布前校验或权限判断。人员配置页保留返回详情入口；发布后重新读取详情并同步步骤状态。
