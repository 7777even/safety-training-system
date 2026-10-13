# 安全教育培训系统开发进度跟踪表

创建日期：2026-07-24

第十批增量（2026-10-12）：遗漏项第 10 批 74 → 75 开发完成（OpenSpec `2026-10-12-complete-batch10-learning-integrity`，用户确认三项范围决策后实施）。**74 防挂机与学习互斥**：PC/H5 学习页 60～180 秒随机间隔弹出两位数算术验证题，弹窗期间暂停有效学时累计，60 秒超时未答对以 `TIMEOUT` 结束当前会话并停止计时；服务端单活跃学习会话互斥（同一账户跨终端、跨培训内容拒绝，5095）与终端标识绑定检测（同一终端其他账户拒绝，5096），90 秒心跳窗口、显式收尾立即释放；新增 migration `V20261012100000`（`device_id` + 活跃索引），init schema-version 与 IT 基线同步至 20261012100000。**75 随机水印**：水印文字优先员工编号，平铺单元尺寸/旋转角/字号/锚点随机化并按 45～90 秒随机间隔重建；考试与课程作答页固定平铺水印不变。**契约**：`endReason` 增加 `HEARTBEAT`、`StudySessionReportRequest` 增加必填 `deviceId`、错误码 5095/5096 登记 `docs/api/course.openapi.json`。**验证**：课程域单测全过（含互斥新增 3 项）、两端 node 定向 11/11、两端 typecheck、目标 lint（PC 仅存量 img/input 警告）、移动端 H5 build、OpenSpec strict 与 git diff --check；真实 SQL IT 已补但 Docker Hub 不可达未执行；75 已于 2026-10-12 PC 与 H5 双端真实验收各 8/8 通过（水印渲染/员工编号/随机重建、验证题随机弹出/须作答/答对恢复/超时停止；采用可控复现、间隔已还原，证据见 QA「真实验收」节），整项勾选关闭；74 互斥冲突提示未做真实验收。74 登录层全局互踢与考试侧强化防作弊按 2026-10-12 人工确认保持延期，**74 不勾选整项（登录层互踢遗留），75 已双端真实验收通过并整项勾选关闭**。详见 `engineering/qa/2026-10-12-batch10-learning-integrity.md` 与本批 `openspec/changes/2026-10-12-complete-batch10-learning-integrity/tasks.md`。

第六批增量（2026-10-10，Codex）：22/26/48 → 67 → 68 → 69 → 62代码补齐、WorkBuddy核心真实验收通过；26/67/68/69/62登记完成，22本期范围完成但仿真子项延期。提交前查出48缺少课程名称筛选，已补PC/H5入口及回归测试，**新增名称入口待真实复验，48暂不整项关闭**。同步T8开班权限恢复及T9/T10界面收敛，共5条migration。后端175项、前端定向15项（本批7项）、两端typecheck/目标lint及strict/diff检查通过；SQL/事务IT仍Docker阻塞，未独立真实验证的负向及下游边界见QA，Change不归档。详见 `engineering/qa/2026-10-10-batch6-course-capabilities.md` 第九节及本批tasks。



第四批增量（更新2026-10-09，fxw11）：**整批已闭合**。**47/61 T1**首页/消息/SSE、本人须知已实现，业务关闭条件（截止窗口/培训生命周期/课程上下架/失效打开）已用真实后端+真实共享开发库+真实调度器全部补齐：截止窗口 `exam 24` 入 24h 窗口后真实扫描生成 4 条 `deadline:`、过期不提醒、重复扫描幂等（4 PASS/0 FAIL，回滚）；隔离临时培训计划真实 cancel → `TRAINING:*:cancelled`、重复提交 8164、学员仍可读、打开已失效培训 **8155**（12 PASS/0 FAIL，已清理）；`course 2` 真实 offline → `COURSE_CATALOG「课程已下架」`只投递 4 名受众（9 PASS/0 FAIL，已还原）。**50 T2** 非微信子项资料读写与**改密真实交互**（改密→旧会话失效 1002→旧密码被拒 1002→新密码可登录→改回，10 PASS/0 FAIL，凭证已还原）全部取证，仅剩微信子项随 46，故 50 整项不勾选。**52 T3** 读/写路径、权限与回滚、界面统一全部通过（写路径临时授权后 17 PASS/0 FAIL，已回滚）。**静态与回归**：PC/H5 `vue-tsc` exit 0、契约测试 3/6/2、9 文件定向 lint exit 0、后端定向 **39 项 0 失败 BUILD SUCCESS**、两端重建 exit 0、**最终真实 HTTP 全端点回归 16 PASS/0 FAIL**。**界面统一**：PC 5 文件 + H5 3 文件改到统一设计系统，新增公共类 `.portal-toolbar`、`.portal-form-card` 与 `MEMBER_TYPE_OPTIONS`；真实渲染 PC hero `x=16/w=1408/radius=12px`、H5 `heroW=357/radius=14.56px`，两端无横向滚动、无裸色值。**数据侧终检**：dev 库无残留（deadline/TRAINING/COURSE_CATALOG 消息 0、临时计划 0、账本 0、9722/9723 绑定 0、消息 29/接收人 29 与基线一致、`exam 24` 时间窗 `NULL`、`course 2` `PUBLISHED|2|1`）。补漏清单 47/52/61 标记完成，50 保留微信子项。详见 `engineering/qa/2026-10-09-batch4-t1-closure-and-final-verification.md`、`engineering/qa/2026-10-09-batch4-t2-t3-and-ui-unification.md`、本批 tasks.md。

最后更新：2026-09-28（**培训计划奖惩闭环 Task 3 真实管理员登录态验收通过并签核闭环**。环境：后端 20080 跑当前工作树代码 + `vpn` profile + 团队远程库 `19.191.192.146:8045/sts`、Redis 16379（三端口探测全通），真实 `POST /api/auth/login`（`DEMO/admin`）取令牌。
**服务端写入校验**：运行中计划（`plan 5`）新增奖励/处罚/0 分/停用记录、编辑、删除全部通过；9 条拒绝路径逐条命中预期错误码 —— 结束计划写入 **409/8242**、跨计划参训人 **400/8243**、计划不存在 **404/8101**、非法类型与分值方向不符 **400/8245**、佐证不存在 **400/8246**、陈旧版本 **409/8244**，失败写入无结果副作用。
**综合分刷新（数值级）**：因运行中计划参训人三项来源分原为空、公式要求齐全才出分，经人工确认后临时填入来源分（60/70/80，基础分 71）做夹具，实测 81 → 86（+5）→ 83（-3）→ 86（编辑 5→8）→ 78（置 `status=0`）→ 81（删处罚）→ 81（删全部验收记录），验收后三项已改回 NULL、综合分已还原为 NULL。
**并发保护**：奖惩写入使 `sts_training_plan.version` **14 → 15**，即结束冻结所依赖的乐观锁在真实 MySQL 与生产配置下生效（该行为正是 `TrainingRewardPenaltyServiceIT` 失败用例所断言者；本机无 Docker，容器 IT 未能重跑）。
**佐证与可选字段**：`GET /training/reward-penalties/1/evidence` → **200 / image/jpeg / 31205B**；无佐证 **404/8247**、记录不存在 **404/8241**；说明与发生日期省略/清空可保存（共享库缺 `V20260924093000`/`V20260924180000` 时表现为 500，经人工确认后在共享库应用两条迁移，Flyway 记账至 `v20260924180000`，`REWARD_PENALTY` 改写命中 0 行即零数据影响）。
未覆盖：PC 界面真实浏览器取证受本机无头 Chrome 不可用阻断；成果/档案的奖惩事实回读缺「已结束计划上的奖惩记录」样本。两处已如实登记，经人工签核后 Task 1/2/2.1/3 全部标完成并归档。详见 `engineering/qa/2026-09-28-close-training-reward-penalty-loop-real-acceptance.md`；小王）
最后更新：2026-09-28（**M4 结果状态双端口径统一为「结果待补全」**：此前 `INCOMPLETE` 同时存在三种中文 ——「结果不完整」（PC 管理端统计/成果页）、「未完成」（PC 门户培训班列表、计划执行面板、移动端培训班列表）、「结果待补全」（移动端档案），本轮按人工确认统一为「结果待补全」，无结果兜底统一为「结果待同步」。改动覆盖 PC `constants/enum-options` 共享枚举、成果页阈值与统计标签、档案三视角统计与说明文、计划执行面板、门户培训班列表，移动端培训班列表，后端档案 PDF 成品（`ArchiveLabels` / `ArchivePdfRenderer`），`docs/api` 三个契约文件的 `resultStatus` 说明与统计字段描述，以及 `openspec` 活规格与未归档 Change 的术语；两端各新增 1 条口径锁定测试。`openspec/changes/archive/` 与历史 QA、进度表旧条目保留当时用词，不回溯改写。详见 `engineering/qa/2026-09-28-unify-m4-result-wording.md`；小王）
最后更新：2026-09-28（**M4 双端闭环真实环境验收通过 —— 标记 M4 完成**。验收环境：后端 20080 跑当前工作树代码 + `vpn` profile + 团队远程库 `19.191.192.146:8045/sts` 与 Redis `16379`，PC dev 5173 / uni H5 dev 5174 经临时 `vite.qa.config.ts` 代理 `/api` 到 20080，CDP 无头 Chrome 取 DOM 事实与截图。**移动端**：`DEMO/superAdmin` 走通「个人中心 → 我的证书 → 在线查看（受控 blob 真实渲染 1400×990）/ 下载（`GET /api/certificate/my-certificates/7/file` 带 `Bearer` → 200 / image/png）」与「个人中心 → 我的培训档案（`测试培训类型 / 共 4 期`，3 条 `合格`、1 条 `RUNNING` 不显示结果）→ 期次详情 `?trainingParticipantId=7` 只读回放」；`DEMO/m4-learner-01`（role 3 学员）证书与档案双空态，读非本人证书文件 **403 / 9416**。**PC 端**：管理员「已签发证书 3 行 / 证书模板 2 行 / 培训档案三视角 / 问卷管理 3 行 / plan 4 已结束成果（69.25、通过率 100%、结果不完整 0）/ plan 4 一期档案页『可下载·归档事实全部完整』」全通过，`GET /archive/trainings/4/file` 200 / application/pdf / 219827B / `%PDF-1.6`，plan 1 结果不完整下载阻断 **409 / 9204**；门户 `/portal/certificates` 空态 + 三筛选 tab 正常，学员访问管理端菜单被守卫拦回 `/portal/class`、档案 API **403 / 1003**（与「不向学员开放 `archive:training:list`」决策一致）。据此 `2026-09-24-close-mobile-learner-outcome-loop` Task 4 完成、`2026-09-28-improve-m4-certificate-and-mobile-archive` 真实登录态验收补齐，两 Change 均标闭环；详细矩阵、未覆盖面与双端措辞差异见 `engineering/qa/2026-09-28-dual-m4-real-environment-acceptance.md`；小王）
最后更新：2026-09-24（M4 档案/资料/证书来源三项收口：`2026-09-24-close-training-archive-loop` T1～T4 代码完成、`2026-09-24-close-training-material-loop` T1～T2 完成、`2026-09-24-separate-certificate-issue-sources` T1～T4 完成；档案事实扩为 22 个顶层属性、签到由线下成绩派生三态、档案文件改 AWT 光栅 PDF 正本，并把原遗留「PDF 内嵌证书编号」收口为「PDF 只呈现证书数量」；自动化全绿，资料闭环与档案闭环两项**真实登录态纵向验收已于 2026-09-24 在 VPN 环境恢复后实跑完成**（三视角逐字段一致、PDF 正本、受控读取、冻结/乐观锁/唯一键逐条通过），详见下方 3.3 节点后的 2026-09-24 增量与 QA 记录；另**归档 18 个已完成 Change 至 `openspec/changes/archive/`**，其中今天 3 个（`...close-training-archive-loop` / `...close-training-material-loop` / `...separate-certificate-issue-sources`）走 `openspec archive`，已把 delta **合并进 `openspec/specs/`**（`m4-minimum-closure` 需求 7→16 条、新建 `training-material` 能力，specs 25→26 个能力），其余 15 个沿用历史纯目录迁移做法；活动 Change 由 22 个减至 **4 个**；小王）
最后更新：2026-09-23（今日整体：`close-training-course-score-loop`、`sync-teacher-task-menu`、`enrich-training-plan-list-display`、`unify-question-bank-practice-review-flow`、`fix-draft-exam-publish-refresh`、`display-exam-resource-names`、`fix-fill-blank-answering` 七个 Change 与管理端工作台/门户/移动端展示层重构；题库练习统一为「不产生及格结论、交卷即生效」，填空题按 `blankCount` 逐空作答，草稿考试发布允许刷新快照且保留人员，菜单 9564/9565 幂等迁移已由 MySQL Testcontainers 验证。按能力拆分的提交清单、口径变化与遗留见 `engineering/reports/2026-09-18-daily-change-brief.md`；fxw11）
最后更新：2026-09-18（`close-training-course-score-loop`：培训绑定且已完成课程按完成范围内 `COURSE_QUIZ` 当前正式分平均写入课程成绩；既有结果同步仅为未冻结参训人补历史空分。独立课程、题库练习、既有非空成绩与获得学时不变。定向零 DB 回归、OpenSpec strict 通过；MySQL IT 受 Docker Hub 拉取 Ryuk 网络阻塞，真实登录态验收待执行；fxw11）
最后更新：2026-09-15（`add-plan-composite-calculation`：PC 计划结果抽屉补齐逐人综合成绩计算、确认和批次历史，复用既有评价接口；PC 定向 Node 23、typecheck、目标 ESLint、OpenSpec strict 和差异检查通过；真实管理员登录态验收待执行；fxw11）
最后更新：2026-09-15（`unify-assessment-execution-snapshot` Task 1 完成：课程/独立考试/培训统一 v1 完整 JSON 快照、规范化
SHA-256、
投影校验和不可变门禁；零 DB 69、Admin MySQL IT 24、Admin-init IT 1；baseline `20260915103000`；lims）
最后更新：2026-09-11（`redesign-training-question-bank-practice-closure` Task 1～3 完成：公共抽题、规则预检、两级冻结题池、不评分提交、反馈投影与
v3 最终快照闭环；零 DB 182、Admin MySQL IT 8、Admin-init IT 1；lims）
最后更新：2026-09-10（`remove-offline-sign-in-from-plan-apis`：线下发布/场次/数据库/工作台彻底移除签到字段与签到表，删除三个签到权限；后端定向零
DB 129、Init 5、MySQL IT 14；lims）
最后更新：2026-09-09（教师任务列表/详情补场次乐观锁 `version`，供 start/complete/cancel 回传；定向零 DB 29；lims）
最后更新：2026-09-10（`improve-teacher-task-detail`：教师任务详情名单补学员姓名；教师端区分未开始、不可录分与无权限提示；后端定向零 DB 27、PC 定向 Node 3、PC typecheck 通过；真实教师登录态验收待执行；fxw11）
最后更新：2026-09-10（`show-teacher-task-plan-name`：教师任务列表补所属培训名称，支持区分同名场次；后端清理后定向零 DB 3、PC 定向 Node 4、PC typecheck 通过；真实教师登录态验收待执行；fxw11）
最后更新：2026-09-10（`align-learner-offline-session-status`：学员端线下卡按教师实际场次状态展示，个人完成状态仍以成绩为准；后端定向零 DB 26、PC 定向 Node 4、PC typecheck 通过；真实学员登录态验收待执行；fxw11）
最后更新：2026-09-09（`add-regulation-personal-training-records`：监管只读下钻计划/参训人/内容结果，须切换视角；定向零 DB
7；lims）
最后更新：2026-09-09（`add-offline-session-state-machine`：场次 start/complete/cancel，PUT 不改状态，仅进行中录分；定向零 DB
40；lims）
最后更新：2026-09-09（`simplify-offline-session-to-score`：线下完成只认成绩，删签到 HTTP，留表；定向零 DB 62 + IT 2；lims）
最后更新：2026-09-09（`zero-hours-for-absent-offline-attendance`：确认 ABSENT/LEAVE 不计线下获得学时且 FAILED；定向零 DB
32；无 DDL；lims）
最后更新：2026-09-09（`distinguish-planned-and-earned-training-hours`：安排学时与获得学时契约收口，公式未改；发布/投影零 DB
40；lims）
最后更新：2026-09-09（`derive-exam-attempt-type-on-start` Task 1.1～2.1：开考类型由服务端派生，交卷后再次进入返回原卷；补考仅
`/retake`；定向零 DB 73；无 DDL；lims）
最后更新：2026-09-09（`adapt-frontend-cover-access-protocol`：补齐移动培训任务 `trainingTypeId`、`coverUrl` 与受控 Blob 封面/占位；封面契约回归、三端类型检查和移动端定向 ESLint 通过；真实登录态验收待使用已可登录环境执行；fxw11）
最后更新：2026-09-08（`unify-cover-access-protocol` Task 4.1：`sts_training_type.image_file_id` 重命名为
`cover_file_id`；migration `V20260908192000`；baseline `20260908192000`；lims）
最后更新：2026-09-08（`unify-cover-access-protocol`：培训类型对外 `coverFileId`，类型/学员计划受控封面 GET，学员课程/考试/培训列表用
`coverUrl`；QA `engineering/qa/2026-09-08-unify-cover-access-protocol.md`；定向零 DB 61；lims）
最后更新：2026-09-08（PC Web 培训接口分层适配：计划创建/apply-template 服务端复制、模板/标准/计划职责收口、`planContentId` 来源展示及学员入口回归；模板 `resourceVersionId` 全链路透传和 4 条当前交互回归基线已补齐，培训 Node 回归 76/76；真实登录态链路待环境验收；QA `engineering/qa/2026-09-08-adapt-pc-training-api-layering.md`；fxw11）
最后更新：2026-09-07（`align-training-layer-apis` Task 1～4：标准口径对齐类型尺子、模板发布不强制执行配置、创建/apply-template
复制课表、预检指定资源 8156；lims）
最后更新：2026-09-07（`unify-training-course-exam-capability` Task 4：开课/考试列表默认含培训对象并暴露/可过滤
`planContentId`；lims）
最后更新：2026-09-07（`unify-training-course-exam-capability` Task 1～3：开课/考试发布收口为对象字段规则，培训改为调用同一套发布；「我的考试」排除
TRAINING_*；lims）
最后更新：2026-09-07（`unify-training-course-exam-capability` Task 1：开课发布已有版本则沿用否则取
current；培训生成创建草稿后调用同一套发布；5068 保持草稿；lims）
最后更新：2026-09-07（开课与考试能力：两处调用；设计见 `unify-training-course-exam-capability`，待确认；lims）
最后更新：2026-09-07（题库、试卷、考试 PC Web 真实接口联调已完成；当前进度切换到培训模块真实接口联调，重点核对培训类型、标准、模板、计划、参训人员、执行配置和发布预检链路；fxw11）
最后更新：2026-09-04（培训计划人员与执行前端收口：接入 `participants/bind-selection` 统一人员选择，支持 ALL/DEPT/USER 批量绑定参训人；发布前无参训人员时前端提示并阻止直接发布；QA `engineering/qa/2026-09-03-training-composition-linkage.md` Task 6；真实接口联调待环境；fxw11）
最后更新：2026-09-03（培训编排前端联动完成：类型→标准→模板→计划→评价版本的筛选、摘要、模板草稿带入、来源变更确认，以及计划四类内容的发布执行配置；QA `engineering/qa/2026-09-03-training-composition-linkage.md`，真实接口联调待环境；fxw11）
最后更新：2026-09-02（M3 培训前端 Task 6 工作台统一完成：标准、模板、评价表单、计划严格采用课程、考试基准骨架；workspace 单一 `admin-detail-page__surface`、步骤组件统一 `admin-detail-page__card` 且无插槽传递；18 项结构回归、类型检查、定向 ESLint、独立构建、严格 OpenSpec 校验与差异检查通过；fxw11）
最后更新：2026-09-01（TRAIN-08 培训计划初培/复审性质、发布冻结学时与监管预警闭环完成；Admin 零 DB 859/1 跳过、
Admin MySQL IT 210、Admin-init IT 1，baseline 20260901103000，lims）
最后更新：2026-08-31（M3 Task 6 全量验证通过并关闭：Admin 零 DB 847/0 失败/1 跳过，Admin MySQL IT 208，Admin-init IT 1
baseline 20260831180000，lims）
最后更新：2026-08-31（M3 Task 6 收紧 end 仅 RUNNING、钉住学时/个人结论口径并补四类混合计划 MySQL 纵向 IT；验证数量见本表
M3/TRAIN-07 行，lims）
最后更新：2026-08-31（M3 整体复核后转“需修正”；新增待确认 Task 6，收紧 end 状态机、明确标准口径并补四类混合计划真实 MySQL 纵向
IT，lims）
最后更新：2026-08-31（M3 Task 5 补充最终结果快照读取 hash 校验；定向 17、Admin 零 DB 841/1 跳过，lims）
最后更新：2026-08-31（M3 Task 5 复核缺口已修：补考窗口回填、开考 snapshotHash、v1 最终快照读取；Admin 零 DB 836/0 失败/1
跳过，lims）
最后更新：2026-08-31（M3 Task 5 补齐计划专属批阅与真实来源状态：EXAM v2 快照、专属 Reviewer、sourceStatus 投影、v2
最终快照、培训题库防线；零 DB 72+38；Admin MySQL IT 206、Admin-init IT 1 baseline 20260831180000，lims）
最后更新：2026-08-31（M3 Task 4 Docker IT 补跑通过：Admin MySQL 205、Admin-init 1；复合索引断言已验证，lims）
最后更新：2026-08-31（M3 Task 4 修正执行与结果闭环偏差：题库抽题、发布快照建考、结果映射、教师归属、加权聚合与快照
finalizedTime；零 DB 定向 75+36 通过，IT 待 Docker，lims）
最后更新：2026-08-31（M3 培训执行闭环 Task 1～3 完成后端：发布冻结、四类真实执行、学员 enter、结果投影与计划
start/end/cancel；IT 待 Docker，前端另开，lims）
最后更新：2026-08-28（音频转码改为 AAC HLS 按片播放，play-info 不兼容遗留 MP3，lims）
最后更新：2026-08-27（归档学员考试封面受控读取 Change，lims）
最后更新：2026-08-27（学员按考试 ID 受控读取封面，任务列表不加 coverFileId，lims）
最后更新：2026-08-27（归档课程考核学员详情可见性 Change，lims）
最后更新：2026-08-27（新文件对象统一增加机构编码根目录，历史对象不迁移，lims）
最后更新：2026-08-26（开课配置与课程测验阅卷流程修整完成：草稿阶段仅配置阅卷人、结果分页 Void 异常修复、新增/编辑弹窗串联发布前置配置；多课程测验按章节平铺并逐项校验阅卷人；阅卷人选择即保存、课程测验仅选择各试卷最新已发布版本；后端目标测试 6/6、PC 测试 5/5、typecheck/lint 通过，Codex）
最后更新：2026-08-25（init 模块化完整建库、登记 baseline，Admin Flyway 仅执行 V 增量，lims）
最后更新：2026-08-20（考试成绩处置重构：逐题核对、服务端预计及格结果与补考资格同源判定完成，Codex）
最后更新：2026-08-20（考试成绩发布后批阅锁定、考试/课程工作台页签归并及考试信息展示优化完成，Codex）
最后更新：2026-08-19（考试工作台策略展示、试卷默认规则契约同步和批阅意见回显回归完成，AI）
最后更新：2026-08-19（PC 学员考试中心补齐交卷后答题回看；真实联调待环境，AI）
最后更新：2026-08-17（PDFBox Page Tree 分层生产链完成，648 页真实 qpdf/Range 回归通过，lims）
最后更新：2026-08-15（记录 PDF Page Tree 分层根治方案与 648 页对比证据，生产生成链尚未实施，AI / lims）
最后更新：2026-08-14（PC/H5 PDF 单页渐进预览实现完成，等待真实浏览器验收；移动 H5 构建基线受阻，AI）
最后更新：2026-08-14（课程源文件下载模式统一为 NONE/ALL/PARTIAL；目标测试 23、Admin 零 DB 609，lims）
最后更新：2026-08-13（系统用户不可见 Unicode 输入校验完成；目标测试 29、Admin 零 DB 609，lims）
最后更新：2026-08-13（新上传 PDF qpdf 线性化完成验收，不处理历史数据；零 DB 596、全量 IT 189，lims）
最后更新：2026-08-13（学员课程详情聚合冻结简介与附件，并新增本人受控下载，lims）
最后更新：2026-08-11（跨机构资源授权退役，课程/考试/培训资源收敛为严格机构隔离，lims）
最后更新：2026-08-10（Servlet ASYNC 二次分派租户上下文修复完成，lims）
最后更新：2026-08-10（superAdmin 登录租户绑定与轻量企业开户完成，lims）
最后更新：2026-08-07（多租户隔离体系重建 Task 1～9 完成并复核异步链路；导出产物 2 条顺延；
转码租户上下文缺陷另开 2026-08-07-fix-transcode-tenant-context 修复，lims）
最后更新：2026-08-04（M2 前端补齐父级知识点、业务选择器、文件上传和统一字典回显；真实联调仍待环境）
最后更新：2026-08-01（菜单展示树与当前前端路由树对齐，lims）
最后更新：2026-08-04（素材媒体增强 Task 1 完成 MinIO 私有存储与可恢复分片上传后端闭环，lims）
最后更新：2026-08-04（素材媒体增强 Task 2 完成异步调度、条件抢占、超时与失败恢复，lims）
最后更新：2026-08-04（素材媒体增强 Task 3 完成 FFmpeg 音视频真实转码与派生结果，lims）
最后更新：2026-08-06（素材媒体增强修复 multipart/ASYNC 真机问题并完成素材 Word 上传兼容性限制，lims）
最后更新：2026-08-10（修复 OnlyOffice 一次性源文件回拉缺少租户上下文；真实后端样本联调仍按 COURSE-02 记录执行，lims）
最后更新：2026-08-04（素材媒体增强 Task 5 完成受控播放预览与 Range 文件访问，lims）

