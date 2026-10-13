# 任务

## T1 契约、数据库与菜单收敛 [x]

- **Owner**：前后端
- **输入**：功能项 67、已确认 design D1～D5。
- **输出**：OpenAPI 删除旧接口；Flyway 回填门槛、删除材料规则表和菜单，课程设置表改为暂时隐藏；init
  schema、schema-version、菜单基准同步。
- **数据影响**：迁移现有有效时长；删除 `sts_course_item_material_rule`；保留 `sts_course_setting` 及历史值并更新 COMMENT。
- **验收**：新旧库均无课程设置菜单和材料规则表；课程设置表保留且明确标注暂时隐藏；ASSET 门槛按既有优先级保留。
- **测试**：迁移 IT、菜单权限一致性测试、OpenSpec strict 校验。

## T2 后端单字段完成闭环与旧快照兼容 [x]

- **Owner**：后端
- **输入**：内容项 `minimumStudySeconds`、学习会话有效秒数、旧版本快照。
- **输出**：类型相关校验、新快照单字段、旧快照只读兼容、素材引用统计收敛。
- **前后端影响**：课程内容项 Contract 删除材料规则相关接口和字段。
- **验收**：ASSET 正数可保存发布；0/负数拒绝；非 ASSET 只接受 0；新旧快照均按约定完成。
- **测试**：Service、Web slice、快照和学习完成判定定向测试。

## T3 PC 管理端单表单与验收文档 [x]

- **Owner**：前端
- **输入**：收敛后的 Item Contract。
- **输出**：删除课程设置页和材料规则面板；ASSET 只保留一个全宽时长控件并随内容保存。
- **前后端影响**：PC 管理端 API、Mock、页面和测试；学员端无需改动。
- **验收**：仅 ASSET 展示要求学习时长，正数校验；排序独占一行；不存在第二个材料规则保存动作。
- **测试**：PC 定向测试、lint、typecheck、build；真实角色端到端验收。

## T4 内容项表单体验与「参与后锁定」收敛 [x]

- **Owner**：前端
- **输入**：内容项检查器（`CourseItemInspector`）在 340～420px 窄栏下的实际渲染问题；`isLockedAfterParticipation` 的原始设计语义。
- **输出**：`DurationInput` 改为可伸缩布局；「参与后锁定」仅对 `QUESTION_BANK` / `QUIZ` 展示并在保存时对其余类型强制 0；Course OpenAPI 字段描述标明适用范围。
- **前后端影响**：仅前端与 OpenAPI 描述，无接口行为变化。
- **验收**：时长三格在检查器最窄列内单行铺满、不换行不裁切数字；素材 / 直播类型不再出现「参与后锁定」。
- **测试**：PC 定向测试 + lint + typecheck + build。

## 验证记录

### T1

- `openspec validate 2026-09-20-simplify-course-item-learning-rule --strict`：`Change ... is valid`。
- `mvn -pl sts-service-admin-init -am test "-Dtest=MenuPermissionConsistencyTest" "-Dsurefire.failIfNoSpecifiedTests=false"`：
  5 例中 4 例通过、1 例失败；失败为存量问题，与本 Change 无关（详见「遗留」）。
- `mvn -pl sts-service-admin -am test-compile`：通过，新增
  `FlywayMigrationIT#shouldCollapseMaterialRuleIntoItemStudySecondsAndRetainHiddenCourseSetting` 已编译进
  `target/test-classes`（已核对 class 内含新方法名与用例数据）。
- **迁移 IT 未执行**：本机 Docker Desktop 未运行，全部 `*IT` 以 `Could not find a valid Docker environment` 报错退出，无法执行
  MySQL Testcontainers 用例。补跑命令：
  `mvn -pl sts-service-admin -am -P integration-test "-Dtest=FlywayMigrationIT#shouldCollapseMaterialRuleIntoItemStudySecondsAndRetainHiddenCourseSetting" "-Dsurefire.failIfNoSpecifiedTests=false" test`。
