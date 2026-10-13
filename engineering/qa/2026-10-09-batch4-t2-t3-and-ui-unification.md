# 第四批 T2/T3 复核、界面统一与真实渲染验收

日期：2026-10-09（第二批）。关联Change：`2026-10-08-complete-batch4-learner-center`。

环境：后端 `AdminApplication` 在 `127.0.0.1:20080`（profile=vpn，共享开发库 `sts`），前端 PC/H5 源码树为当前工作区未提交状态。账号口径见 §2。

前置结论：本轮**未新增功能代码**。核对工作区发现 ChatGPT 因限额中断前，T2（本人资料与改密）与 T3（统一学习档案与追加更正）的前后端代码已写入树内；因此本轮工作转为「复核 + 界面统一 + 取证」，并把 tasks.md 中「T2/T3未编码」的过期标记修正。

---

## 1. 功能项与文件映射

| 功能项 | Task | 后端 | PC | H5 |
|---|---|---|---|---|
| 47/61 学习首页与消息 | T1 | `LearnerController` / `LearnerMessageService` / `V20261008180000` | `views/user-center/messages/index.vue` | `pages/messages/index.vue` |
| 50 本人资料与改密（非微信） | T2 | `MyProfileController` / `MyProfileService` / `MyProfileVO` | `views/user-center/profile/index.vue` | `pages/profile/edit.vue` |
| 52 统一历史与追加更正 | T3 | `LearnerHistoryController` / `HistoryCorrectionController` / `LearnerHistoryService` / `V20261009100000` | `views/user-center/history/index.vue`、`components/HistoryRecordPanel.vue`、`admin/result/archive/components/ArchiveHistoryPanel.vue` | `pages/history/index.vue` |

本轮界面统一改动共 8 个页面/组件文件 + 2 个公共文件：`constants/enum-options.ts`（新增 `MEMBER_TYPE_OPTIONS`）、`styles/portal.less`（新增公共类 `.portal-toolbar`、`.portal-form-card`）。

---

## 2. 账号与环境事实（本轮实测）

| 项 | 值 |
|---|---|
| 登录通道 | `POST /api/auth/login`，体为 `{"identityType":"local","authParams":{"orgCode","userCode","password":<SHA256 明文>}}` |
| 鉴权头 | `Authorization: Bearer <token>`（**不是** `satoken`） |
| `DEMO/admin` | 口令 `admin123`，`user_id=2`，org2 示例企业，`role_id=1` |
| `DEMO/operator01` | 口令 `admin123`，`user_id=3`，org2 |
| 端口 | 20080 后端；本轮前端用 `vite build` + 静态服务（5182/5183）取证，未用 dev server |
| Redis | `19.191.192.146:16379`，权限缓存键 `sts:cache:sys:user:perms::<userId>`（精确 `EXISTS` 判断，勿用 `--scan`） |
| Flyway | 最新两条 `20261009100000`、`20261008180000` 均 `success=1` |

### 2.1 数据观察（既有问题，非本批引入）

- `us_sys_user.phone` 中 `operator01`（`user_id=3`）行的值是**掩码字符串** `130****2333`。`MyProfileService.read()` 直接回传 `user.getPhone()`，不做掩码，因此「本人页」也会显示这串掩码。`admin` 行为正常值 `13800000000`。
  - 判定：**开发库数据污染**。`MyProfileService` 的注释已写明「禁止把脱敏手机号提交回数据库」，说明团队已知该风险；本次不擅自改数据，留待用户决策是否复位。
- `MenuPermissionConsistencyTest` 2 项失败：用脚本复现比对后确认是 HEAD 既有漂移（菜单侧 187 项 / Controller 侧 185 项，仅 `asset:asset:preview`、`training:offline-score:edit` 在 HEAD 即缺后端能力点；全树漂移 期望 44 / 实际 51 在 HEAD 同样成立）。新增的 `archive:history:correct`、`archive:history:correction:list` 在**两侧都存在**，未被漏登记。

---

## 3. 静态与定向测试

