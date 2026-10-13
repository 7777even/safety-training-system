# 第四批 T1 业务关闭与整批收口验收

日期：2026-10-09（第三批）。关联Change：`2026-10-08-complete-batch4-learner-center`。Owner：fxw11。

环境：后端 `AdminApplication` 在 `127.0.0.1:20080`（profile=vpn，共享开发库 `sts`）；前端 PC/H5 为当前工作区未提交源码。前置记录：`engineering/qa/2026-10-09-batch4-t2-t3-and-ui-unification.md`、`engineering/qa/2026-10-09-batch4-verification-guide.md`。

---

## 1. 本轮范围与结论

上一轮结束时遗留两项：**（a）T1 自身的业务关闭条件**（培训生命周期、截止窗口、失效消息、课程上下架的完整真实验收）、**（b）仓库内更早会话遗留的临时构建配置**。本轮全部处理完毕，并补做了被开发库掉线阻塞的 **T2 改密真实交互取证**。

结论：**第四批 47/61（T1）、50 非微信子项（T2）、52（T3）的代码、契约、界面统一与真实验收均已闭合**；50 整项因保留微信子项（随 46）不勾选。

所有验收均在**真实后端 + 真实共享开发库 + 真实无头 Chrome**上完成，写类用例一律**可回滚**并在结束时复核恢复原状（未提交 Git、未部署、未改动生产库）。

---

## 2. T1 业务关闭条件（四项真实验收）

### 2.1 截止窗口（用真实调度器 `LearnerReminderScheduler`）

实现依据：`LearnerReminderScheduler` `@Scheduled(fixedDelay=60000, initialDelay=15000)` 逐租户调 `LearnerNotificationService.scan(now)`；`LearnerMessageService.shouldRemind` 判定 `deadline ∈ (now, now+24h]` 且状态未终态；事件键 `kind:resourceId:itemId:deadline:<endTime>` 由唯一键保证幂等。

样本：`org2` 的 `exam 24`（4 名候选 46/47/48/49 → 用户 1/2/3/4），原始 `open_end_time=NULL`；基线 deadline 消息数 0。

| 步骤 | 结果 |
|---|---|
| 负例：`open_end_time = now-1h`（已过期），等一轮真实扫描 | deadline 消息数 **0**（与基线一致）→ 过期不提醒 |
| 正例：`open_end_time = now+12h`（入 24h 窗口），等一轮真实扫描 | 新增 **4** 条，事件键 `FORMAL_EXAM:{46,47,48,49}:null:deadline:2026-10-09T23:30:54`，各投递对应用户 |
| 幂等：再等一轮扫描 | 仍 **4** 条（不重复投递） |
| 回滚 | 删除 4 条 deadline 消息与接收人；`exam 24.open_end_time` 还原为 `NULL` |

**4 PASS / 0 FAIL**（脚本 `.workbuddy/tmp/qa-batch4/t1-deadline-acceptance.sh`，日志 `t1-deadline.log`）。

### 2.2 培训生命周期（隔离临时样本 + 真实 HTTP）

样本：`org2` 新建临时培训计划（`plan_code=QA_T1_TMP_<ts>`、`publish_status=PUBLISHED`）+ 参训人 `operator01(user 3)`；不触碰任何既有业务数据。

| 用例 | 结果 |
|---|---|
| 真实 HTTP `POST /api/training/plans/{id}/cancel`（`training:plan:publish`） | code=0；`publish_status='CANCELLED'` |
| 消息持久化 | event_key `TRAINING:<participantId>:null:cancelled` 唯一一行，正文含「培训已取消」（HEX 断言，终端 GBK 不干扰） |
| 接收人隔离 | 仅 `user 3` |
| 幂等 | 重复提交 → **8164**「仅已发布或进行中的计划可取消」，消息数仍为 1 |
| 学员侧通知仍可查看 | `GET /api/learner/messages` code=0，列表中含该条（`kind=TRAINING`、`content=培训已取消`） |
| **打开已失效培训** | `GET /api/training/learner/participants/{id}` → **HTTP 404 / code=8155「培训任务对当前学员不可见」**（明确业务原因，非静默成功、非 500） |
| 回滚 | 删除消息+接收人+参训人+临时计划，复核全 0 |

**12 PASS / 0 FAIL**（HTTP 6 + DB 6；脚本 `t1-training-lifecycle-acceptance.sh` + `t1-training-http.mjs`）。

### 2.3 课程上下架（`CoursePublishServiceImpl` 切面 → `catalog` 家族）

实现依据：`LearnerNotificationAspect` 拦截 `CoursePublishServiceImpl.publish/offline`，`businessChanged("catalog",...)` 比较「上下架前后状态键」`publish_status:current_version_id:version`，接收人取 `courseAudience = 报名 ∪ 收藏`。

样本：`org2` 的 `course 2`（`PUBLISHED`、`current_version_id=2`、`version=1`、受众 `user 1,2,3,4`）；基线 `COURSE_CATALOG` 消息数 0。

