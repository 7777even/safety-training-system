# Tasks：M2 题库、试卷与考试前端 Mock 优先闭环

> 状态：已确认并实施（2026-07-28）。本次范围变更为 Mock 优先和独立工作流页面，原 Task 1～3 的最小列表实现不作为本版完成依据；Task
> 6 真实接口联调仍待后端环境。

## Task 1：题库资源、题目编辑页与版本管理

- 状态：已完成（2026-07-28）
- Owner：AI
- 输入：`question.openapi.json`；既有题库列表页；Mock 开关规范
- 输出：题库列表、题目新建页、题目编辑/版本页，以及与 Contract 一致的题库 Mock 和真实请求切换
- 前后端影响：PC Web；不修改后端与 OpenAPI
- API 依赖：知识点、题库分类、题库、题目、题目发布、素材关联和批量导入接口
- 数据影响：无数据库变化；Mock 数据仅在浏览器会话中保存
- 验收标准：题目编辑与发布状态清晰区分；发布后的版本只读；导入展示 Contract 的成功/失败行数；页面可在
  `VITE_USE_API_MOCK=false` 下原样走真实请求；复杂编辑不使用 Dialog/Drawer
- 测试方式：先写 API Mock 切换、导入结果和独立页面行为失败测试；运行题库定向测试、PC `lint`、`typecheck`、`build`

完成记录：先观察 Mock 开关和独立题目页面不存在的失败测试，再新增 `question` Contract Mock、真实请求切换、题目新建页和题目详情/版本页。发布版本在
Mock 与页面中均不可编辑；素材关联迁入详情页；填空答案、简答关键词和判分开关按 Contract 录入；导入展示批次成功/失败行数。2026-07-28
补充：题库、知识点树和题库分类统一为左侧一级选择、右侧展示对应数据的主从布局，题库默认选中首项；新增题库可按 Contract
关联题库分类和知识点。验证通过 Node 10/10、定向 lint、typecheck、build 和 `git diff --check`；真实接口联调留待 Task 6。

## Task 2：试卷工作台组卷、预览与版本发布

- 状态：已完成（2026-07-28，整改确认）
- Owner：AI
- 输入：Task 1；`paper.openapi.json`
- 输出：试卷列表、同页新建/编辑/组卷工作台、只读版本页，以及 Contract Mock 和真实请求切换
- 前后端影响：PC Web；不修改后端与 OpenAPI
- API 依赖：试卷 CRUD、组卷、预览、发布和版本详情接口
- 数据影响：无数据库变化；草稿和 Mock 均只在浏览器会话内保留
- 验收标准：新建、编辑和组卷在同一工作台完成；固定试卷从题库选择题目，随机试卷配置抽题规则，自定义试卷组合二者且显示中文说明；完整展示
  PaperCreate/PaperUpdate/PaperCompose/PaperVO/PaperPreview/PaperVersion 的 Contract 字段，其中总分和及格分只读计算；发布前必须预览；随机规则使用
  Contract 的 `drawCount` 和 `sortOrder`；发布后版本只读；`currentVersionNo` 仅作为版本序号，必须通过版本列表取得真实 `paperVersionId`；复杂组卷不使用
  Dialog/Drawer
- 测试方式：先写组卷规则、预览/发布、Mock 切换失败测试；运行试卷定向测试、PC `lint`、`typecheck`、`build`

原完成记录保留：先观察 Paper Mock 开关、独立组卷/版本页面缺失的失败测试；随后补齐试卷 CRUD、创建、组卷、预览、发布和版本快照的
Contract Mock 与真实请求切换。2026-07-28 已确认整改：收敛为同页试卷工作台、从题库选题、补足字段与结构化预览；
原方案曾把 `currentVersionNo` 当作 `paperVersionId`，并在无草稿读取接口时仅用预览结果回读。2026-08-01 按补全后的 Contract 更正：
通过 `GET /api/paper/papers/{paperId}/versions` 将版本序号解析为真实 `paperVersionId`，再读取版本详情恢复题目、抽题规则和随机种子；考试新建仅使用已发布版本 ID。定向 Node
7/7、lint、typecheck、build、`git diff --check` 已通过；真实接口联调留待 Task 6。

## Task 3：考试独立详情、候选人与批阅人配置