| 检查 | 命令（cwd） | 结果 |
|---|---|---|
| PC 类型 | `node ../node_modules/vue-tsc/bin/vue-tsc.js --noEmit`（`frontend/pc-web`） | exit 0 |
| H5 类型 | 同上（`frontend/mobile-app`） | exit 0 |
| 契约测试 | `node tests/learner-history.test.mjs` / `learner-home-message.test.mjs` / `my-profile-password.test.mjs` | 3 / 6 / 2 全绿 |
| ESLint | 对 9 个被改文件定向 `eslint <paths> --max-warnings=0` | exit 0（PC/H5 各一次） |
| 后端定向 | `mvn -pl sts-service-admin -am test -Dtest="..."` | 39 项 0 失败 `BUILD SUCCESS` |

后端定向覆盖：`DevelopmentMigrationConfigurationTest`(1)、`MyProfileServiceTest`(3)、`ArchiveControllerTest`(4)、`ExamLearnerNoticeTest`(1)、`LearnerAssessmentCenterServiceTest`(2)、`LearnerControllerTest`(1)、`LearnerMessageStreamTest`(1)、`LearnerHistoryServiceTest`(3)、`LearnerHomeServiceTest`(3)、`LearnerMessageServiceTest`(2)、`LearnerNotificationServiceTest`(1)、`OrganizationServiceImplTest`(17)。

操作要点（易踩）：
- 后端 `-am` 必须带，否则 `sts-common` 从 `.m2` 解析旧 jar，报 `NoSuchField` 之类假缺陷。
- PC 与 H5 的 `vue-tsc` **不可并行**，共享 `@shared/types` 声明竞态会报假类型错误。
- 取退出码用 `node xxx.js > log 2>&1; echo $?`，接 `| tail` 会取到 tail 的退出码而假绿。
- 全量 `node --test tests/` 会 `MODULE_NOT_FOUND`，须用 glob `node --test "tests/*.test.mjs"`。全量跑存在 64 项失败，逐项核对后确认**无一项落在本轮被改文件上**（引用的是缺失/重命名文件，如 `exams/paper/editor/index.vue`、`portal/home/index.vue` 缺 `border-radius: var(--card-radius)`、`@shared/utils/exam` 解析失败等）。

---

## 4. 真实 HTTP 验收

| 端点 | 方法 | 结果 |
|---|---|---|
| `/api/system/users/my-profile` | GET | code=0；admin 手机号 `13800000000`、`version=0`、`passwordChangeAllowed=true` |
| `/api/learner/history?pageNum=1&pageSize=5` | GET | code=0，`total=83` |
| `/api/learner/messages` | GET | code=0，5 条 |
| `/api/learner/messages/stream` | GET(SSE) | HTTP 200，`text/event-stream`，`event:messages data:{"total":5}` |
| `/api/learner/home/tasks` | GET | code=0 |

负例（全部符合预期）：明细 `BOGUS` → 1001；缺 `id` → 9205；列表非法类型 → 1001；日期倒置 → 1001；`corrections` 无权限 → 1003；`PUT my-profile` 版本过期 → 2040；改密旧密码错 → 1001；坏 JSON → 400；operator01 读 admin 记录 → 9205。注意 `/learner/tasks` 是 9999「No static resource」，真实端点是 `/learner/home/tasks`。

---

## 5. T3 档案更正写路径真实验收（本轮补足）

迁移 `V20261009100000` 首行注释明确「**不自动绑定任何现有角色**」，因此 9722/9723 零角色绑定是**设计如此**，不是缺陷。要取写路径实证需临时授权，做法与回滚如下（全部可回滚，已还原）：

1. 备份：`us_sys_role_menu` 中 9722/9723 无任何行（改动前状态）。
2. 临时授权：`INSERT INTO us_sys_role_menu(role_id,menu_id,create_by,create_time) VALUES (1,9722,1,NOW()),(1,9723,1,NOW());` → `role_menu_id` 633/634。
3. 立即失效权限缓存：`DEL sts:cache:sys:user:perms::2 sts:cache:sys:user::2`（数据类改动不会自动失效缓存）。
4. 目标记录：`admin(userId=2, org2)` 的 `PRACTICE/69`（"端到端验证试卷"，起始 `version=0`、`status=ANSWERING`）。

结果 **17 PASS / 0 FAIL**（脚本 `.workbuddy/tmp/qa-batch4/t3-write-acceptance.mjs`）：

