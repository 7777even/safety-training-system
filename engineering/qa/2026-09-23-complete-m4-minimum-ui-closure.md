# M4 最小界面闭环补齐 QA

日期：2026-09-23
Change：`2026-09-23-complete-m4-minimum-ui-closure`
结论：**实现、定向自动化、菜单/权限迁移、遗留菜单清理、四类真实角色验收，以及证书签发全链均已落地并通过真实环境取证**；另修复了「进入培训成果页面后左侧菜单栏整条消失」的模块归属缺陷，并按用户要求把证书模板设计器改造为底图所见即所得 + 拖拽定位、修复了坐标字段契约缺陷。**Task 1、Task 2、Task 3、Task 4 全部已完成**，仅余双轴独立 Review 待提交后补做，见第四节与第五节。

## 一、本轮自动化证据（2026-09-23 重跑）

| 项 | 命令 | 结果 |
|---|---|---|
| 档案服务单测 | `mvn -pl sts-service-admin -Dtest=ArchiveServiceImplTest test` | `Tests run: 5, Failures: 0, Errors: 0` |
| 前端 M4 定向 | `node --test tests/m4-minimum-ui-closure.test.mjs tests/m4-outcome-closure.test.mjs`（cwd `frontend/pc-web`） | `tests 19 / pass 19 / fail 0`（含「成果页面归属应用中心模块，进入后侧栏不得整条消失」，已做 Red 验证：移除回退逻辑时该条失败；另 `m4-outcome-closure` 中「证书管理提供模板创建…」一条随设计器改版把旧 DOM 断言 `value="CERT_NO"` 升级为变量契约 `code: 'CERT_NO'`，改前确为失败；新增「设计器两栏各自滚动，编辑下方元素时画布常驻可见且可反向定位」一条，同样已做 Red 验证；本轮又在该条上追加证书变量中文口径断言，见 3.8） |
| PC 类型检查 | `node ../node_modules/vue-tsc/bin/vue-tsc.js --noEmit` | 退出码 0 |
| 改动文件 lint | `eslint src/router/admin-dynamic-routes.ts --max-warnings=0` | 退出码 0 |
| 菜单权限一致性 | `mvn -pl sts-service-admin-init -Dtest=MenuPermissionConsistencyTest test` | `Tests run: 6, Failures: 1`（既有 `menusShouldMatchEveryControllerPermissionAndContainNoUnsupportedPermission`） |
| 差异空白检查 | `git diff --check` | 干净 |
| 证书坐标契约 | `mvn -pl sts-service-admin -Dtest=CertificateElementJsonContractTest test` | `Tests run: 4, Failures: 0, Errors: 0`（TDD：先 Red 后 Green） |
| 迁移 MySQL IT | `TESTCONTAINERS_RYUK_DISABLED=true mvn -pl sts-service-admin -P integration-test -Dtest=FlywayMigrationIT test` | `Tests run: 7, Failures: 0, Errors: 0` |
| 档案 MySQL IT | 同上，`-Dtest=ArchiveServiceIT` | `Tests run: 3, Failures: 0, Errors: 0` |
| 初始化基线 IT | `TESTCONTAINERS_RYUK_DISABLED=true mvn -pl sts-service-admin-init -P integration-test -Dtest=AdminInitFlywayIT test` | `Tests run: 1, Failures: 0, Errors: 0`（改前 Red：`expected: 1L but was: 0L`） |

菜单一致性失败项**只剩延期模块**，M4 权限码已不在缺失清单内：`elements not found` = `regulation:overview:view`、`regulation:warning:action`、`regulation:learning:view`、`regulation:learning:export`、`regulation:warning:export`、`regulation:warning:list`、`ops:workbench:enterprise`（7 个，regulation/ops 范围）；`elements not expected` = `training:offline-score:edit`、`asset:asset:preview`（指南 §7 P2 已登记）。新增的 `shouldSeedM4ResultMenusWithoutConflictingIdentifiers` 通过。

## 二、迁移执行（人工授权后，经 Flyway 正规执行）

授权：用户 2026-09-23 明确授权。执行方式一律为**重启后端并开启 Flyway**（`--spring.flyway.enabled=true`），而非手工跑 SQL —— 因为 `V20260921170000` 是 `ALTER TABLE ... ADD COLUMN`（非幂等），手工执行而不写 `flyway_schema_history` 会导致后续 Flyway 重复 ALTER 失败。

