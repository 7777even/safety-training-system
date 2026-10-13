# Tasks：课程学习进度上报闭环与完成阈值生效

> 状态：2026-09-17 经人工确认口径后实施完成。
> Owner：fxw11。
> 口径来源：功能项 67 / 74 / 76 / 77；本 Change 的 proposal、design、spec delta。

## Task 1：客户端上报前结算有效学习时长

- **状态**：已完成（2026-09-17）
- **Owner**：fxw11
- **输入**：本 Change 的 design D1；`frontend/pc-web/src/views/portal/class/learning.vue`；
  `frontend/mobile-app/src/pages/course/learning.vue`。
- **输出**：两端 `reportCurrentSession()` 在取值前调用 `syncStudyClock(now)`，覆盖心跳、切换内容项、
  页面卸载、媒体自然结束四条上报路径。
- **前后端影响**：仅前端（PC + 移动端同批）。
- **API 依赖**：无接口、字段、错误码变化。
- **数据影响**：无。仅改变上报请求中 `validStudySeconds` 的取值。
- **验收标准**：
  1. 页面可见、停留于文档类内容项、不切标签页时，心跳上报的 `validStudySeconds` 不为 0。
  2. 切走标签页期间不计入有效秒数。
  3. 可计时媒体暂停期间不计入有效秒数。
  4. 连续两次上报之间不重复计入已结算区间，总有效秒数不超过真实活跃时长。
  5. 两端行为一致。