路线依据：`docs/requirement/end-to-end-development-roadmap.md`

## 1. 使用说明

本表用于登记路线图各业务能力的开发状态，方便持续更新和查看全局进度。它不是第二套需求规格或任务清单；具体范围、验收标准和实施任务必须以已确认的
`openspec/changes/<change>/tasks.md` 为唯一依据。

更新规则：

1. 状态只能填写：`待确认`、`未开始`、`进行中`、`受阻`、`待验收`、`已完成`、`延期`。
2. 开始开发时填写负责人、OpenSpec Change / Task、开始日期和下一步。
3. 标记为“已完成”前，必须填写完成日期和可复核的验收证据。
4. 验收证据优先填写 OpenSpec Task、QA、测试报告、API Contract、正式 migration 或关键代码路径，不填写“已完成”之类的循环描述。
5. 一个能力只更新一行；实施细节和子任务进度回写对应 OpenSpec `tasks.md`，不在本表扩展平行任务。
6. 遇到业务规则、API、权限、数据库或架构决策问题时，状态改为“受阻”，在“阻塞项 / 下一步”中写明需要谁确认。
7. 每次更新状态时同步修改本文“最后更新”日期和该行“行更新时间”。

状态说明：

| 状态  | 使用场景                           |
|-----|--------------------------------|
| 待确认 | 已有需求或设计，但进入开发所需的业务、权限或架构决策尚未确认 |
| 未开始 | 前置条件已明确，尚未进入实施                 |
| 进行中 | 已按确认的 OpenSpec Task 开始实施       |
| 受阻  | 已开始或准备开始，但被明确问题阻断              |
| 待验收 | 代码已完成，正在执行测试、Review 或人工验收      |
| 已完成 | 对应能力的目标端到端链路、测试、Review 和文档均通过  |
| 延期  | 已明确不属于本期范围                     |

## 2. 里程碑总表

| 里程碑        | 业务结果                                       | 当前状态 | 完成日期       | 验收证据                                                                                                                                                                                                                                                                                                                                        | 阻塞项 / 下一步                                                             | 行更新时间      |
|------------|--------------------------------------------|------|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|------------|
| M0 基础支撑    | 认证、系统管理、机构主数据、机构成员、数据范围、统一人员选择和严格机构隔离可支撑业务 | 已完成  | 2026-08-14 | `org.openapi.json` 已补充机构分页、详情、新增；跨机构资源授权已退役；统一人员选择已收敛；机构维护与前端真实接口联调完成初步版本，真实环境测试收尾。                                                                                                                                                                                                                                                         | 后续根据真实业务反馈持续优化，无当前阻塞                                                  | 2026-08-14 |
| M1 课程闭环    | 素材基础 CRUD、管理员建课开课、学员学习和管理员学习事实形成闭环         | 已完成  | 2026-07-24 | 后端：COURSE-01/03/04 + asset/course OpenAPI + BASE-02～03；2026-08-11 起课程严格按机构隔离；QA `engineering/qa/2026-07-24-m1-course-backend-close.md`（lims 后端口径）                                                                                                                                                                                           | COURSE-06～08 前端另开，不阻塞 lims；下一步 M2 题库试卷                                | 2026-08-11 |
| M2 课程与考试闭环 | 建题组卷、考试作答、批阅、成绩发布并回接课程                     | 已完成  | 2026-09-07 | lims 后端：QUESTION/PAPER/EXAM + OpenAPI + migration；QA `engineering/qa/2026-07-24-question-paper-exam-m2.md`；PC Web 题库、试卷、考试真实接口联调已完成；考试定义、组卷、考生/阅卷人配置、作答、批阅、成绩发布和课程考试项回接按 OpenAPI 完成对齐。                                                                                                                                                      | 后续进入培训模块真实接口联调；题目版本历史、批阅历史、学员考试记录、考生导入如需增强另开 M2.1 确认；COURSE-06～08 仍延期 | 2026-09-07 |
| M3 培训执行闭环  | 标准/模板、培训计划、课程/考试/线下执行形成参训结果                | 已完成  | 2026-09-16 | `2026-07-29-complete-m3-training-execution-loop` Task 1～6：发布冻结、四类真实执行、结果投影、v2 快照、end 仅 RUNNING、学时/个人结论口径与四类混合计划 MySQL 纵向 IT。QA `engineering/qa/2026-08-31-m3-training-execution-loop-task6.md`。Admin 零 DB 847/0 失败/1 跳过；Admin MySQL IT 208；Admin-init IT 1，baseline `20260831180000`。PC/移动端按当前 OpenAPI 对齐；2026-09-16 已完成管理员、学员、教师三角色真实链路验收。 | 无当前阻塞                                                                 | 2026-09-16 |
| M4 培训成果闭环  | 正式综合结果、档案、证书和学员个人成果可追溯                     | 已完成  | 2026-09-20 | lims 人工确认「后端可交互即可」归档 `archive/2026-09-20-2026-09-20-realign-m4-minimum-closure`。问卷/自动综合分/已结束结果/专家查课/证书真实 PNG 与本人查看已交付；2026-09-21 `2026-09-21-remove-archive-attendance-prerequisite` 移除伪 ATTENDANCE 前置，归档版本持久化真实 `fileInfoId` 并回读下载。QA `engineering/qa/2026-09-21-implement-m4-pc-outcome-closure.md`；2026-09-23 `2026-09-23-complete-m4-minimum-ui-closure` 补齐 M4 正式菜单节点（`menus.json`/在线菜单树 +25、`V20260923110000` 幂等迁移并授予 `admin`）与个人档案文件端点（改为按可见事实即时组装 JSON）。主规格 `openspec/specs/m4-minimum-closure/spec.md`。                                                                     | Task 1～4 全部已完成；仅余双轴独立 Review（改动仍在未提交工作树）；不需要签到 Change；**2026-09-28 双端真实环境验收通过，M4 标记完成**（PC 已签发证书/证书模板/培训档案/问卷管理/已结束成果/一期档案六大页面 + `archive/trainings/4/file` 200 PDF 正本 + plan 1 结果不完整 409·9204 阻断 + 学员访问管理端 403·1003；移动端「我的证书」「我的培训档案」两条链路端到端通过，含受控 blob 预览、受控下载 200·image/png 与越权 403·9416 拒绝；QA `engineering/qa/2026-09-28-dual-m4-real-environment-acceptance.md`） | 2026-09-28 |
| M5 监管闭环    | 园区监管工作台、学时预警、学习统计与企业工作台                    | 前后端闭环完成 | 2026-09-20 | 当前 Change `2026-09-29-close-m5-pc-regulation`；功能项 1、2、3、4、7、8 已完成。机构开户、监管/企业真实登录、统计下钻与 CSV、预警查看/修正 ENDED、跨租户权限及企业工作台已验收；Flyway migrations 已应用测试库。新开户默认授权排除机构管理 4 项的代码补漏已通过定向测试。 | FlywayMigrationIT 未运行（本机无 Docker），需在 Docker CI 补跑；新开户权限补漏待部署后真实登录复验；详见 `engineering/qa/2026-09-30-m5-pc-regulation-closeout.md` | 2026-09-30 |
| M6 素材媒体增强  | 真实转码、派生文件、在线预览、播放和失败恢复形成闭环                 | 已完成  | 2026-08-14 | multipart/ASYNC、Word 兼容性、源文件回拉、受控预览与播放已完成；新上传 PDF qpdf 线性化已验收；64.6MB/648 页与权限受限 63.86MiB/2517 页真实 PDF 校验通过；零 DB 620（0 失败、1 跳过）、全量 MySQL/MinIO IT 189/189；QA `engineering/qa/2026-08-17-support-restricted-pdf-preview.md`                                                                                                                   | 后续补充标准 DOCX OnlyOffice 全页视觉复核及更多真实样本，不阻塞当前版本                          | 2026-08-17 |