| 用例 | 判定 |
|---|---|
| 授权后 `GET .../corrections` | code=0，`version=0` |
| `POST` 追加第二版 | code=0，`version=1`，展示状态 `ANSWERING→CLEARED` |
| 原始摘要不回写 | `original.status` 仍为 `ANSWERING` |
| 响应/重读带回留痕 | `corrections.length=1`，`before.status=ANSWERING`、`after.status=CLEARED`、`reason` 与 `operatorId=2` 正确 |
| 继续追加 | `version=2`，`corrections.length=2` |
| 负例 版本过期 | 2040「档案已被更正，请重新读取最新版本」 |
| 负例 非法字段 `title` | 1001「更正字段不合法」 |
| 负例 跨领域状态 `PASSED`（PRACTICE） | 1001「状态不属于该来源领域」 |
| 负例 值未变化 | 1001「更正值与当前展示一致」 |
| 负例 理由为空 | 1001 |
| 负例 跨机构人员 999999 | 9205 |
| 本人档案列表仍可读 | code=0，`total=83` |

落库证据（`sts_archive_history_correction`，`before_json/after_json` 为完整 JSON）：`correction_id=3`(v1,`{"status":"CLEARED"}`)、`correction_id=4`(v2,`{"status":"SUBMITTED"}`)，`operator_id=2`、`operator_name` 快照有值、`operated_time` 2026-10-09 10:47。

回滚与复核：`DELETE FROM sts_archive_history_correction WHERE correction_id IN (3,4);`、`DELETE FROM us_sys_role_menu WHERE menu_id IN (9722,9723);`，再 `DEL` 两个缓存键。复核：账本 0 行、临时绑定 0 行；`GET`/`POST` 该端点恢复为 1003「无此权限：archive:history:correction:list / correct」。dev 库已回到改动前状态。

---

## 6. 真实渲染验收

### 6.1 PC 三页（puppeteer-core + 系统 Chrome，headless）

| 页面 | 几何与结构 |
|---|---|
| `/user-center/profile` | `heroCount=1`、`x=16`、`w=1408`、`radius=12px`、`padding=32px`、`formCards=3`、无横向滚动 |
| `/user-center/history` | `listCards=20`、`covers=20`、`chips=20`、`sectionHead=1`、`splitActions=20`、`toolbar=1`、`共 83 条` |
| `/user-center/messages` | `listCards=5`、`covers=5`、`chips=5`、`共 5 条` |

### 6.2 H5 三页（`--deviceScaleFactor=2`，390×844）

登录自动化踩坑：uni H5 的 `<button>` 编译为自定义元素 `<uni-button class="submit-button">`，`querySelectorAll('button')` **恒为空**；且未登录时 App 只在目标页弹「去登录」对话框、**不做路由跳转**，所以必须先落到 `#/pages/auth/LoginPage` 填表提交。修正后 1s 内取到 `safety-training-token`。

| 页面 | 几何与结构 |
|---|---|
| `pages/history/index` | `heroW=357`、`radius=14.56px`、`eyebrow=MY HISTORY`、`cards=20`、20 个「原始记录」link、20 个 `learner-actions--split`，真实数据 |
| `pages/messages/index` | `heroW=357`、`heroTop=56`、`eyebrow=MY MESSAGES`、`cards=5`、`共 5 条` |
| `pages/profile/edit` | `heroW=357`、`eyebrow=MY PROFILE`、`cards=3`、`inputs=6`；只读区实测「账号 admin / 机构 示例企业 / 部门 发展管委会 / 角色 admin / 身份 INTERNAL」 |

三条硬校验：`rawHex=false`（无遗留裸色值）、`docScrollW==docClientW==390`（无横向滚动）、`.learner-input` 实测盒高 **37px**（未踩「column 父容器把 `flex-basis` 当高度、输入框撑成大空盒」的老坑）。`learner-hero__deco` 虽 `getBoundingClientRect().right > 视口`，但 `.learner-hero{overflow:hidden}` 已裁剪，不影响文档宽度。

截图与报告：`.workbuddy/tmp/qa-batch4/shots/`（`pc-ui-report.json`、`h5-ui-report.json`、`h5-geometry.json` 及同名 PNG）。一键复跑：`bash .workbuddy/tmp/qa-batch4/run-suite.sh`。

---

## 7. 本轮新增/变更的公共 UI 契约