- `docs/api/course.openapi.json` 删除 `CourseSettingController` 标签、`CourseItemController` 描述收敛为「内容项 / 直播场次」、
  字段描述统一为「要求学习时长（秒）」；JSON 解析通过。
- 菜单基准：`menus.json`、`menus-back.json`、`current-online-menu-tree.json` 已无 `course:setting:*` 与菜单 302/1311/1312；
  `MenuPermissionConsistencyTest#displayMenuTreeShouldMatchCurrentFrontendMenuTree` 通过（种子树与线上树一致）。
- `application.yml` `sts.init.schema-version` → `20260920190000`；`schema-course.sql` 删除材料规则表定义，课程设置表保留并标注暂时隐藏。
- 文档同步：`docs/architecture/menu-role-matrix.csv`、`menu-permission-configuration-guide.md`、`docs/requirement/system-menu.md`、
  `superadmin-validation-data.md`、`role-workflows.md`、`course-module-task-plan.md`、`docs/architecture/asset-transcode-design.md`。

### T2

- `mvn -pl sts-service-admin -am test "-Dtest=com.sts.course.**" "-Dsurefire.failIfNoSpecifiedTests=false"`：
  34 个单元测试类全绿（`CourseItemServiceImplTest` 12、`CourseLearningServiceImplTest` 22、`CourseSnapshotAssemblerTest` 4、
  `CourseAssetReferenceServiceImplTest` 2、`CourseAssetReferenceControllerWebSliceTest` 2 等）；36 个 error 全部来自需要 Docker 的 `*IT`，非单元失败。
- 编译通过，证明删除的 `CourseSetting*` / `CourseItemMaterialRule*` 无残留引用。
- 旧快照兼容：`ItemSnapshot.materialRule` 标 `@JsonProperty(access = WRITE_ONLY)` 并移出 `@JsonPropertyOrder`；
  `MaterialRuleSnapshot` 只保留 `requiredSeconds`/`source` 且 `@JsonIgnoreProperties(ignoreUnknown = true)`；
  `CourseLearningServiceImpl.resolveRequiredSeconds` 保持「CUSTOM → 内容项 → DEFAULT」旧解析。
- 素材引用统计：`CourseAssetReferenceVO` 删除 `auxAudioCount`，`referenceCount = itemCount + downloadCount`。
- 文案统一：DTO 校验消息、服务错误消息、OpenAPI 描述统一为「要求学习时长」。

### T3

- `vue-tsc --noEmit`（pc-web）：exit 0。
- `eslint <8 个改动文件> --max-warnings=0`：exit 0（顺带清掉 `mock/course-operations.ts` 中 HEAD 即存在的未使用导入 `CourseItemType`）。
- `vite build`（pc-web）：exit 0，仅既有 chunk 体积告警。
- PC 定向 Node 测试（`course-foundation-management` / `course-foundation-mock` / `course-operations` /
  `course-operations-ui` / `course-workflow-optimization`）：**44 例全部通过**（T3 首轮为 44 例 16 失败，均为存量；T4 已把 16 例全部收口，
  详见「验证记录 → T4 → A」）。首轮已做过 HEAD 基线对照（备份本 Change 的 13 个前端文件与已删除的 `CourseSettingsPanel.vue` → 逐条
  `git checkout HEAD --` → 重跑 → 还原并 sha1 校验）：失败用例名与我的版本逐条一致，**零新增失败**。
- 页面口径：`CourseItemInspector.vue` 仅 `itemType === 'ASSET'` 渲染单个「要求学习时长」全宽控件 + 提示文案，
  排序独立成行；`CourseContentPanel.vue` / `CoursePublishPanel.vue` 仅 ASSET 展示「要求学习 / 要求学习时长」；
  材料规则折叠面板与 `saveRule`/`restoreRule` 第二个保存动作已删除。
