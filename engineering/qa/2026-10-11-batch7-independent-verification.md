# 第七批（T1～T7）独立真实验证记录

日期：2026-10-11　执行：WorkBuddy（小王）　对象：`engineering/qa/2026-10-11-batch7-training-matrix-and-dispatch.md`、`engineering/qa/2026-10-11-batch7-workbuddy-handoff.md`
证据根目录：`.workbuddy/tmp/`（`batch7-unit.log`、`batch7-pc-tests.log`、`batch7-it.log`、`batch7-flyway.log`、`batch7-cacheprobe.log`、`b7-baseline.log`、`b7-mine21.log`、`b7-tc2.log`）

## 一、结论摘要

| 声明项 | 本次实测 | 判定 |
|---|---|---|
| 迁移/菜单/schema 三处同步 | 4 个迁移在册，`schema-version` 与两个 IT 常量三处一致；菜单 9830~9833/9840/9841/9850~9852 在册；OpenSpec strict 通过 | **证实** |
| 后端单元测试全通过 | 18 个目标类共 **198 项 0 失败**，BUILD SUCCESS | **证实** |
| 第七批新增前端测试全通过 | 13 个新增/改动测试文件 **95 项 0 失败** | **证实** |
| PC typecheck / 目标 lint | typecheck `EXIT=0`；23 个改动文件 lint `EXIT=0`，零告警 | **证实** |
| 「SQL/Flyway/锁/回滚待 Docker 恢复后补验」 | **Docker 本轮可用**，7 个新增 IT 首次实跑：**17 项 6 失败 7 错误，仅 1 项通过** | **未闭合**（见第四节） |
| Flyway 升级不扩权 | 4 个升级用例真实通过（96.05s） | **证实，可关闭该子项** |
| 「exam-management 7 项 HEAD 基线失败维持」「本轮未修改这些考试业务文件」 | 21 个既有测试文件基线对照：HEAD 13 失败 → 现状 17 失败，**新增 4 项**（全部指向本轮改写的 `ExamAssignmentPanel.vue`） | **不成立**（见第五节） |

**总判定：53 / 71 / 72 / 73 / 85 均不能回填为已完成。**代码层（单测、类型、样式、静态契约）成立；但数据库层验证首次实跑即失败、前端另引入 4 项新增契约失败。QA 记录中"待 Docker 恢复后补验"的若干条 IT，**不能按"恢复即可通过"回填**。

## 二、静态核对

- 迁移在册且有序：`V20261011090000__add_person_classification.sql`、`V20261011110000__add_training_matrix.sql`、`V20261011120000__add_training_plan_approval.sql`、`V20261011130000__add_training_plan_change.sql`。
- 版本三处一致：`sts-service-admin-init/application.yml` 的 `sts.init.schema-version: 20261011130000` = `AbstractMysqlIntegrationTest.INIT_SCHEMA_VERSION` = `FlywayMigrationIT.INIT_SCHEMA_VERSION`。
- 新增菜单均在 `data/menus.json`：9830/9831/9832/9833、9840/9841、9850/9851/9852（9851 为 9852 父级）。
- `bash /d/nodejs/openspec validate 2026-10-10-complete-batch7-training-matrix-and-dispatch --strict` → `is valid`；`git diff --check` 无输出。

## 三、后端单测与前端定向（全通过）

后端（`backend/`，JDK21 + `-pl sts-service-admin -am test`，18 个目标类）：