### 2.1 第一轮（上一轮执行）：菜单 9700 段 + 档案文件列

| 指标 | 执行前 | 执行后 |
|---|---|---|
| `us_sys_menu` 行数 / `MAX(menu_id)` | 241 / 9605 | **266 / 9750** |
| `menu_id >= 9700` | 0 | **25** |
| `admin` 角色菜单绑定 | 208 | **233** |
| `sts_archive_version.file_info_id` | 不存在 | **已存在** |
| `flyway_schema_history` 末条 | `20260921160000` | **`20260923110000` success=1** |

### 2.2 第二轮（本轮执行）：门户证书节点 + 遗留菜单软删

| 指标 | 执行前 | 执行后 |
|---|---|---|
| `us_sys_menu` 有效行（`deleted=0`） | 266 | **235**（软删 32 + 新增 1） |
| `MAX(menu_id)` | 9750 | **9751** |
| 遗留段 9566～9605 有效行 / 失效行 | 32 / 8 | **0 / 40** |
| 门户 `#1` 子节点排序 | 9525(2) 9526(3) 9527(4) **9601(5)** 9528(6) | 9525(2) 9526(3) 9527(4) **9751(5)** 9528(6) |
| `us_sys_role` | 4（含上一轮临时建的 `m4_expert_review`） | **3**（自造角色已删除） |
| `flyway_schema_history` 末条 | `20260923110000` | **`20260923130000` success=1** |

- `V20260923120000__add_portal_my_certificates_menu.sql`：新增 `menu_id=9751`（`parent_id=1`、`path=certificates`、`component=portal/certificates/index`、`perms=training:learner:task`、`sort_order=5`、`menu_type=C`），并按 `role_level='STUDENT'` 授予 role 2 / role 3。
- `V20260923130000__soft_delete_legacy_m4_result_menus.sql`：`UPDATE us_sys_menu SET deleted = menu_id WHERE menu_id BETWEEN 9566 AND 9605 AND deleted = 0`（32 行；该段 `role_cnt` 全为 0）。写法与库内既有失效行一致。

Flyway 日志：`Successfully applied 2 migrations to schema 'sts', now at version v20260923130000`。

**环境操作留痕**（均在本机，非生产）：

1. 停掉旧后端（PID 36896），`mvn -pl sts-service-admin -am package -DskipTests` 重建 jar（校验 jar 内含 `V20260923120000/130000` 两个条目）。
2. 以 `--spring.flyway.enabled=true --server.port=20080` 启动执行迁移（`nohup` 方式启动的进程在命令返回后被回收，迁移已完成，最终改用可持久后台方式重启）。
3. `mvn package` 前必须停后端（Windows JVM 锁 jar）。
4. Redis 权限缓存：删除 `sts:cache:sys:user:perms::2`（等同 `evictAllPerms`）；学员新菜单 `9751` 为新增授权，缓存已清后重新计算。
5. pc-web dev server 运行在 **5175**（5173/5174 已被占用）；后端在 **20080**。

## 三、真实环境验收证据

### 3.1 管理员真实浏览器路径（CDP 驱动本机 Chrome，真实登录，截图存档）

脚本 `.workbuddy/tmp/m4-browser-accept.mjs`，输出 `.workbuddy/tmp/m4-browser-acceptance.txt`，截图 `.workbuddy/tmp/shots/`。

| 路由 | 结果 |
|---|---|
| 登录 `DEMO/admin/admin123` | 成功，落地 `/portal/class` |
| 侧边栏 | 出现「培训成果」及其下 **问卷管理 / 培训档案 / 证书模板 / 签发规则 / 已签发证书**（M4 菜单生效） |
| `/admin/result/survey` | 标题「问卷管理」，**表格 3 行真实数据**（`M4-QA-UI-20260923-COPY` 未投放、`M4-QA-UI-20260923` 已投放、`M4-QA-1790128608390` 已投放） |
| `/admin/result/archive` | 标题「培训档案」，三视角页签（一期一档 / 企业档案 / 个人档案） |
| `/admin/result/certificate/templates` | 标题「证书模板」，空态「暂无证书模板，先新建一个模板」 |
| `/admin/result/certificate/rules` | 标题「签发规则」，空态「暂无签发规则，先新建一条规则」 |
| `/admin/result/certificate/certificates` | 标题「已签发证书」，空态「暂无签发记录」，共 0 条 |
| `/admin/business-center/training/plan` | 培训计划列表 **4 行**（含已结束 `1231`） |

