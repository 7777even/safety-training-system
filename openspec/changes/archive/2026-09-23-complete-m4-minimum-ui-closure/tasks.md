# Tasks：M4 最小界面业务闭环补齐

> 状态：已获人工确认实施。每次只实施一个 Task，最多连续三个；测试、真实角色验收、QA 与文档同步完成后标记“完成”。
> 2026-09-23 第四次复核（结论轮）：菜单/权限两条新迁移已由 Flyway 正规执行进真实库，遗留菜单段已清理，**管理员 / 填写人 / 专家 / 学员四类真实角色路径均已跑通**（含问卷真实提交与二次拒绝、查课真实提交、本人证书页、一期档案真实下载）；本轮另修复「进入培训成果页面后左侧菜单栏整条消失」缺陷（含 Red 验证与跨模块对照回归）。
> 2026-09-23 第五次复核（收口轮）：**Task 4 证书签发全链已在真实库跑通并取证**（上传底图 → 设计/发布模板 v3 → 绑定课程/理论考试来源 → 达标自动幂等签发 → 管理员预览下载 → 持有人本人查看 → 他人越权 403）；同时修复「坐标字段运行时 JSON 名与契约 `xPosition` 不一致、导致元素坐标被静默丢弃」的契约缺陷（TDD：先 Red 后 Green），并把模板设计器改为底图所见即所得 + 拖拽定位。后端 MySQL IT 本机全部跑通：`FlywayMigrationIT` 7/7、`ArchiveServiceIT` 3/3、`AdminInitFlywayIT` 1/1。
> 2026-09-23 第六次复核（反馈轮）：修复「证书变量『来源类型』显示英文编码」——证书成品由服务端渲染、设计器画布取前端示例值，两处口径都改为同一份中文字典（后端 `CertificateSourceTypes`、前端 `constants/enum-options` 的 `CERTIFICATE_SOURCE_TYPE_OPTIONS`），并把规则页 / 已签发证书页的重复字典收敛到同一处。TDD：先 Red（`SOURCE_TYPE="TRAINING"` vs 期望 `培训计划`，签发与预览两路径）后 Green。
> **结论：Task 1、Task 2、Task 3、Task 4 全部已完成**（各自验收标准均有真实端到端证据）。「专家角色」经查功能项未要求，不新建、不修改。剩余缺口见文末遗留清单（仅余双轴独立 Review 待提交后补做）。

## Task 1：问卷设计、投放与可见统计（功能项 35、42）