```
Tests run: 198, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

分项：ExamServiceImplTest 48、TrainingWorkflowServiceImplTest 34、TrainingPlanServiceImplTest 33、TrainingPlanPublishValidatorTest 23、TrainingPlanApprovalServiceTest 9、TrainingPlanChangeServiceTest 9、PersonClassificationServiceTest 8、PersonSelectionServiceImplTest 6、TrainingMatrixServiceTest 5、TrainingPlanChangeSafetyTest 5、TrainingAnnualPlanServiceTest 4、LearnerNotificationServiceTest 4、PersonProfileServiceTest 3、ExamReviewerEligibilityTest 2、LearnerMessageServiceTest 2、PersonClassificationSqlTest 1、PersonPickerServiceImplTest 1、PersonPickerControllerWebSliceTest 1。

> 与 QA 记录的小差异：`TrainingPlanServiceImplTest` 实测 33 项（记录写 32），应为后续补测，非失败。

前端（`frontend/pc-web/`）：

- 新增/改动测试 95 项全通过（`person-classification`、`training-matrix`、`training-workspace-ui`、`person-picker`、`person-selection-dispatch`、`training-composition-linkage`、`exam-batch7-dispatch`、`message-exams`、`learner-home-message`、`training-learner-api` = 83 项；`training-approval`、`training-change`、`training-task-dispatch` = 12 项）。
- `vue-tsc --noEmit` → `EXIT=0`，无输出。
- 对 23 个改动/新增 `.ts`/`.vue` 跑 eslint `--max-warnings=0` → `EXIT=0`（`src/types/components.d.ts` 被仓库 eslint 配置忽略，不计入口径）。

## 四、数据库 IT：Docker 可用，首次实跑 17 项仅 1 项通过

命令：`mvn -pl sts-service-admin -am test -Pintegration-test -Dtest=PersonClassificationIT,PersonDispatchIT,TrainingMatrixIT,TrainingMatrixTransactionIT,TrainingPlanApprovalIT,TrainingPlanChangeIT,ExamBatch7DispatchIT`（容器 `mysql:8.0.45`，本机已缓存）。

| IT | 结果 | 直接现象 |
|---|---|---|
| PersonClassificationIT | 5 跑 3 失败 | `departmentsIncludeDescendants...:49`、`trainingMembershipAndRewards...:63` 改数据后查询未变；`profileClearingAndStaleWrite...:83` 期望 0 实得 1 |
| PersonDispatchIT | 1 跑 1 失败 | `currentMembersChangeWhileAssignedSnapshotRemains:42` 期望抛异常未抛 |
| TrainingMatrixIT | 1 跑 1 失败 | `matrixVersionAndImmutableSourceAreIndependent:28` 过期写未被拒 |
| TrainingMatrixTransactionIT | 1 通过 | 唯一通过项 |
| TrainingPlanApprovalIT | 3 跑 1 失败 1 错误 | `:49` `BusinessException 培训计划版本冲突`；`annualAssociation...` 期望"回滚验证"实得"版本冲突" |
| TrainingPlanChangeIT | 4 错误 | 上下文加载失败：`TrainingPlanChangeService` 构造参数期望 `TrainingPlanServiceImpl`，实得 `jdk.proxy2.$Proxy255` |
| ExamBatch7DispatchIT | 2 错误 | `@Autowired ExamServiceImpl` 期望实现类，实得 `jdk.proxy2.$Proxy159` |

### 逐条根因（已分类，均为测试夹具缺陷，非生产缺陷）

1. **MyBatis 一级缓存 vs JdbcTemplate 旁路写入**（3 例：PersonClassificationIT×2、PersonDispatchIT×1）。
   同一事务内参数完全相同的二次查询命中 SqlSession 级 L1 缓存；中间的数据修改走 `JdbcTemplate`，MyBatis 无从感知、不清缓存，于是返回改前结果。
   **判定实验**：以 `-Dmybatis-plus.configuration.local-cache-scope=STATEMENT` 重跑 → PersonClassificationIT 3 失败 → **1 失败**，PersonDispatchIT 1 失败 → **0 失败**，仅剩版本相关那 1 例。根因确证。
2. **乐观锁版本未建立**（4 例：PersonClassificationIT 1、TrainingMatrixIT 1、TrainingPlanApprovalIT 2）。
   `insert` 后实体 `version` 仍为 `null`（`MybatisMetaObjectHandler` 的 `strictInsertFill("version")` 对未标注 `@TableField(fill=...)` 的字段不生效，实际靠 DB 默认值 0），MP 拦截器对 null 版本直接跳过版本条件与自增：
   - `PersonClassificationIT:82` 首次 `updateById` 返回 1 但版本未推进（试跑实证：由 `cacheprobe` 隔离后的唯一残留失败）；
   - `TrainingMatrixIT:28` 同机制，过期写"看似成功"；
   - `TrainingPlanApprovalIT:49` 直接传 `p.getVersion()`（=null）给 `submit`，与 `lockForEdit` 读到的 0 不等 → 8104「培训计划版本冲突」。
   即：**这 4 例没有真正走到乐观锁分支**，其"验证乐观锁"的声明不成立。
3. **测试切片注入实现类而非接口**（6 例：ExamBatch7DispatchIT 2、TrainingPlanChangeIT 4）。
   `ExamServiceImpl` / `TrainingPlanServiceImpl` 被 AOP 代理后类型为 JDK Proxy，`@Autowired` 按实现类注入必然失败。
   附带**潜在生产风险**（需人工决策）：任何"insert 后未回读、直接 `updateById` 同一对象"的写法，乐观锁会静默失效（本条为代码分析结论，未在真实业务路径复现）。

### 已真实通过、可回填的部分

`FlywayMigrationIT#shouldUpgradePersonClassificationWithoutGrantingExistingRoles+shouldUpgradeTrainingMatrixWithoutGrantingExistingRoles+shouldUpgradePlanApprovalWithoutGrantingExistingRoles+shouldUpgradePlanChangesWithoutGrantingExistingRoles` → **Tests run: 4, Failures: 0, Errors: 0（96.05 s）**。
即"新库 init 全量 → 增量升级 → 人员/矩阵/计划保留、新表与新菜单建立、**既有角色不自动扩权**"这一条已获真实 MySQL 证据。

## 五、前端回归对照：新增 4 项契约失败