截图：`01-login-filled.png`、`02-after-login.png`、`04-survey.png`、`05-archive.png`、`06-cert-templates.png`、`07-cert-rules.png`、`08-cert-issued.png`、`09-result-plan.png`。

### 3.2 管理员真实接口路径（`.workbuddy/tmp/m4-deep-evidence.mjs`）

迁移前 archive/certificate/survey 六个端点全部 **403/1003**；迁移 + 清缓存后全部 **200/code 0**：

- Task 2 成果：`GET /training/plans/4/ended-result` → 200/code 0（ENDED、参训 1、合格 1、平均综合分 69.25、`resultStatus=PASSED`）；`GET /training/plans/3/ended-result`（RUNNING）→ **409/8123「仅已结束培训可查看正式结果」**。
- Task 3 档案：`GET /archive/trainings/4` → 200（`missingItems=[]`）；`GET /archive/enterprises` → 200（2 条）；**`GET /archive/persons/1/file` → 200，`content-type=application/json`，`filename=archive-person-superAdmin.json`**（修复点，真实环境验证通过）。
- Task 1 问卷：`GET /survey/surveys` → 200（total 3）；`GET /survey/instances` → 200（total 2）；`GET /survey/instances/2/stats` → 200。
- Task 4 证书：`/certificate/certificates`、`/certificate/templates`、`/certificate/my-certificates` 均 200。

### 3.3 权限覆盖核对（自审，不能替代独立 Review）

`@SaCheckPermission` 实际注解与菜单节点逐条对齐，无缺口：

- survey：`instance:add|list`、`survey:add|delete|edit|list` → 节点 #9710-9715 覆盖；代码中**不存在** `survey:instance:submit` 注解（提交端点仅校验登录）。
- certificate：`cert:list`、`rule:add|delete|edit|list`、`template:add|delete|edit|list|publish` → 节点 #9730-9750 覆盖；**不存在** `cert:issue|revoke|reissue` 注解（达标自动签发）。学员「本人证书」两个端点为 `@SaCheckPermission(value = {"certificate:cert:list", "training:learner:task"}, mode = SaMode.OR)`，role 3 持有后者。
- archive：`training:create|list` → 节点 #9720-9721、#9702 覆盖。

### 3.4 四类真实角色浏览器验收（CDP 驱动，脚本 `.workbuddy/tmp/m4-role-browser.mjs`）

账号由 `POST /api/system/users` 创建（密码传 SHA-256 摘要，服务端 BCrypt），**角色一律复用现有 role**（按用户 2026-09-23 指示）。截图见 `.workbuddy/tmp/shots-{learner,filler,expert,admin}/`。

| 角色 | 账号（角色） | 关键结果 | 截图 |
|---|---|---|---|
| 学员 | `m4-learner-01`（role 3 学员） | 门户导航出现「我的证书」；`/portal/certificates` 渲染 `h1=我的证书`，空态「暂未获得证书」 | `02-my-certificates.png` |
| 填写人 | `m4-filler-01`（role 3 学员） | `/survey/fill/1` →「M4非生产投放」3 题全必填；作答 3/3 提交 →「已完成填写」；**刷新后仍为已提交** | `04-submitted.png`、`05-reenter.png` |
| 专家 | `m4-expert-01`（**复用 role 1 管理员**） | 计划 1 工作区 →「专家查课」→「填写查课表」→ 填 ITEM_1/3 分数（40/50）与 ITEM_2/4 意见、摘要 → 提交成功 | `08-fill-dialog.png` |
| 管理员（回看） | `admin`（role 1） | 查课弹窗显示「已提交 / 总分 80 / 评价人 M4验收专家 / 提交时间 2026-09-23T13:55:55 / 查看详情」 | `10-admin-review.png` |

### 3.5 落库与接口硬证据（`.workbuddy/tmp/m4-final-verify.mjs`、`m4-archive-cert-verify.mjs`）