| 用例 | 结果 |
|---|---|
| 真实 HTTP `PUT /api/course/courses/2/offline`（`{version:1}`） | HTTP 200 / code=0；`publish_status='OFFLINE'` |
| 消息生成 | 新增 1 条 `COURSE_CATALOG`，标题「课程已下架」，事件键 `catalog:2:OFFLINE:2:2:PUBLISHED:2:1`（含前后状态，体现按状态幂等） |
| 受众隔离 | 接收人集合恰为 `1,2,3,4`，与该课程报名受众完全一致 |
| 回滚 | 课程 `publish_status/version/current_version_id/update_by/update_time` 全部还原；消息与接收人清理干净 |

**9 PASS / 0 FAIL**（脚本 `t1-course-catalog-acceptance.sh` + `t1-course-catalog-http.mjs`）。

> 说明：`catalog` 的「课程已上架或更新」文案分支与本次「课程已下架」共用同一 `businessChanged` 代码路径（仅一处三目文案差异），且 `publish()` 在内容未变时会因快照哈希一致而提前返回、不产生状态变化，故未单独构造内容变更样本。

### 2.4 T1 其余已覆盖项

- 首页任务 `GET /api/learner/home/tasks`、消息分页 `GET /api/learner/messages`、SSE `GET /api/learner/messages/stream`（`event:messages data:{"total":N}`）、本人考试须知 `GET /api/exam/learner/exams/{id}/notice` 全 code=0。
- 既有真实数据（开发库已有、由真实业务操作产生）：`COURSE:*:assigned`（课程安排）、`COURSE_QUIZ:*:assigned`（随堂测试任务）、`FORMAL_EXAM:*:assigned`/`opened:*`（考试安排/开考）。

---

## 3. T2 改密真实交互（解除上一轮阻塞）

上一轮因共享开发库连接超时（`ERROR 2003 (10060)`）被前置检查拦截、未做任何改动；本轮库恢复后执行。

链路：`DEMO/operator01`（口令走环境变量）→ 改密 → 旧会话立即失效 → 旧密码登录被拒 → 新密码可登录 → 改回原密码 → 复核恢复。

| 用例 | 结果 |
|---|---|
| 前置 原密码可登录 | code=0 |
| 负例 旧密码错 | **1001**「旧密码不正确」 |
| 负例 非 64 位摘要 | **1001**（`must match "^[a-f0-9]{64}$"`） |
| 正例 改密 | code=0 |
| 旧会话立即失效 | **1002**（token 无效） |
| 旧密码登录被拒 | **1002** |
| 新密码可登录 | code=0 |
| 回滚 改回原密码 / 原密码可登录 / 临时密码失效 | 全部符合 |

**10 PASS / 0 FAIL**；operator01 凭证已还原，未留残留（脚本 `t2-password-acceptance.mjs`）。

---

## 4. 遗留项处置

`frontend/pc-web/vite.batch4-qa.config.ts`、`frontend/mobile-app/vite.batch4-qa.config.ts` 是更早会话遗留的临时验收配置（代理指向已失效的 `127.0.0.1:20082`，无任何脚本/文档引用）。本轮**移出源码树**至 `.workbuddy/tmp/qa-batch4/legacy/` 留存（非破坏性），源码树已无任何临时构建配置；`git status` 不再出现此类噪声文件。验收所需的 vite 配置由 `run-suite.sh` 按需生成并在退出时清理。

---

## 5. 回归复核（本轮复跑）

| 检查 | 命令（cwd） | 结果 |
|---|---|---|
| PC 类型 | `vue-tsc --noEmit`（`frontend/pc-web`） | exit 0 |
| H5 类型 | `vue-tsc --noEmit`（`frontend/mobile-app`） | exit 0 |
| 契约测试 | `node tests/{learner-history,learner-home-message,my-profile-password}.test.mjs` | 3 / 6 / 2 全绿 |
| 定向 ESLint | 9 个被改文件 `eslint --max-warnings=0` | exit 0 |
| 后端定向 | `mvn -pl sts-service-admin -am test -Dtest="..."` | **39 项 0 失败 `BUILD SUCCESS`** |

后端定向覆盖：`DevelopmentMigrationConfigurationTest`(1)、`MyProfileServiceTest`(3)、`ArchiveControllerTest`(4)、`ExamLearnerNoticeTest`(1)、`LearnerAssessmentCenterServiceTest`(2)、`LearnerControllerTest`(1)、`LearnerMessageStreamTest`(1)、`LearnerHistoryServiceTest`(3)、`LearnerHomeServiceTest`(3)、`LearnerMessageServiceTest`(2)、`LearnerNotificationServiceTest`(1)、`OrganizationServiceImplTest`(17)。

---

## 6. 最终真实 HTTP 全端点回归

一次跑完 47/61/50/52 的全部新增端点与关键负例，**16 PASS / 0 FAIL**（脚本 `t-final-smoke.mjs`）：