- **实施与验证**：
  - 改动 2 个文件各 3 行（含注释）：PC 77-80 行附近、移动端 125-128 行附近。
    结算与清零的衔接由 `syncStudyClock` 自身保证——它把区间起点重置为 `now`，上报后清零累计值，
    因此不会重复计入。
  - pc-web `vue-tsc --noEmit` exit 0；`eslint` 该文件 exit 0。
  - mobile-app `vue-tsc --noEmit` exit 0；`eslint` exit 0；`uni build -p h5` → `DONE Build complete`。
  - **真实端到端（2026-09-17 17:04，真实 Chrome + 5173 dev server + 20080 后端 + 测试库）**：
    以 `superAdmin` 登录，进入 `enrollment 1` 的文档类内容项「课程学习」（PDF，无媒体控制），
    静置 100 秒且不切标签页，捕获 3 次心跳上报，`validStudySeconds` = **30 / 30 / 29**，
    请求全部 200；落库 `sts_course_study_session` 200/201/202 三条一致。
    **该现象在修复前为恒 0**（同一账号同一内容项的 16:29 历史会话有效秒数全为 0），
    因此验收标准 1 通过。证据：`.workbuddy/e2e-out/reports.json`、`03-learning-end.png`。
  - 验收标准 2（不可见区间不计入）**已于 2026-09-17 17:25 补做真实端到端**
    （`.workbuddy/e2e-out/visibility-ab.mjs`）：有头 Chrome 新开标签页置前，学习页 `visibilityState`
    实测走完 `visible`→`hidden`→`visible`（未用模拟）；隐藏窗口 60 秒跨过两次心跳，落库
    `sts_course_study_session` 205～208 的「墙钟秒数 / 有效秒数」分别为 30/30、60/10、90/**0**、120/19，
    7 项断言全通过（`visibility-report.json` `passed: true`）。内容项 `study_seconds=59` = 四次上报之和，
    同时构成验收标准 4（不重复计数）的运行时证据。
  - 验收标准 3（可计时媒体暂停不计入）**仍只有代码层守卫**：本轮环境 `sts_asset_asset` 仅有 1 条
    `DOCUMENT` 素材，无视频素材与 `HLS` 派生产物，无法构造真实 HLS 播放场景；
    守卫接线已核对（PC `AssetViewer`、移动端 `AssetPreview` 均绑定 `@play`/`@pause`），详见遗留项 1。
  - 验收标准 5（两端一致）**已于 2026-09-17 17:41 补做真实端到端**
    （`.workbuddy/e2e-out/mobile-learning.mjs`）：移动端 uni H5 走真实 UI 登录进入
    `#/pages/course/learning?enrollmentId=1`，页面渲染 PDF（34 页）+ 水印 + 学时说明，
    静置 80 秒捕获 2 次心跳 `validStudySeconds = 30 / 29`，`device_type = H5`，
    落库会话 209/210；5 项断言全通过（`mobile-report.json` `passed: true`）。

## Task 2：内容项完成阈值三级解析生效

- **状态**：已完成（2026-09-17）
- **Owner**：fxw11
- **输入**：本 Change 的 design D2；`CourseLearningServiceImpl.resolveRequiredSeconds` /
  `maybeCompleteItem`；`ItemSnapshot.minimumStudySeconds`；`MaterialRuleSnapshot.source` /
  `requiredSeconds`。
- **输出**：阈值按「自定义材料规则要求秒数 → 内容项最低学习秒数 → 材料规则默认值」解析；
  阈值为 0 时不再判定完成。
- **前后端影响**：仅后端。
- **API 依赖**：无接口、字段、错误码变化。
- **数据影响**：无表结构与 migration 变化；仅改变完成判定所读的阈值来源。
- **验收标准**：
  1. 未配自定义材料规则、内容项最低学习秒数为 60 的内容项，累计达到 60 秒后转 COMPLETED。
  2. 配了要求学习秒数 120 的自定义材料规则时，累计 30 秒不完成、累计 120 秒完成。
  3. 三级阈值均为 0 时首次上报不直接完成。
  4. QUIZ / QUESTION_BANK 不因秒数达到阈值而完成。
  5. 已 COMPLETED 的内容项不因阈值变化回退。
  6. 报名进度百分比与学习状态按既有规则重算，无额外副作用。
- **实施与验证**：
  - `CourseLearningServiceImpl` 新增常量 `MATERIAL_RULE_SOURCE_CUSTOM = "CUSTOM"`；
    `resolveRequiredSeconds` 改为三级回退；`maybeCompleteItem` 增加 `requiredSeconds <= 0` 提前返回。
  - Red（改动前）：`reportSession_itemMinimumSeconds_takesEffectOverDefaultRule` 期望 `LEARNING`
    实际 `COMPLETED`；`reportSession_missingThreshold_doesNotComplete` 期望 `LEARNING` 实际 `COMPLETED`。
    日志：`Tests run: 21, Failures: 2`。
  - Green（改动后）：`Tests run: 21, Failures: 0, Errors: 0` / `BUILD SUCCESS`。
  - 验收标准 4、5 由既有用例 `reportSession_quizItem_doesNotCompleteFromStudySeconds` 与
    `maybeCompleteItem` 的已完成短路分支覆盖。
  - **真实端到端判定矩阵（2026-09-17 17:02，真实 HTTP 上报 + 测试库，非单测）**：
    内容项 1「课程学习」为 ASSET；快照 `minimumStudySeconds` 与材料规则 `source`/`requiredSeconds`
    由 `.workbuddy/e2e-out/run-matrix.sh` 逐组改写，每组先重置进度再上报，读回落库判定：

    | 组 | 快照 source | 规则要求秒数 | 内容项最低秒数 | 上报秒数 | 实际判定 | 期望 |
    |---|---|---|---|---|---|---|
    | A | CUSTOM | 60 | 30 | 35 | `LEARNING` | ✅ CUSTOM 覆盖内容项门槛 |
    | B | DEFAULT | 60 | 30 | 35 | `COMPLETED` | ✅ 回退到内容项 30s |
    | C | DEFAULT | 0 | 0 | 40 | `LEARNING` | ✅ 阈值缺失不判定完成 |
    | D | CUSTOM | 0 | 30 | 35 | `COMPLETED` | ✅ 要求秒数为 0 视为未配置，回退内容项 |

    **A 与 B 构成新旧代码分水岭**：同一份 `source=DEFAULT` + 35 秒的输入，
    旧代码（16:47 实测，`requiredSeconds=60`）判定 `LEARNING`，新代码判定 `COMPLETED`。
    验收标准 1、2、3、6 由此通过。

## Task 3：移除时长模式可配置性

- **状态**：已完成（2026-09-17）
- **Owner**：fxw11
- **输入**：本 Change 的 design D3；`CourseItemInspector.vue` 材料学习规则面板；
  `MaterialRuleUpdateRequest`；`CourseItemServiceImpl.updateMaterialRule`；
  `frontend/pc-web/src/api/course-operations.ts`。
- **输出**：管理端材料规则面板不再出现「时长模式」；后端放开该字段必填并在为空时补 `AUTO`。
- **前后端影响**：前端（管理端）+ 后端。
- **API 依赖**：依赖 Task 4 同步契约（`durationMode` 移出 `required`）。接口路径与错误码不变。
- **数据影响**：无 schema 变化；`duration_mode` 列继续按 `AUTO` 落库。
- **验收标准**：
  1. 管理端 ASSET 内容项材料规则面板无「时长模式」选择项。
  2. 保存材料规则时不传 `durationMode` 仍成功，数据库写入 `AUTO`。
  3. 老调用方继续传 `AUTO` / `MANUAL` 仍被接受，不报错。
  4. 保存后 `requiredSeconds` 等其余字段行为不变。
- **实施与验证**：
  - 后端：`MaterialRuleUpdateRequest.durationMode` 去掉 `@NotBlank`；`CourseItemServiceImpl`
    新增常量 `DURATION_MODE_AUTO`，`updateMaterialRule` 在字段为空时补 `AUTO`（避免非空列约束失败），
    `buildDefaultMaterialRule` 改用该常量。
  - 管理端：删除「时长模式」单选（`ElRadioGroup`）；`ruleForm.durationMode` 保留 `'AUTO'` 并加注释说明
    仅为兼容后端非空列；`MaterialRule` 类型中该字段改为可选并标注已废弃。
  - 新增用例 `updateMaterialRule_withoutDurationMode_fallsBackToAuto` 验证验收标准 2、3。
  - `CourseItemServiceImplTest` 11 个用例 0 失败；pc-web `vue-tsc` exit 0；两处改动文件 `eslint` exit 0。
  - **真实端到端（2026-09-17 17:04，20080 运行的新代码实例）**：
    `PUT /api/course/items/1/material-rule` 请求体**完全不含 `durationMode`** → HTTP **200**；
    回读 `GET` 得 `durationMode="AUTO"`、`version` 由 1 递增到 2；
    数据库 `sts_course_item_material_rule.duration_mode` = `AUTO`，`watermark_mode` = `SYSTEM`。
    对照：同一探针在旧代码实例（PID 36544）上返回 **400 `durationMode: 时长模式不能为空`**。
    验收标准 2、4 由此通过。管理端面板无「时长模式」项已由页面抓取确认（`07-item-inspector.png`）。
  - 验收标准 3（老调用方继续传 `AUTO`/`MANUAL` 仍接受）由单测覆盖，未在真实接口上复测。

## Task 4：修正 OpenAPI 字段描述与示例

- **状态**：已完成（2026-09-17）
- **Owner**：fxw11
- **输入**：本 Change 的 design D4；`docs/api/course.openapi.json`。
- **输出**：`attemptCount` 描述、`durationMode` 示例值与废弃标注、`minimumStudySeconds` 描述。
- **前后端影响**：仅契约文档。
- **API 依赖**：本 Task 是 Task 3 的契约前置。
- **数据影响**：无。
- **验收标准**：
  1. `attemptCount.description` 说明按学习会话上报次数累加。
  2. `durationMode` 示例值为 `AUTO`，描述标明已废弃且不再参与完成判定。
  3. `minimumStudySeconds` 描述补齐与自定义材料规则「要求学习秒数」的覆盖关系。
  4. `MaterialRuleUpdateRequest.required` 不再包含 `durationMode`。
  5. JSON 合法且与 `MaterialRuleVO` 的 enum 取值一致。
- **实施与验证**：
  - `attemptCount` 描述改为「学习会话次数：每次有效的学习进度上报累加一次，不是作答次数。」
  - `MaterialRuleUpdateRequest.durationMode` 与 `MaterialRuleVO.durationMode` 描述标注已废弃；
    前者从 `required` 数组移除。
  - `minimumStudySeconds` 三处 schema 描述统一为完成门槛口径（含材料规则覆盖说明）。
  - **顺带修正同类错误**：材料规则两处 example 的 `durationMode: "FIXED"`（不在 enum 内）改为 `"AUTO"`；
    `watermarkMode: "NAME"`（不在 enum `[SYSTEM, CUSTOM, NONE]` 内）改为 `"SYSTEM"`。
  - 校验：Node 解析 JSON 通过，并逐项读出 `required`、enum、example、description 复核一致。
  - **2026-09-17 复核补正两处**：① `specs/course-learning-progress/spec.md` 原用 `## Requirements` 头，
    导致 `openspec validate <change> --strict` 报 `No delta sections found`；已改为 `## ADDED Requirements`，
    重新校验输出 `Change '…' is valid`。② `MaterialRuleVO.durationMode` 原描述「恒为 AUTO」与实现不符
    （`getMaterialRule()` 命中已有自定义规则时经 MapStruct 原样回读，历史 `MANUAL` 会返回 `MANUAL`）；
    已改为「不参与完成判定；服务端保存材料规则时统一按 AUTO 落库，历史数据可能仍为 MANUAL」。
  - `git diff --check` 无空白错误。

## 遗留

1. **可计时媒体暂停（Task 1 验收标准 3）无端到端证据**：环境内 `sts_asset_asset` 只有 1 条 `DOCUMENT` 素材，
   没有视频素材与 `HLS` 派生产物，无法构造真实 HLS 播放。要真验需「上传视频 → 转码 → 写
   `sts_asset_derivative`（HLS 行）→ 发布新课程版本」，属需人工确认的数据构造，本期未执行；
   守卫接线（`@play`/`@pause` → `mediaPlaying` → `syncStudyClock`）已静态核对。**需人工决策是否投入**。
2. 验证期间在团队测试库 `enrollment 1` 上真实写入了学习会话与进度数据，验证后已用
   `.workbuddy/e2e-out/threshold-restore.sql` 还原（快照回 `CUSTOM/60/30`，内容项与报名回 `NOT_STARTED/0`）；
   会话行 205～210 保留。
3. 防快进、倍速限制、离页暂停计时、水印四个材料规则字段仍未被客户端消费（功能项 75，延期项）。
4. LIVE 内容项完成路径未定义（直播参与事实缺失），本期维持不自动完成；
   管理端仍可给 LIVE 填「最低学习秒数」，但后端仅对 ASSET 判定，该值不生效。
5. `creditAwarded` 仍恒为 0，学分发放链路未实现。
6. 学习进度表对文档类内容项仍渲染「最近位置(秒) / 结束位置(秒)」为「—」，列口径未调整（本期非目标）。
7. **两端「可计时媒体」判定口径不一致**：PC 用 `mediaFormat === 'hls'`，移动端用 `assetType ∈ {VIDEO, AUDIO}`。
   正常转码路径下等价，仅「视频素材缺 HLS 派生」的退化路径分叉（该路径下两端都放不了）。建议后续统一。
8. 心跳上报 `endReason` 恒为 `USER_EXIT`，周期检查点与用户退出无法区分，做时长对账时容易误读。
9. QA / Retro 已于 2026-09-17 补齐（`engineering/qa/`、`engineering/retro/` 同名文件），
   进度表增量记录见 `engineering/plans/end-to-end-development-progress-tracker.md`。
10. 双轴 Review 已完成（见 QA 文档），发现 2 处规格符合性问题并已修正；代码质量轴留 4 条观察项待后续处理。