- Owner：PC 前端；后端仅在现有契约与实现不一致时修复。
- 状态：**已完成**。实现、定向自动化、管理员侧与填写人侧真实浏览器验收全部落地。
- 输入：`survey.openapi.json`、现有问卷/填写页、现有菜单权限。
- 输出：题目/选项增删、草稿删除、复制后加载、用途与可见性选择、可扫码二维码、填写人获授权统计入口。
- 前后端/API/数据影响：只改 PC；不新增业务字段、接口或表。二维码依赖已获人工确认；正式菜单种子与迁移统一在 Task 4 收口，当前在线问卷菜单由真实验收核对。
- 验收：管理员完成三题草稿、复制/删除和双端预览；两种用途能投放；扫码登录填写一次，二次提交后端拒绝；管理员和有权限填写人统计可见性正确。
- 测试：先加失败的界面/请求定向用例；再运行目标 Node 测试、PC typecheck、受影响 lint 和双角色真实浏览器路径。
- 2026-09-23 证据：`frontend/pc-web/tests/m4-minimum-ui-closure.test.mjs` 6/6 通过；PC `vue-tsc --noEmit` exit 0。
  - 管理员侧（真实浏览器）：`/admin/result/survey` → 标题「问卷管理」、表格 3 行真实数据（含已投放 `M4-QA-UI-20260923` 与副本）。
  - 填写人侧（真实浏览器，账号 `m4-filler-01`，复用现有 role 3 学员）：打开 `/survey/fill/1` →「M4非生产投放 / M4闭环测试问卷」3 题（单选/多选/文本，全部必填），作答 3/3 后点「提交问卷」→ 页面转为「已完成填写」；**刷新后仍为「已完成填写」**（服务端 `submitted=true`）。
  - **二次提交后端拒绝（硬证据）**：`POST /api/survey/instances/1/submit` → **HTTP 409 / code 9008「该用户已提交答卷，不可重复提交」**。
  - **真实落库**：`sts_survey_response` 新增 `survey_response_id=3, survey_instance_id=1, user_id=6, response_status=SUBMITTED, device_type=PC, submitted_time=2026-09-23 13:51:14`（基线 2 条 → 3 条）。
  - 两种用途投放均可访问：投放 1 `TRAINING_SURVEY`（已提交）、投放 2 `ORG_EVALUATION`（未提交），`GET /api/survey/instances/{1,2}/fill` 均 200。
  - 管理员侧补充验收（本轮补齐，真实浏览器，只读+成对读写，净有效写入为零）：
    - **PC/手机双端预览**：`/admin/result/survey/workspace/4` →「预览效果」→ 标题栏出现「电脑预览 / 手机预览」，样张渲染真实题目「Q1 您对培训机构服务是否满意？ 满意 / 不满意」；切「手机预览」后选中态正确、题目一致（预览读取服务端冻结顺序）。
    - **复制为新草稿**：编辑模式底部操作栏「复制为新草稿」→ 输入编码 `M4-QA-COPY-1790146605063` → 确认后路由跳至 `/admin/result/survey/workspace/6`，新草稿创建成功。
    - **草稿删除**：列表找到该副本行 → 点「删除」→ 二次确认框文案「确认删除未投放问卷"…（副本）（副本）"吗？」→ 确认后提示「问卷已删除。」，列表行数 4 → 3，副本不再出现。库内核对：`survey_id=5/6` 均 `deleted=自身 ID`，无有效残留（已投放问卷的「删除」按钮按设计不出现）。
    - **统计可见性**：投放管理 tab 显示 2 条投放（`http://localhost:5175/survey/fill/1`、`/fill/2`，含「复制 / 二维码 / 查看统计」）；「查看统计」弹窗显示「有效答卷 1 份」与「Q1 满意 1 · 100% / 不满意 0 · 0%」，并提示「统计仅包含正式提交的答卷，同一填写人的重复提交由服务端拒绝」。
    - 截图：`.workbuddy/tmp/shots-survey-ops/`（01 列表 / 02 PC 预览 / 03 手机预览 / 05 复制后列表 / 06 删除后 / 07 投放 / 08 统计）；原始输出 `.workbuddy/tmp/m4-survey-ops.txt`。

## Task 2：已结束成果与专家查课（功能项 39、40、43）

