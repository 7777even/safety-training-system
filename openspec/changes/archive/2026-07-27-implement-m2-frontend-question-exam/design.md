# Design：M2 题库、试卷与考试前端 Mock 优先闭环

## 1. 依据与边界

- 后端规格：`openspec/changes/2026-07-24-implement-question-paper-exam/`。
- 接口唯一来源：`docs/api/question.openapi.json`、`docs/api/paper.openapi.json`、`docs/api/exam.openapi.json`、
  `docs/api/course.openapi.json`、`docs/api/common-audience.openapi.json`。
- 业务顺序：`docs/requirement/end-to-end-development-roadmap.md` 的阶段 2、阶段 3。

前端不得重定义题目版本、试卷版本、考试尝试、分数或权限语义。Mock 与真实请求必须共用 API 类型、入参和 `Result` 解包逻辑；任何
Contract 缺口须暂停当前 Task，先更新并确认 OpenAPI。

## 2. Contract Mock 与切换策略

每个 M2 API 模块使用统一规则：`import.meta.env.VITE_USE_API_MOCK !== 'false'` 时调用同域 Mock，否则调用已经保留的真实请求分支。Mock
文件置于各端的 `src/mock/`，必须标记 `TODO replace real api`，只保存当前会话内的最小种子数据。

Mock 必须复现 Contract 中的成功数据、分页、版本冲突、权限拒绝、导入失败和提交失败等页面需要处理的场景；不得添加 OpenAPI
未定义的字段、状态或权限规则。真实接口可用后，设置 `VITE_USE_API_MOCK=false`，执行同一测试场景的真实联调；联调和验收通过后才删除
Mock 文件与分支。

## 3. 领域对象与不可变边界

| 对象        | 负责人角色  | 可变边界                              | 冻结/发布边界                              |
|-----------|--------|-----------------------------------|--------------------------------------|
| 知识点、题库、题目 | 管理员    | 知识点树、题库与题目当前编辑内容                  | 发布题目后创建只读题目版本                        |
| 试卷        | 管理员    | 草稿组卷与抽题规则                         | 发布时固化题目版本快照                          |
| 考试        | 管理员    | 新建时选择 `paperVersionId`；已有考试编辑其余规则 | 发布后考生按指定版本作答；编辑不得更换 `paperVersionId` |
| 考试尝试      | 学员     | 未提交答案可暂存                          | 开始时冻结题目/选项；交卷后不可由学员修改                |
| 批阅与成绩     | 教师、管理员 | 教师追加主观题批阅；管理员发布结果                 | 已发布成绩是学员和课程读取的正式结果                   |

已确认的 Contract 解释：`currentVersionNo` 是试卷版本序号，不是 `paperVersionId`；版本详情和新建考试必须通过版本列表取得真实
`paperVersionId`。

## 4. 信息架构与路由

页面组件位于既有业务目录。列表页继续由 `system/users/profile` 返回的 `C` 类型菜单注册；新增、编辑、详情和配置页由前端受保护内部路由注册，不创建或要求配置隐藏
`C` 类型菜单。内部路由必须以来源列表菜单和既有按钮权限为前置条件，且不得参与后台侧栏导航。本 Change
不修改后端初始化菜单；线上菜单由管理员按交付的配置手工维护。

### 4.1 PC 管理端（业务中心）

| 路径                                                                    | 页面职责                           |
|-----------------------------------------------------------------------|--------------------------------|
| `/admin/business-center/exams/question`                               | 题库、知识点、分类与题目列表                 |
| `/admin/business-center/exams/question/create`                        | 新建题目                           |
| `/admin/business-center/exams/question/:questionId`                   | 题目编辑、素材关联和版本发布/查看              |
| `/admin/business-center/exams/paper`                                  | 试卷列表与状态筛选                      |
| `/admin/business-center/exams/paper/editor`                           | 新建试卷并在同一工作台完成基础信息、题库选题、组卷和预览   |
| `/admin/business-center/exams/paper/editor/:paperId`                  | 编辑已有试卷并在同一工作台完成基础信息、题库选题、组卷和预览 |
| `/admin/business-center/exams/paper/:paperId/version/:paperVersionId` | 已发布试卷版本只读详情                    |
| `/admin/business-center/exams/exam`                                   | 考试列表与发布状态                      |
| `/admin/business-center/exams/exam/create`                            | 新建考试并选择试卷版本                    |
| `/admin/business-center/exams/exam/:examId`                           | 考试规则详情/编辑与发布                   |
| `/admin/business-center/exams/exam/:examId/assignment`                | 考生参与范围和批阅人配置                   |
| `/admin/business-center/exams/exam/:examId/grading/:examAttemptId`    | 教师批阅指定作答                       |
| `/admin/business-center/exams/exam/:examId/results/:examAttemptId`    | 管理员发布成绩、创建补考和重置练习              |

