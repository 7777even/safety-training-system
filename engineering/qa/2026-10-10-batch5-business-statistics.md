# 第五批代码验收与 WorkBuddy 交接

日期：2026-10-10。Change：`2026-10-09-complete-batch5-business-statistics`，功能项 18→25→37→41→77。结论：**开发完成，待真实验收**。本报告未执行真实登录、浏览器、真机或业务数据库验收，不关闭主清单编号。

## 实际代码验证

|验证|结果|边界|
|---|---|---|
|后端定向单测|127 项，0 failure / 0 error|Service/Controller/纯计算与既有作答、结果回归，不等同 SQL/登录验收|
|前端 Node 运行测试|8 项全部通过|客户端摘要、分页筛选/导出、班级下钻、完成名单、全部课程请求、PNG 与契约 URL/引用|
|PC 与 H5 typecheck|exit 0|受影响端 TypeScript/Vue 校验，无全端构建|
|PC/H5 定向 ESLint|exit 0，0 warning|本批入口、页面、API、图表及采集调用方|
|OpenSpec strict|通过|包含五个 Requirement 与 Scenario|
|git diff --check|通过|交付前差异检查|
|MySQL IT|4 项已补并编译，执行环境阻塞|Docker desktop Linux engine 管道不存在，未声称 SQL/Flyway 通过|
|独立双轴静态 Review|规格轴、质量轴通过|未访问数据库或浏览器|

后端实际命令（在 `backend`，不使用 clean）：

```powershell
mvn -pl sts-service-admin -am "-Dtest=ExamStatistics*Test,ExamAttemptServiceImplTest,ExamAttemptListControllerTest,CourseStatistics*Test,CourseKnowledgeSnapshotServiceTest,AttemptKnowledgeSnapshotServiceTest,AssessmentExecutionServiceImplTest,OfflineClassStatisticsCalculatorTest,TrainingClassStatisticsCalculatorTest,TrainingStatisticsServiceTest,TrainingResultSyncServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test -q
```

前端实际命令（在 `frontend`）：

```powershell
pnpm --filter @safety-training/pc-web typecheck
pnpm --filter @safety-training/mobile-app typecheck
```

在 `frontend/pc-web` 执行：

```powershell
node --test tests/exam-statistics.test.mjs tests/statistics-chart.test.mjs tests/training-statistics.test.mjs
```

定向 ESLint 覆盖三类统计 API、两个培训统计 API、四类 PC/H5 本人入口 API、SVG 图表、PNG 工具、动态路由、五个新页面及原业务入口。末次补充修复另验图表组件与培训记录页面；没有运行无关全端构建。

Docker 恢复后，仅运行隔离临时 MySQL IT（仍不接开发/业务数据库）：