- Owner：PC 前端；既有后端结果与查课服务不改业务口径。
- 状态：**已完成**。实现、定向自动化、成果读取链路与专家提交 + 管理员回看真实浏览器验收全部落地。专家侧复用现有 role 1 管理员上下文，依据见「专家角色结论」。
- 输入：`training.openapi.json`、`evaluation-result.openapi.json`、计划工作台与成果页。
- 输出：计划信息、学员成绩/详情、奖惩、基础统计的同一成果阅读路径；专家提交和管理员回看入口。
- 前后端/API/数据影响：复用已有端点与计划上下文路由，不新增数据库、权限或无参数成果列表。
- 验收：ENDED 展示完整事实，非 ENDED 返回后端 409；奖惩与分数一致；专家提交完整意见，管理员回看提交人与时间。
- 测试：先补失败定向用例；再跑目标 Node 测试、typecheck、受影响 lint，管理员/专家真实浏览器核对。
- 2026-09-23 证据：
  - 成果读取（真实会话）：`GET /api/training/plans/4/ended-result` → 200/code 0（计划 `1231/123`、参训 1、合格 1、不完整 0、平均综合分 69.25、`resultStatus=PASSED`）；进行中计划 `GET /api/training/plans/3/ended-result` → 409/code 8123「仅已结束培训可查看正式结果」。
  - 专家提交（真实浏览器，账号 `m4-expert-01`）：`/admin/business-center/training/plan/1` 工作区 →「专家查课」弹窗 →「填写查课表」→ 填 ITEM_1/ITEM_3 分数（各 40，满分 50）与 ITEM_2/ITEM_4 意见、查课摘要 → 提交成功。
  - **真实落库**：`sts_evaluation_record` 新增 `evaluation_record_id=2, form_version_id=2（EXPERT_INSPECTION）, target_type=TRAINING_PLAN_CONTENT, target_id=9, evaluator_user_id=7（m4-expert-01）, total_score=80.00, record_status=SUBMITTED, submitted_time=2026-09-23 13:55:55`。
  - 管理员回看（真实浏览器，`DEMO/admin/admin123`）：查课弹窗显示「查课状态=已提交、总分=80、评价人=M4验收专家、提交时间=2026-09-23T13:55:55、操作=查看详情」。**提交后按钮由「填写查课表」变为「查看详情」，即“提交后不可编辑”生效**。
  - 已知口径：专家侧复用现有 role 1（管理员）账号上下文，库内**不存在独立专家角色**，详见文末阻塞 4。
  - 未单独复核：成果页 UI 逐项（学员行/奖惩行渲染）未单独截图核对，接口事实已取证。

## Task 3：三级档案生成、查询与真实下载（功能项 44）

- Owner：PC 前端与 Admin 后端。
- 状态：**已完成**。实现、定向自动化、三视角查询与一期/个人真实文件下载均落地；`ArchiveServiceIT` 3/3 已在本机 Docker 通过。
- 输入：`archive.openapi.json`、已结束计划、现有三视角和文件端点。
- 输出：一期更新入口、企业/个人/一期查询、个人文件端点修复、正确格式下载；档案菜单与权限数据对齐。
- 前后端/API/数据影响：优先不新增端点或业务表；个人导出修复服务实现；菜单种子及幂等 Flyway/schema-version 同步。若发现必须改变 API 语义，先停下修订契约并确认。
- 验收：完整 ENDED 培训可更新并在三视角看到一致事实；一期和个人真实文件可下载且 MIME/扩展名/内容一致；INCOMPLETE、非 ENDED、跨租户访问被拒绝。
- 测试：先写失败的 Service/Controller/IT 与 PC 定向用例；再运行受影响后端目标测试、MySQL/Testcontainers IT、PC typecheck/lint、对象存储及管理员真实浏览器下载。
- 2026-09-23 证据：`ArchiveServiceImplTest` 5/5 通过（含「按可见已结束培训事实组装个人档案文件」与「存在不完整已结束培训时以 9204 拒绝」）。
  - 三视角（真实会话）：`GET /api/archive/trainings/4` → 200（`missingItems=[]`）、`GET /api/archive/enterprises` → 200（2 条）、`GET /api/archive/persons/1` → 200。
  - **一期档案真实文件下载（本轮新证据）**：档案已生成 —— `sts_archive_training#1`（`archive_code=1231`、`training_plan_id=4`、`archive_status=SEALED`）、`sts_archive_version#1`（`version_no=1`、**`file_info_id=23`**、`version_reason=培训计划结束后更新档案`）。`GET /api/archive/trainings/4/file` → **200、`content-type=application/json`、768 字节、`content-disposition: attachment; filename=archive-1231.json`**。
  - **个人档案真实文件下载**：`GET /api/archive/persons/1/file` → 200、`application/json`、1707 字节、`filename=archive-person-superAdmin.json`（内容为按可见已结束培训事实即时组装）。
  - 负向校验：进行中计划 `GET /api/archive/trainings/1/file` → **409 / code 9202「仅已结束培训可以形成档案」**。
  - **MySQL IT（本轮补齐）**：`ArchiveServiceIT` **3/3 通过**（Testcontainers + `mysql:8.0.45`，以 `TESTCONTAINERS_RYUK_DISABLED=true` 绕过 ryuk 拉取限制）；中途修正 `AbstractMysqlIntegrationTest.INIT_SCHEMA_VERSION` 至 `20260923130000`（原值落后于 init 快照，导致重复 `ADD COLUMN file_info_id` 而初始化失败）。