- **2026-09-21 真实环境验收已完成**：文祥确认由本人在真实环境完成测试验收并通过。详见 `engineering/qa/2026-09-21-simplify-course-item-learning-rule.md`。
- **自动化迁移 IT 未运行**：本地 Testcontainers 无可用 Docker 环境；不影响已完成的真实环境业务验收，但集成测试结果仍单独待补。

### T4

- `vue-tsc --noEmit`（pc-web）：exit 0；`eslint src/components/duration/DurationInput.vue
  src/views/admin/business-center/course/components/CourseItemInspector.vue src/mock/course-operations.ts --max-warnings=0`：exit 0。
- `vite build`（pc-web）：`✓ built in 1m 12s`，仅既有 chunk 体积告警。
- **A. 收口 16 例存量失败**（5 个课程测试文件，全部为测试落后于已合并实现，非放宽断言）：
  1. 4 处路径常量改指现行文件：`course/detail/CourseWorkbench.vue` → `course/workspace/index.vue`、
     `course/detail/course-workspace-state.ts` → `course/workspace/course-workspace-state.ts`、
     `course/{components/→}AssetPickerDialog|asset-picker-state` → `src/components/asset-picker/*`（8 例转绿）。
  2. 删除已并入工作台面板的 `course/offering/index.vue` 常量（3 处引用改为只读面板）。
  3. `课程内容项覆盖…五种类型` → 改为断言现行四种开放类型 `ASSET/LIVE/QUESTION_BANK/QUIZ`，并断言不得再出现 `EXAM`/`PRACTICE`
     （依据已归档规格 `course-assessment-source-separation`）。
  4. 章节弹窗断言加 `\s+` 适配多行标签；内容面板改为「唯一弹窗是章节表单」；开课面板断言从内联 `class="offering-editor"`
     改为受控弹窗 `v-model="editorVisible"` + `class="offering-form-grid"`。
  5. `buildCourseWorkspaceQuery` 断言补第三位 `getCurrentTabTitle()` 参数。
  6. `assignCourseEnrollments` 入参由数组改为 `{ selectionType: 'USER', subjectIds: [...] }`（`PersonSelectionRequest`）。
  7. 乐观锁断言从「面板必须引用常量」改为断言现行机制：请求层保留 2040 业务码（`CourseApiError(response.code, response.message)`）
     + 全局错误处理展示服务端文案；依据 `AppBaseMapper` 2040 文案与 `main.ts#setRequestErrorHandler`。
  - 结果：`--test` 5 个文件 **44 例 44 通过 0 失败**。`eslint tests/*.mjs` 的 `no-undef (URL/Buffer)` 为 HEAD 既有配置缺口
    （已用 `git show HEAD:… | eslint --stdin` 对照：同规则同条数），非本次引入。
  - 新增断言把 T4 的字段规则固定下来（`v-if="supportsLockAfterParticipation"`、类型判定、
    保存时对非考核类强制 0）。
- **「参与后锁定」适用范围的后端现状（只读核查，未改代码）**：`getIsLockedAfterParticipation()` 在全仓 Java 中零调用；
  `CourseSnapshotAssembler` / `ItemSnapshot` 不含该字段；`CourseLearningServiceImpl`、开课与发布流程均不读它。相关且唯一的校验是
  `CourseItemServiceImpl#assertAssessmentReferenceMutable`（无进度且无开课时才允许改题库/试卷引用，否则 5043），**无条件**生效。
- **未执行**：真实浏览器端到端（pc-web dev server 未启动）。时长控件为 CSS 布局修复，仅做了窄栏宽度推算（检查器列 340～420px、
  正文 304px，三格可伸缩后单行铺满），**未做真实渲染截图验证**。


## 遗留