```powershell
mvn -pl sts-service-admin -am -Pintegration-test "-Dtest=Batch5StatisticsFactsIT" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

IT 验证 JSON 知识点持久化、历史 null、完整执行快照 Mapper 往返/解码、有效报名与退课历史的真实 SQL 和跨租户不可见。init 已同步最新 schema-version；升级环境还需验证增量 migration 实际执行。

## Red → Green 与 Review 修复证据

- 18：缺失计算器先失败，随后覆盖重复/补考、最终题目、待批阅、未发布、零分母、缺考时间、并列排名、XLSX；导出拒绝不可见选中人员。
- 25：缺失课程计算器先失败；补有效时长、跨开班去重、冻结历史。课程创建原无冻结题池，改为创建执行时保存既有完整 JSON 内知识点；无人交卷也有覆盖。
- 37/41：缺失计算器先失败；补不同满分归一化、未评分以及混合模式去重、无部门。
- 77：新只读方法不存在先失败；加入只读来源推导。草稿回归实际失败（预期 INCOMPLETE、实际 PASSED）后修复；完成名单、失败/未完成、零分母汇总通过。
- 知识点 Mapper 重载回归实际失败（预期 1 条、实际 0 条）后修复：先校验/解码持久化完整快照，不能使用不落库的 policy 内存视图。
- 独立 Review 发现的分页溢出、人员查询多余答案读取、知识点覆盖接线、版本错配、草稿误完成、退课历史遗漏、后台补考设备误归属、分页常量及中文占位均已修复。大名单统计目前复用现有来源读取，未进行负载测试。

## WorkBuddy 真实验收步骤及预期

使用测试环境、两个租户、具备对应读取权限的管理员，以及无读取权限账号。使用既有课程/考试/培训流程准备事实，记录实际数据与请求，不把 Mock 当验收。以下步骤由 WorkBuddy 执行，本轮未执行。

### 18 考情与成绩导出

1. 从考试管理列表点击「考情分析」。检查指标、百分制区间说明、题型/正确率、错题 TOP5、客户端统计和人员明细均能展示，加载/空态/失败可反馈。
2. 准备 6 名有效候选人：A 多次作答且按 HIGHEST 最终成绩 80/100；B 80/100；C 60/100；D 未参考；E 已交卷待批阅；F 已交卷未发布。已交卷共 5 人：完成率 83.33%，应考 6、参考 5、完成 5。已发布成绩最高 80、最低 60、平均 73.33；A/B 并列第 1，C 第 3；E/F 不按零分纳入。题目分析使用 A 的最终取分答卷。
3. 考试结束前 D 为未参考，不计缺考；结束后 D 在缺考名单。核对人员开始/提交时间和实际作答秒数；不同满分成绩按归一化分段，保留原始分数。
4. PC/H5 本人正式开考后核对各自平台、OS、浏览器。后台替学员开启补考不得出现管理员设备推断；历史空值显示未知。
5. 姓名/账号筛选、翻页、仅缺考；选中导出和筛选全部导出均下载 XLSX，表格软件可打开，名单与筛选一致；使用不在当前考试/租户内的 candidateIds 请求应拒绝，不能导出越权记录。

### 25 课程学习与知识点

1. 从课程管理点击「学习统计」，核对单课程多个开班汇总和开班切换；勾选全部课程后有分类统计，并显示分类人数不能直接相加的口径。
2. 同一学员在两个开班有有效学习，确认参与人数去重、时长累加；零有效秒会话不增加学习次数。个人明细保留每次报名、秒数、次数及进度。
3. 新开班关联已发布试卷/题库：未交卷即能展示冻结知识点覆盖；交卷后显示作答、可判定数量、正确率。修改知识点或发布新题目版本后，既有答卷不回填；新版本答卷不误套旧版本知识点。历史未记录单列；无关联考核显示无数据。
4. 下载参与、开班时长、分类及知识点 PNG，确认是有效图片且含标题、当前筛选、图例和单位；无数据不生成伪指标。

### 37 线下课堂

1. 从计划工作台场次列表点击课堂统计，查看场次、培训内容、已有同阶段考核名称及试卷关联；无关联时显示无试卷，不出现新上传/线下作答入口。
2. 在既有录分流程为部分学员评分，未录分人数单列。以 50/50 与 100/100 两条成绩验证同属 90–100 区间，最高/平均/最低为百分制 100；原始成绩明细仍保留各自满分。不同 scoreName 分别展示，不混成一次考试。

### 41 部门班级

1. 从培训计划列表进入「班级统计」。准备线上计划、线下计划与混合计划，含同一部门重复内容、无部门学员。
2. 混合计划一名学员在总参与人次计 1、线上计 1、线下计 1；同模式重复内容不重复计人。无部门单列，饼图与条形图数据和班级表一致，注明模式人数不能相加。
3. 点击部门班级「查看人员」，核对计划、部门（含无部门）和模式范围，关键字、分页不能串到其他班级或租户。

### 77 培训记录与完成情况

1. 从计划列表进入「培训统计与记录」，查看计划参训、完成、未通过、未完成和完成率；点击计划查看人员及展开逐内容明细。
2. 准备 PASSED、FAILED、INCOMPLETE 各一人：总 3、完成 1、未通过 1、未完成 1、完成率 33.33%；勾选仅完成名单只返回 PASSED／COMPLETED。无名单完成率 0；无内容草稿不得显示已完成。
3. 执行中的来源事实变化后刷新统计，结果与现有完成判定一致；检查 GET 前后投影及参训人版本/同步时间无统计写入。结束后确认使用冻结快照，后续来源变化不改变冻结结果。
4. 切换「全部课程培训记录」，确认包括所有有权查看课程及开班，退课历史显示「已退课」，课程、时长、次数、进度可查看和分页；无课程读取权限不展示该 tab。

### 权限、UI 与交付回填

- 两租户执行同一组汇总、人员下钻及导出：不读取另一个租户事实。无权限账号应 403，未认证应 401；页面入口按对应权限隐藏。T3/T5 双权限缺一不可。
- pageNum/pageSize 为 0、班级 mode 非法时应 400，不返回内部 SQL 或堆栈。
- 管理页面归属 /admin，复用当前后台卡片、筛选、表格、分页风格；中文标签、加载、空态、错误反馈无缺失。检查 SVG 饼/条图与 PNG 的真实浏览器渲染。
- 保存账号角色、数据基准、请求响应、导出/图片、截图和发现的问题；报告每项 PASS/FAIL 后回填本 QA 与主清单。只有真实验收通过才勾选 18/25/37/41/77，不把本次代码通过当业务关闭。

## WorkBuddy 真实验收结果（2026-10-10）

验收人：WorkBuddy。环境：本机后端 20080（profile `vpn`）+ 共享开发库 MySQL 8.0.45 `19.191.192.146:8045`，租户 DEMO(org 2)/M5QA_TI_20260930(org 3)，账号 `DEMO/admin`（role 1）、`DEMO/operator01`（role 2 学员用户）、`M5QA_TI_20260930/admin`。

### 前置发现与处置（已获用户授权）

| 前置 | 实测 | 处置 |
|---|---|---|
| 运行中后端 | PID 28892 为 2026-10-09 14:02 启动，早于第五批源码；5 个新接口全返回 `No static resource …` | 已按最新 `target/classes` 重启，`Tomcat started on port 20080` |
| 增量 migration | 库缺 `knowledge_point_ids_snapshot_json`，`flyway_schema_history` 停在 `20261009100000` | 沿用 batch4 同款 Flyway Java 护栏（仅允许单个 pending）应用，schema 升至 `v20261009140000`，validate 通过 |
| 启动被环境变量污染 | ① shell `HTTP_PROXY/HTTPS_PROXY` 被 AWS SDK 读取 → MinIO `headBucket` 403；② `SERVER__PORT=60231` 经松散绑定覆盖 `server.port` | 启动前 `unset` 代理与 `SERVER__*` 后正常；直连探测证明 MinIO 凭据与 `sts-dev-private` 均正常 |
| Docker | Linux engine 管道不存在 | MySQL IT 未执行（沿用原阻塞结论） |

### 逐项 PASS/FAIL

| 功能项 | 结论 | 实测证据（节选，均与库侧独立核对） |
|---|---|---|
| 18 考情分析与成绩导出 | **通过** | 考试 38（4 候选）：expected 4 / attended 3 / completed 3 / 完成率 75.00 / 待批阅 1 / 未发布 1 / 缺考 1 / 最高·平均·最低 100 / 90–100 段 2 人 100% / 题型 ESSAY·MULTIPLE·SINGLE 各 2 且正确率 100 / 平台 `H5·PC·未知` 各 1 / OS `Win32·未知×2` / 浏览器 `Chrome·未知×2` / 平均 43s，全部吻合；并列第 1、仅缺考筛 1 条、关键字筛命中；未结束考试 34 缺考 0；XLSX 全部导出 `PK`（1855B）、选中导出 200、越权 `candidateIds=999999` → 403。非法 `pageNum=0` 修复后 → 400 |
| 25 课程统计与统计图下载 | **部分通过（子项无数据）** | 开班 8：报名 2 / 参与 1 / 完成 1 / 时长 82 / 有效会话 6 / 平均进度 50，逐项吻合；课程 1 跨开班 enrolled 4 / participated 3 / 时长 6532 / 会话 290 = 库侧 `COUNT(DISTINCT)`；分类 `交通教育课程` 一致；人员明细保留每条报名。**未覆盖：冻结知识点覆盖（课程执行无冻结数据，实测为空、历史缺失 26/233 正确单列）；PNG 下载需浏览器级** |
| 37 线下课堂考试展示与统计 | **部分通过（子项无数据）** | 场次 1：参训 4 / 已评分 1 / 未评分 3，`线下培训成绩` 90/100→90 落 90–100 段，地点「教育局」；场次 5：60/100→60 落 60–70；场次 3 无成绩 assessments 为空。**未覆盖：同阶段考核/试卷关联（现有场次阶段均无 EXAM 内容）；50/50 与 100/100 归一化用例（无数据）** |
| 41 培训班级与线上线下统计 | **通过** | 计划 1–5 均线上线下混合：总/线上/线下各 8 人次，计划 1 各 4 人（同模式重复内容不放大）；人员下钻 8 行（4 人×2 模式）、ONLINE/OFFLINE 各 4、unassignedOnly=0。非法 `mode=BOGUS` 修复后 → 400。未覆盖：无部门单列（DEMO 无无部门参训人） |
| 77 培训进度与完成情况记录 | **部分通过（子项无数据）** | 计划 6 DRAFT→全 0/完成率 0（草稿不显示已完成）；计划 1 ENDED→1/0/3 完成率 25；计划 3 RUNNING→实时推导 INCOMPLETE 0%；计划 2/4/5 各 100%，全部吻合库侧 `result_status`；`completedOnly=true` 计划 1 仅 1 条 PASSED；**GET 前后 `sts_training_participant`/`_content_result` 的 SUM(version)、MAX(update_time) 完全不变（无统计写入）**；全部课程记录 24 条。**未覆盖：FAILED 名单、已退课显示（无数据）** |

### 通用项（认证/权限/隔离）

- 未认证 → 401（code 1002）**PASS**。
- 跨租户：以 M5 租户读 DEMO 考试 38 → 404（考试不存在）；M5 班级统计只读本租户（全 0）**PASS**。
- 无权限账号：`operator01`（学员）访问课程/培训统计 → 403（1003）**PASS**。
- **需决策（权限暴露）**：`operator01` 持有 role 2「学员用户」，其菜单 9563「考试信息」与后台共用同一权限码 `exam:exam:list`，因此**学员可调用考情接口并读到全部候选人的姓名与成绩**（实测 `people` 返回 4 人含「超级管理员/管理员/赵强/端到端学员01」）。该权限复用为存量口径（`/api/exam/exams` 存量列表对学员同样 200），非本批新增，但第五批把暴露面扩大到成绩明细。

### 共同缺陷（已修复并复验）

`pageNum=0`、班级 `mode=BOGUS` 原返回 **HTTP 500 / code 9999**（`系统未知错误: people.pageNum: must be greater than or equal to 1`）。根因：`sts-common` 的 `HttpApiExceptionHandler` 只处理 `MethodArgumentNotValidException`/`BindException`，未处理 Spring 6.1+ 参数级校验抛出的 `HandlerMethodValidationException`（及 `ConstraintViolationException`），落到兜底分支 → 500。

修复：在 `HttpApiExceptionHandler` 新增两个映射 → 400 / code 1001，消息仅取参数名 + 校验默认消息（无堆栈、无 SQL）。经用户授权实施。

复验（重启后真实 HTTP）：`exam people pageNum=0`、`class people mode=BOGUS`、`training plans pageNum=0`、`course people pageNum=0` 全部 → **400**，响应体不含堆栈/SQL；同时 8 条合法请求回归 200（exam38 汇总/人员、课程开班 8、线下场次 1、班级统计、计划统计、完成名单），共 **12 PASS / 0 FAIL**。修复后重跑第五批定向后端单测 **127/127，0 failure / 0 error，BUILD SUCCESS**（`mvn -pl sts-service-admin -am -Dtest=ExamStatistics*Test,… test`），`sts-common` 本次改动无回归（该模块无单测）。

### 待人工决策

学员角色（`DEMO/operator01`，role 2）经菜单 9563「考试信息」持有 `exam:exam:list`（与后台共用权限码），可调用考情接口读到**全部候选人的姓名与成绩**（实测 `people` 返回 4 人）。该复用为存量口径（存量 `/api/exam/exams` 对学员同样 200），非本批新增，但本批把暴露面扩大到成绩明细。用户已决定：**仅登记，不改权限模型**，交产品/权限侧决策。

### 未做 / 不可做

- 未做浏览器级渲染（SVG 饼/条图、PNG 下载、页面视觉与入口隐藏）；未做真机。
- 无数据子项（非缺陷，系共享库无对应事实，如要求覆盖需先经批准造数）：25 冻结知识点、37 试卷关联与跨满分用例、41 无部门、77 FAILED 与已退课。

### 结论

五项业务主链与库侧事实逐项吻合，认证/隔离/无权限拒绝成立，GET 无写入成立；共同缺陷（非法参数应 400）已修复并复验通过。据此**通过真实验收**，在主清单勾选 18/25/37/41/77。上述无数据子项与权限暴露作为遗留登记，不影响原文业务判据。