## 3. 能力进度明细表

### 3.1 基础支撑与课程闭环

| 编号          | 能力                                | 前置依赖                         | 当前状态   | 负责人              | OpenSpec Change / Task                                                                                                     | 开始日期         | 完成日期         | 验收证据                                                                                                                                                                                                                           | 阻塞项 / 下一步                                                                        | 行更新时间        |
|-------------|-----------------------------------|------------------------------|--------|------------------|----------------------------------------------------------------------------------------------------------------------------|--------------|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|--------------|
| BASE-01     | 登录、用户、部门、角色、菜单、字典及系统管理            | 无                            | 已完成    | lims / AI / 开发团队 | 已归档系统管理相关 Change；`2026-08-01-align-menu-tree-with-current-frontend` Task 1；`2026-08-13-reject-invisible-user-input` Task 1 | —            | 2026-08-13   | `docs/api/system-admin.openapi.json`；用户写入不可见 Unicode 校验；目标测试 29/29、Admin 零 DB 609/609；QA `engineering/qa/2026-08-13-reject-invisible-user-input.md`                                                                            | 本次不涉及持久化，无需新增 MySQL IT；既有 migration IT 阻塞保持原记录                                   | 2026-08-13   |
| BASE-02     | 机构成员、标签岗位与角色数据范围                  | BASE-01                      | 已完成    | AI               | 后端 `2026-07-24-implement-org-member-data-scope` Task 1～3；前端 `2026-07-24-implement-m1-frontend-foundation-course` Task 1    | 2026-07-24   | 2026-07-24   | `docs/api/org.openapi.json`；migration V20260724120000/001；前端 QA `2026-07-24-m1-frontend-task1-foundation.md`                                                                                                                   | 角色数据范围与机构成员表已由 BASE-05 删除并取代                                                     | 2026-07-24   |
| BASE-03     | 统一人员选择与具体业务人员绑定                   | BASE-02                      | 已完成    | lims             | `2026-08-11-simplify-system-participant-selection` Task 1～3                                                                | 2026-08-11   | 2026-08-11   | `docs/api/person-selection.openapi.json`；最终结构已进入 init 完整 SQL；课程/考试/培训/问卷绑定具体人员；隔离零 DB 与 MySQL IT                                                                                                                               | 前端以 OpenAPI 为准另开                                                                 | 2026-08-25   |
| BASE-04     | 跨机构资源授权退役与严格机构隔离                  | BASE-02                      | 已完成    | lims             | `2026-08-11-remove-resource-org-grant` Task 1～3                                                                            | 2026-08-11   | 2026-08-11   | migration V20260811140000；resource-grant API/后端删除；同机构/禁用状态零 DB 36；全量零 DB 561/561、IT 191/191；`engineering/qa/2026-08-11-remove-resource-org-grant.md`                                                                           | 前端资源授权页面/API/Mock 由前端协作方另开删除                                                     | 2026-08-11   |
| BASE-05     | 多租户隔离体系重建（扁平租户、强制隔离、企业编码登录、监管跨租户） | BASE-01～BASE-04              | 已完成    | lims             | `2026-08-06-rebuild-multi-tenant-isolation` Task 1～9                                                                       | 2026-08-06   | 2026-08-07   | `openspec/changes/2026-08-06-rebuild-multi-tenant-isolation/tasks.md`；`docs/api/regulator.openapi.json` 等 10 份契约同步；零 DB 533 + IT 189 全绿；6 个域的 `*TenantBoundaryIT`；6 个异步入口逐个核对并附 IT                                             | 导出产物 2 条验收标准顺延（执行器未开发）；切换视角缺 Web 层端到端用例；转码租户上下文缺陷已另开 Change 修复                   | 2026-08-07   |
| BASE-06     | superAdmin 登录租户绑定与轻量企业开户          | BASE-05                      | 已完成    | lims             | `2026-08-10-bind-superadmin-login-tenant` Task 1～3；`2026-08-10-fix-tenant-context-async-dispatch` Task 1                   | 2026-08-10   | 2026-08-10   | 登录租户写入会话并约束普通业务；开户仅创建管理员且 `dept_id` 可空；migration V20260810151000；system-admin/org OpenAPI；ASYNC 二次分派重新绑定租户；QA `2026-08-10-bind-superadmin-login-tenant`、`2026-08-10-fix-tenant-context-async-dispatch`；零 DB 561 + IT 198       | 旧 superAdmin Token 需重新登录；前端以 OpenAPI 为准另开                                        | 2026-08-10   |
| BASE-07     | init 完整建库与 Admin Flyway 增量        | BASE-01～BASE-06              | 已完成    | lims             | 已归档 `archive/2026-08-27-2026-08-25-integrate-flyway-mysql` Task 1～3                                                        | 2026-08-25   | 2026-08-25   | `backend/sts-service-admin-init/src/main/resources/schema/` 13 个完整模块；baseline `20260811150000`；Admin 当前 0 个增量；init IT 1/1、Admin MySQL IT 185/185、Admin 零 DB 668（跳过 1）；QA `engineering/qa/2026-08-25-integrate-flyway-mysql.md` | init 仅首次部署执行；未来首次数据库变化时由 Admin 执行新增 pending V；init 既有菜单种子一致性 2 项失败另行处理           | 2026-08-25   |
| COURSE-01   | 素材元数据、文件关联、CRUD、启停用和删除保护          | BASE-01                      | 已完成    | AI / 开发团队        | 已归档 `implement-course-asset-library` Task 1                                                                                | —            | 2026-07-22   | `docs/api/asset.openapi.json`；完整结构见 `backend/sts-service-admin-init/src/main/resources/schema/schema-asset.sql`；素材后端测试                                                                                                         | 当前只推进基础 CRUD 真实联调；转码由 COURSE-02 最后实施                                             | 2026-07-24   |
| COURSE-03   | 课程分类、设置、定义、章节和内容编排后端              | BASE-01、COURSE-01            | 已完成    | AI / 开发团队        | 已归档 `implement-course-config-authoring` Task 1～3                                                                           | —            | 2026-07-22   | `docs/api/course.openapi.json`；课程正式 migration；课程后端单测和集成测试                                                                                                                                                                      | 考试类内容项由 EXAM-04 跟踪                                                               | 2026-07-24   |
| COURSE-04   | 课程版本、开课、报名、学习事实和评价后端              | COURSE-03                    | 已完成    | AI / 开发团队 / lims | 已归档课程发布、开课与学习 Change；`unify-cover-access-protocol` Task 2.1                                                                | —            | 2026-08-14   | 原完成证据不变。2026-09-08 学员课程列表/任务/学习课程 VO 以 `coverUrl` 替换 `coverFileId`。QA `engineering/qa/2026-09-08-unify-cover-access-protocol.md`                                                                                               | 前端以 OpenAPI 为准另开                                                                 | 2026-09-08   |
| COURSE-05   | 课程与素材 PC 管理端                      | COURSE-01、03、04              | 已完成    | AI / 开发团队        | `implement-course-admin-frontend` Task 1～4；`2026-08-10-optimize-course-center-scope-selectors` Task 1～3                    | —            | 2026-07-23   | `engineering/qa/2026-07-22-course-admin-frontend.md`；`engineering/qa/2026-08-10-course-center-scope-selector-optimization.md`；PC 课程页面和前端测试                                                                                     | Mock 页历史交付；真实联调属 COURSE-06（前端另开）                                                 | 2026-08-10   |
| COURSE-06   | PC 管理端真实接口、权限和异常联调                | BASE-02～04、COURSE-01、03～05   | 延期     | —                | 前端协作方另开 Change                                                                                                             | —            | —            | 默认 Mock 仍存在；不纳入 lims M1 关闭证据                                                                                                                                                                                                   | 前端按 `docs/api` 关 Mock 联调；不阻塞 M1 后端关闭                                             | 2026-07-24   |
| COURSE-07   | 学员 PC 我的课程、选课、学习和结果               | BASE-03、COURSE-01、04         | 已完成    | AI               | `2026-07-24-implement-m1-frontend-foundation-course` Task 3；`2026-08-12-implement-learner-course-learning-ui` Task 1       | 2026-07-27   | 2026-08-12   | PC 定向测试 4/4、门户路由测试 6/6、兼容参数 typecheck、目标文件 lint、PC build；新增课程学习工作台，复用现有 tasks/play-info/sessions Contract                                                                                                                      | 真实接口联调与 Mock 收敛待后续环境；评论/笔记/高级播放器不在本 Task                                         | 2026-08-12   |
| COURSE-08   | 学员移动端课程学习                         | COURSE-07                    | 已完成    | AI               | `2026-07-24-implement-m1-frontend-foundation-course` Task 3                                                                | 2026-07-27   | 2026-07-27   | 移动定向测试 2/2、lint、typecheck、H5 build；同 QA                                                                                                                                                                                        | 真实联调与关 Mock 属 Task 4                                                             | 2026-07-27   |
| COURSE-09   | 课程端到端验收与 M1 关闭                    | COURSE-01/03/04、BASE-02～03   | 已完成    | lims / AI        | 后端口径关闭；QA `2026-07-24-m1-course-backend-close`；`2026-08-11-simplify-system-participant-selection`                          | 2026-07-24   | 2026-08-11   | 素材/课程/开课学习 OpenAPI + migration + 后端测试；指派报名、开放全员/具体白名单校验；同机构隔离回归；转码后置                                                                                                                                                           | 前端 E2E（06～08）另开，不纳入 lims 关闭证据                                                    | 2026-08-11   |
| 编号          | 能力                                | 前置依赖                         | 当前状态   | 负责人              | OpenSpec Change / Task                                                                                                     | 开始日期         | 完成日期         | 验收证据                                                                                                                                                                                                                           | 阻塞项 / 下一步                                                                        | 行更新时间        |
| ----------- | --------------------------        | ---------------------------- | ------ | -----------      | ----------------------------------------------------------                                                                 | ------------ | ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------                                                                             | ------------------------------------------                                       | ------------ |
| BASE-01     | 登录、用户、部门、角色、菜单、字典及系统管理            | 无                            | 已完成    | lims / AI / 开发团队 | 已归档系统管理相关 Change；`2026-08-01-align-menu-tree-with-current-frontend` Task 1                                                 | —            | 2026-08-01   | `docs/api/system-admin.openapi.json`；`engineering/qa/2026-08-01-align-menu-tree-with-current-frontend.md`；菜单一致性测试 5/5；Admin 零 DB 429/429                                                                                       | migration MySQL IT 因 Docker socket 不可用待补跑                                        | 2026-08-01   |
| BASE-01A    | 机构主数据登记、层级、启停用和引用保护               | BASE-01                      | 进行中    | lims             | 已归档 `archive/2026-08-27-2026-07-27-implement-organization-master-data`；见 `archive-record.md`                               | 2026-07-27   | —            | Task 1 曾进入 TDD；后续租户开户改写了机构语义，原 Task 2～3 未按本 Change 关闭                                                                                                                                                                          | 重启须新 Change；记录 `engineering/plans/2026-08-27-backend-openspec-archive-record.md` | 2026-08-27   |
| BASE-02     | 机构成员、标签岗位与角色数据范围                  | BASE-01                      | 已完成    | AI               | `2026-07-24-implement-org-member-data-scope` Task 1～3                                                                      | 2026-07-24   | 2026-07-24   | `docs/api/org.openapi.json`；migration V20260724120000/001；`engineering/qa/2026-07-24-org-member-data-scope.md`；零 DB 42 + IT 5                                                                                                  | 前端页面另开；课程域仍可用 DefaultOrganizationService                                         | 2026-07-24   |
| BASE-03     | 统一人员选择与具体业务人员绑定                   | BASE-02                      | 已完成    | lims             | `2026-08-11-simplify-system-participant-selection` Task 1～3                                                                | 2026-08-11   | 2026-08-11   | `docs/api/person-selection.openapi.json`；最终结构已进入 init 完整 SQL；课程/考试/培训/问卷绑定具体人员；隔离零 DB 与 MySQL IT                                                                                                                               | 前端以 OpenAPI 为准另开                                                                 | 2026-08-25   |
| BASE-04     | 跨机构资源授权退役与严格机构隔离                  | BASE-02                      | 已完成    | lims             | `2026-08-11-remove-resource-org-grant` Task 1～3                                                                            | 2026-08-11   | 2026-08-11   | migration V20260811140000；resource-grant API/后端删除；同机构/禁用状态零 DB 36；全量零 DB 561/561、IT 191/191；`engineering/qa/2026-08-11-remove-resource-org-grant.md`                                                                           | 前端资源授权页面/API/Mock 由前端协作方另开删除                                                     | 2026-08-11   |
| COURSE-01   | 素材元数据、文件关联、CRUD、启停用和删除保护          | BASE-01                      | 已完成    | AI / 开发团队        | 已归档 `implement-course-asset-library` Task 1                                                                                | —            | 2026-07-22   | `docs/api/asset.openapi.json`；完整结构见 `backend/sts-service-admin-init/src/main/resources/schema/schema-asset.sql`；素材后端测试                                                                                                         | 当前只推进基础 CRUD 真实联调；转码由 COURSE-02 最后实施                                             | 2026-07-24   |
| COURSE-03   | 课程分类、设置、定义、章节和内容编排后端              | BASE-01、COURSE-01            | 已完成    | AI / 开发团队        | 已归档 `implement-course-config-authoring` Task 1～3                                                                           | —            | 2026-07-22   | `docs/api/course.openapi.json`；课程正式 migration；课程后端单测和集成测试                                                                                                                                                                      | 考试类内容项由 EXAM-04 跟踪                                                               | 2026-07-24   |
| COURSE-04   | 课程版本、开课、报名、学习事实和评价后端              | COURSE-03                    | 已完成    | AI / 开发团队 / lims | 已归档课程发布、开课与学习 Change；`unify-cover-access-protocol` Task 2.1                                                                | —            | 2026-08-14   | 原完成证据不变。2026-09-08 学员课程列表/任务/学习课程 VO 以 `coverUrl` 替换 `coverFileId`。QA `engineering/qa/2026-09-08-unify-cover-access-protocol.md`                                                                                               | 前端以 OpenAPI 为准另开                                                                 | 2026-09-08   |
| COURSE-05   | 课程与素材 PC 管理端                      | COURSE-01、03、04              | 已完成    | AI / 开发团队        | `implement-course-admin-frontend` Task 1～4                                                                                 | —            | 2026-07-23   | `engineering/qa/2026-07-22-course-admin-frontend.md`；PC 课程页面和前端测试                                                                                                                                                              | Mock 页历史交付；真实联调属 COURSE-06（前端另开）                                                 | 2026-07-24   |
| COURSE-06   | PC 管理端真实接口、权限和异常联调                | BASE-02～04、COURSE-01、03～05   | 延期     | —                | 前端协作方另开 Change                                                                                                             | —            | —            | 默认 Mock 仍存在；不纳入 lims M1 关闭证据                                                                                                                                                                                                   | 前端按 `docs/api` 关 Mock 联调；不阻塞 M1 后端关闭                                             | 2026-07-24   |
| COURSE-07   | 学员 PC 我的课程、选课、学习和结果               | BASE-03、COURSE-01、04         | 已完成    | AI / lims        | `2026-07-24-implement-m1-frontend-foundation-course` Task 3；后端 `2026-07-27-add-learner-course-reading-apis`                | 2026-07-27   | 2026-07-27   | PC 定向测试 3/3、typecheck、定向 lint、build；后端零 DB 370 + IT 85；QA `2026-07-27-m1-frontend-task3-learner-course`                                                                                                                        | 真实联调与关 Mock 属 Task 4                                                             | 2026-07-27   |
| COURSE-08   | 学员移动端课程学习                         | COURSE-07                    | 已完成    | AI               | `2026-07-24-implement-m1-frontend-foundation-course` Task 3                                                                | 2026-07-27   | 2026-07-27   | 移动定向测试 2/2、lint、typecheck、H5 build；同 QA                                                                                                                                                                                        | 真实联调与关 Mock 属 Task 4                                                             | 2026-07-27   |
| COURSE-09   | 课程端到端验收与 M1 关闭                    | COURSE-01/03/04、BASE-02～03   | 已完成    | lims / AI        | 后端口径关闭；QA `2026-07-24-m1-course-backend-close`；`2026-08-11-simplify-system-participant-selection`                          | 2026-07-24   | 2026-08-11   | 素材/课程/开课学习 OpenAPI + migration + 后端测试；指派报名、开放全员/具体白名单校验；同机构隔离回归；转码后置                                                                                                                                                           | 前端 E2E（06～08）另开，不纳入 lims 关闭证据                                                    | 2026-08-11   |