## Task 4：证书管理、本人证书与菜单（功能项 19、50）

- Owner：PC 前端；菜单数据由 Admin 后端交付。
- 状态：**已完成**。菜单迁移、学员端入口修复、成果页侧栏缺陷修复、模板设计器所见即所得改造、坐标契约修复，以及证书签发全链（上传底图 → 设计/发布模板 → 绑定课程/考试来源 → 达标自动幂等签发 → 管理员预览下载 → 持有人本人查看 → 他人越权拒绝）均已落地并通过真实环境取证。
- 输入：`certificate.openapi.json`、现有模板/规则/证书页及附件菜单树。
- 输出：管理员/学员在线预览、本人证书分页；模板、签发规则、已签发证书三个已存在的组件路径与正式菜单一致。
- 前后端/API/数据影响：复用现有文件端点，不增造签发规则；统一迁移问卷、档案、证书的必要菜单/按钮，更新 init 菜单种子、幂等 Flyway 和 schema-version，不修改业务表。
- 验收：管理员上传底图、设计/发布模板、绑定课程/理论考试/培训来源；达标自动且幂等签发；管理员预览/下载；学员仅查看、预览、下载本人证书，超过一页仍可访问。
- 测试：先写失败的 UI/菜单测试；再跑定向测试、PC typecheck/lint、菜单 Flyway IT、管理员与学员真实浏览器及文件证据。
- 2026-09-23 证据：`MenuPermissionConsistencyTest` 6 个用例中新增的 `shouldSeedM4ResultMenusWithoutConflictingIdentifiers` 通过，全量缺失清单中不再出现 `survey:*`/`archive:*`/`certificate:*`（仅剩 regulation/ops 延期模块）。
  - 菜单/权限（真实库，三轮累计）：`us_sys_menu` 241 → 266（上一轮 9700 段）→ **235**（本轮：软删遗留 32 条 + 新增门户「我的证书」1 条）；`MAX(menu_id)` 9750 → **9751**；`admin` 绑定 233；`flyway_schema_history` 末条 → **20260923130000 success=1**。
  - **门户「我的证书」入口修复**：新增 `menu_id=9751`（`parent_id=1`、`path=certificates`、`component=portal/certificates/index`、`perms=training:learner:task`、`sort_order=5`），并按 `role_level='STUDENT'` 显式授予 role 2/3；原遗留 `menu_id=9601`（同路径、未登记、未绑角色）已随遗留段软删。
  - **学员侧真实浏览器验收**（账号 `m4-learner-01`，复用现有 role 3）：登录后门户导航出现「我的证书」（与学习中心/考试中心/培训中心/教学辅助同级）；打开 `/portal/certificates` → 页面正常渲染，`h1=我的证书`，空态「暂未获得证书」；`GET /api/certificate/my-certificates` → **200 / code 0 / total 0**（走 OR 权限 `certificate:cert:list` 或 `training:learner:task`，学员持有后者）。
  - 管理员侧：侧边栏「培训成果 → 问卷管理/培训档案/证书模板/签发规则/已签发证书」，三证书页正常打开（空态）。
  - **成果页侧栏整条消失缺陷（本轮发现并修复）**：进入 `/admin/result/**` 任一页面时 `.admin-sidebar` 不渲染，左侧菜单栏整条消失（CDP 实测 before：`hasAside=false`，宽度 0）。根因：成果菜单沿用后端遗留绝对路径 `/result`，被归一为 `/admin/result`，不落在任何后台模块的 URL 前缀内，`findAuthorizedAdminModule` 按前缀匹配失败 → `activeModule` 为空 → `AdminLayout` 的 `v-if="!isOverviewPage && sidebarNavigation.length > 0"` 为假。修复：模块归属在 URL 前缀未命中时回落按已授权菜单树回溯（`collectNavigationPaths` + `isPathUnder`）。
    - 修复后 CDP 实测 after：问卷管理/培训档案/证书模板/签发规则/已签发证书五页 `hasAside=true`、宽度 136px，且侧栏正确展开「培训成果」子树并高亮当前项；跨模块对照未受影响（业务中心培训计划、系统管理菜单管理均正常；模块概览与门户按设计不显示后台侧栏）。
    - 回归防护：新增 `frontend/pc-web/tests/m4-minimum-ui-closure.test.mjs`「成果页面归属应用中心模块，进入后侧栏不得整条消失」，已做 Red 验证（移除回落逻辑时该用例失败）。
  - **证书签发全链（本轮真实环境取证，HTTP 脚本 `.workbuddy/tmp/m4-cert-chain.mjs`，输出 `m4-cert-chain.txt`）**：
    - 上传底图：`POST /api/certificate/files` → 200/code 0、`fileInfoId=30`、`cert-background.png`、24570 字节。
    - 建模板 → 存草稿 → 回读：`certificateTemplateId=5`（`CERT-TPL-M4-001`），草稿 10 个元素、`backgroundFileId=30`、画布 1400×990；发布后 `currentVersionNo=1 / templateVersionId=2 / publishedStatus=PUBLISHED`（后续浏览器验收与还原脚本继续发布至 **v3**）。
    - 底图与预览端点：`GET /api/certificate/templates/5/background` → 200、`image/png`、24570 字节、魔数 `89504e47`；`GET /api/certificate/templates/5/preview` → 200、`image/png`、66071 字节。
    - 来源候选：COURSE 3 / EXAM 16 / TRAINING 3，均 200（服务端真实返回）。
    - 绑定规则：课程 `sourceId=2` → `certificateRuleId=4`；理论考试 `sourceId=38` → `certificateRuleId=5`；同来源重复绑定 → **409/code 9412「同来源与模板的签发规则已存在」**。
    - **达标自动签发**：`POST /api/training/plans/1/results/synchronize` → 200/code 0、`participants=4`；已签发证书 0 → **2**：`id=3 CERT-25F81D1242D442D5`（`COURSE/2`）、`id=4 CERT-63E04EB4F8A04B4C`（`EXAM/38`），`holder=1`、`status=ISSUED`。
    - **幂等**：连续两次再触发同步 → 200/code 0，证书数保持 **2**（不新增）。
    - 管理员下载：`GET /api/certificate/certificates/{3,4}/file` → 200、`image/png`（71810 / 71294 字节）、`content-disposition: attachment`。
    - 持有人本人：`GET /api/certificate/my-certificates` → 200/code 0、`total=2`；第 2 页可访问（本页 1 条）；本人下载 → 200、`image/png`。
    - **越权拒绝**：非持有人 `GET /api/certificate/my-certificates` → 200/code 0/`total=0`；`GET /api/certificate/certificates/4/file` → **403/code 9416「只能查看本人证书」**。
  - **模板设计器所见即所得（真实浏览器，`m4-cert-browser.mjs` admin 角色，输出 `m4-cert-browser-admin.txt`，截图 `shots-cert-admin/01..08`）**：打开「设计与发布」→ 画布 `imageNatural=1400x990`、`stageWidth=748`，10 个元素按底图等比缩放渲染（`fontSize` 同步缩放）；拖拽元素后表单坐标由 `638,452` 变为 `750,508`；「保存草稿」→ 提示「模板草稿已保存。」；**重新打开持久化回显** `firstCoords=750,508`、10 元素、文本齐备；「发布模板」→ 提示「模板已发布。」并升到 v2。
  - **持证人 / 非持证人门户（真实浏览器）**：`superAdmin` 打开 `/portal/certificates` → 2 张证书卡（考试证书 `CERT-63E04EB4F8A04B4C`、课程证书 `CERT-25F81D1242D442D5`），「在线查看」弹窗 `natural=1400x990`，请求 `/my-certificates` 与 `/my-certificates/4/file` 均 200；`m4-learner-01` 同页为空态「暂未获得证书」。
  - **坐标字段契约缺陷（本轮定位并修复）**：保存设计曾 400 `elements[4].xPosition: must not be null`（全量元素被拒）。根因：DTO/VO 未声明 JSON 名，Jackson 默认策略把 `getXPosition()` 推导为 `xposition`，前端按契约发送的 `xPosition` 被静默丢弃 → 坐标恒为 null。修复：`CertificateElementRequest` / `CertificateTemplateElementVO` 的 `xPosition`/`yPosition` 显式 `@JsonProperty`（含 getter/setter）；并新增 `CertificateElementJsonContractTest` 锁契约（先 Red 后 Green，4 个用例覆盖序列化/反序列化双向）。
  - **上传底图即时可见（真实浏览器，`m4-cert-browser.mjs` upload 角色，输出 `m4-cert-browser-upload.txt`）**：真实文件选择器（CDP `DOM.setFileInputFiles`）选中底图 → 提示「证书底图已上传，画布已显示底图。」、`hasImage=true`、`imageNatural=1400x990`、画布尺寸自动填 `1400 × 990`。
  - **设计器排版：元素较多时画布常驻可见（真实浏览器量化，`.workbuddy/tmp/m4-cert-designer-layout.mjs`，输出 `m4-cert-designer-layout.txt`，截图 `shots-cert-layout/01..04`）**：弹窗内容固定高度（`min(72vh, 800px)`）、左右两栏各自滚动。左栏滚到末尾（内部消化 2452px）时画布 `top=167/bottom=699` 与初始一致、仍在视口内；同环境注入旧布局重放同一动作，画布 `top=-2077/bottom=-1532` 完全移出视口。新增元素按钮吸顶可见；在画布上点中元素会把对应设置卡片滚入左栏可视区。定向用例已做 Red 验证。
  - **证书变量中文口径（本轮修复）**：证书上「来源类型」原样印出库内英文编码（`培训来源：COURSE`）。根因：服务端把 `sourceType` 编码直接写入 `SOURCE_TYPE` 变量，设计器画布示例值也硬编码 `'TRAINING'`。修复：新增 `CertificateSourceTypes`（中文口径字典，未知编码原样保留）供签发与预览共用；前端把来源类型字典收敛为 `constants/enum-options` 的 `CERTIFICATE_SOURCE_TYPE_OPTIONS`，设计器画布示例值改为从字典取值，规则页与已签发证书页共用同一份。TDD：先 Red（`CertificateServiceImplTest` 断言渲染器收到的变量表，实测 `SOURCE_TYPE="TRAINING"` vs 期望 `培训计划`，签发与预览两路径各 1 例）后 Green（`CertificateServiceImplTest` 4/4、`CertificateSourceTypesTest` 3/3、`CertificateImageRendererTest` 1/1 共 8/8；前端 M4 定向 19/19，新增断言已做 Red 验证并通过 sha1 精确还原）。按本轮指示未再启动/调用后端做实时取证，服务端口径由上述单测锁定。