内部路由授权映射如下：

| 路径                                                                    | 来源菜单 | 注册前置权限                   |
|-----------------------------------------------------------------------|------|--------------------------|
| `/admin/business-center/exams/question/create`                        | 题库中心 | `question:question:add`  |
| `/admin/business-center/exams/question/:questionId`                   | 题库中心 | `question:question:list` |
| `/admin/business-center/exams/paper/editor`                           | 试卷管理 | `paper:paper:add`        |
| `/admin/business-center/exams/paper/editor/:paperId`                  | 试卷管理 | `paper:paper:edit`       |
| `/admin/business-center/exams/paper/:paperId/version/:paperVersionId` | 试卷管理 | `paper:paper:list`       |
| `/admin/business-center/exams/exam/create`                            | 考试管理 | `exam:exam:add`          |
| `/admin/business-center/exams/exam/:examId`                           | 考试管理 | `exam:exam:list`         |
| `/admin/business-center/exams/exam/:examId/assignment`                | 考试管理 | `exam:exam:edit`         |
| `/admin/business-center/exams/exam/:examId/grading/:examAttemptId`    | 考试管理 | `exam:review`            |
| `/admin/business-center/exams/exam/:examId/results/:examAttemptId`    | 考试管理 | `exam:score:publish`     |

试卷的“新建、编辑、配置”进入同一工作台页面；版本详情保持独立只读页。仅轻量确认（如发布确认、删除确认）使用确认弹窗，不使用抽屉承载组卷、考试人员或题目编辑工作流。

### 4.2 学员 PC

| 路径                                             | 页面职责                            |
|------------------------------------------------|---------------------------------|
| `/portal/exams`                                | 待参加、进行中和已完成考试任务                 |
| `/portal/exams/:examId/attempt/:examAttemptId` | 专注作答：顶部考试信息/计时/交卷，桌面侧边答题卡与题目工作区 |
| `/portal/exams/:examId/result/:examAttemptId`  | 已发布成绩、答题卡、解析与考试记录入口             |

PC 作答页参考附件的信息层级：固定顶部显示总分、及格线、倒计时和提交操作；桌面侧边栏显示答题卡和题号状态；主体按题型分段展示题目。具体颜色仍遵循清爽现代风，不复制附件中的品牌色或移动端布局。

### 4.2.1 门户菜单与内部路由治理（后续页面开发强制遵循）

门户中仅配置用户能主动进入的业务中心为 `C` 类型菜单；由中心页上下文进入的作答、详情、结果、编辑、版本、配置等页面必须作为前端受保护内部路由注册，不配置为可见菜单，也不伪造隐藏
`C` 菜单。内部页注册以来源中心菜单和“查看”按钮权限为前提；页面内的新增、保存、提交、发布等操作必须继续受对应 `F`
类型按钮权限控制。这样既支持刷新和直链访问，也不会把工作流步骤碎片化为门户导航。

考试中心的完整配置如下；`<门户根菜单ID>` 由实际菜单树中的门户根节点替换，菜单 ID 由系统创建后返回，不在文档中硬编码：

| 层级   | 菜单名称    | 类型             | 父级          | path                                           | component              | perms                       | 可见       | 用途                           |
|------|---------|----------------|-------------|------------------------------------------------|------------------------|-----------------------------|----------|------------------------------|
| 门户入口 | 考试中心    | C              | `<门户根菜单ID>` | `/portal/exams`                                | `portal/exams/index`   | `exam:learner:task:list`    | 是        | 门户顶栏唯一可点击考试入口，展示本人考试任务       |
| 内部路由 | 考试作答    | INTERNAL（前端注册） | 考试中心        | `/portal/exams/:examId/attempt/:examAttemptId` | `portal/exams/attempt` | `exam:learner:attempt:view` | 否        | 从开始/继续作答进入；不创建菜单记录           |
| 内部路由 | 考试结果    | INTERNAL（前端注册） | 考试中心        | `/portal/exams/:examId/result/:examAttemptId`  | `portal/exams/result`  | `exam:learner:attempt:view` | 否        | 从已发布结果入口或交卷后进入；不创建菜单记录       |
| 按钮   | 开始考试    | F              | 考试中心        | —                                              | —                      | `exam:attempt:start`        | 是（不参与导航） | 控制任务页“开始考试”入口                |
| 按钮   | 查看/继续作答 | F              | 考试中心        | —                                              | —                      | `exam:learner:attempt:view` | 是（不参与导航） | 控制“继续作答”和“查看结果”入口，并作为内部页注册前提 |
| 按钮   | 暂存答案    | F              | 考试中心        | —                                              | —                      | `exam:attempt:answer`       | 是（不参与导航） | 控制作答页暂存及答案编辑能力               |
| 按钮   | 提交试卷    | F              | 考试中心        | —                                              | —                      | `exam:attempt:submit`       | 是（不参与导航） | 控制手动交卷与倒计时触发的超时交卷能力          |

