# Tasks：考试真实联调与全流程契约对齐

> 状态：Task 1～6 已完成；Task 7 待非生产环境与测试账号授权。一次只执行一个已确认 Task。

## Task 1：统一考试创建与草稿详情表单布局

- 状态：已完成（2026-08-17）
- Owner：AI
- 输入：当前 `ExamDefinitionForm`、`docs/design/pc-web-ui-standard.md`、本 Change 的设计
- 输出：双列基础表单、整行长字段/策略区，以及符合详情卡滚动和底部操作栏规则的创建与草稿详情页
- 前后端影响：PC Web；不修改后端与 OpenAPI
- API 依赖：既有考试创建、详情和更新接口
- 数据影响：无
- 验收标准：新建考试的常规字段不再集中于左侧；共享表单与考试详情草稿模式布局一致；控件宽度、滚动区域和固定操作栏符合 PC UI 标准；不改变请求字段与默认值
- 测试方式：先增加会失败的结构/布局回归测试；最小实现转绿；运行定向测试、PC Web lint 或 typecheck，以及 `git diff --check`

完成记录：先新增“考试创建共享表单按详情页标准双列展示常规字段”测试并观察失败；随后将基础信息改为 `ElRow :gutter="24"` 和双列 `ElCol`，将须知内容保留为整行字段。定向 UI 测试通过，PC Web `typecheck` 通过，受影响文件 ESLint 通过，`git diff --check` 通过。规格符合性 Review：通过；代码质量 Review：通过。

## Task 2：移除考试模块的废弃受众调用并改用人员选择绑定

- 状态：已完成（2026-08-17）
- Owner：AI
- 输入：当前 `exam.openapi.json` 的 `BindSelectionRequest`、`person-selection.openapi.json`、既有 `PersonPickerDialog`
- 输出：考试 API、考生配置页和考试 Mock/Test 中不再存在 `/api/common/audiences`、`bind-audience` 或受众下拉；考生选择与绑定遵循 `bind-selection`
- 前后端影响：PC Web；当前 OpenAPI 不变。若人工选择变更通知范围契约，则升级为 API Contract Change 后另行实施
- API 依赖：`GET /api/common/person-picker`、`POST /api/exam/exams/{examId}/candidates/bind-selection`、`GET /api/exam/exams/{examId}/candidates`
- 数据影响：无；真实联调仅在非生产环境写入明确的测试考试考生数据
- 验收标准：选人后请求体包含 `selection`、`contextType=STANDALONE`、`contextId=0`；服务端考生列表正确回显；考试模块没有废弃路径引用；批阅人替换保持当前契约
- 测试方式：Red→Green 的 API/页面定向测试；关闭 Mock 的请求检查；PC Web lint 或 typecheck；`git diff --check`

完成记录：按已确认选项 1 移除前端“成绩通知范围”输入。先将考试 API 定向测试改为 `bind-selection` 请求并观察 `bindExamSelection is not a function` 失败；随后以 `selectionType=USER`、具体 `subjectIds` 实现考生绑定，替换旧的受众 API 和 Mock，人员配置页改为现有机构人员选择器。定向 API/UI 测试 18/18 通过，PC Web `typecheck` 通过，受影响文件 ESLint 通过；考试模块定向搜索确认不存在 `/api/common/audiences`、`bind-audience` 及旧受众函数；`git diff --check` 通过。规格符合性 Review：通过；代码质量 Review：通过。

## Task 3：限制新建考试仅选择试卷最新发布版本

- 状态：已完成（2026-08-18）
- Owner：AI
- 输入：已确认的版本语义；`paper.openapi.json` 的版本列表降序约定；新建考试页
- 输出：每份试卷仅一个最新已发布版本的新建考试选择器
- 前后端影响：PC Web；不修改后端与 OpenAPI
- API 依赖：`GET /api/paper/papers`、`GET /api/paper/papers/{paperId}/versions`
- 数据影响：无
- 验收标准：同一试卷的版本 4、版本 6 均已发布时，考试创建页只显示版本 6；历史版本继续由试卷版本列表读取，不被删除或变更状态
- 测试方式：先增加失败的版本选择回归测试；以最小实现转绿；运行考试管理定向测试、PC Web typecheck 或 lint，以及 `git diff --check`

完成记录：先新增“新建考试每份试卷只展示最新的已发布版本”回归测试并观察失败；随后在已有发布状态筛选后按 `versionNo` 降序，仅保留第一个版本。历史版本接口与数据均未修改。该定向测试通过，PC Web `typecheck` 和受影响文件 ESLint 通过，`git diff --check` 通过。规格符合性 Review：通过；代码质量 Review：通过。

## Task 7：执行考试真实接口端到端验收

- 状态：完成
- Owner：AI
- 输入：已完成的 Task 1、Task 2；明确指定的非生产环境；管理员、学员、批阅教师和成绩发布权限测试账号
- 输出：真实验收记录、失败响应证据、双轴 Review、`tasks.md` 状态更新、QA/Retro 与进度表回填
- 前后端影响：PC Web、OpenSpec、engineering；不修改未确认的后端 API、权限或数据库
- API 依赖：design 第 4 节列出的全部考试闭环操作
- 数据影响：只在指定非生产环境创建和使用最小测试数据；不操作生产数据
- 验收标准：创建→人员配置→发布→学员作答→教师批阅→发布成绩→学员查看成绩逐段完成；请求、响应、权限和错误提示均与 OpenAPI 一致；Mock 不作为验收证据；双轴 Review 结论为通过，或偏差明确标为需人工决策
- 测试方式：`VITE_USE_API_MOCK=false` 的定向自动化与人工联调；PC Web 受影响验证；OpenAPI JSON 解析；`git diff --check`