- PC `styles/portal.less` 新增 `.portal-toolbar`（横向节奏）与 `.portal-toolbar .el-input{width:240px}`。出现第 5 个页面需要同一规格，故进公共样式而非页面 scoped。
- PC `styles/portal.less` 新增 `.portal-form-card`，供本人资料类表单页复用。
- PC `constants/enum-options.ts` 新增 `MEMBER_TYPE_OPTIONS`（内部成员/外部成员/监管成员），与用户管理共用同一中文口径。
- H5 未新增公共类，全部复用既有 `learner-*` 骨架与全局 `.segmented-filter`。

---

## 8. 双轴复核（T2 / T3）

对 T2、T3 各做「规格一致」+「代码质量」两轴独立复核。

### 8.1 规格轴（结论：一致）

| 规格要求（`spec-delta.md`） | 实现落点 | 判定 |
|---|---|---|
| 只允许本人编辑姓名/手机号/邮箱并使用版本校验 | `MyProfileRequest` 仅三字段 + `@NotNull version`；`users.updateWithVersionCheck` | ✓ |
| 专用本人响应读真实手机号，保留管理端脱敏 | `MyProfileVO` 独立于管理 `UserVO`；`read()` 直取 `user.getPhone()` | ✓ |
| 改密校验旧摘要并复用 BCrypt | `passwords.matches(old, credential)` + `passwords.encode(new)` | ✓ |
| 禁止日志记录凭证 | `MyPasswordRequest @ToString(onlyExplicitlyIncluded)`、两个端点 `@OperLog(saveRequestData=false, saveResponseData=false)`、`@JsonProperty(WRITE_ONLY)` | ✓ |
| 成功后撤销账号会话 | `afterCommit(() -> StpUtil.logout(userId))` | ✓ |
| 含各来源已有历史 | Mapper 六源：`COURSE`/`FORMAL_EXAM`/`COURSE_QUIZ`/`PRACTICE`/`SIMULATION`/`TRAINING` | ✓ |
| 保持成绩/答案可见性 | 每来源独立 `score_visible`（练习/模拟恒 0；考试按 `RESULT_EFFECTIVE`/`PUBLISHED`；培训需 `ENDED` + 已定稿） | ✓ |
| 更正 MUST 校验专用权限 + 原记录读取权限 + 机构 + 理由 + 字段类型 + 版本 | `@SaCheckPermission` + `StpUtil.checkPermission`、`requireRead(type)`、`TenantContext.requireTenantId()` 三重机构约束、理由非空 ≤500、`validateValue` 按来源白名单、版本不符 2040 | ✓ |
| 保留前后值与操作者时间 | `before_json`/`after_json`/`operator_id`/`operator_name` 快照/`operated_time` | ✓ |
| 只追加，不覆盖正式事实/冻结资产/证书 | Mapper 仅 `append`（注释明示「无更新或删除方法」），来源表只读 | ✓ |
| 学员只读本人记录与留痕 | `LearnerHistoryController` 仅两个 GET，无 `userId` 入参 | ✓ |

契约面：四个端点族分别登记于 `docs/api/system-admin.openapi.json`（`my-profile`/`my-password`）、`learner.openapi.json`（`learner/history`）、`archive.openapi.json`（`archive/persons`）；`archive.openapi.json` 的 `x-permission` 写作「`archive:history:correct` AND 对应来源读取权限」，与实现的双重校验一致；`LearnerHistory` = `recordType,recordId,title,original,current,version,reference,corrections`、`HistoryCorrection` = `correctionId,version,before,after,reason,operatorId,operatorName,operatedTime`、`HistoryCorrectionRequest` required = `version,changes,reason` —— 与实现**逐一吻合**；`memberType` 契约枚举 `INTERNAL/EXTERNAL/REGULATOR` 与新增 `MEMBER_TYPE_OPTIONS` **完全一致**。

### 8.2 质量轴（结论：通过，4 条非阻断观察）

**优点**：Mapper 只暴露一个写方法；所有查询 `#{}` 参数化且强制 `organization_id + user_id` 双约束（无注入面、无越租户读）；`lockUser` 行锁 + 唯一键 `uk_history_version` 双重并发保障；`changePassword` 用 `FOR UPDATE` + 实体乐观锁；`afterCommit` 保证事务回滚时不误撤销会话 / 误失效缓存。