`INTERNAL` 是前端路由元数据，不是 `system-admin.openapi.json` 可持久化的菜单类型；实际菜单仅创建一条 `C` 记录和四条 `F`
记录。前端须按上表把门户内部路由注册在考试中心来源路由下，设置 `hiddenInMenu: true`，并在页面按钮处使用 profile 返回的
`perms` 进行显示/禁用控制；后端仍是接口鉴权的唯一安全边界。

### 4.3 移动端 uni-app

| pages.json 路径        | 页面职责                              |
|----------------------|-----------------------------------|
| `pages/exam/index`   | 考试任务列表                            |
| `pages/exam/attempt` | 作答页：紧凑顶部状态、题目步骤、底部作答/暂存操作与独立答题卡入口 |
| `pages/exam/result`  | 成绩、答题卡与解析页                        |

移动端使用 44px 以上触控目标、卡片与单列信息层级；结果页可使用分数环形进度表达通过状态，但颜色和状态语义服从设计基准。不得将
PC 表格、侧栏或密集题目布局直接压缩到移动端。

## 5. Contract 映射

| 能力    | 必用接口                                                                                                                            |
|-------|---------------------------------------------------------------------------------------------------------------------------------|
| 题库与题目 | `/api/question/knowledge-points/*`、`/api/question/bank-categories`、`/api/question/banks`、`/api/question/questions`、题目发布/素材/导入接口 |
| 组卷与版本 | `/api/paper/papers`、`/{paperId}/compose`、`/{paperId}/preview`、`/{paperId}/publish`、`/versions/{paperVersionId}`                 |
| 考试编排  | `/api/exam/exams`、发布、候选人参与范围绑定、候选人列表、批阅人替换                                                                                      |
| 作答与成绩 | `/api/exam/attempts/start`、尝试详情、答案暂存、交卷/超时交卷、批阅、发布成绩、补考、练习重置                                                                    |

请求、`Result` 外壳、分页、错误码、乐观锁与权限异常一律由 OpenAPI 和既有请求层处理。

## 6. 实施顺序

```text
人工确认 Mock 优先与路由
  → Task 1：题库独立编辑页与 Contract Mock
  → Task 2：试卷独立组卷/版本页与 Contract Mock
  → Task 3：考试独立详情/人员配置页与 Contract Mock
  → Task 4：学员 PC/移动端作答与结果页
  → Task 5：批阅、成绩处置和课程考试项回接
  → Task 6：VITE_USE_API_MOCK=false 真实联调与 M2 验收
```

## 7. 风险与取舍

- Mock 仅使前端开发可继续，不证明后端权限、数据范围、幂等、跨域或登录状态；Task 6 必须在真实环境补齐。
- 随机卷快照由后端发布时固定，前端只展示预览与版本详情；浏览器不抽题。
- 超时交卷、防作弊事件、幂等和判分均以后端状态为准；前端仅提供 Contract 已定义的触发入口与反馈。
- 动态路由依赖 profile 菜单；若不为独立详情页配置可解析路径，直接访问会被守卫重定向。因此菜单配置是独立页面可访问的前置条件。
- 固定试卷从题库选择并固化明确题目；随机试卷只保存抽题规则；自定义试卷是固定题目与随机规则的组合。总分和及格分由题目分值与
  `passRatio` 计算并只读展示，发布后以服务端返回值为准；不得自行向组卷请求增加 `totalScore` 或 `passScore`。
- 已确认（2026-07-28）：Task 3 严格按现有 `exam.openapi.json` 实现。由于 `ExamVO` 未返回 `policy`
  ，详情页不得回显或更新既有考试策略，策略仅在新建考试时提交；由于 Contract 未提供批阅人读取接口，人员配置页不回显当前批阅人，保存时按
  `USER` 来源整体替换。本项作为后续 OpenAPI 优化记录，不在当前 Task 擅自扩展接口。