1. **存量菜单权限不一致（非本 Change 引入，且不可能在本 Change 内修完）**：
   `MenuPermissionConsistencyTest#menusShouldMatchEveryControllerPermissionAndContainNoUnsupportedPermission` 要求
   `menus.json` 权限集与全部 `@SaCheckPermission` **完全相等**。实测差异：菜单多出 `asset:asset:preview`、`training:offline-score:edit`
   （无 Controller 校验；前者被前端 `admin-dynamic-routes.ts` 当作资产页路由权限使用，后者仅存在于 `menus.json` / `menus-back.json` /
   `current-online-menu-tree.json`）；控制器另有 40 个权限码尚无对应菜单（`archive:*`、`certificate:*`、`ops:*`、`regulation:*`、
   `regulator:*`、`survey:*`）。两端都要靠 M4/M5 模块菜单落地才能收敛，属跨模块授权边界，需人工决策后另开 Change。
2. **迁移 IT 尚未执行**：Docker Desktop 未运行，需在有 Docker 的环境补跑（命令见「验证记录 → T1」）。
3. **已部署库需人工执行迁移**：`main.flyway.enabled=false` 时 `V20260920190000__simplify_course_item_learning_rule.sql` 不自动执行；
   该迁移只删除独立材料规则表；课程设置表及历史值保留并标注暂时隐藏。执行前仍需人工确认并备份。
   **版本号说明**：本迁移原拟命名 `V20260920160000`，但 `origin/main` 已有 `V20260920160000__hide_m4_out_of_scope_comments.sql`
   （Flyway 不允许重复版本），且上游 `sts.init.schema-version` 已推进到 `20260920180000`、`spring.flyway.out-of-order: false`，
   故改名为 `20260920190000` 并同步 `sts.init.schema-version`。
4. **真实角色端到端验收已完成**：2026-09-21 文祥确认真实环境测试通过；QA 记录保留用户自行验收事实，未补写未提供的截图或日志。
5. `docs/requirement/course-module-task-plan.md`、`docs/architecture/asset-transcode-design.md` 保留原文，另加「同步说明」标注本次变化，
   未改写历史实施清单。
6. **「参与后锁定」字段当前不产生任何后端行为（需人工决策）**：全仓 `getIsLockedAfterParticipation()` 零调用，快照 JSON 不含该字段，
   学习 / 发布 / 开课流程均不读它；唯一相关校验是 `CourseItemServiceImpl#assertAssessmentReferenceMutable`，它**无条件**拒绝修改
   已有参与事实的考核资源引用（5043），不读该开关。另外该字段只在创建时写入（`ItemUpdateRequest` 无此字段、页面编辑态禁用）。
   三选一需文祥拍板：(A) 删除字段（需 migration + 契约同步，属 L4）；(B) 保留并让开关真正驱动校验（会放宽现有已确认的无条件保护，
   需改规格）；(C) 保留现状（仅落库并声明适用范围）。
   **2026-09-20 文祥决策：选 (C) 保留现状**——字段继续只落库，前端只在题库练习 / 课程测验展示；等考核类锁定规则统一设计时再启用。
7. **无用导出**：`src/constants/course.ts#OPTIMISTIC_LOCK_ERROR_MESSAGE` 与 `src/api/course-foundation.ts#isOptimisticLockError`
   在 `src/` 内已无调用方（课程乐观锁冲突现由请求层保留 2040 业务码 + 全局错误处理展示服务端文案「数据已被他人修改，请刷新后重试」），
   仅测试引用。是否清理需人工确认。

## 2026-09-20 冲突收敛记录

- 人工确认沿用已经合并的前端/API收敛：课程设置页面、菜单、权限和 `/api/course/settings` 不恢复。
- 原 migration 尚未在目标环境执行；在执行前将 `sts_course_setting` 从“删除”调整为“保留历史表和值并标注暂时隐藏”。
- `sts_course_item_material_rule` 仍按已确认最小化方案删除，要求学习时长继续收敛到
  `sts_course_item.minimum_study_seconds`。
- PC 课程定向 Node 测试 5 个文件通过；OpenSpec strict、Flyway migration 资源测试、`git diff --check` 通过。
- MySQL 8.0.45 临时库实执行验证：历史行保留 1/1、10 个配置列注释命中 10/10、表注释命中 1/1；临时库已删除。