| # | 观察 | 严重度 | 建议 |
|---|---|---|---|
| 1 | `correct()` 先 `source(user,type,id)` 再 `detail(user,type,id,true)`，而 `detail()` 内部又调 `source()`，而 `source()` 每次都跑 `sources(user)`（4 条 SQL）⇒ 单次更正多约 4 条查询 | 低（性能） | 把已取的 `source` 传进 `detail` 复用 |
| 2 | 权限在 Controller `@SaCheckPermission` 与 Service `StpUtil.checkPermission` 各校验一次 | 无（有意） | 维持：属 defence-in-depth，注释已说明 |
| 3 | `corrections()` 硬编码 `admin=true` ⇒ 留痕视图 `visible=true`，始终展示成绩 | 低（认知） | 该端点仅对持 `correction:list` 的管理者开放，符合「管理端可见」口径；**不可当学员视图复用** |
| 4 | 契约 `LearnerHistory` 未标 `required`（实现恒返回 8 字段） | 低（契约精度） | 可补 `required` 列表 |

---

## 9. 用户决策与本轮执行结果

| 决策项 | 用户选择 | 执行结果 |
|---|---|---|
| T2 改密真实交互取证 | **授权执行并回滚** | ✅ **已完成**（库恢复后于 2026-10-09 11:26 执行）：`DEMO/operator01` 走「改密 → 旧会话立即失效 → 旧密码登录被拒 → 新密码可登录 → 改回原密码 → 复核恢复」，**10 PASS / 0 FAIL**，operator01 凭证已还原为初始口令。脚本 `.workbuddy/tmp/qa-batch4/t2-password-acceptance.mjs`（口令走环境变量）。上一次执行受阻的根因是共享开发库连接超时（`ERROR 2003 (10060)`），非策略阻断；脚本前置检查正确拦截、未做任何改动。 |
| `operator01.phone` 掩码污染 | **暂不复位，仅在文档登记** | 未改任何数据；成因与结论已写入 §2.1 与补漏清单 50 项 |
| 临时构建配置 | **删除** | 已删除两个 `vite.qa-ui.config.ts`；改由 `run-suite.sh` **按需生成 + 退出时 trap 清理**，已实测生成后 PC/H5 构建均 `exit=0`。另将更早会话遗留的 `frontend/{pc-web,mobile-app}/vite.batch4-qa.config.ts`（代理指向已失效的 20082、无任何引用）**移出源码树**到 `.workbuddy/tmp/qa-batch4/legacy/` 留存，源码树已无任何临时构建配置 |
| 双轴 Review | **现在执行** | 已完成，见 §8 |

`run-suite.sh` 生成配置时的坑（已写进脚本注释）：**不要**写 `emptyOutDir: true` —— 产物目录在项目 root 之外，vite 默认会跳过清空；显式置 `true` 会触发本机 sandbox 的 `SAFE_DELETE_BULK_CONFIRM_REQUIRED` 批量删除拦截，导致 H5 构建 `exit=1`。

---

## 10. 仍开放项（未标记完成）

> **2026-10-09 11:2x 更新**：第 1、2、5 项已于本轮后续解除，详见 `engineering/qa/2026-10-09-batch4-t1-closure-and-final-verification.md`。T2 改密取证完成（10 PASS / 0 FAIL）；T1 业务关闭条件（截止窗口 / 培训生命周期 / 失效消息 / 课程上下架）已完成真实验收；遗留临时构建配置已移出源码树。本清单仅保留真正的残留项。

1. ~~**T2 改密真实交互取证**~~ → **已解除**：库恢复后执行 **10 PASS / 0 FAIL**。
2. ~~**T1 自身的业务关闭条件**~~ → **已解除**：截止窗口 4/4、培训生命周期 12/12、课程上下架 9/9、失效打开 8155，均已真实取证。
3. **`operator01.phone` 掩码污染**：按用户决定暂不复位（既有开发库数据，非本批引入）。
4. **双轴 Review 的 4 条观察**：均为低危，未改动代码；如需收口（尤其观察 1 的重复取源）另行安排。
5. ~~**仓库内遗留的过期验收配置**~~ → **已解除**：两个 `vite.batch4-qa.config.ts` 已移出源码树至 `.workbuddy/tmp/qa-batch4/legacy/`。