### 3.2 题库、试卷与考试闭环

| 编号          | 能力                     | 前置依赖                               | 当前状态 | 负责人          | OpenSpec Change / Task                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | 开始日期       | 完成日期       | 验收证据                                                                                                                                                                                          | 阻塞项 / 下一步                     | 行更新时间      |
|-------------|------------------------|------------------------------------|------|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------|------------|
| QUESTION-01 | 知识点、题库、题型与题目不可变版本      | M1、COURSE-01                       | 已完成  | lims / AI    | 后端 `2026-07-24-implement-question-paper-exam` Task 1；前端 `2026-07-27-implement-m2-frontend-question-exam` Task 1、5.1                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | 2026-07-24 | 2026-09-07 | migration V20260724160000；`docs/api/question.openapi.json`；资源严格按机构隔离；PC Web 题库真实接口联调已完成，列表、题目维护、题型字典和版本读取按真实接口完成对齐。                                                                           | 当前无真实联调阻塞；后续按业务反馈优化           | 2026-09-07 |
| QUESTION-02 | 题目素材、Excel 导入导出和用户错题状态 | QUESTION-01                        | 已完成  | lims         | 同上 Task 2                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | 2026-07-24 | 2026-07-24 | JSON 导入 + import_batch；素材关联；错题 upsert                                                                                                                                                         | WORD/真实 Excel 后续              | 2026-07-24 |
| PAPER-01    | 固定、随机、自定义试卷和试卷版本       | QUESTION-01                        | 已完成  | lims / AI    | 后端 `2026-07-24-implement-question-paper-exam` Task 3；前端 `2026-07-27-implement-m2-frontend-question-exam` Task 2、5.1；`2026-08-17-optimize-paper-workbench-real-integration` Task 1、1.1、1.3、1.4                                                                                                                                                                                                                                                                                                                                                                                           | 2026-07-24 | 2026-09-07 | `docs/api/paper.openapi.json`；统一组卷工作台、分类/封面选择、分类默认筛题、题目编码/题干与字典回显；试卷固定、随机、自定义组卷和发布预览已完成 PC Web 真实接口联调。                                                                                        | 当前无真实联调阻塞；封面查看后续随受控文件读取体验继续优化 | 2026-09-07 |
| EXAM-01     | 考试定义、规则、人员、负责人和阅卷人     | PAPER-01、BASE-03                   | 已完成  | lims / AI    | 后端 `2026-07-24-implement-question-paper-exam` Task 4、`2026-08-11-simplify-system-participant-selection`、`inherit-exam-rules-from-paper` Task 1、`2026-08-27-support-draft-exam-participant-configuration` Task 1；前端 `2026-08-17-align-exam-real-integration` Task 1～6、8、`2026-08-18-refactor-exam-workbench` Task 1、`2026-08-18-extract-exam-paper-workspace-panels` Task 1、`2026-08-18-add-exam-attempt-management-entry` Task 1、`2026-08-18-refine-exam-workflow-entries` Task 1～5、已归档 `archive/2026-08-27-2026-08-19-sync-exam-paper-default-contract-and-review-regression` Task 1～3 | 2026-07-24 | 2026-09-07 | `docs/api/exam.openapi.json` 26 operations 已全部映射至 PC Web；考生绑定已覆盖 ALL/DEPT/USER；批阅页支持长事件内部滚动和完整评分查看；PC Web 考试定义、规则、人员、负责人、阅卷人、作答、批阅和成绩发布真实接口联调已完成。                                             | 当前无真实联调阻塞；后续进入培训模块真实接口联调      | 2026-09-07 |
| EXAM-02     | 学员 PC/移动作答、快照、暂存和交卷    | EXAM-01                            | 已完成  | lims         | 同上 Task 4；`derive-exam-attempt-type-on-start` Task 1.1                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | 2026-07-24 | 2026-09-09 | 原完成证据不变。2026-09-09 通用开考忽略请求 `attemptType`，已有非作废终态则返回原卷。QA `engineering/qa/2026-09-09-derive-exam-attempt-type-on-start.md`                                                                    | 前端以 OpenAPI 为准另开              | 2026-09-09 |
| EXAM-03     | 自动判分、人工批阅、改分留痕和成绩发布    | EXAM-02                            | 已完成  | lims / AI    | 后端 Task 4；前端 M2 Task 5                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | 2026-07-24 | 2026-09-07 | 客观 AUTO、主观批阅只追加、成绩发布；教师批阅任务工作台、独立批阅页、反作弊事件、成绩发布流水及发布处置页；PC Web 真实接口联调已完成。                                                                                                                     | 当前无真实联调阻塞；后续按业务反馈优化           | 2026-09-07 |
| EXAM-04     | 补考、练习重置、错题及课程考试项回接     | EXAM-03、COURSE-04                  | 已完成  | lims / AI    | 后端 Task 4、`2026-08-19-enforce-exam-retake-policy` Task 1；`derive-exam-attempt-type-on-start` Task 1.1；前端 M2 Task 5                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | 2026-07-24 | 2026-09-09 | 原完成证据不变。2026-09-09 仅 `POST /exam/candidates/{id}/retake` 可创建 RETAKE。QA `engineering/qa/2026-09-09-derive-exam-attempt-type-on-start.md`                                                       | 前端以 OpenAPI 为准另开              | 2026-09-09 |
| EXAM-05     | 考试端到端验收与 M2 关闭         | QUESTION-01～02、PAPER-01、EXAM-01～04 | 已完成  | lims         | 同上 Task 5                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | 2026-07-24 | 2026-07-24 | QA + OpenAPI + 进度表；零 DB 13 + CourseItem 7 / IT 3                                                                                                                                              | 前端联调另开                        | 2026-07-24 |
| EXAM-06     | 学员考试任务、作答恢复与成绩可见性      | EXAM-01～03                         | 已完成  | lims / AI    | 已归档学员考试封面 Change；`unify-cover-access-protocol` Task 3.1                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | 2026-07-29 | 2026-09-08 | 原完成证据不变。2026-09-08 学员考试任务增加 `coverUrl`，仍不返回 `coverFileId`。QA `engineering/qa/2026-09-08-unify-cover-access-protocol.md`                                                                       | 前端以 OpenAPI 为准另开              | 2026-09-08 |
| EXAM-07     | 课程考核与独立考试来源分离          | QUESTION-01～02、PAPER-01、COURSE-04  | 已完成  | lims / Codex | 后端已归档 `archive/2026-08-27-2026-08-25-separate-course-assessment-from-exam` Task 1～3；已归档 `archive/2026-08-27-2026-08-27-fix-course-assessment-result-visibility` Task 1；前端 `2026-08-26-sync-course-assessment-ui` Task 1～3 已完成                                                                                                                                                                                                                                                                                                                                                           | 2026-08-25 | 2026-09-07 | 课程 `QUESTION_BANK -> questionBankId`、`QUIZ -> paperVersionId`；课程/独立批阅入口与结果分域；课程测验自动生效；课程考核与独立考试来源分离真实接口联调已完成。                                                                                 | 当前无真实联调阻塞；后续按业务反馈优化           | 2026-09-07 |
| EXAM-08     | 教师批阅任务与考试作答成绩分页        | EXAM-01～03                         | 已完成  | lims         | 已归档 `archive/2026-08-27-2026-07-30-add-exam-review-attempt-list-apis` Task 1；修复已归档 `2026-07-31-fix-exam-attempt-organization-join` Task 1                                                                                                                                                                                                                                                                                                                                                                                                                                               | 2026-07-30 | 2026-07-31 | `ExamAttemptController`、聚合查询 Mapper、`docs/api/exam.openapi.json`；零 DB 全量 385/385、目标服务 3/3；`ExamAttemptQueryMapperIT` 2/2；QA `engineering/qa/2026-07-31-fix-exam-attempt-organization-join.md` | 无；前端以 OpenAPI 为准联调            | 2026-07-31 |

### 3.3 培训标准、计划与执行闭环

| 编号       | 能力               | 前置依赖                 | 当前状态 | 负责人          | OpenSpec Change / Task                                                                                                                                                                                                                                                                                                                                                                                                                      | 开始日期       | 完成日期       | 验收证据                                                                                                                                                                                                                                                                                                                 | 阻塞项 / 下一步                    | 行更新时间      |
|----------|------------------|----------------------|------|--------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------|------------|
| TRAIN-01 | 培训类型和培训标准        | M2                   | 已完成  | lims         | 原 Task 1；`align-training-layer-apis` Task 1.1；`unify-cover-access-protocol` Task 1.1、4.1                                                                                                                                                                                                                                                                                                                                                    | 2026-07-24 | 2026-07-24 | 原完成证据不变。2026-09-08 类型对外 `coverFileId`；`GET /training/types/{id}/cover`；无图 404/`8164`。列 `image_file_id` → `cover_file_id`（`V20260908192000`，baseline `20260908192000`）。QA `engineering/qa/2026-09-08-unify-cover-access-protocol.md`                                                                                  | 前端以 OpenAPI 为准另开             | 2026-09-08 |
| TRAIN-02 | 培训模板、阶段、内容和机构隔离  | TRAIN-01             | 已完成  | lims         | 同上 Task 2；`2026-08-11-remove-resource-org-grant` Task 1；`align-training-layer-apis` Task 2.1                                                                                                                                                                                                                                                                                                                                                | 2026-07-24 | 2026-08-11 | 原完成证据不变。2026-09-07 模板发布不强制执行配置，空课表 400/`8042`。QA `engineering/qa/2026-09-07-align-training-layer-apis.md`；定向零 DB `TrainingTemplateServiceImplTest` 8                                                                                                                                                                 | 前端以 OpenAPI 为准另开             | 2026-09-07 |
| TRAIN-03 | 评价表单定义与不可变版本     | TRAIN-01             | 已完成  | lims         | 同上 Task 3                                                                                                                                                                                                                                                                                                                                                                                                                                   | 2026-07-24 | 2026-07-24 | migration V20260724182000；`evaluation-form.openapi.json`；零 DB 3 + IT 2                                                                                                                                                                                                                                               | 评价作答属 M4                     | 2026-07-24 |
| TRAIN-04 | 培训计划、阶段、内容和参训人员  | TRAIN-02～03、BASE-03  | 已完成  | lims         | 原 Task 4；`align-training-layer-apis` Task 3.1、4.1                                                                                                                                                                                                                                                                                                                                                                                           | 2026-07-24 | 2026-09-04 | 原完成证据不变。2026-09-07 创建带合格模板即复制课表；`POST /training/plans/{id}/apply-template`；不合格模板 400/`8157`，非草稿 409/`8158`；PUT 来源 ID 不复制；预检缺指定资源 400/`8156`。QA `engineering/qa/2026-09-07-align-training-layer-apis.md`；定向零 DB `TrainingPlanServiceImplTest` 25、`TrainingPlanPublishValidatorTest` 18、`TrainingPlanControllerTest` 4 | 前端以 OpenAPI 为准另开             | 2026-09-07 |
| TRAIN-05 | 课程、考试和线下执行实例生成   | TRAIN-04、M1、M2       | 已完成  | lims         | 原 Task 2；`unify-training-course-exam-capability` Task 1～2                                                                                                                                                                                                                                                                                                                                                                                   | 2026-07-24 | 2026-07-24 | 开课发布：已有版本则沿用否则取 current；培训草稿后走 `publishOffering`。考试发布按 `planContentId` 物化 STANDALONE_*/TRAINING_*；培训创建草稿后走 `ExamService.publish`。QA `engineering/qa/2026-09-07-unify-training-course-exam-capability.md`。零 DB 见 QA；IT：`TrainingMixedPlanLifecycleIT` 2、`DraftExamParticipantLifecycleIT` 4 通过                        | 前端以 OpenAPI 为准另开             | 2026-09-07 |
| TRAIN-06 | 线下成绩、附件、奖惩和教师任务  | TRAIN-05             | 已完成  | lims / fxw11 | 同上 Task 6；`simplify-offline-session-to-score` Task 1～3；`remove-offline-sign-in-from-plan-apis` Task 1.1～2.1；`add-offline-session-state-machine` Task 1～2；`add-teacher-task-offline-session-version` Task 1；`2026-09-10-improve-teacher-task-detail` Task 1～2；`2026-09-10-show-teacher-task-plan-name` Task 1                                                                                                                                | 2026-07-24 | 2026-09-16 | 线下完成只认教师成绩；签到 HTTP、签到表、场次签到列、空归档签到快照、工作台签到统计和三个签到权限均删除。教师任务返回参训名单；一场一人一条成绩。场次 start/complete/cancel，PUT 不改状态，仅 IN_PROGRESS 录分。QA `engineering/qa/2026-09-10-remove-offline-sign-in-from-plan-apis.md`；后端定向零 DB 129、Init 5、MySQL IT 14。2026-09-16 已完成真实教师登录态验收。                                                                               | 无当前阻塞；签到若恢复须另开 Change | 2026-09-16 |
| TRAIN-07 | 培训执行端到端验收与 M3 关闭 | TRAIN-01～06          | 已完成  | lims         | `unify-training-course-exam-capability` Task 3～4；`unify-cover-access-protocol` Task 1.1；`derive-exam-attempt-type-on-start` Task 2.1；`distinguish-planned-and-earned-training-hours` Task 1.1；`learner-offline-session-view` Task 1.1；`simplify-offline-session-to-score` Task 1～3；`remove-offline-sign-in-from-plan-apis` Task 1.1～2.1；`add-teacher-task-offline-session-version` Task 1；`sync-mobile-training-execution-closure` Task 1～2 | 2026-07-24 | 2026-09-16 | 线下执行仅保留场次状态与成绩事实；签到 HTTP、存储、配置、统计和权限均删除。PC 与移动端已同步场次状态、名单/成绩和获得学时语义；QA `engineering/qa/2026-09-10-remove-offline-sign-in-from-plan-apis.md`、`engineering/qa/2026-09-10-sync-mobile-training-execution-closure.md`；后端定向零 DB 129、Init 5、MySQL IT 14。2026-09-16 已完成管理员、学员、教师真实链路验收。                                                                   | 无当前阻塞            | 2026-09-16 |
| TRAIN-08 | 培训计划初培/复审学时判定与预警 | TRAIN-01、TRAIN-04、M5 | 已完成  | lims         | `2026-09-01-add-training-plan-nature` Task 1～2                                                                                                                                                                                                                                                                                                                                                                                              | 2026-09-01 | 2026-09-01 | 计划 `INITIAL/REVIEW`、发布冻结 `applicableRequiredHours` 和 v2 类型快照；migration V20260901103000；监管按冻结值与条件最大值判定并记录来源；QA `engineering/qa/2026-09-01-training-plan-nature.md`；Admin 零 DB 859/1 跳过、MySQL IT 210、Admin-init IT 1                                                                                                   | 前端以 OpenAPI 为准另开             | 2026-09-01 |