| 项 | 证据 |
|---|---|
| 问卷真实提交 | `sts_survey_response` 2 → 3 条；新增 `survey_response_id=3, survey_instance_id=1, user_id=6, response_status=SUBMITTED, device_type=PC, submitted_time=2026-09-23 13:51:14` |
| **二次提交后端拒绝** | `POST /api/survey/instances/1/submit` → **HTTP 409 / code 9008「该用户已提交答卷，不可重复提交」** |
| 两种用途投放可访问 | `GET /survey/instances/1/fill`（`TRAINING_SURVEY`）与 `/instances/2/fill`（`ORG_EVALUATION`）均 200 |
| 查课真实提交 | `sts_evaluation_record` 1 → 2 条；新增 `evaluation_record_id=2, form_version_id=2`(EXPERT_INSPECTION)`, target_type=TRAINING_PLAN_CONTENT, target_id=9, evaluator_user_id=7, total_score=80.00, record_status=SUBMITTED, submitted_time=2026-09-23 13:55:55` |
| 查课汇总（管理员视角） | `GET /training/plans/1/expert-inspections` → 记录=2、已提交、总分 80、评价人 M4验收专家、时间 `2026-09-23T13:55:55` |
| **一期档案真实下载** | `sts_archive_training#1`（`archive_code=1231`、`training_plan_id=4`、`SEALED`）、`sts_archive_version#1`（`version_no=1`、**`file_info_id=23`**）；`GET /archive/trainings/4/file` → **200、`application/json`、768 字节、`filename=archive-1231.json`** |
| 个人档案真实下载 | `GET /archive/persons/1/file` → 200、1707 字节、`filename=archive-person-superAdmin.json` |
| 档案负向校验 | 进行中计划 `GET /archive/trainings/1/file` → **409 / code 9202「仅已结束培训可以形成档案」** |
| 学员本人证书 | `GET /certificate/my-certificates` → 200 / code 0 / **total 0**（空态，权限口径正确） |
| 学员 profile | `perms` 9 条（含 `training:learner:task`，不含 `certificate:cert:list`）；菜单树含 `9751`「我的证书」 |

### 3.6 「培训成果」页面左侧菜单栏整条消失（本轮发现并修复）

**复现（CDP 驱动，脚本 `.workbuddy/tmp/m4-sidebar-diag.mjs`，证据 `m4-sidebar-diag-before.txt`）**：管理员登录后依次打开 `/admin/result/survey`、`/admin/result/archive`、`/admin/result/certificate/templates`、`/admin/result/certificate/rules`、`/admin/result/certificate/certificates`，五页 `.admin-sidebar` **均不存在**（`hasAside=false`、宽度 0）；对照页 `/admin/business-center/training/plan` 侧栏正常（`hasAside=true`、宽度 136px）。

**根因**：成果目录菜单节点 `9700` 沿用后端遗留的**绝对路径** `/result`，前端 `resolveFullPath` 将其归一为 `/admin/result`（见 `LEGACY_RESULT_PATH`）。该节点在菜单树上是「应用中心 `9530`（`/admin/business-center`）」的子节点，但归一后的 URL 不在模块前缀内，于是 `findAuthorizedAdminModule` 的路径前缀匹配落空 → `activeModule` 为 `undefined` → `sidebarNavigation` 为空 → `AdminLayout` 的 `v-if="!isOverviewPage && sidebarNavigation.length > 0"` 为假，整条侧栏不渲染。这是菜单数据（绝对路径）与布局模块判定（URL 前缀约定）之间的口径不一致，不是页面本身的问题。

**修复**：`frontend/pc-web/src/router/admin-dynamic-routes.ts` 的 `findAuthorizedAdminModule` 在 URL 前缀未命中时，回落按**已授权菜单树**回溯所属模块（新增 `collectNavigationPaths` 与私有工具 `isPathUnder`）。不改菜单数据、不改路由前缀、不改 URL。

**验证（after，证据 `m4-sidebar-diag-after.txt` + 截图 `shots-sidebar-after/`）**：