- 状态：已完成（2026-07-28，Mock 前端闭环）
- Owner：AI
- 输入：Task 2；`exam.openapi.json`；`common-audience.openapi.json`
- 输出：考试列表、新建页、详情/发布页、考生与批阅人配置页，以及 Contract Mock 和真实请求切换
- 前后端影响：PC Web；不修改后端与 OpenAPI
- API 依赖：考试 CRUD/发布、候选人参与范围绑定与列表、批阅人替换接口
- 数据影响：无数据库变化；人员配置由 Mock 或后端保存
- 验收标准：新建考试选择试卷版本；编辑既有考试不得更换 `paperVersionId`；人员配置默认使用已确认的
  `contextType='STANDALONE'`、`contextId=0`、`assignmentSource='USER'`；列表与详情使用完整 Contract 字段；复杂人员配置不使用
  Dialog/Drawer
- 原取舍（2026-07-28）已被 2026-07-31 补全的 OpenAPI 替代：`ExamVO.policy` 用于详情回显和草稿更新；
  `GET /api/exam/exams/{examId}/reviewers` 用于回显批阅人；候选人列表按标准分页返回姓名、账号、机构和部门。
- 完成记录：先观察考试 Mock、详情读取与独立页面不存在的失败测试；随后新增 Contract
  Mock/真实切换、考试列表、新建、详情/发布和人员配置独立页面。已发布考试只读；既有考试不提交 `paperVersionId`；考生范围固定使用
  `STANDALONE/0` 并回读候选人；批阅人固定按 `USER` 整体替换。Node 5/5、定向 lint、typecheck、build、`git diff --check` 与双轴
  Review 通过；真实接口联调留待 Task 6。
- 2026-08-01 Contract 回读记录：考试详情恢复策略编辑且继续禁止更换 `paperVersionId`；人员配置页回显当前批阅人，
  候选人支持姓名/账号、状态分页筛选；Mock 与真实请求保持同一分页响应结构。真实接口联调仍留待 Task 6。
- 测试方式：先写创建/编辑边界、人员配置、Mock 切换失败测试；运行考试定向测试、PC `lint`、`typecheck`、`build`

## Task 3.1：后台独立页面的授权内部路由

- 状态：已完成（2026-07-29）
- Owner：AI
- 输入：Task 1～3 已有页面、profile 菜单树、既有按钮权限和本 Change 的内部路由映射
- 输出：已授权的新增、编辑、详情、版本和人员配置页可直接访问，但不作为菜单或侧栏节点显示；无相应权限的内部页面不注册
- 前后端影响：PC Web；不修改后端、数据库或 OpenAPI
- API 依赖：`system/users/profile` 返回的菜单树和 `perms`；M2 既有接口权限
- 数据影响：无
- 验收标准：题库、试卷和考试各保留一个列表菜单；八条后台内部路径按映射权限注册；登录后直接访问已授权内部地址不会被回退；内部页面不出现在模块导航；无权限时不注册；后端接口仍由既有权限校验保护
- 测试方式：先写动态路由注册和导航排除的失败测试；运行定向 Node 测试、PC `lint`、`typecheck`、`build`

完成记录：先新增“题库、试卷和考试内部页面按按钮权限注册且不参与菜单导航”的失败测试；随后实现授权内部路由注册表、来源列表菜单查找、权限变更路由刷新和导航排除。题库、试卷和考试列表页的新增、编辑、详情、删除等入口同步按既有
`perms` 控制显示。定向路由测试、PC `typecheck`、`build` 与 `git diff --check` 通过；全量 `lint` 因既有
`AssetPreviewPanel.vue` 的一条 warning 未通过，本 Task 未修改该文件。

## Task 4：学员 PC 与移动端作答、结果查看