> 2026-09-24 TRAIN-06 增量：已确认的 `2026-09-23-close-training-reward-penalty-loop` Task 2.1 完成奖惩佐证受控预览与可选说明/发生日期的数据库迁移；零 DB 12/12、目标 MySQL IT 2/2、迁移 1/1、受影响旧升级用例 3/3、Admin init 1/1、PC 静态验证通过。完整奖惩 IT 的既有计划乐观锁断言失败；管理员真实登录态及对象存储文件预览仍待 Task 3 验收。证据见 `engineering/qa/2026-09-23-close-training-reward-penalty-loop.md`。
>
> 2026-09-28 TRAIN-06 收口：上述「待 Task 3 验收」已由真实管理员登录态实跑补齐（13 条拒绝路径、综合分数值级 81→86→83→86、
> 计划版本 14→15、佐证受控读取 200/image/jpeg），并已人工签核闭环；两处缺口（PC 界面浏览器取证受本机环境阻断、
> 成果/档案奖惩回读缺数据集样本）如实登记。共享库缺的两条 09-24 迁移经人工确认后已应用（Flyway 记账至 `v20260924180000`）。
> 证据见 `engineering/qa/2026-09-28-close-training-reward-penalty-loop-real-acceptance.md`。

> 2026-09-24 M4 增量（档案 / 资料 / 证书来源）：三个 Change 的代码与自动化验证已完成 ——
> `2026-09-24-close-training-archive-loop`（T1～T4：档案事实扩为 22 个顶层属性、签到由线下成绩派生
> `SIGNED`/`NOT_RECORDED`/`NOT_APPLICABLE` 三态、归档快照写实与素材登记、档案文件改 AWT 光栅 PDF 正本
> 含错误码 **9211**）、`2026-09-24-close-training-material-loop`（T1～T2：受控 `/file` 端点与五类资料类型）、
> `2026-09-24-separate-certificate-issue-sources`（T1～T4：三类来源独立触发、`EXAM` 候选排除派生考试、
> 同来源单启用 9418，含真实 HTTP 端到端）。
> 本轮另把 design 第 8 节的遗留「档案 PDF 内嵌证书编号与持证人 ID 但下载只校验档案权限」**收口**为
> 「PDF 只呈现证书数量、明细回受控端点」，同步 spec-delta/design 并新增 2 例测试。
> 自动化：档案定向单测 28/28、MySQL IT 10/10、资料单测 15/15、资料 IT 5/5、PC typecheck 与改动文件
> eslint 通过、前端定向测试零新增失败（HEAD 基线逐条对照）、三个 Change `openspec validate --strict` 通过。
> **真实登录态纵向验收已于 2026-09-24 在恢复后的环境实跑完成**（`19.191.192.146` 的
> `16379`/`8045`/`20080` 恢复可达后，起 `sts-service-admin` 于 20080 直连）：
> ・资料闭环 —— MinIO 真实落盘（`minio-private://DEMO/source/2026/09/<uuid>.png`）、受控读取逐字节一致、
>   冻结 8237、跨阶段 8235、无效类型 8234、不可见文件 8236、乐观锁 8238、同计划唯一键 8232；浏览器 blob 预览通过。
> ・档案闭环 —— 期次/企业/个人三视角 22 字段逐字段一致、签到三态、缺事实拒绝且无脏残留；
>   plan 2 首次归档产出 PDF 正本（`%PDF-1.6`、198562 字节）、plan 4 历史 JSON 重归档为 PDF（版本 1→3）；
>   企业筛选 `trainingTypeId`/`planName`/`startTimeFrom`+`endTimeTo`(ISO) 有效；浏览器渲染 3 行企业 / 11 区块一期 / 2 张证书。
> ・未覆盖项已如实登记（不伪证）：证书清单「无权」分支、跨租户拒绝、个人档案 PDF 成功路径、档案素材真实登记。
> 证据：`engineering/qa/2026-09-24-close-training-archive-loop.md`、`...material-loop.md`、
> `...separate-certificate-issue-sources.md`；浏览器事实截图 `engineering/assets/2026-09-24-ui-archive-*.png`（证据已于 2026-09-28 清理）。
> 遗留需人工决策：`openPersonFile` 对多期缺失项取并集导致整体拒绝导出，是否改为「只导出完整的期」。
> 台账收口（同日）：**18 个已完成 Change 已归档**至 `openspec/changes/archive/`，活动区 22→**4 个**；
> 其中今天 3 个走 `openspec archive` **已把 delta 合并进 `openspec/specs/`**（`m4-minimum-closure` 需求 7→16 条、
> 新建 `training-material` 能力，specs 25→26 个能力，两个 spec `spec validate` 均通过），其余 15 个沿用历史
> 纯目录迁移；`2026-09-21-remove-archive-attendance-prerequisite` 按其 Task 3 要求的证据（`ArchiveServiceIT` 5/5
> 含文件 ID 回读、真实生成/下载 PDF 正本、结果不完整 409/9204、管理员浏览器下载）复核后收口并归档
> （其 delta 早已在基线，归档用 `--skip-specs` 避免仅措辞换行的无意义改写）。
> 剩余活动 Change（2026-09-28 二次更新）：**已全部归档，活动区 5→0 个**。
> 本轮把 5 个 Change 移入 `openspec/changes/archive/`（纯目录迁移，spec delta **不入基线**，经人工确认）：
> `2026-09-21-implement-m4-pc-outcome-closure`、`2026-09-24-close-mobile-learner-outcome-loop`、
> `2026-09-24-close-training-material-scope-loop`、`2026-09-28-improve-m4-certificate-and-mobile-archive`、
> `2026-09-23-close-training-reward-penalty-loop`。
> 其中 `close-training-reward-penalty-loop` 的 Task 3 于 2026-09-28 完成真实管理员登录态服务端纵向验收并经人工签核，
> 两处缺口（PC 界面浏览器取证、成果/档案奖惩回读）已如实登记于其 tasks.md 与验收 QA。
>
> 上一轮口径（2026-09-28 更新，保留备查）：**已完成待归档 4 个**（`close-training-material-scope-loop`、
> `implement-m4-pc-outcome-closure`、`close-mobile-learner-outcome-loop`、`improve-m4-certificate-and-mobile-archive`）、
> 未闭环 1 个（`close-training-reward-penalty-loop` T3 真实管理员登录态验收）。
> 其中 `implement-m4-pc-outcome-closure` 8 个 Task 于 2026-09-24 验收认定通过（Task 1 自动化，Task 2～8 真实角色验收
> 已于 2026-09-23 实跑取证），**2026-09-28 双端真实环境复验通过**，缺口仅余「投放后编辑 409 无独立端到端证据」；
> 双轴独立 Review 已于 2026-09-24 在两份收口 QA 中补齐。
> `close-mobile-learner-outcome-loop` Task 1～4 于 2026-09-28 全部完成（Task 4 真实登录态纵向验收实跑通过），Change 闭环。
> `improve-m4-certificate-and-mobile-archive` Task 1～3 的开发、自动化与真实登录态验收均已完成，Change 闭环。
>
> 移动端 M4 缺口（学员成果在线查看）：已开 Change `2026-09-24-close-mobile-learner-outcome-loop`，
> 2026-09-24 人工确认为「**仅在线查看、不导出 PDF**」。**不向学员开放 `archive:training:list`** 的决策已记录：
> 该权限共门控 6 个管理端端点（`ArchiveController`：期次详情/文件、企业清单、个人档案/文件、从计划建档），
> 且 `ArchiveServiceImpl.getPerson(userId, ...)` 无「本人」收口、租户隔离只收窄到同企业，开放即等于学员可查他人档案与全员成绩；
> 菜单节点本已存在（`us_sys_menu.menu_id=9720`，F 类，父 9702，仅授 `admin`）。若后续确需学员自助导出，
> 应新增「本人收口」端点（属 L4，须人工确认），而非复用管理端权限。
>
> **2026-09-28 闭环**：`2026-09-24-close-mobile-learner-outcome-loop` Task 1～4 全部完成，Task 4 真实登录态纵向验收实跑通过
> （`DEMO/superAdmin` 与 `DEMO/m4-learner-01`）：移动端「个人中心 → 我的证书 → 在线查看（受控 blob 真实渲染 1400×990）/
> 下载（受控端点 200·image/png）」与「个人中心 → 我的培训档案（`测试培训类型 / 共 4 期`）→ 期次详情
> `?trainingParticipantId=7` 只读回放」两条链路端到端通过；学员证书与档案双空态、读他人证书文件 403/9416；
> PC 门户 `/portal/certificates` 空态 + 三筛选 tab 正常，学员访问管理端菜单被守卫拦回 `/portal/class`、档案 API 403/1003，
> 与「不向学员开放 `archive:training:list`」的决策一致。证据 `engineering/qa/2026-09-28-dual-m4-real-environment-acceptance.md`。

> **2026-09-24 培训资料范围收口（Change `2026-09-24-close-training-material-scope-loop`，Task 1～6 全部完成）**：
> 培训资料类型收口为 `GENERAL`/`IMAGE`/`VIDEO`/`EVALUATION`（历史 `REWARD_PENALTY` 经 `V20260924180000` 有损降级为 `GENERAL`，
> `sts.init.schema-version` → `20260924180000`）；档案三视角与归档素材登记统一 `status = 1`，统计同源收敛；
> 档案奖惩表新增「佐证」列（按 `training:reward:list` 门控，复用既有受控端点，PDF 不内嵌）；
> 新增**学员侧只读端点**（列表 + 受控文件，只本人/只 `GENERAL`/只启用，**响应不含 `fileInfoId`**，
> 他人与不存在参训统一 8155 不泄漏存在性）；PC 门户与移动端新增「培训资料」区块，**只预览不下载**。
> **零权限变更**（沿用 `training:learner:task`，未向学员发放 `training:attachment:list`）。
> 自动化：后端真实编译 1033 源文件、定向零 DB **73/73**、Testcontainers MySQL IT **23/23**
> （Flyway 9 / 档案 6 / 资料 5 / 学员工作流 3）；PC & 移动端 typecheck + 定向 ESLint + H5 build 通过；
> PC 静态契约 12/12、移动端 6/6；`openspec validate --strict` 通过。
> 真实登录态纵向验收：学员侧 13 组 HTTP 用例逐条命中（含 md5 与源文件逐字节一致），
> 移动端 PDF/图片预览、门户在线预览、档案奖惩佐证预览、工作台类型下拉只剩 4 项；
> **管理端 UI 全链路 9 步**（上传 → 档案含 → 停用 → 档案不含 → 启用 → 档案含 → 删除）；
> 学员端停用态页面补证（停用资料不出现 + 全不可见时不渲染区块）。
> 证据 `engineering/qa/2026-09-24-close-training-material-scope-loop.md`、
> Retro `engineering/retro/2026-09-24-close-training-material-scope-loop.md`；
> 截图 `engineering/assets/2026-09-24-ui-{mobile,portal}-material-*.png`（证据已于 2026-09-28 清理）、`...-ui-chain-archive-material-*.png`。
> **未覆盖项已如实登记**：移动端原生分支未真机验证、跨租户拒绝仅有 IT 证据、停用态文件读取仅接口层证据、
> 管理端档案「下载」按钮刻意保留（只约束学员端）。
> **闭环判定**：Task 1～6 完成，四项验收标准逐条有真实登录态证据，可归档；
> 归档需合并 delta 进 `openspec/specs/`（`training-material-scope`、`learner-material-reading` 为新增能力，
> `m4-minimum-closure` 为 MODIFIED），属规格基线变更，**待人工确认后执行**。

### 3.4 综合结果、档案与证书

| 编号         | 能力                | 前置依赖                               | 当前状态 | 负责人  | OpenSpec Change / Task                                                | 开始日期       | 完成日期       | 验收证据                                                                                                              | 阻塞项 / 下一步                         | 行更新时间      |
|------------|-------------------|------------------------------------|------|------|-----------------------------------------------------------------------|------------|------------|-------------------------------------------------------------------------------------------------------------------|-----------------------------------|------------|
| RESULT-01  | 问卷定义、版本、投放、作答和统计  | M3、BASE-03                         | 已完成  | lims | 已归档 `archive/2026-09-20-2026-09-20-realign-m4-minimum-closure` Task 2 | 2026-09-20 | 2026-09-20 | 复制/PC·手机预览/投放用途与可见性/填写链接与二维码内容/登录提交/统计权限已落地；`docs/api/survey.openapi.json` 已对齐 Controller。零 DB 18 通过              | 前端以 OpenAPI 为准另开                  | 2026-09-20 |
| RESULT-02  | 专家查课、人员评价和课程质量评价  | TRAIN-03、M3                        | 已完成  | lims | 同上 Task 4                                                             | 2026-09-20 | 2026-09-20 | 表单入口仅 COMPOSITE/EXPERT_INSPECTION；专家提交与计划内只读查询保留；通用评价入口已删除                                                        | 前端以 OpenAPI 为准另开                  | 2026-09-20 |
| RESULT-03  | 综合成绩来源、计算批次和正式结果  | RESULT-02、M3                       | 已完成  | lims | 同上 Task 3                                                             | 2026-09-20 | 2026-09-20 | 自动综合分+奖惩刷新；`GET /training/plans/{id}/ended-result`；缺来源 INCOMPLETE 不补零；计算批次公开 API 已删除                              | 前端以 OpenAPI 为准另开                  | 2026-09-20 |
| ARCHIVE-01 | 个人、机构和期次档案及归档版本   | RESULT-03                          | 已完成  | lims / fxw11 | `2026-09-21-remove-archive-attendance-prerequisite` Task 1～3（2026-09-24 收口并归档） | 2026-09-20 | 2026-09-21 | 仅 ENDED 且结果完整可归档；`ATTENDANCE` 伪前置已删除；`sts_archive_version.file_info_id` 经 migration、init schema、Entity 和服务回读同步，正式文件端点使用原始受控文件。定向单测 3/3 通过。**2026-09-24 收口**：Task 3 要求的双轴 Review 与真实环境验收已由档案闭环实跑覆盖（真实对象存储生成/下载 PDF 正本、结果不完整 409/9204、管理员浏览器下载、`ArchiveServiceIT` 5/5 含文件 ID 回读断言），证据见 `engineering/qa/2026-09-24-close-training-archive-loop.md`；**该 Change 已归档**（其 spec delta `m4-minimum-closure` MODIFIED 2 条早已并入基线，归档时用 `--skip-specs` 避免仅措辞换行的无意义改写）。 | 2026-09-24 增量：档案事实扩为 22 个顶层属性、签到由线下成绩派生三态、一期与个人档案文件改为 PDF 正本（9211）；原遗留「PDF 内嵌证书编号与持证人 ID」已收口为「只呈现数量」；代码与自动化全绿，**真实登录态纵向验收已于 2026-09-24 完成**（三视角 22 字段一致、PDF 正本与历史 JSON 重归档、浏览器渲染通过，证据见 `engineering/qa/2026-09-24-close-training-archive-loop.md`）。剩余：证书清单「无权」分支、跨租户拒绝、个人档案 PDF 成功路径、档案素材真实登记未覆盖（已登记，见 QA） | 2026-09-24 |
| CERT-01    | 证书模板、版本和签发规则      | M2 或 RESULT-03                     | 已完成  | lims | `2026-09-21-complete-certificate-management-workflow` Task 1～2        | 2026-09-20 | 2026-09-21 | 模板详情/草稿保存/不可变发布/真实底图样式渲染；规则详情/更新/统一来源候选；新编辑权限与 migration；零 DB 18/18、证书 MySQL IT 10/10、定向 Flyway/Admin-init IT 2/2 | 前端以 OpenAPI 为准另开                  | 2026-09-21 |
| CERT-02    | 证书签发、查看下载、撤销和人工重发 | CERT-01                            | 已完成  | lims | 同上 Task 6                                                             | 2026-09-20 | 2026-09-20 | 达标自动签发真实 PNG；幂等；管理员查询下载；`/certificate/my-certificates` 仅本人；撤销/重发入口已删除                                             | 前端以 OpenAPI 为准另开                  | 2026-09-20 |
| RESULT-04  | 成果端到端验收与 M4 关闭    | RESULT-01～03、ARCHIVE-01、CERT-01～02 | 已完成 | lims / fxw11 | `2026-09-21-implement-m4-pc-outcome-closure` Task 1～8；`2026-09-21-remove-archive-attendance-prerequisite` Task 1～3（2026-09-24 收口并归档） | 2026-09-20 | — | 2026-09-23 复核：PC M4 定向测试 7/7、typecheck 退出码 0、档案后端单测 5/5、菜单一致性新增用例通过（缺失清单已无 archive/certificate/survey）；真实环境取得 `/training/plans/{id}/ended-result` 的 ENDED 完整事实与 RUNNING 409/8123。QA `engineering/qa/2026-09-23-complete-m4-minimum-ui-closure.md`；另修复成果页侧栏整条消失（模块归属回退，Red 验证通过）。2026-09-23 收口轮补齐 Task 4 证书全链：上传底图 → 设计/发布模板 v3 → 绑定课程/理论考试 → 达标自动幂等签发（证书 3/4，持有人 user 1）→ 管理员预览下载 → 持有人本人查看 → 他人 403/9416；另修复坐标字段运行时 JSON 名与契约不一致（`xPosition` 被静默丢弃）导致的设计保存 400；后端 MySQL IT 全绿（`FlywayMigrationIT` 7/7、`ArchiveServiceIT` 3/3、`AdminInitFlywayIT` 1/1）。 | Task 1～4 全部已完成；双轴 Review 已于 2026-09-24 补齐（见 `engineering/qa/2026-09-24-close-training-archive-loop.md` 与 `...material-loop.md`）。2026-09-24 另有三项 M4 收口（档案事实、资料/影像闭环、证书签发来源）代码与自动化验证完成，且两项**真实登录态纵向验收已于同日实跑通过**（资料闭环与档案闭环，证据见对应 QA 与 `engineering/assets/2026-09-24-ui-archive-*.png`（证据已于 2026-09-28 清理））；剩余未覆盖面已如实登记。移动端 M4 缺口另开 Change `2026-09-24-close-mobile-learner-outcome-loop`（已人工确认，边界为「只做在线查看、不导出 PDF」，不开放 `archive:training:list` 给学员） | 2026-09-24 |