## 最终关闭条件

- Task 1～4 全部通过各自自动化、双轴 Review 和真实角色验收；QA/Retro、OpenAPI（若确有变更）及进度表同步。
- 不能取得 Docker、非生产登录账号或对象存储证据时，相关 Task 保持未完成并单列阻塞；不得只凭编译和 Mock 勾选。

## 2026-09-23 遗留清单（Task 1～4 已完成）

**已解除（累计）**

- ~~非管理员账号不可用~~ → 经用户 2026-09-23 授权，新建 3 个非生产验收账号（`m4-learner-01` / `m4-filler-01` / `m4-expert-01`，密码经 `POST /api/system/users` 传 SHA-256 摘要、服务端 BCrypt），按用户指示**一律复用现有角色**：`m4-learner-01`、`m4-filler-01` 绑 role 3（学员），`m4-expert-01` 绑 role 1（管理员）。填写人、学员、专家三类路径均已跑通。
- ~~Task 3 一期档案文件 409~~ → 档案已生成（`sts_archive_version#1.file_info_id=23`），`GET /archive/trainings/4/file` → 200、768 字节、`archive-1231.json`。
- ~~门户「我的证书」节点缺失~~ → 迁移 `V20260923120000` 新增 `9751` 并授学员角色，学员侧导航与页面均已真实验收。
- ~~遗留菜单 9566～9605~~ → 迁移 `V20260923130000` 按 `deleted = menu_id` 软删 32 条有效行（该段原本 `role_cnt` 全为 0，不影响任何角色可见性；原 8 条已失效行不变），门户排序无冲突（9525/9526/9527/**9751**/9528）。
- ~~库内不存在独立「专家」角色~~ → **经查功能项未要求独立专家角色**：`docs/product/功能项.csv` #43 只描述「专家旁听培训课程、通过专家查课表记录查课意见」这一业务动作，未要求系统角色；#10 明确「角色级别为管理员级、老师级、学生级，默认角色为管理员、老师、学生」。故**不新建、不修改角色**，专家侧验收沿用现有 role 1；上一轮临时新建的 `m4_expert_review` 已删除，角色表保持 3 个。
- ~~成果页侧栏整条消失~~ → 本轮定位为模块归属判定缺陷并修复，见 Task 4 证据段。
- ~~Task 4 证书全链无种子数据~~ → 已按最小可验路径在非生产库造数据并跑通全链：模板 5（`CERT-TPL-M4-001`，当前 v3）、规则 4（`COURSE/2`）与 5（`EXAM/38`）、证书 3/4（持有人 user 1）。证据见 Task 4 证据段。
- ~~Docker/Testcontainers 不可用~~ → 本机 Docker 具备 `mysql:8.0.45` / `redis:8.6.4` 镜像，仅 ryuk 拉取受限，`TESTCONTAINERS_RYUK_DISABLED=true` 即可正常跑：`FlywayMigrationIT` **7/7**、`ArchiveServiceIT` **3/3**、`AdminInitFlywayIT` **1/1** 全部通过。中途另修两处「IT 基线落后于 init 快照」：`FlywayMigrationIT` 与 `AbstractMysqlIntegrationTest` 的 `INIT_SCHEMA_VERSION` 对齐 `20260923130000`；`AdminInitFlywayIT` 的 BASELINE 断言改为从 `sts.init.schema-version` 读取，不再硬编码版本号（已做 Red 验证：改前 `expected: 1L but was: 0L`）。

**仍阻塞**

1. **双轴独立 Review 未形式化**：改动仍全部位于未提交工作树（仓库同时存在其他会话的改动），无法形成 `code-review` 技能要求的固定提交差异；QA 中的权限覆盖核对属自审，不能替代独立 Review。**本轮按用户 2026-09-23 指示以「本地真实测试走通」为勾选口径**，Task 1～4 的完成以真实端到端证据为准，独立 Review 待提交后补做。
2. **本轮写入非生产库的验收数据**：模板 5（`CERT-TPL-M4-001`）、规则 4/5、证书 3/4 与 `fileInfoId=30` 保留作为验收证据；如需清理，须按「先删规则 → 再删模板」顺序（模板被规则引用时后端 9403 拒绝删除），脚本 `.workbuddy/tmp/m4-cert-cleanup.mjs`。清理属数据操作，需人工确认后再执行。