- 状态：已完成（2026-07-30，Mock 前端业务闭环）
- Owner：AI
- 输入：Task 3；尝试、答案暂存、交卷、超时交卷与成绩 Contract；已确认的考试页面视觉参考
- 输出：PC/移动端考试任务、作答、答题卡、结果和解析独立页面，以及 Contract Mock 和真实请求切换
- 前后端影响：PC Web、mobile-app 与必要 shared 纯类型；不修改后端
- API 依赖：开始作答、尝试详情、答案暂存、交卷、超时交卷及考试任务读取 Contract
- 数据影响：无数据库变化；答案、尝试和成绩由 Mock 或后端写入
- 验收标准：PC 有固定考试状态区、计时、提交、答题卡和题目工作区；移动端有触控友好的任务/作答/结果页面；暂存或交卷失败不伪造成功；交卷后不可编辑；成绩和解析仅在已发布后展示
- 测试方式：先写 PC/移动端失败测试；运行两端定向测试、`lint`、`typecheck` 与各自 `build`

完成记录：先观察学员考试 API 与 PC/移动端独立页面不存在的失败测试；随后增加以 `exam.openapi.json` 为准的共享纯类型、双端
Mock/真实接口切换、任务/作答/结果独立页和移动端 `pages.json` 路由。PC
作答页提供固定状态区、倒计时、答题卡、题目工作区、暂存及超时交卷；移动端采用触控任务卡、题号导航和窄屏作答页。保存/提交失败只显示真实错误，交卷后锁定编辑；结果页仅当
`scoreVisible=true` 且 `scorePublishStatus='PUBLISHED'` 时展示正式成绩和解析。PC/移动端定向 Node 测试 3/3、两端
typecheck、PC build、移动端 H5 build、移动端 lint 和 `git diff --check` 通过；PC 全量 lint 仅剩既有 `AssetPreviewPanel.vue`
warning，未修改该文件。真实接口联调留待 Task 6。

补充记录（2026-07-29）：按确认的门户菜单治理方案，考试中心保留 `/portal/exams` 单一 `C` 类型入口；作答和结果页改由门户内部路由注册，注册前提为来源菜单存在且具备
`exam:learner:attempt:view`。任务页的开始/查看入口，以及作答页的暂存/提交入口按 profile 返回的 `exam:attempt:start`、
`exam:learner:attempt:view`、`exam:attempt:answer`、`exam:attempt:submit` 按钮权限控制。实际菜单数据仍由管理员配置，前端不写入菜单表。

## Task 5：教师批阅、成绩处置与课程考试项回接

- 状态：已完成（2026-07-29，Mock 前端闭环）
- Owner：AI
- 输入：Task 4；批阅、发布成绩、补考、练习重置与课程内容项 Contract
- 输出：教师批阅页、管理员成绩处置入口和课程考试项回接
- 前后端影响：PC Web；不修改后端与 OpenAPI
- API 依赖：批阅、发布成绩、补考、练习重置、课程内容项读写接口
- 数据影响：无数据库变化；批阅、成绩和课程完成态均由 Mock 或后端维护
- 验收标准：成绩未发布前不显示为正式成绩；课程仅引用可用考试资源；完成态完全按 Contract 返回状态展示；所有失败有中文反馈
- 测试方式：先写批阅/发布/回接失败测试；运行定向测试、PC `lint`、`typecheck`、`build`

阶段记录：已先观察教师批阅 API 和独立批阅/成绩处置页面不存在的失败测试；随后实现作答快照读取、主观题批阅、正式成绩发布、补考开新尝试和练习重置的
Contract Mock/真实请求切换，以及独立教师批阅页、成绩处置页。Course Contract 补充后，课程内容项以 `examId` 关联已发布考试，PC/移动端均通过
`POST /api/course/learning/enrollments/{enrollmentId}/items/{itemId}/exam-attempts` 启动作答，并完全依据 `examTaskStatus`
、成绩发布状态与正式成绩展示回接事实。2026-07-29 审计修复了通用开始作答接口返回 `ExamAttemptVO` 后仍按 ID 导航、PC
端课程考试启动缺失及课程考试状态未展示的问题；补充已发布考试 Mock 种子，并为教师批阅和成绩处置补齐 `exam:review`、
`exam:score:publish` 授权内部路由。M2 定向 Node 39/39、PC/移动端 typecheck、PC build、移动端 H5 build 与 OpenAPI UTF-8
解析通过；真实接口联调留待 Task 6。