### 3.5 运营分析、监管与预警

| 编号     | 能力              | 前置依赖             | 当前状态 | 负责人  | OpenSpec Change / Task                                                | 开始日期       | 完成日期       | 验收证据                                                                                      | 阻塞项 / 下一步        | 行更新时间      |
|--------|-----------------|------------------|------|------|-----------------------------------------------------------------------|------------|------------|-------------------------------------------------------------------------------------------|------------------|------------|
| OPS-01 | 企业管理员工作台        | M3、M4            | 后端完成 | lims | 已归档 `archive/2026-09-20-2026-09-20-realign-m5-minimum-closure` Task 5 | 2026-09-20 | 2026-09-20 | 仅 `GET /ops/workbench/enterprise`：权限化入口、真实概览、分类待办可加总；教师/学员工作台已删                           | 前端以 OpenAPI 为准另开 | 2026-09-20 |
| OPS-02 | 课程、考试、培训和综合结果统计 | M3、M4            | 已收口  | lims | 同上 Task 1/6                                                           | 2026-09-20 | 2026-09-20 | 中山50超范围：`/ops/stats*` 与通用 drilldown 已删除                                                   | 不作为当前产品入口        | 2026-09-20 |
| OPS-03 | 通用导出任务中心        | OPS-02           | 已收口  | lims | 同上 Task 1/6                                                           | 2026-09-20 | 2026-09-20 | `sts_ops_export_job` 保留并【暂时隐藏】；监管同步 UTF-8 BOM CSV 见 REG                                   | 不作为当前产品入口        | 2026-09-20 |
| REG-01 | 园区监管工作台与学习统计    | TRAIN-01         | 后端完成 | lims | 同上 Task 3                                                             | 2026-09-20 | 2026-09-20 | 合并 overview+aggregates 为 `/regulation/workbench` 与 learning-stats；培训机构纳入汇总；零分母为 0         | 前端以 OpenAPI 为准另开 | 2026-09-20 |
| REG-02 | 培训计划学时预警自动刷新    | REG-01           | 后端完成 | lims | 同上 Task 4                                                             | 2026-09-20 | 2026-09-20 | 固定规则 `PLAN_HOURS_INSUFFICIENT`；计划保存幂等刷新；规则 CRUD/手工 detect 已删                              | 前端以 OpenAPI 为准另开 | 2026-09-20 |
| REG-03 | 预警待查看/已查看与真实导出  | REG-02           | 后端完成 | lims | 同上 Task 4                                                             | 2026-09-20 | 2026-09-20 | UNREAD/READ/ENDED；详情标记已查看；修正后退出默认列表；CSV 带 BOM。分派/评论/关闭已删                                  | 前端以 OpenAPI 为准另开 | 2026-09-20 |
| REG-04 | 监管端到端验收与 M5 关闭  | OPS-01、REG-01～03 | 已完成  | lims | 同上 Task 7                                                             | 2026-09-20 | 2026-09-20 | 2026-09-20 人工确认后端口径归档；QA `engineering/qa/2026-09-20-realign-m5-minimum-closure.md`；主规格已同步 | 前端以 OpenAPI 为准另开 | 2026-09-20 |

### 3.6 周边与延期能力

| 编号         | 能力                     | 前置依赖         | 当前状态 | 负责人  | OpenSpec Change / Task                                                                                                                                                                                                                   | 开始日期       | 完成日期       | 验收证据                                                                                                                                               | 阻塞项 / 下一步                         | 行更新时间      |
|------------|------------------------|--------------|------|------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|------------|----------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------|------------|
| SUPPORT-01 | 站内消息和主线最小提醒            | 随各业务阶段接入     | 未开始  | 待分配  | 由对应业务 Change 引用                                                                                                                                                                                                                          | —          | —          | 已有数据模型设计                                                                                                                                           | 先支撑任务下发和成绩发布，完整消息中心后置             | 2026-07-24 |
| SUPPORT-02 | 积分、分享、直播增强和复杂导出        | M3 后按价值排序    | 未开始  | 待分配  | 需要时创建独立 Change                                                                                                                                                                                                                           | —          | —          | 部分需求和设计                                                                                                                                            | 不阻塞主线，阶段 8 后逐项评估                  | 2026-07-24 |
| COURSE-02  | 素材转码、派生文件、在线预览、播放和失败恢复 | M5、COURSE-01 | 已完成  | lims | 已归档 `archive/2026-08-27-2026-08-04-implement-asset-transcode-backend`；`archive/2026-08-13-linearize-all-pdf-previews`；`archive/2026-08-27-2026-08-17-support-restricted-pdf-preview`；`2026-08-28-switch-audio-transcode-to-hls` Task 1～2 | 2026-08-04 | 2026-08-28 | 音频成功产物改为 AAC 128kbps 5 秒 HLS，play-info 音频只认 `hls`，遗留 TRANSCODED 返回 `original`；零 DB 与 play-info 定向测试见 QA `2026-08-28-switch-audio-transcode-to-hls` | 前端以 OpenAPI 为准另开播放器对接；不自动重转遗留 MP3 | 2026-08-28 |
| DEFER-01   | 仿真软件、仿真练习和仿真考试         | 独立产品、运行和安全协议 | 延期   | 待分配  | 重新启动时创建独立 Change                                                                                                                                                                                                                         | —          | —          | 需求规格明确本期延期                                                                                                                                         | 本期不设计、不开发、不测试                     | 2026-07-24 |
| DEFER-02   | 人脸识别认证及人脸防作弊           | 独立供应商和合规方案   | 延期   | 待分配  | 重新启动时创建独立 Change                                                                                                                                                                                                                         | —          | —          | 需求规格明确本期延期                                                                                                                                         | 本期不设计、不开发、不测试                     | 2026-07-24 |

## 3.3 2026-08-12 增量记录

| 能力 | 状态 | OpenSpec Change / Task | 验收证据 | 阻塞项 | 行更新时间 |
|---|---|---|---|---|---|
| PC Web 浏览器标签页登录会话隔离 | 已完成 | `2026-08-12-isolate-browser-tab-sessions` Task 1 | `engineering/qa/2026-08-12-browser-tab-session-isolation.md`；会话隔离测试通过；PC build 通过 | typecheck、lint 和部分全量合约测试存在既有失败 | 2026-08-12 |
| PC Web 持久化且隔离的浏览器登录会话 | 已完成 | `2026-08-12-persist-isolated-browser-sessions` Task 1 | `engineering/qa/2026-08-12-persist-isolated-browser-sessions.md`；会话槽位测试 2/2；改动文件 ESLint；PC build | 全工程 typecheck 存在既有 TS5103 配置失败 | 2026-08-12 |

| 移动端学员课程学习与多格式素材预览 | 初步版本完成，自动化与真实环境验收收尾 | `2026-08-12-implement-mobile-learner-course-learning` Task 1～7 | PC 定向测试 10/10；移动端定向测试 11/11；两端 typecheck/lint/build；真实环境测试完成初步版本；QA/Retro 记录 | 无当前阻塞，后续根据真实使用反馈优化 | 2026-08-14 |
| 移动端课程目录与独立素材学习界面调整 | 初步版本完成，自动化与真实环境验收收尾 | `2026-08-13-adjust-mobile-learning-task-page` Task 1 | 移动端定向测试 11/11；typecheck、lint、H5 build；课程详情移除素材预览，新增独立学习页与当前目录高亮；真实环境测试完成初步版本 | 无当前阻塞，后续根据真实使用反馈优化 | 2026-08-14 |
| PC/H5 PDF 单页渐进预览 | 待验收（Task 3 已完成） | `2026-08-14-optimize-pdf-progressive-viewer` Task 1～3 | 共享控制器
TDD；4 个前端定向测试文件；后端 PDF 定向 16/16；两端定向 lint/typecheck；PC build；生产链 648 页样本通过 PDFBox/qpdf 与 2
MB/3 MB/32 次 Range 门槛；QA/Retro 与架构记录 | 待真实登录态 Chrome/H5 Network 与视觉验收；移动 H5 build 受既有 Uni/Pinia
传递依赖解析问题阻塞 | 2026-08-17 |

## 3.3 PC Web 全局界面统一

| 能力 | 当前状态 | OpenSpec Change / Task | 验收证据 | 阻塞项 / 下一步 | 行更新时间 |
| --- | --- | --- | --- | --- | --- |
| PC Web 后台组件与门户 layout 统一 | 已完成 | `2026-08-14-unify-pc-web-layout-style` Task 1-3 | `docs/design/pc-web-ui-standard.md`；`engineering/qa/2026-08-14-unify-pc-web-layout-style.md`；UI 标准测试 4/4；PC Web build 通过 | 后台真实视觉交互需在可登录环境补验 | 2026-08-14 |

## 3.4 2026-08-19 增量记录

| 能力                         | 状态  | OpenSpec Change / Task                                                                              | 验收证据                                                                                                                           | 阻塞项 / 下一步                                                   | 行更新时间      |
|----------------------------|-----|-----------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|------------|
| PC Web 考试补考管理与成绩状态展示       | 已完成 | `2026-08-19-complete-exam-retake-management` Task 1～3                                               | 策略配置、成绩处置开补考与枚举翻译定向测试 28/28；PC Web typecheck；受影响生产源码 ESLint；QA/Retro                                                           | 真实环境联调待提供非生产环境和角色测试账号                                       | 2026-08-19 |
| PC Web 考试工作台步骤控制与试卷规则默认值   | 已完成 | `2026-08-19-refine-exam-workspace-navigation-and-paper-defaults` Task 1～2                           | 定向自动化测试 5/5；PC Web typecheck；受影响生产源码 ESLint；`engineering/qa/2026-08-19-refine-exam-workspace-navigation-and-paper-defaults.md` | 需在非生产环境验证实际版本详情回填                                           | 2026-08-19 |
| PC Web 考试成绩发布锁定、工作台页签与信息展示 | 已完成 | 已归档 `archive/2026-08-27-2026-08-20-fix-exam-publish-lock-and-workbench-tabs` Task 1～3               | 后端批阅锁定目标测试 20/20；前端定向回归 9/9；PC Web typecheck、受影响 ESLint；QA/Retro                                                               | 真实非生产环境需补验发布后批阅只读；后端全量转码测试受本机 ffmpeg/qpdf 环境阻塞              | 2026-08-20 |
| PC Web 考试成绩处置与补考资格展示       | 已完成 | 已归档 `archive/2026-08-27-2026-08-20-redesign-exam-score-disposition-and-retake-eligibility` Task 1～3 | 后端目标测试 26/26；前端定向回归 6/6；PC Web typecheck、受影响 ESLint；OpenAPI JSON 校验；QA/Retro                                                   | 真实非生产环境需补验教师核对、发布与开补考；全量 PC lint 受既有 PdfViewer DOM 类型规则错误阻断 | 2026-08-20 |

## 3.5 2026-08-26 增量记录

| 能力          | 状态  | OpenSpec Change / Task                                                    | 验收证据                                                                                                | 阻塞项                                   | 行更新时间      |
|-------------|-----|---------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|---------------------------------------|------------|
| 素材转码空产物成功防护 | 已完成 | 已归档 `archive/2026-08-27-2026-08-26-reject-empty-transcode-success` Task 1 | Stub 使用 `PROVIDER_DISABLED` 明确失败；视频必须具有 HLS+THUMBNAIL；目标测试 11/11、Admin 零 DB 691（0 失败、1 跳过）；QA/Retro | 线上既有伪成功数据未处理；大文件真实失败原因待部署后依据新失败记录继续诊断 | 2026-08-26 |

## 3.6 2026-08-27 增量记录

| 能力                | 状态  | OpenSpec Change / Task                                                          | 验收证据                                                                                                                                            | 阻塞项 / 下一步                | 行更新时间      |
|-------------------|-----|---------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------|------------|
| 课程封面上传、保存与受控查看/下载 | 已完成 | 已归档 `archive/2026-08-27-2026-08-27-complete-course-cover-api` Task 1            | `course.openapi.json`、`system-admin.openapi.json`；Admin 零 DB 运行 718（0 失败、1 跳过）；MySQL IT 192/192；QA/Retro                                        | 前端以 OpenAPI 为准另开；旧对象不迁移  | 2026-08-27 |
| 新文件对象统一增加机构编码根目录  | 已完成 | 已归档 `archive/2026-08-27-2026-08-27-prefix-file-objects-with-tenant-code` Task 1 | 新对象 `{ORG_CODE}/...`；历史键兼容读取；`system-admin.openapi.json`/`course.openapi.json`；目标零 DB 54/54；Admin 零 DB 727（0 失败、1 跳过）；MySQL IT 193/193；QA/Retro | 前端以 OpenAPI 为准另开；历史对象不迁移 | 2026-08-27 |
| PC Web 草稿考试人员配置契约同步 | 已完成 | `2026-08-27-sync-draft-exam-personnel-configuration` Task 1 | 草稿空阅卷人、空数组清空与发布后人员保留定向测试 3/3；PC Web typecheck；QA/Retro | 真实非生产环境需补验草稿空态与清空后保存 | 2026-08-27 |

## 3.7 2026-08-28 增量记录

| 能力 | 状态 | 负责人 | OpenSpec Change / Task | 开始日期 | 完成日期 | 验收证据 | 阻塞项 / 下一步 | 行更新时间 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 移动端课程题库练习与课程测验闭环 | 已完成 | Codex | `2026-08-28-complete-mobile-course-assessment` Task 1 | 2026-08-28 | 2026-08-28 | `course.openapi.json` 状态与错误码同步；课程域作答/结果页；移动端定向测试 15/15、typecheck、受影响 ESLint、H5 build；QA/Retro 与双轴 Review | 真实非生产环境需以学员账号验证课程域接口和主观题批阅后的自动生效 | 2026-08-28 |
| 移动端正式考试考前须知与单向开考 | 已完成 | Codex | `2026-08-28-add-mobile-exam-notice-before-start` Task 1～2 | 2026-08-28 | 2026-08-28 | 移动端须知页、既有考试详情与开考接口复用；移动端定向测试 20/20、typecheck、受影响 ESLint、H5 build；QA/Retro 与双轴 Review | 已确认学员角色具备既有 `exam:exam:list`；待真实非生产环境完成须知到开考的联调 | 2026-08-28 |

## 3.8 2026-09-07 增量记录

| 能力 | 状态 | 负责人 | OpenSpec Change / Task | 开始日期 | 完成日期 | 验收证据 | 阻塞项 / 下一步 | 行更新时间 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 题库、试卷、考试 PC Web 真实接口联调 | 已完成 | fxw11 | M2 题库、试卷、考试前端真实接口联调 | 2026-09-07 | 2026-09-07 | 题库、试卷、考试页面已按真实接口完成联调；考试定义、组卷、考生/阅卷人配置、作答、批阅、成绩发布和课程考试项回接已对齐 OpenAPI。 | 当前无真实联调阻塞；后续按业务反馈优化 | 2026-09-07 |
| 培训模块 PC Web 真实接口联调 | 进行中 | fxw11 | `2026-09-04-prevent-invalid-training-rules`；`2026-09-07-gate-training-start-and-learner-visibility`；培训类型、标准、模板、计划、参训人员、执行配置和发布预检链路 | 2026-09-07 | — | 已完成培训编排前端联动、参训人员批量绑定、规则前置校验和发布预检提示；已收紧“全部内容安排后开始”与学员仅执行中/结束可见的状态门禁，自动化通过。 | 真实登录态完成 4/5 安排失败不可开始且学员不可见、5/5 安排成功后学员可执行、结束仅查结果、取消不可见验收 | 2026-09-07 |