| 页面 | 路径 | `.admin-sidebar` |
|---|---|---|
| 问卷管理 | `/admin/result/survey` | ✅ true / 136px |
| 培训档案 | `/admin/result/archive` | ✅ true / 136px |
| 证书模板 | `/admin/result/certificate/templates` | ✅ true / 136px |
| 签发规则 | `/admin/result/certificate/rules` | ✅ true / 136px |
| 已签发证书 | `/admin/result/certificate/certificates` | ✅ true / 136px |
| 对照：培训计划 | `/admin/business-center/training/plan` | ✅ true / 136px（未受影响） |
| 对照：菜单管理 | `/admin/system/menu` | ✅ true / 136px（未受影响） |
| 对照：模块概览 | `/admin/overview` | — false（设计如此：`isOverviewPage`） |
| 对照：门户课程 | `/portal/class` | — false（走 `PortalLayout`，无后台侧栏） |

侧栏不仅恢复，且正确展开「培训成果」子树并高亮当前项，顶部「应用中心」模块高亮与面包屑（应用中心 / 培训成果 / 问卷管理）均正确（截图 `shots-sidebar-after/问卷管理.png`）。

**回归防护**：`tests/m4-minimum-ui-closure.test.mjs` 新增「成果页面归属应用中心模块，进入后侧栏不得整条消失」，断言侧栏显隐依赖 `sidebarNavigation`、成果节点归一为 `/admin/result` 且父节点为 `/admin/business-center`、以及模块判定存在菜单树回退；已做 Red 验证（临时移除回退逻辑 → 该用例失败，还原后 7/7 通过）。

### 3.7 问卷管理员侧操作补充验收（PC/手机预览、复制、删除、统计可见性）

脚本 `.workbuddy/tmp/m4-survey-ops2.mjs`（CDP 驱动，全部轮询等待而非固定 sleep），原始输出 `.workbuddy/tmp/m4-survey-ops.txt`，截图 `.workbuddy/tmp/shots-survey-ops/`。**净有效写入为零**：复制出一份新草稿后立即删除该副本。

| 验收项 | 结果 |
|---|---|
| PC 预览 | `/admin/result/survey/workspace/4` →「预览效果」→ 出现「电脑预览 / 手机预览」，样张渲染真实题目「Q1 您对培训机构服务是否满意？ 满意 / 不满意」 |
| 手机预览 | 切「手机预览」后选中态正确，题目与服务端冻结顺序一致 |
| 复制为新草稿 | 编辑模式底部「复制为新草稿」→ 输入编码 → 确认后路由跳至 `/admin/result/survey/workspace/6`，新草稿创建成功 |
| 草稿删除 | 列表副本行「删除」→ 二次确认「确认删除未投放问卷"…（副本）（副本）"吗？」→ 提示「问卷已删除。」，列表 4 → 3 行，副本消失 |
| 删除落库 | `sts_survey_survey` 中本轮副本 `survey_id=5/6` 均 `deleted=自身 ID`，无有效残留；已投放问卷不出现「删除」按钮（符合设计） |
| 投放管理 | 显示 2 条投放（`/survey/fill/1`、`/survey/fill/2`），操作含「复制 / 二维码 / 查看统计」 |
| **统计可见性** | 「查看统计」弹窗：**有效答卷 1 份**、`Q1 满意 1 · 100% / 不满意 0 · 0%`，并提示「统计仅包含正式提交的答卷，同一填写人的重复提交由服务端拒绝」 |

> 口径说明：本节验的是**管理员侧**统计可见性。有权限填写人的统计入口沿用既有权限模型（`survey:instance:*`），本轮未单独造填写人统计视角；填写人侧另已验收「填写一次 + 二次提交被拒」（见 3.4/3.5）。

### 3.8 证书签发全链与模板设计器（本轮补齐，Task 4 关闭依据）

**全链（HTTP 脚本 `.workbuddy/tmp/m4-cert-chain.mjs`，输出 `m4-cert-chain.txt`）**：