## Task 4：将考生绑定界面完整映射到人员选择契约

- 状态：已完成（2026-08-18）
- Owner：AI
- 输入：`BindSelectionRequest`、`PersonSelectionRequest`、既有 `PersonSelectionDialog`
- 输出：考生的全部人员、按部门和指定人员三种简洁选择模式，以及与其一致的 `selectionType` / `subjectIds` 请求
- 前后端影响：PC Web；不修改 OpenAPI、后端或数据库
- API 依赖：`POST /api/exam/exams/{examId}/candidates/bind-selection`、当前人员/部门读取接口
- 数据影响：无；真实联调仅在指定非生产环境绑定测试人员
- 验收标准：三种选择模式均可提交有效 Contract；`ALL` 时 `subjectIds` 为空，`DEPT` / `USER` 时必须至少一个 ID；`contextType=STANDALONE`、`contextId=0` 不向用户暴露；批阅人逐人选择保持不变
- 测试方式：Red→Green 的组件/请求定向测试；PC Web typecheck 与受影响 lint；`git diff --check`

完成记录：先增加考生范围三模式的定向用例并观察 `PersonSelectionDialog` 不存在的失败；随后复用现有人员范围选择组件，提交其输出的 `selectionType` 与 `subjectIds`，并保留 `contextType=STANDALONE`、`contextId=0` 为系统上下文。未选择范围时禁止保存；批阅人仍为明确用户选择。定向测试、PC Web `typecheck`、受影响 lint 与 `git diff --check` 通过。规格符合性 Review：通过；代码质量 Review：通过。

体验补正（2026-08-18）：在考生区展示全员规则、已选部门标签或指定人员表格；人员范围选择器接收已选人员并在再次打开时回显，部门继续由既有 `subjectIds` 回显。移除三条面向实现细节的提示文案。定向测试、PC Web `typecheck`、受影响 lint 与 `git diff --check` 通过。规格符合性 Review：通过；代码质量 Review：通过。

## Task 5：补齐作答端反作弊记录与结果字段可见性

- 状态：已完成（2026-08-18）
- Owner：AI
- 输入：`AnticheatEventRequest`、作答策略字段、学员 PC/移动端作答与结果页
- 输出：最小反作弊事件上报，以及按服务端返回字段显示的标准答案/解析结果页
- 前后端影响：PC Web、移动端；不修改 OpenAPI、后端、权限或数据库
- API 依赖：`POST /api/exam/attempts/{examAttemptId}/anticheat-events`、学员作答和结果读取接口
- 数据影响：非生产联调会新增最小反作弊审计事件；不操作生产数据
- 验收标准：失焦、页面隐藏和超时交卷均按 Contract 上报最小事件；成绩与答案/解析可见性独立遵从服务端字段裁剪；不新增未确认监控能力
- 测试方式：Red→Green 的 PC/移动端定向测试；PC 和移动端受影响 typecheck/lint；`git diff --check`

完成记录：先增加作答端事件上报和结果字段裁剪用例并观察失败；随后在 PC 端记录窗口失焦、页面隐藏与超时交卷，在移动端记录页面隐藏与超时交卷。事件上报失败被吞并，不阻断作答或交卷。标准答案和解析仅在服务端返回对应字段时渲染。PC/移动端定向测试、PC Web `typecheck`、两端受影响 lint 与 `git diff --check` 通过；移动端 `typecheck` 因现有 `tsconfig.json` 的 `ignoreDeprecations` 与当前 TypeScript 不兼容未能执行。规格符合性 Review：通过；代码质量 Review：通过。

## Task 6：清理无操作入口的兼容字段展示并完成模块契约回归

- 状态：已完成（2026-08-18）
- Owner：AI
- 输入：design 第 6 节审计矩阵、现有考试 API 类型与管理/教师/学员页面
- 输出：不展示无可用选择契约的历史通知范围 ID；考试模块 Contract 映射回归用例
- 前后端影响：PC Web、OpenSpec；不修改 `exam.openapi.json`
- API 依赖：现有考试读写、人员选择、批阅、成绩与学员端接口
- 数据影响：无
- 验收标准：`resultNotifyAudienceId` 不作为输入或只读原始 ID 展示；其它可操作业务字段均有明确控件、自动派生来源或被说明为服务端只读字段；无废弃路径调用
- 测试方式：Contract 字段—请求—页面定向回归；PC Web typecheck/lint；`git diff --check`

完成记录：先增加详情页不得展示 `resultNotifyAudienceId` 原始 ID 的定向用例并观察失败；随后在已发布考试策略展示中显式过滤该兼容字段，保留 API 类型以兼容现有 Contract。候选人、作答审计、成绩字段可见性和兼容字段的定向回归、PC Web `typecheck`、受影响 lint 与 `git diff --check` 通过。规格符合性 Review：通过；代码质量 Review：通过。

## Task 8：统一考试详情三步工作台

- 状态：已完成（2026-08-18）
- Owner：AI
- 输入：现有考试详情、人员配置页，试卷管理详情与课程详情工作台样式
- 输出：设置考试信息、人员配置、发布考试三步导航，以及与既有发布状态同步的操作入口
- 前后端影响：仅 PC Web；不修改 OpenAPI、后端、权限或数据库
- API 依赖：既有考试详情、人员配置和发布接口
- 数据影响：无
- 验收标准：草稿考试可按“设置考试信息 → 人员配置 → 发布考试”顺序完成操作；人员配置不复制到详情页；已发布考试显示完成状态且不重复发布；页面文案和布局与现有详情工作台一致
- 测试方式：先增加失败的步骤栏/导航定向测试；最小实现转绿；PC Web typecheck 与受影响 lint；`git diff --check`