## 3.9 2026-09-11 增量记录

| 能力                  | 状态  | 负责人  | OpenSpec Change / Task                                          | 开始日期       | 完成日期       | 验收证据                                                                                                                                                                                                                     | 阻塞项 / 下一步                                  | 行更新时间      |
|---------------------|-----|------|-----------------------------------------------------------------|------------|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------|------------|
| 培训题库练习抽题、冻结与不评分结果闭环 | 已完成 | lims | `redesign-training-question-bank-practice-closure` Task 1.1～3.1 | 2026-09-11 | 2026-09-11 | 公共候选池与最大匹配引擎；计划池/执行池/Attempt 三层冻结；不评分完成及反馈摘要投影；migration 至 `20260911152000`；Training/Exam OpenAPI；零 DB 182/182、Admin MySQL IT 8/8、Admin-init IT 1/1；QA `engineering/qa/2026-09-11-training-question-practice-closure.md` | 前端以 OpenAPI 另开；共享 VPN 环境 HTTP 登录态联调待可连接时执行 | 2026-09-11 |

## 3.10 2026-09-15 增量记录

| 能力 | 当前状态 | 负责人 | OpenSpec Change / Task | 开始日期 | 完成日期 | 验收证据 | 阻塞项 / 下一步 | 行更新时间 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PC Web 计划、课程与考试时间范围录入统一 | 已完成 | fxw11 | `2026-09-15-unify-schedule-time-range-input` Task 1 | 2026-09-15 | 2026-09-15 | 范围控件定向回归 2/2、PC typecheck、受影响 ESLint、`git diff --check`；QA/Retro | 待非生产环境真实登录态核对计划冻结态与五类表单请求载荷；聚合既有回归 14 项无关失败已记录 QA | 2026-09-15 |
| 综合成绩来源可见、空来源保护与结果抽屉优化 | 已完成 | fxw11 | `2026-09-15-fix-composite-score-source-visibility` Task 1～2 | 2026-09-15 | 2026-09-15 | 计算服务 6/6、结果同步 29/29、PC Node 19/19、typecheck、目标 ESLint、OpenSpec 严格校验；QA/Retro | 待非生产环境核对历史执行中计划的受控计分配置修复与重新计算，不自动改生产数据 | 2026-09-15 |

## 3.11 2026-09-17 增量记录

| 能力 | 当前状态 | 负责人 | OpenSpec Change / Task | 开始日期 | 完成日期 | 验收证据 | 阻塞项 / 下一步 | 行更新时间 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 课程学习进度上报闭环与完成阈值生效 | 已完成 | fxw11 | `2026-09-17-close-course-learning-progress-loop` Task 1～4 | 2026-09-17 | 2026-09-17 | 后端定向单测 32/32（`CourseLearningServiceImplTest` 21 + `CourseItemServiceImplTest` 11）；pc-web/mobile typecheck + lint 0 警告、H5 build 通过；真实端到端（真实 Chrome + 后端 20080 + 团队测试库）：文档类内容项心跳 30/30/29，移动端 uni H5 心跳 30/29（`device_type=H5`），切标签页隐藏窗口 60 秒有效秒数 0（`sts_course_study_session` 205～208 墙钟 30/60/90/120 对有效 30/10/0/19），阈值三级解析判定矩阵 A～D 全中，`PUT material-rule` 不带 `durationMode` → 200 且落 `AUTO`；`openspec validate --strict` 通过；QA/Retro `engineering/qa/2026-09-17-close-course-learning-progress-loop.md`、`engineering/retro/2026-09-17-close-course-learning-progress-loop.md` | 媒体暂停不计入（spec Scenario）仅有代码守卫：环境无视频素材与 HLS 派生产物，需人工确认是否构造 HLS 资产做端到端；防快进/倍速限制/离页暂停/水印为延期项；PC 与移动端「可计时媒体」判定口径不一致（`format==='hls'` vs `assetType∈{VIDEO,AUDIO}`）建议后续统一 | 2026-09-17 |

## 3.12 2026-09-18 增量记录

| 能力 | 当前状态 | 负责人 | OpenSpec Change / Task | 开始日期 | 完成日期 | 验收证据 | 阻塞项 / 下一步 | 行更新时间 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 填空题按空位作答与跨端答案项统一 | 待真实验收 | Codex | `2026-09-18-fix-fill-blank-answering` Task 1～4 | 2026-09-18 | 2026-09-18 | `ExamLearnerServiceImplTest` 23/23；PC/移动 typecheck；跨端定向回归 4/4；OpenAPI JSON、OpenSpec 严格校验、差异检查通过；QA/Retro | 需以真实多空题在 PC/H5 完成加载、暂存、恢复、交卷验收；移动课程考核页存在既有 ESLint `Event` 全局声明问题 | 2026-09-18 |
| 培训课程正式成绩闭环（培训绑定且已完成课程汇总测验正式分） | 待真实验收 | fxw11 | `2026-09-18-close-training-course-score-loop` Task 1～3 | 2026-09-18 | 2026-09-18 | 定向零 DB 91/91（`CourseLearningServiceImplTest` / `TrainingCourseScoreCalculatorTest` / `TrainingWorkflowServiceImplTest` / `TrainingResultSyncServiceImplTest`）；OpenSpec strict、`git diff --check`、QA/Retro 通过 | `TrainingMixedPlanLifecycleIT` 因 Docker Hub 拉取 `testcontainers/ryuk` 超时未进入业务断言（未伪报通过）；真实登录态与历史补账验收待执行 | 2026-09-18 |
| 教学任务菜单与门户培训列表权限节点同步 | 已完成 | fxw11 | `2026-09-18-sync-teacher-task-menu` T1～T2 | 2026-09-18 | 2026-09-18 | `MenuPermissionConsistencyTest` 2/2、`FlywayMigrationResourcesTest` 1/1、MySQL Testcontainers `FlywayMigrationIT#shouldRepairExistingTeacherTaskMenuAndAddLearnerTrainingPermission` 1/1（含重复执行幂等）；两个 JSON 解析与 `git diff --check` 通过 | 已部署库需执行 `V20260918154500__sync_teacher_task_menu.sql`（`main.flyway.enabled=false` 时不自动跑，需手工执行） | 2026-09-18 |
| 培训计划列表补培训时间、地点与创建时间 | 已完成 | fxw11 | `2026-09-18-enrich-training-plan-list-display` T1 | 2026-09-18 | 2026-09-18 | PC 定向 Node 6/6、目标 ESLint、`pnpm typecheck`、QA/Retro | 无 | 2026-09-18 |
| 题库练习与线下考试流程统一（不产生及格结论、不要求批阅人） | 待真实验收 | fxw11 | `2026-09-18-unify-question-bank-practice-review-flow` T1～T4 | 2026-09-18 | 2026-09-18 | 后端定向单测（`AssessmentExecutionServiceImplTest` / `ExamAttemptServiceImplTest` / `ExamLearnerServiceImplTest` / `ExamServiceImplTest` / `OfflineScoreServiceTest`）、PC 定向 Node、PC/移动 `vue-tsc`、ESLint、OpenAPI JSON、`git diff --check` | 抽题规则弹窗与「无正式分数」新口径只在 mock 与单测层闭环，未做真实环境点击验收；历史题库练习作答仍带旧总分/及格线快照，未做数据迁移（按旧语义展示） | 2026-09-18 |
| 草稿考试发布允许刷新执行快照（原返回 7003） | 待真实验收 | fxw11 | `2026-09-18-fix-draft-exam-publish-refresh` | 2026-09-18 | 2026-09-18 | `AssessmentExecutionServiceImplTest` 新增「已配置人员仍刷新成功」回归（`assertExecutionUnused` → `assertExecutionRefreshable`）；`DraftExamParticipantLifecycleIT` 同步为刷新/删除/唯一键复用 | tasks 最后一项「部署环境下题库练习真实发布验收」未勾选 | 2026-09-18 |
| 考试列表与详情回显试卷版本与题库名称 | 已完成 | fxw11 | `2026-09-18-display-exam-resource-names` T1～T2 | 2026-09-18 | 2026-09-18 | `ExamServiceImplTest` 45/45；PC 定向 Node 名称回显断言；`pnpm typecheck`、目标 ESLint、`git diff --check` | 无（顺带修掉 `ExamInformationPanel`/`ExamDefinitionForm` 未声明事件透传，已补回归断言） | 2026-09-18 |
| 管理端工作台 ws-* 视觉规范 + 门户三中心 / 移动端三页 / 批阅页与成绩处置页重排 | 已完成（仅展示层） | fxw11 | 无 OpenSpec Change（L1/L2 展示层重构） | 2026-09-18 | 2026-09-18 | `vue-tsc` exit 0、改动文件 ESLint 0 error；真实渲染（Chrome + vite 5173 + 后端 20080）：门户考试卡内联色值 0、课程学习页 chips=3 / 无重复 `ElAlert` / 滚动辅助工具 4 按钮、一键到底 `scrollTop=43430`；处置页 DOM 克隆施压 18/18（摘要与动作区高度在 1 行/31 行流水下完全相等）、批阅页 42/42；全量 Node 394 例零新增失败；移动端真实渲染（uni H5 5174）筛选取值与可报名课程选课通过 | 批阅页对 `EFFECTIVE` 判为「成绩已发布」而整页只读，属既有语义、观感似矛盾，需与相关会话确认后再动；真实点击未覆盖全部改动页面 | 2026-09-18 |

## 3.13 2026-09-20 增量记录

| 能力               | 当前状态 | 负责人          | OpenSpec Change / Task                                         | 开始日期       | 完成日期       | 验收证据                                                                                                                               | 阻塞项 / 下一步                                                    | 行更新时间      |
|------------------|------|--------------|----------------------------------------------------------------|------------|------------|------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|------------|
| 延期考试策略列统一标注为暂时隐藏 | 已完成  | lims / Codex | `2026-09-20-close-standalone-exam-anticheat-and-visibility` T3 | 2026-09-20 | 2026-09-20 | 11 列保留并同步 init SQL、`V20260920200000`、schema version；OpenSpec strict、Flyway 资源测试、MySQL 8.0.45 临时库实执行、`git diff --check` 通过；QA/Retro | Testcontainers 未识别本机 Docker，自动化 `FlywayMigrationIT` 待环境可用时补跑 | 2026-09-20 |

## 3.14 2026-09-21 增量记录

| 能力                     | 当前状态 | 负责人        | OpenSpec Change / Task                                         | 开始日期       | 完成日期       | 验收证据                                                                                                                                                                                                      | 阻塞项 / 下一步                                                 | 行更新日期      |
|------------------------|------|------------|----------------------------------------------------------------|------------|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------|------------|
| 课程内容项材料学习规则最小化闭环       | 已完成  | fxw11 / 文祥 | `2026-09-20-simplify-course-item-learning-rule` T1～T4          | 2026-09-20 | 2026-09-21 | 后端定向单测 42/42；PC 定向测试 44/44；OpenSpec strict；文祥确认已在真实环境完成验收；QA `engineering/qa/2026-09-21-simplify-course-item-learning-rule.md`；Retro `engineering/retro/2026-09-21-simplify-course-item-learning-rule.md` | Testcontainers 迁移 IT 未在本机 Docker 环境运行，需在可用 CI/Docker 环境补跑 | 2026-09-21 |
| M4/M5 OpenAPI 机器可读契约补齐 | 已完成  | lims       | `2026-09-21-complete-m4-m5-openapi-contracts` Task 1           | 2026-09-21 | 2026-09-21 | 五份契约修复；Controller-operation 14/3/14/1/15 零差异；18 个目标 DTO/VO 字段零差异；悬空 `$ref` 0；证书隐藏字段按 TDD 从公开模型删除，定向测试 7/7；OpenSpec strict、JSON 解析、`git diff --check` 通过；QA/Retro                                          | 前端以修复后的 OpenAPI 为准另开消费与联调                                 | 2026-09-21 |
| 证书模板设计与签发规则管理闭环        | 已完成  | lims       | `2026-09-21-complete-certificate-management-workflow` Task 1～2 | 2026-09-21 | 2026-09-21 | 模板设计草稿、发布历史、真实 PNG 样式、引用删除保护；规则详情/更新/统一来源候选；`certificate:rule:edit` migration；零 DB 18/18、证书 MySQL IT 10/10、定向 Flyway/Admin-init IT 2/2；OpenAPI 17 operations；QA/Retro                                     | 前端另开；档案列表、本人档案和 ATTENDANCE 事实另开 Change                    | 2026-09-21 |
| M4 PC 培训成果闭环 | 已完成 | fxw11 | `2026-09-21-implement-m4-pc-outcome-closure` Task 1～8；`2026-09-21-remove-archive-attendance-prerequisite` Task 1～3（2026-09-24 收口并归档） | 2026-09-21 | — | 问卷、结束成果、专家查课、档案、证书与本人证书按当前 OpenAPI 接入；2026-09-23 `2026-09-23-complete-m4-minimum-ui-closure` 补齐 M4 正式菜单节点与个人档案文件端点；PC typecheck 退出码 0、M4 测试 7/7、档案单测 5/5、菜单一致性新增用例通过；QA/Retro 已记录；2026-09-23 另修复成果页侧栏整条消失（`findAuthorizedAdminModule` 增加按菜单树的归属回退），跨模块对照回归通过 | 四类真实角色验收已通过（管理员/填写人/专家/学员）：问卷真实提交与二次提交 409/9008、专家查课提交与管理员回看、一期档案真实下载 200/768B、门户「我的证书」入口；Task 4 证书全链已在真实库跑通（模板 5/v3、规则 4/5、证书 3/4，幂等、下载、本人查看、他人 403），Task 1～4 全部完成；双轴独立 Review 已于 2026-09-24 在两份收口 QA 中补齐；**2026-09-28 双端真实环境复验通过**（已签发证书 3 行、证书模板 2 行、培训档案三视角、问卷管理 3 行、plan 4 已结束成果 69.25/通过率 100%/结果不完整 0、plan 4 一期档案「可下载·归档事实全部完整」+ 文件 200·application/pdf·219827B；QA `engineering/qa/2026-09-28-dual-m4-real-environment-acceptance.md`） | 2026-09-28 |

> 2026-09-28 M4 双端展示增量：`2026-09-28-improve-m4-certificate-and-mobile-archive` Task 1～3 开发与自动化验证完成；证书来源名称、受控图片、移动端档案文案已调整。真实登录态验收已于 2026-09-28 双端实跑通过（移动端证书卡受控图片真实渲染 1400×990、下载捕获 200·image/png、档案分期 `测试培训类型 / 共 4 期` 与期次跳转 `participantId=7`；PC 管理员已签发证书 3 行、证书模板 2 行、门户「我的证书」空态与三筛选 tab），**增量闭环**；原生端（App/小程序 `openDocument` 分支）仍未真机验证。详见 `engineering/qa/2026-09-28-dual-m4-real-environment-acceptance.md` 与 `engineering/qa/2026-09-28-improve-m4-certificate-and-mobile-archive.md`。

> 2026-09-29 COURSE-08 补漏：已归档 M1 前端 Task 3 的移动端课程评价入口遗漏，本次补齐已完成课程评价提交与本人回看；定向用例 1/1、移动 typecheck、受影响 lint、H5 build 和 PC typecheck 通过。课程测试文件整体 17/20，3 项本次未修改模块的断言失败（未复测修改前基线）；真实学员登录态未验收。同次开课评价列表的后续实施见下一条记录，详见 `engineering/qa/2026-09-29-mobile-course-evaluation-gap.md`。

> 2026-09-29 同次开课评价补齐：`2026-09-29-show-peer-course-evaluations` 已获确认并完成 Task 1～2 的开发与自动化验证；PC 卡片评价抽屉及移动课程详情均可分页查看同次开课评价，匿名仅显示日期。后端定向单测 42/42、双端新增定向用例及 typecheck/lint/build、OpenSpec strict validate 通过。真实 SQL IT 因 Docker 不可用未跑通；真实双学员登录态由用户手动验收，故尚未标记真实业务闭环。详见 `engineering/qa/2026-09-29-show-peer-course-evaluations.md`。