| 环节 | 证据 |
|---|---|
| 上传底图 | `POST /api/certificate/files` → 200/code 0、`fileInfoId=30`、`cert-background.png`、24570 字节 |
| 建模板 / 存草稿 / 回读 | `certificateTemplateId=5`（`CERT-TPL-M4-001`）；草稿 10 元素、`backgroundFileId=30`、画布 1400×990 |
| 发布 | `currentVersionNo=1 / templateVersionId=2 / publishedStatus=PUBLISHED`（后续浏览器验收与还原脚本继续发布至 **v3**） |
| 底图 / 预览端点 | `GET /templates/5/background` → 200、`image/png`、24570B、魔数 `89504e47`；`GET /templates/5/preview` → 200、`image/png`、66071B |
| 来源候选 | COURSE 3 / EXAM 16 / TRAINING 3，均 200 |
| 绑定规则 | 课程 `sourceId=2` → `certificateRuleId=4`；理论考试 `sourceId=38` → `certificateRuleId=5`；同来源重复绑定 → **409/code 9412** |
| **达标自动签发** | `POST /training/plans/1/results/synchronize` → 200/code 0、`participants=4`；证书 0 → **2**：`id=3 CERT-25F81D1242D442D5`（COURSE/2）、`id=4 CERT-63E04EB4F8A04B4C`（EXAM/38），`holder=1`、`ISSUED` |
| **幂等** | 连续再触发两次同步 → 200/code 0，证书数保持 2 |
| 管理员下载 | `GET /certificates/{3,4}/file` → 200、`image/png`（71810 / 71294B）、`attachment` |
| 持有人本人 | `GET /my-certificates` → 200/code 0、`total=2`，第 2 页可访问；本人下载 200、`image/png` |
| **越权拒绝** | 非持有人 `GET /my-certificates` → `total=0`；`GET /certificates/4/file` → **403/code 9416「只能查看本人证书」** |

**模板设计器所见即所得（真实浏览器，`m4-cert-browser.mjs` admin 角色，输出 `m4-cert-browser-admin.txt`，截图 `shots-cert-admin/01..08`）**：打开「设计与发布」→ 画布 `imageNatural=1400x990`、`stageWidth=748`，10 个元素按底图等比缩放渲染（字号同步缩放）；拖拽后表单坐标 `638,452` → `750,508`；「保存草稿」提示「模板草稿已保存。」；重新打开**持久化回显** `firstCoords=750,508`；「发布模板」提示「模板已发布。」并升 v2。

**上传底图即时可见（真实浏览器，`m4-cert-browser.mjs` upload 角色，输出 `m4-cert-browser-upload.txt`，截图 `shots-cert-upload/11..12`）**：打开「设计与发布」→ 画布初始 `imageNatural=1400x990`；点「重新上传底图」打开 `el-upload` 弹窗 → 以真实文件选择器（CDP `DOM.setFileInputFiles`）选中 `cert-background.png` → 点「完成」→ 提示「**证书底图已上传，画布已显示底图。**」、`hasImage=true`、`imageNatural=1400x990`、画布尺寸自动填 `1400 × 990`、10 个元素按新底图重渲染、`fileHint=已关联文件，可保存发布`；请求序列 `200 /templates/5`、`200 /templates/5/background`、`200 /api/system/files`。**直接对应用户反馈的「左侧上传底图右侧看不到效果」**——现已由真实浏览器证明上传后右侧画布立即出图且尺寸自动跟随。

**设计器排版：元素较多时画布常驻可见（真实浏览器量化，`.workbuddy/tmp/m4-cert-designer-layout.mjs`，输出 `m4-cert-designer-layout.txt`，截图 `shots-cert-layout/01..04`）**：设计器左栏元素卡片堆叠后，原布局由外层容器整页滚动，滚动编辑靠下元素时画布被一起滚出视口（用户反馈"滚动下滑设置看不到右侧预览"）。改为弹窗内容固定高度（`min(72vh, 800px)`）、左右两栏各自滚动后实测：

| 指标 | 改前（同环境注入旧布局重放） | 改后 |
|---|---|---|
| 左栏滚动 | 整页滚动（`.designer-pane` scrollHeight = clientHeight = 3114） | 左栏内部滚动 2452px（scrollHeight 3132 / clientHeight 680） |
| 滚到末尾时画布位置 | `top=-2077 / bottom=-1532`，**完全移出视口** | `top=167 / bottom=699`，**与初始完全一致** |
| 画布在视口内 | ❌ | ✅（innerHeight 945） |
| 新增元素按钮 | 被滚走 | 吸顶可见（滚动后 top 94） |

另验反向定位：在画布上点中末位元素 → 左栏 `scrollTop=2452`，对应卡片（533–755）落入左栏可视区（94–774）。定向用例新增「设计器两栏各自滚动，编辑下方元素时画布常驻可见且可反向定位」，已做 Red 验证（把布局回退为旧版即失败，还原后 sha1 一致）。