2026-07-30 按补全后的 `exam.openapi.json` 再次核验并补齐可操作入口：新增 `/admin/business-center/exams/review`
教师批阅任务工作台和 `/admin/business-center/exams/results` 成绩管理工作台，分别消费
`GET /api/exam/review-tasks` 与 `GET /api/exam/attempts`；批阅、成绩发布、补考和练习重置继续进入独立详情页处理。PC/移动端学员结果页
补齐正确答案、标准答案与题目解析，仍严格受 `scoreVisible` 和 `PUBLISHED` 约束。复核同时修正补考必填
`clientAttemptToken`、成绩发布可选 `publishedBy` 以及补考/练习重置按钮权限映射。先观察新增 Contract 断言失败，再以最小实现转绿；
M2 定向 Node 39/39、双端定向 lint、PC/移动端构建通过。全量 PC typecheck 复跑被工作区同期非本 Task 文件的 3 处隐式 `any`
阻塞，本 Task 文件无新增类型错误；真实接口联调仍归 Task 6。

2026-08-01 按最终 `question.openapi.json`、`paper.openapi.json`、`exam.openapi.json` 和 `course.openapi.json` 复验：
题目发布状态改为服务端筛选并回读素材；试卷草稿/发布版本完整回读；批阅页展示反作弊事件；成绩处置页展示成绩发布流水；
课程考试项仍以 `examId` 关联并通过课程上下文开考。M2 定向 Node 44/44、PC typecheck、PC build、移动端 H5 build 通过，
定向 ESLint 0 error（保留既有格式 warning）；四份 OpenAPI JSON 可解析且无悬空 `$ref`。真实接口联调仍归 Task 6。

## Task 5.1：M2 表单选择、父级知识点与字典回显整改

- 状态：已完成（2026-08-04）
- Owner：AI
- 输入：Task 1～5 已有页面；`question.openapi.json`、`paper.openapi.json`、`exam.openapi.json`；使用过程反馈
- 输出：知识点父级选择和必填校验；题库分类名称校验；试卷/考试分类、题库和文件选择；统一 label-value 字典回显
- 前后端影响：PC Web；不修改后端与 OpenAPI
- API 依赖：知识点树、题库分类、题库/题目列表和详情、试卷版本、参与范围、系统文件上传接口
- 数据影响：无数据库变化；页面继续提交 OpenAPI 已定义的 ID 和枚举 value
- 验收标准：知识点可选择父级新增子节点；编码/名称按 Contract 校验；文件 ID 不再手输；试卷分类默认筛选组卷题库；
  新建考试从试卷版本默认带入分类和封面且允许修改；题型、难度、顺序、计分、状态等字段提交 value、展示中文 label；
  已选试卷题目展示编码和题干
- 测试方式：先观察新增页面行为测试失败，再完成最小实现；运行 M2 Node 回归、PC typecheck、目标 lint/build 和双轴 Review

完成记录：新增统一考试域字典和回显函数；题库导入文件、试卷/考试封面改用统一上传组件；题目素材、试卷分类、考试分类、题库和通知范围改为
Contract 列表选择；试卷选题弹窗增加题库分类筛选并默认使用试卷分类；版本/预览通过题目详情补查编码和题干；机构切换会清空并重载失效的下游选择，避免跨机构外键残留。M2 Node 52/52
通过，PC typecheck 通过；当前 Node 16.19 无法启动仓库现有 ESLint 10/Vite 8，目标 lint/build 留待 Node 20+ 环境复跑。
题库分类编码未实现：当前 `BankCategoryCreateRequest/VO` 没有 `categoryCode`，须补充 OpenAPI 后另行实现。

## Task 6：真实接口切换、端到端验收与记录

- 状态：待开始
- Owner：AI
- 输入：Task 1～5 完成；真实后端接口环境
- 输出：`VITE_USE_API_MOCK=false` 真实联调、M2 QA、Retro、OpenSpec/进度表回填和双轴 Review 结论
- 前后端影响：工程记录与前端验收；不改变业务功能
- API 依赖：Task 1～5 的全部 Contract
- 数据影响：无
- 验收标准：管理员建题组卷→发布考试并圈人→学员 PC/移动端作答→教师批阅→发布成绩→课程读取正式结果的真实链路通过；Mock
  不作为验收证据；权限、数据范围、版本、快照、幂等与异常恢复有证据
- 测试方式：关闭 Mock 后执行 Task 1～5 回归测试、两端目标构建与人工真实联调；完成规格符合性和代码质量双轴 Review