| 用例 | 结果 |
|---|---|
| T1 `GET /learner/home/tasks` | code=0 |
| T1 `GET /learner/messages` 分页 | code=0，total=5 |
| T1 `GET /learner/messages/stream` | HTTP 200、`text/event-stream`、`event:messages data:{"total":5}` |
| T1 `GET /exam/learner/exams/34/notice` | code=0 |
| T2 `GET /system/users/my-profile` | code=0，`phone=13800000000`、`version=1`、`passwordChangeAllowed=true` |
| T2 `PUT my-profile` 旧版本号 | **2040**「数据已被他人修改，请刷新后重试」 |
| T2 `PUT my-password` 旧密码错 | **1001** |
| T3 `GET /learner/history` 分页 | code=0，total=83 |
| T3 `GET /learner/history/{type}/{id}` 详情 | code=0（`PRACTICE/69`） |
| T3 非法来源类型 | **1001** |
| T3 `corrections` 无权限 | **1003**（未自动授权） |
| 隔离 学员读本人历史 | code=0，total=34 |
| 隔离 学员读 `corrections` | **1003** |
| 隔离 管理员读学员记录 `COURSE/5` | **9205**「本人历史记录或人员不存在」 |

---

## 7. 最终真实渲染（复跑，两端构建 `exit=0`）

### 7.1 PC（puppeteer-core + 系统 Chrome，headless）

| 页面 | 几何与结构 |
|---|---|
| `/user-center/profile` | `heroCount=1`、`heroX=16`、`heroW=1408`、`radius=12px`、`padding=32px`、`toolbar=1`、`formCards=3`、`sectionHeads=3`、无横向滚动 |
| `/user-center/history` | `listCards=20`、`covers=20`、`chips=20`、`sectionHeads=1`、`splitActions=20`、`toolbars=1`、`共 83 条` |
| `/user-center/messages` | `listCards=5`、`covers=5`、`chips=5`、`共 5 条` |

### 7.2 H5（390×844，`deviceScaleFactor=2`，`loggedIn=true` 真实数据）

| 页面 | 几何与结构 |
|---|---|
| `pages/history/index` | `heroTop=56`、`heroW=357`、`radius=14.56px`、`deco=1`、`eyebrow=MY HISTORY`、`cards=20`、20 个「原始记录」link、20 个 split 操作 |
| `pages/messages/index` | `eyebrow=MY MESSAGES`、`cards=5`、`共 5 条` |
| `pages/profile/edit` | `eyebrow=MY PROFILE`、`cards=3`、`inputs=6`；只读区「账号 admin / 机构 示例企业 / 部门 发展管委会 / 角色 admin / 身份 INTERNAL」 |

三条硬校验均通过：`rawHex=false`、`docScrollW==docClientW==390`、`.learner-input` 盒高 **37px**（未踩弹性高度老坑）。`learner-hero__deco` 属装饰圆，已被 `.learner-hero{overflow:hidden}` 裁剪，不影响文档宽度。

截图与报告：`.workbuddy/tmp/qa-batch4/shots/`。一键复跑：`bash .workbuddy/tmp/qa-batch4/run-suite.sh`。

---

## 8. 环境与操作要点（沉淀）

- 开发库为远程 `19.191.192.146:8045`，**会掉线**；写类验收脚本一律先做「前置可登录」检查，失败即**不改动任何数据**退出。
- Node 在 Windows 上 `spawnSync(mysql.exe)` 会偶发 **`EBUSY`**；验收脚本改为 **bash 跑 mysql、Node 只做 HTTP** 的分工，避免该问题。
- 终端为 GBK，库内中文是 UTF-8，直接比对中文会**假阴性**；断言中文一律用 `HEX(...)`。
- 消息表基线：`sts_common_message` / `sts_common_message_recipient`；验收后须同时清理**消息与接收人**两表。
- 数据类改动**不会自动失效 Redis 权限缓存**（`sts:cache:sys:user:perms::<userId>`）。

---

## 9. 结论

- **T1（47/61）业务关闭条件**：截止窗口、培训生命周期、课程上下架、失效消息打开全部真实验收通过；T1 可关闭。
- **T2（50 非微信子项）**：资料读写 + 改密交互真实验收通过；50 整项因微信子项（随 46）**不勾选**。
- **T3（52）**：读路径、写路径、权限与回滚、界面统一全部通过；52 可关闭。
- **整批静态与后端回归**：全绿（`vue-tsc` ×2、契约测试 3/6/2、ESLint、后端 39 项 0 失败）。
- **真实渲染**：PC 3 页 + H5 3 页几何与结构均符合统一设计系统，无横向滚动、无裸色值。
- **数据侧**：所有写类验收均回滚，开发库无残留（exam 24 时间窗还原 `NULL`、course 2 状态还原、临时培训计划与消息全部删除、T3 账本与临时授权已回滚、operator01 凭证已还原）。