**持证人 / 非持证人门户（真实浏览器，输出 `m4-cert-browser-holder.txt` / `m4-cert-browser-learner.txt`）**：`superAdmin` 打开 `/portal/certificates` → 2 张证书卡（考试 / 课程各一），「在线查看」弹窗 `natural=1400x990`，`/my-certificates` 与 `/my-certificates/4/file` 均 200；`m4-learner-01` 同页为空态「暂未获得证书」。

**坐标字段契约缺陷（本轮定位并修复，TDD）**：

- 现象：保存设计 400 `elements[4].xPosition: must not be null`（全部元素坐标被拒）。
- 根因：DTO/VO 未声明 JSON 名，Jackson 默认策略把 `getXPosition()` 推导为 `xposition`，前端按契约发送的 `xPosition` 被静默丢弃 → 坐标恒为 null。属**运行时行为与 OpenAPI 契约不一致**，不是前端问题。
- 修复：`CertificateElementRequest` / `CertificateTemplateElementVO` 的 `xPosition`/`yPosition` 显式 `@JsonProperty`（含 getter/setter）；新增 `CertificateElementJsonContractTest` 锁契约（先 Red 后 Green，4 用例覆盖序列化 / 反序列化双向）。
- 连带：`m4-outcome-closure.test.mjs` 中断言旧设计器 `value="CERT_NO"` 的一条，随设计器改版升级为变量契约断言（改前为失败，见第一节）。

**证书变量中文口径（本轮修复，TDD）**：

- 现象：证书上「来源类型」原样印出库内英文编码（如 `培训来源：COURSE`）；设计器画布示例值硬编码为 `'TRAINING'`。
- 根因：两类渲染路径各有一处英文来源——服务端把 `sourceType` 编码直接写入 `SOURCE_TYPE` 变量（正式签发与模板预览都走它）；前端设计器画布的示例值写死英文编码。
- 修复：后端新增 `CertificateSourceTypes` 中文口径字典（未知编码原样保留，便于发现来源脏数据），签发与预览共用；前端把来源类型字典收敛为 `constants/enum-options` 的 `CERTIFICATE_SOURCE_TYPE_OPTIONS`，设计器画布示例值改为从字典取值，规则页与已签发证书页共用同一份（原先三处各写一遍）。
- 验证（先 Red 后 Green）：`CertificateServiceImplTest` 断言渲染器实际收到的变量表，改前实测 `SOURCE_TYPE="TRAINING"` vs 期望 `培训计划`（签发 + 预览各 1 例失败）。修复后 `CertificateServiceImplTest` 4/4、`CertificateSourceTypesTest` 3/3、`CertificateImageRendererTest` 1/1（共 8/8）；前端 M4 定向 18/18，新增 5 条断言（字典内容、设计器示例取自字典、规则页 / 已签发证书页共用字典、无重复字典）已做 Red 验证（回退示例值即失败），源码 sha1 精确还原；`vue-tsc --noEmit` 0 error、`eslint --max-warnings=0` 通过。
- 说明：按本轮要求未启动 / 调用后端做实时取证，服务端渲染口径由上述单测锁定；已签发证书的 PNG 为签发时固化产物，历史图不会变化，新签发与预览即按中文口径渲染。

## 四、未完成的真实验收与阻塞

**Task 状态结论**：**Task 1、Task 2、Task 3、Task 4 = 全部已完成**（各自验收标准均有真实端到端证据；Task 4 见 3.8）。

1. **双轴独立 Review 未形式化**：改动全为未提交工作树内容（仓库同时存在其他会话的改动），无法形成 `code-review` 技能要求的固定提交差异。第 3.3 节的权限覆盖核对属自审，不能替代独立 Review。本轮按用户 2026-09-23 指示以「本地真实测试走通」为勾选口径，独立 Review 待提交后补做。
2. **本轮写入非生产库的验收数据**：模板 5、规则 4/5、证书 3/4 与 `fileInfoId=30` 保留作为验收证据；如需清理按「先删规则 → 再删模板」顺序（模板被规则引用时后端 9403 拒绝删除），脚本 `.workbuddy/tmp/m4-cert-cleanup.mjs`。属数据操作，需人工确认后再执行。