方法：`git worktree add --detach .workbuddy/tmp/baseline-wt HEAD` 建 HEAD 基线（`frontend/node_modules`、`frontend/pc-web/node_modules` 建 junction），对 **HEAD 中已存在的 21 个受影响测试文件**逐一致跑，按**用例名**比对（编号不可比）。跑完已删除联接→删除工作树→`git worktree prune`，依赖完好（`frontend/node_modules` 635 项、`pc-web/node_modules/@vue` 正常）。

| 组 | tests | pass | fail |
|---|---|---|---|
| HEAD 基线 | 191 | 178 | 13 |
| 当前工作区 | 191 | 174 | **17** |

差集（**新增 4 项，全部失败**，无一项消失）：

| 新增失败用例 | 所在测试文件 | 直接原因 |
|---|---|---|
| 负责人和批阅人均从所属机构的人员选择器选择 | `exam-management.test.mjs` | 断言 `/PersonPickerDialog/`，本轮已改为统一 `PersonSelectionDialog` |
| 人员配置保存成功后可进入发布考试步骤 | `exam-workspace-navigation-and-paper-defaults.test.mjs` | 断言 `configurationSaved = computed(() => candidateTotal.value > 0)`，实现已改为 `actualCandidateCount.value` |
| 人员配置和发布核查各自只加载一次人员数据，并向工作台回传统计 | 同上 | 同上（`emit('saved', candidateTotal.value, ...)` 签名/取值已变） |
| 草稿人员配置允许保存空阅卷人数组 | 同上 | 断言整段旧实现文本 |

四个用例共同读到 **`ExamAssignmentPanel.vue`（本轮改动，`PersonPickerDialog` 2 处 → 0 处）**，即根因是"T7 改写了人员配置面板的语义与人员选择组件，但没有同步更新既有静态契约测试"。

> 与 QA 记录的直接冲突：记录称"扩大检查 `exam-management.test.mjs` 共 17 项，10 通过/7 失败；在 HEAD 导出基线复现相同 7 项""已有 exam-management 7 项 HEAD 基线失败维持单列"。实测 `exam-management.test.mjs` 为 **9 通过/8 失败**，其中 1 项（负责人和批阅人均从所属机构的人员选择器选择）**是本轮新增**；另有 3 项新增失败在 `exam-workspace-navigation-and-paper-defaults.test.mjs`（该文件本轮未被检查）。基线 13 项存量失败确实未变化。

## 六、本次未验证的边界

- 未做真实登录、浏览器与真机验收（与 QA 记录一致，不伪造）。
- 短信通道按用户确认延期，`85` 仍为部分完成。
- 因第四节夹具缺陷，以下**仍无有效证据**：分类交并集/年龄边界的真库 SQL、岗位与标签启用状态、撤回/逻辑删除排除、矩阵来源冻结与乐观锁、计划行锁串行、审批/变更事务回滚、考试人员组展开的持久化与替换。
- 第五节 4 项新增失败是否为"同意接受的行为变化"需人工决策（要么更新测试断言，要么回退面板语义）。

## 七、建议的下一步（按优先级）

1. 修 IT 夹具（不改生产代码）：① 改数据后 `SqlSession.clearCache()` 或改用 MyBatis 通路写入；② insert 后**回读实体**再取 `version` 参与更新/提交；③ 测试切片按接口注入（`ExamService` / `TrainingPlanService`）。
2. 修完重跑第四节 7 个 IT + 4 个 Flyway 升级用例，用 `Tests run:` 逐类结果回填 53/71/72/73/85。
3. 同步更新 `ExamAssignmentPanel.vue` 相关的 3 项断言（`exam-management` 1 项 + `exam-workspace-navigation-and-paper-defaults` 3 项中的对应用例），使前端恢复"零新增失败"。
4. 回填口径：仅"migration/init/schema 同步 + 升级不扩权"**可以**标记完成；其余保持未勾选，并按 `2026-10-11-batch7-workbuddy-handoff.md` 的 11 步做真实环境验收。

## 八、修正后的记录口径

- `engineering/qa/2026-10-11-batch7-training-matrix-and-dispatch.md` 中"`exam-management` 7 项基线失败维持""本轮未修改这些考试业务文件"应更正为"**基线 13 项存量失败维持，另有 4 项新增失败指向本轮改写的 ExamAssignmentPanel**"。
- 该文件中"Docker 不可用，SQL IT 未通过"应更正为"**Docker 可用后首次实跑 17 项仅 1 项通过，根因为 IT 夹具缺陷；Flyway 升级 4 项已真实通过**"。


## 2026-10-12 失败修复与最新复验
本次数据库17项及Flyway升级4项共21项真实通过；PC定向75项、选择器目标1项、H5定向14项通过，考试回归恢复HEAD相同7项存量失败、零新增失败。修复仅涉及测试夹具与旧选择器断言，两轴Review通过。详情见 engineering/qa/2026-10-12-batch7-fix-and-reverification.md。本段覆盖此前Docker阻塞及本批新增失败口径；仍待真实界面验收，短信延期，整项不关闭。按用户要求运行配置不提交，新库init需显式同步schema-version 20261011130000。