> 2026-09-29 M4 专家查课补漏：`2026-09-29-review-expert-inspections-in-archive` Task 1～2 已完成开发与定向自动化验证。已结束计划可只读回看已提交查课；一期档案页面和新生成的 PDF 收录专家逐项意见，个人/企业档案不扩大可见范围。后端档案测试 19/19、PC typecheck、定向 ESLint、OpenAPI JSON 解析通过；真实管理员登录及新 PDF 下载待验，故增量暂不标记闭环。详见 `engineering/qa/2026-09-29-review-expert-inspections-in-archive.md`。

> 2026-09-29 台账收口：上两条增量对应的两个 Change（`2026-09-29-show-peer-course-evaluations`、
> `2026-09-29-review-expert-inspections-in-archive`）经人工决策**按「开发侧完成」归档**，
> 走 `openspec archive --skip-specs -y` 纯目录移动（`diff -rq` 确认 `openspec/specs` 无差异），
> 归档目录累计 **170** 个，活动区 **2 → 0**；`openspec list` → `No active changes found.`、`openspec doctor` → `OpenSpec root: ok`。
> 同时订正 `review-expert-inspections-in-archive/tasks.md` 中两条与 QA 证据矛盾的「管理员真实登录」勾选为**未验证**
> （归档时 CLI 亦报告 `Task status: 2/4 tasks`）。
> **残余缺口**：两者的真实登录态验收仍需补做，补验结果写入各自 QA，不回改已归档目录。
> 另新增 `docs/requirement/l3-l4-business-requirement-checklist.md`（L4 20 条 + L3 六域 + 功能项 1～87 的 Change 覆盖矩阵）。

> 2026-10-08 第三批功能补漏：`2026-10-08-complete-batch3-learner-capabilities` T1～T5开发及定向验证完成，覆盖14/49/60→55→54→58→59（七项）。PC/H5练习错题、独立理论模拟、正式/课程我的考试、课程关注/收藏和既有教师关注均已接入真实接口；OpenAPI、完整init、3个增量及schema-version同步。108条后端目标/原域回归、5条前端运行用例、两端typecheck、新增及受影响入口lint、OpenSpec strict和契约检查通过。7条MySQL目标用例编译通过但Docker daemon不可用。**真实PC/H5多角色验收已完成：56/56接口断言通过（真实登录态、真实库），PC/H5逐页渲染与截图取证，七项（14/49/60/55/54/58/59）已关闭；Change待归档。** 容器内MySQL IT与并发写入为剩余补证项，不阻断业务判定。QA/双轴Review：`engineering/qa/2026-10-08-batch3-learner-capabilities.md`；开发Retro：`engineering/retro/2026-10-08-batch3-learner-capabilities.md`。

## 4. 完成记录填写模板

某行完成时，至少按以下格式填写：

| 字段                     | 填写要求                      | 示例                                  |
|------------------------|---------------------------|-------------------------------------|
| 当前状态                   | 改为 `已完成`                  | 已完成                                 |
| 负责人                    | 填写实际负责人或团队                | 张三 / 课程团队                           |
| OpenSpec Change / Task | 填写已确认且实际执行的 Change 和 Task | `2026-08-01-course-learning` Task 2 |
| 开始日期                   | `YYYY-MM-DD`              | 2026-08-02                          |
| 完成日期                   | `YYYY-MM-DD`              | 2026-08-08                          |
| 验收证据                   | 至少包含测试/QA和一个实现真相源         | QA 文档；OpenAPI；migration；自动化测试       |
| 阻塞项 / 下一步              | 完成后写“无”或明确后续非阻塞事项         | 无                                   |
| 行更新时间                  | 本次修改日期                    | 2026-08-08                          |
## 2026-10-08 第三批验收修正补记

用户新增授权修正收藏分类及记录展示，见 `openspec/changes/2026-10-08-complete-batch3-learner-capabilities/tasks.md` T6/T7。关注课程保持课程专属；我的收藏增加题目分类；练习/模拟记录补来源、冻结题干、时间、真实进度与提交统计。开发与目标测试完成（后端19、前端7，两端typecheck/定向lint通过）；无数据库结构变化。新版真实登录QA、独立复审待主线，MySQL IT因Docker不可用阻塞；此前批次关闭记录不代表本次修正已完成新版验收。证据见同批QA新增T6/T7节。

## 2026-10-09 第四批 T2/T3 复核 + 界面统一 + 真实渲染取证

现象：`tasks.md` 记「T2/T3未编码」，但工作区实际已有 ChatGPT 中断前写入的 T2（50 本人资料与改密）与 T3（52 统一学习档案与追加更正）前后端代码，且 20080 上运行的就是这份代码。结论：本轮转为复核 + 界面统一 + 取证，并修正过期标记。

- **静态与定向**：PC/H5 `vue-tsc --noEmit` 均 exit 0；契约测试 3/6/2 全绿；9 个被改前端文件定向 ESLint exit 0。后端 `mvn -pl sts-service-admin -am test -Dtest=...` 39 项 0 失败 `BUILD SUCCESS`。
- **真实 HTTP**：`my-profile`、`learner/history`（83 条）、`learner/messages`（5 条）、`messages/stream`（SSE）、`learner/home/tasks` 全 code=0；负例 1001 / 9205 / 2040 / 400 与 operator01 跨用户 9205 均符合预期。
- **T3 写路径补足取证**：`V20261009100000` 注释明示「不自动绑定任何现有角色」，故 9722/9723 零绑定属设计。临时授权 role 1 + 清 `sts:cache:sys:user:perms::2` 后，对 `admin` 的 `PRACTICE/69` 追加两版（version 0→1→2，原始摘要不回写，留痕 before/after/reason/operator 完整落库），负例版本过期 2040、非法字段 1001、跨领域状态 1001、值未变化 1001、理由空 1001、跨机构 9205 —— **17 PASS / 0 FAIL**。已删除测试账本行与临时绑定、清缓存，复核恢复 1003，dev 库回到改动前状态。
- **界面统一**：PC `user-center/{profile,history,messages}`、`admin/result/archive/components/ArchiveHistoryPanel.vue`、`components/HistoryRecordPanel.vue` 与 H5 `pages/{history,messages,profile-edit}` 改走统一设计系统；新增公共类 `.portal-toolbar`、`.portal-form-card` 与 `MEMBER_TYPE_OPTIONS`。
- **真实渲染**：PC 三页 hero `x=16 / w=1408 / radius=12px / padding=32px`、封面 20+5、chip 20+5、无横向滚动；H5 三页 `heroW=357 / heroTop=56 / radius=14.56px`、真实数据（历史 20 卡、消息 5 条、资料 3 卡 6 输入）、`.learner-input` 盒高 37px、无横向滚动。一键复跑 `bash .workbuddy/tmp/qa-batch4/run-suite.sh`。
- **仍开放**：T2 改密真实交互取证（执行被环境敏感内容策略阻断，按规则未重试，待用户授权）、T2/T3 双轴Review、T1 自身业务关闭条件。→ **上述三项已于同日后续轮次全部解除，见下节**。
- **环境/数据观察（非本批引入）**：`us_sys_user.phone` 中 operator01 行存储值即为掩码 `130****2333`（既有污染，是否复位待决策）；`MenuPermissionConsistencyTest` 2 项失败经复现证实为 HEAD 既有漂移，新权限点在两侧均存在。
- 证据：`engineering/qa/2026-10-09-batch4-t2-t3-and-ui-unification.md`；`.workbuddy/tmp/qa-batch4/`。

## 2026-10-09 第四批 T1 业务关闭 + T2 改密补验 + 整批收口

用户指令：完成阻塞项与遗留项，验证第四批功能项补全已完成，并在补漏清单标记完成项。开发库已恢复（上一轮 T2 改密受阻仅因 `19.191.192.146:8045` 连接超时）。

- **T2 改密真实交互（解除阻塞）**：`DEMO/operator01` 走「改密 → 旧会话立即失效(1002) → 旧密码登录被拒(1002) → 新密码可登录 → 改回原密码 → 复核恢复」，**10 PASS / 0 FAIL**，凭证已还原未留残留。
- **T1 截止窗口（真实调度器）**：`exam 24`（4 名候选）`open_end_time=now-1h` 时真实扫描**不提醒**（0 条）；改 `now+12h` 后生成 **4 条** `FORMAL_EXAM:{46,47,48,49}:null:deadline:<ts>`；再等一轮仍 4 条 —— **4 PASS / 0 FAIL**，回滚后时间窗还原 `NULL`。
- **T1 培训生命周期（隔离样本 + 真实 HTTP）**：新建临时计划(PUBLISHED)+参训人(operator01)，真实 `POST /training/plans/{id}/cancel` → `CANCELLED`、`TRAINING:<pid>:null:cancelled` 唯一持久化、正文「培训已取消」，重复提交 **8164** 不重复投递，学员 `GET /learner/messages` 仍可读，**打开已失效培训** `GET /training/learner/participants/{id}` → **HTTP 404 / 8155「培训任务对当前学员不可见」** —— **12 PASS / 0 FAIL**，样本全部清理。
- **T1 课程上下架（真实 HTTP）**：`course 2` 真实 `PUT /course/courses/2/offline` → `OFFLINE`、生成 `COURSE_CATALOG「课程已下架」`、事件键 `catalog:2:OFFLINE:2:2:PUBLISHED:2:1`、接收人恰为该课程 4 名报名受众 —— **9 PASS / 0 FAIL**，课程状态与消息均还原。
- **整批最终回归**：PC/H5 `vue-tsc` exit 0；契约测试 3/6/2；9 文件定向 ESLint exit 0；后端定向 **39 项 0 失败 `BUILD SUCCESS`**；两端重建 exit 0；**最终真实 HTTP 全端点回归 16 PASS / 0 FAIL**；PC 三页 / H5 三页真实渲染几何与结构均符合统一设计系统（无横向滚动、无裸色值、`.learner-input` 盒高 37px）。
- **遗留项处置**：更早会话遗留的 `frontend/{pc-web,mobile-app}/vite.batch4-qa.config.ts`（代理指向已失效 20082、无引用）移出源码树至 `.workbuddy/tmp/qa-batch4/legacy/`，源码树已无临时构建配置。
- **补漏清单标记**：47（学习首页与系统消息）、52（个人学习档案与历史追溯）、61（消息提醒中心）标记完成；**50（个人中心）因微信子项随 46 保留不勾选**，其非微信子项全部取证。中山已关闭 3 项、裕龙已关闭 7 项。
- 证据：`engineering/qa/2026-10-09-batch4-t1-closure-and-final-verification.md`；脚本与截图 `.workbuddy/tmp/qa-batch4/`（`t1-deadline-acceptance.sh`、`t1-training-lifecycle-acceptance.sh`+`t1-training-http.mjs`、`t1-course-catalog-acceptance.sh`+`t1-course-catalog-http.mjs`、`t2-password-acceptance.mjs`、`t-final-smoke.mjs`、`run-render.sh`）。

## 2026-10-10 第五批业务统计开发交付（待真实验收）

已确认 Change `2026-10-09-complete-batch5-business-statistics`，T1→T5 依序实施功能项 18→25→37→41→77：考情及 XLSX、课程分类/个人/知识点及 PNG、线下课堂评分分析、部门班级线上/线下分析、计划进度与全部课程培训记录。均已完成真实后端接口及 PC 原业务入口；PC/H5 仅必要开考采集。

代码证据：后端定向 127/127，前端运行 8/8，两端 typecheck/定向 lint、OpenSpec strict、独立双轴静态 Review 通过。知识点 migration/init/schema-version 已同步；4 项 MySQL IT 已补并编译，Docker Linux engine 不可用，执行阻塞。

状态：**开发完成，待 WorkBuddy 真实验收**。本次未执行真实登录、浏览器渲染、真机、业务库或 SQL/Flyway 验收；五个原文编号保留未勾选。QA 与真实验收步骤：`engineering/qa/2026-10-10-batch5-business-statistics.md`；复盘：`engineering/retro/2026-10-10-batch5-business-statistics.md`。保留第四批及已有环境改动，未提交/推送。

### 2026-10-11 第七批 T1 推进
人员特征、条件分类与用户组（53子能力）代码完成；后端12项、前端4项、PC typecheck/定向lint、strict/diff、双轴Review通过。MySQL IT因Docker不可用阻塞，真实验收由WorkBuddy执行。T2课件执行口径待人工确认，T2～T7未完成；53/71/72/73/85不整项关闭，85短信延期。规格：`openspec/changes/2026-10-10-complete-batch7-training-matrix-and-dispatch`；证据：`engineering/qa/2026-10-11-batch7-training-matrix-and-dispatch.md`。

### 2026-10-11 第七批 T2/T3 推进
人工确认矩阵复用课程发布版本内课件。T2完成矩阵维护、人工计划草稿创建、最大学时合并与来源冻结；T3完成岗位/用户组统一选择、组合候选筛选及既有参训名单/系统消息链路接入。后端T2 28项、T3 52项，前端T2 20项、T3 37项及PC typecheck/目标lint通过，双轴独立Review修复后通过。MySQL IT编译通过但Docker执行阻塞，真实验收待WorkBuddy；扩大考试回归7项旧失败在HEAD基线复现，未宣称全套测试通过。T1～T3开发完成，达到最多连续三个Task边界；T4～T7未实施，85短信延期，功能项不整项勾选。详见本批QA/Retro与OpenSpec tasks。

### 2026-10-11 第七批 T4推进
年度文件管理/冻结关联及单级首次审批开发完成；全部草稿写入和名单调整接入计划行锁及版本门禁，历史已发布保持有效。Java79项、PC59项/typecheck/目标lint、strict/契约/diff及双轴Review修复后通过。MySQL行锁/年度事务/升级IT已补并编译，Docker不可用，真实SQL未验证；WorkBuddy待真实验收。72仍不整项勾选，T5～T7尚未完成，85短信延期。

### 2026-10-11第七批T5推进
72计划变更审批开发完成：独立草稿、提交冻结人员/年度依据、批准事务生效及版本/事实保护；已有执行安排无法安全原子同步时拒绝完整变更，进行中仅四项。Java121项/PC行为10项、typecheck/lint、独立双轴Review通过；真实MySQL IT及升级因Docker不可用阻塞，WorkBuddy待验收。130000迁移/init/schema同步；72不整项关闭，T6/T7继续开放，85短信延期。详见本批QA与tasks。

### 2026-10-11第七批T6与本阶段交付
73现有任务新建/查询/编辑/发布/执行/统计及PC/H5本人任务路径核验完成；补“转为培训任务”入口和缺安排/开始提示，复用现有执行与统计。Java22、PC43、H514及PC消息7项、typecheck/lint及独立双轴Review通过，真实验收待WorkBuddy。T1～T6开发完成；本阶段T4～T6达到最多连续三个Task边界，T7留下一阶段，85短信延期。Docker SQL/Flyway及事务IT未验证，各原文项保持不勾选关闭。证据和真实步骤见本批QA/tasks，最新schema版本20261011130000。

### 2026-10-11第七批T7与全批开发交付
T7完成考试岗位/组/部门/姓名评卷范围服务端展开、去重及现有阅卷资格校验，考生复用统一展开；PC保存回读实际人数，已绑定条件清空避免评卷单改重算旧考生组，系统消息复用真实名单与PC/H5本人双键入口。62项唯一后端目标测试（最新通知4项独立回归）、PC/两端消息22项、PC typecheck/目标lint、strict/契约/diff及双轴Review通过。SQLIT2项编译通过，Docker不可用未验证；本批schema保持130000。
T1～T7已确认范围开发完成，待WorkBuddy真实验收；短信延期使85部分完成，未扩大延期防作弊等范围，所有原文项仍不整项关闭。验收交接：engineering/qa/2026-10-11-batch7-workbuddy-handoff.md；完整证据：本批QA。代码未提交/推送。


## 2026-10-12 失败修复与最新复验
本次数据库17项及Flyway升级4项共21项真实通过；PC定向75项、选择器目标1项、H5定向14项通过，考试回归恢复HEAD相同7项存量失败、零新增失败。修复仅涉及测试夹具与旧选择器断言，两轴Review通过。详情见 engineering/qa/2026-10-12-batch7-fix-and-reverification.md。本段覆盖此前Docker阻塞及本批新增失败口径；仍待真实界面验收，短信延期，整项不关闭。按用户要求运行配置不提交，新库init需显式同步schema-version 20261011130000。