> 已解除的旧阻塞：填写人 / 专家 / 学员账号不可用、一期档案 `.../file` 409、门户「我的证书」入口断链、遗留菜单 9566～9605、成果页侧栏整条消失、「专家」角色存废（功能项未要求）、**证书全链无种子数据（本轮造数据并跑通，见 3.8）**、**Docker/Testcontainers 不可用（本轮 `FlywayMigrationIT` 7/7、`ArchiveServiceIT` 3/3、`AdminInitFlywayIT` 1/1 全部通过）** —— 逐项证据见第 2.2、3.4、3.5、3.6、3.8 节及第 5 节。

## 五、缺口处理记录

### 5.1 门户「我的证书」菜单节点缺失（已关闭）

`views/portal/home/index.vue:24` 的 `openCertificates()` 注释写「入口不依赖后台菜单权限」，但 `/portal/certificates` **既不在静态路由、也不在 `PORTAL_INTERNAL_ROUTE_DEFINITIONS`**，只能由菜单节点注册。种子 `menus.json` 与历史迁移都没有该节点，因此学员点「我的证书」会因路由未注册而无法进入。

真实库中原有一个手工节点 `#9601`「我的证书」（门户 `#1` 下、组件 `portal/certificates/index`、权限 `training:learner:task`、`sort_order=5`），但它**未绑定任何角色**，且未登记进种子/迁移，所以同样不生效。

**处理（用户选择「新建正式节点并授学员角色」）**：迁移 `V20260923120000` 新增 `#9751`（同门户位置、同路径、同组件、同权限、`sort_order=5`）并显式授予 `role_level='STUDENT'` 的全部角色；原 `#9601` 随 5.2 的遗留段软删。种子 `menus.json`（233 节点）与 `current-online-menu-tree.json` 已同步。**学员真实浏览器验收通过**（见 3.4）。

### 5.2 真实库遗留手工菜单 9566～9605（已执行软删）

2026-09-21 经菜单管理界面由 `superAdmin`（`create_by=1`）手工创建，**未绑定任何角色（`role_cnt` 全为 0）、未回写 `menus.json`、全库零工程记录**。该段 `deleted` 列取值分两类：

**处理前仍有效（`deleted=0`，32 条）**：9566/9567/9569/9570～9573（培训成果目录、问卷管理、培训档案、证书管理及三叶子页）、9574～9580、9586、9588、9589、9590～9600（各类按钮节点）、9601（门户「我的证书」）、9602～9605（挂在 #705 下的专家查课按钮）。这些与正式节点 9700 段归一后落在**同一路由路径**，一旦被勾选角色就会出现重复动态路由与重复侧边栏项。

**处理前已失效（`deleted` = 自身 `menu_id`，8 条）**：9568、9581～9585、9587、9594。

**处理（用户选择「软删有效集」）**：迁移 `V20260923130000` 把 32 条有效行置 `deleted = menu_id`，与既有失效行写法一致；该段从未绑定角色，软删**不影响任何用户的可见性**。执行后该段有效行归 0、失效行 40，门户排序无冲突。

### 5.3 库内不存在独立「专家」角色（已关闭：功能项未要求）

**核查依据**：`docs/product/功能项.csv` #43 原文为「为方便在培训过程中会有专家旁听培训课程，可通过专家查课表记录查课意见，对课程质量进行监督和改进，支持新建专家查课表和管理查课表」——描述的是**业务动作**，未要求系统内存在「专家」角色；#10 明确「角色级别为管理员级、老师级、学生级。其中默认角色为：管理员、老师、学生」。因此**功能项不要求独立专家角色**。

**处理**：不新建、不修改任何角色。上一轮临时新建的验收角色 `m4_expert_review`（roleLevel=TEACHER）已删除，`us_sys_role` 保持 3 个现有角色。专家查课链路的验收使用 `m4-expert-01` + 现有 role 1 上下文（`profile.perms` 176 条，与 `admin` 一致）。

- 口径说明：本轮验证的是**查课链路可达且数据真实落库**（专家填表 → 提交 → 管理员回看提交人与时间），**不是角色隔离**。`evaluation:record:*` 目前仅管理员角色持有，若将来需要「专家只能查课、不能管理后台」的隔离，属权限模型变更，须另立 Change 并人工确认，本轮不做。
