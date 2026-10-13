# QA：课程学习进度上报闭环与完成阈值生效

## 范围

OpenSpec Change：`2026-09-17-close-course-learning-progress-loop` Task 1～4。

- 后端：`CourseLearningServiceImpl`（阈值三级解析）、`CourseItemServiceImpl`（材料规则落库兜底）、
  `MaterialRuleUpdateRequest`（放开 `durationMode` 必填）。
- 契约：`docs/api/course.openapi.json`。
- 前端：PC `views/portal/class/learning.vue`、`views/admin/business-center/course/components/CourseItemInspector.vue`、
  `src/api/course-operations.ts`；移动端 `pages/course/learning.vue`。
- 不变：数据库结构与 migration、权限码、接口路径与错误码、课程完成规则、学分、培训学时投影。

## 业务闭环结论

1. **有效学习时长真实产生**：客户端在每一次上报检查点取值前结算活跃区间，纯文档类内容项的心跳
   `validStudySeconds` 不再为 0；页面不可见区间不计入。
2. **完成阈值真实生效**：ASSET 内容项按「自定义材料规则要求秒数 → 内容项最低学习秒数 → 材料规则机构默认值」
   三级解析，首个大于 0 的值即采用；三档全为 0 时不再判定完成。
3. **「时长模式」退役**：管理端不再展示该设置；不传该字段保存材料规则仍成功，落库 `AUTO`。
4. **契约与实现一致**：`attemptCount`、`durationMode`、`minimumStudySeconds` 三处描述/示例已对齐。

## TDD 与零 DB 验证

Red 阶段（改动前）`CourseLearningServiceImplTest` 两条新用例失败：`Tests run: 21, Failures: 2`；
Green 阶段通过。本轮复核于 2026-09-17 17:43 重新执行：

```text
mvn -pl sts-service-admin -Dtest=CourseLearningServiceImplTest,CourseItemServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：`Tests run: 32, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`
（`CourseItemServiceImplTest` 11、`CourseLearningServiceImplTest` 21）。日志 `.workbuddy/e2e-out/targeted-tests.log`。

覆盖：自定义规则覆盖内容项门槛、缺省回退内容项门槛、阈值全 0 不完成、QUIZ 不按时长自动完成、
已完成不回退、缺 `durationMode` 时落 `AUTO`、老调用方继续传值仍被接受。

## 前端静态验证

pc-web `vue-tsc --noEmit` exit 0；改动文件 `eslint --max-warnings=0` exit 0。
mobile-app `vue-tsc --noEmit` exit 0、`eslint` exit 0、`uni build -p h5` → `DONE Build complete`。
`git diff --check` 无空白错误。

## 真实端到端（真实 Chrome + 真实后端 20080 + 团队测试库）

环境：后端 `localhost:20080`（IDEA 常驻实例，profile=`vpn` → `19.191.192.146:8045/sts`）；
pc-web dev 5173；移动端 uni H5 dev（本轮 5173，端口随占用漂移）。
被测对象：`enrollment 1` 的 ASSET 内容项「课程学习」（PDF，无媒体控制）。

### Task 1 验收标准 1：文档类内容项心跳不为 0

2026-09-17 17:04，以 `superAdmin` 登录，静置 100 秒不切标签页，捕获 3 次心跳
`validStudySeconds = 30 / 30 / 29`（修复前同一账号同一内容项的历史会话有效秒数全为 0）。
证据 `.workbuddy/e2e-out/reports.json`、`03-learning-end.png`。

### Task 1 验收标准 2：切走标签页期间不计入（本轮新增，脚本 `.workbuddy/e2e-out/visibility-ab.mjs`）

真实有头 Chrome，通过新开标签页并置前使学习页进入后台；`document.visibilityState` 实测走完
`visible`(t=4s) → `hidden`(t=47s) → `visible`(t=107s)，未使用任何模拟手段。
隐藏窗口 17:26:34–17:27:34（60 秒）跨过两次 30 秒心跳：

| 会话 id | 墙钟区间 | 墙钟秒数 | `valid_study_seconds` | 说明 |
|---|---|---|---|---|
| 205 | 17:25:49 → 17:26:19 | 30 | **30** | 全程可见 |
| 206 | 17:25:49 → 17:26:49 | 60 | **10** | 隐藏前残余 11 秒 |
| 207 | 17:25:49 → 17:27:19 | 90 | **0** | 该区间全程隐藏 |
| 208 | 17:25:49 → 17:27:49 | 120 | **19** | 恢复可见后重新计时 |

- 断言（`.workbuddy/e2e-out/visibility-report.json`，`passed: true`）：隐藏前心跳 ≥25；
  隐藏窗口内至少一次心跳；隐藏窗口累计有效秒数 ≤10（窗口实际 60 秒）；存在为 0 的心跳；
  恢复可见后重新计时 ≥15；visibilityState 三段完整；4 次上报全部 2xx。
- 内容项 `study_seconds = 59`（= 30+10+0+19），`attempt_count = 4`，与四次上报逐条吻合。

### Task 1 验收标准 4：结算与清零衔接不重复计数

同上轮：墙钟会话时长 120 秒，落库有效秒数 59 秒，恰好等于四次上报之和，无重复累计。

### Task 1 验收标准 5：两端行为一致（本轮新增，脚本 `.workbuddy/e2e-out/mobile-learning.mjs`）

移动端 uni H5 走真实 UI 登录（`input.uni-input-input` 三格回读为 `DEMO / superAdmin / superAdmin123`，
登录 HTTP 200），进入 `#/pages/course/learning?enrollmentId=1`；页面渲染出 PDF 阅读器（`canvas` 存在、
共 34 页）、水印平铺、学时说明文案。静置 80 秒捕获 2 次心跳：

| 会话 id | `valid_study_seconds` | `device_type` | traceId |
|---|---|---|---|
| 209 | **30** | H5 | `h5-course-1-1-…-1` |
| 210 | **29** | H5 | `h5-course-1-1-…-2` |

报告 `.workbuddy/e2e-out/mobile-report.json`，`passed: true`（5 项断言全通过）。

### Task 2：完成阈值三级解析判定矩阵（17:02，真实 HTTP 上报 + 测试库）

内容项 1 的快照 `minimumStudySeconds` 与材料规则 `source`/`requiredSeconds` 逐组改写，每组先重置进度再上报：

| 组 | 快照 source | 规则要求秒数 | 内容项最低秒数 | 上报秒数 | 实际判定 | 期望 |
|---|---|---|---|---|---|---|
| A | CUSTOM | 60 | 30 | 35 | `LEARNING` | ✅ CUSTOM 覆盖内容项门槛 |
| B | DEFAULT | 60 | 30 | 35 | `COMPLETED` | ✅ 回退到内容项 30s |
| C | DEFAULT | 0 | 0 | 40 | `LEARNING` | ✅ 阈值缺失不判定完成 |
| D | CUSTOM | 0 | 30 | 35 | `COMPLETED` | ✅ 要求秒数为 0 视为未配置，回退内容项 |

A 与 B 构成新旧代码分水岭：同一份 `source=DEFAULT` + 35 秒输入，旧代码判 `LEARNING`、新代码判 `COMPLETED`。
复跑脚本 `.workbuddy/e2e-out/run-matrix.sh`。

### Task 3：不传时长模式仍可保存

`PUT /api/course/items/1/material-rule` 请求体完全不含 `durationMode` → HTTP **200**；
回读 `GET` 得 `durationMode="AUTO"`、`version` 由 1 递增到 2；库中 `duration_mode = AUTO`。
对照：同一探针在旧代码实例上返回 400 `durationMode: 时长模式不能为空`。
管理端面板已无「时长模式」项（`07-item-inspector.png`）。

### Task 4：契约核对

2026-09-17 17:40 逐字段读出复核：

- `MaterialRuleUpdateRequest.required` 已不含 `durationMode`；其 `durationMode` 有 `example: "AUTO"`、
  `enum: [AUTO, MANUAL]`、描述标注已废弃与留空按 AUTO 落库。
- `MaterialRuleVO.durationMode` 示例为 `AUTO`，描述标注已废弃（本轮修正措辞，见下）。
- 材料规则两处 `watermarkMode` 示例为 `SYSTEM`，落在 `enum: [SYSTEM, CUSTOM, NONE]` 内。
- `CourseItemProgressVO.attemptCount` 描述改为「学习会话次数：每次有效的学习进度上报累加一次，不是作答次数」。
- `minimumStudySeconds` 三处（`ItemCreateRequest` / `ItemUpdateRequest` / `CourseItemVO`）描述统一补齐
  「ASSET 内容项已配置自定义材料规则时，由材料规则的『要求学习秒数』覆盖此值」。
- `course.openapi.json` 可被 JSON 解析，`git diff --check` 通过。

## 双轴 Review

### 规格符合性

逐条对照 `specs/course-learning-progress/spec.md` 的 4 条 Requirement、11 个 Scenario：

| Requirement | 结论 |
|---|---|
| 有效学习时长按活跃区间累计上报 | 符合。「文档类内容项心跳不为 0」「不可见区间不计入」「结算与清零不重复」三个 Scenario 均有真实端到端证据；服务端只累加请求中的 `validStudySeconds`，不自行推算会话时长 |
| 内容项完成阈值由内容项门槛与自定义材料规则共同决定 | 符合。四级判定矩阵覆盖三档回退与全 0 不完成；QUIZ/QUESTION_BANK 与 LIVE 不按时长完成由 `maybeCompleteItem` 的 `itemType != ASSET` 短路与既有用例覆盖；COMPLETED 不回退 |
| 时长模式不再作为可配置项 | 符合。管理端无该项；不传字段保存成功且落 `AUTO` |
| 契约描述与实现语义一致 | **发现 2 处不符，均已修正**（见下） |

**本轴发现并修正的问题**：

1. **delta 规格头错误（阻断）**：`specs/course-learning-progress/spec.md` 使用 `## Requirements`，
   导致 `openspec validate <change> --strict` 报 `No delta sections found` / `Change must have at least one delta`。
   已改为 `## ADDED Requirements`，重新校验输出 `Change '…' is valid`。
2. **`MaterialRuleVO.durationMode` 描述与实现不符（低）**：原描述写「恒为 AUTO」，但
   `CourseItemServiceImpl.getMaterialRule()` 命中已有自定义规则时走 MapStruct `materialRuleConverter.toVO(custom)`
   原样回读，历史/异常数据中的 `MANUAL` 会被原样返回。已把描述改为
   「不参与完成判定；服务端保存材料规则时统一按 AUTO 落库，历史数据可能仍为 MANUAL」。

**本轴未覆盖**：Scenario「可计时媒体暂停期间不计入」只有代码守卫，无端到端证据——
本轮环境 `sts_asset_asset` 仅有 1 条 `DOCUMENT` 素材，没有视频素材与 `HLS` 派生产物，
构造真实 HLS 播放需要「上传视频 → 转码 → 写 `sts_asset_derivative` → 发布新课程版本」，
属需人工确认的数据构造，未擅自执行。守卫本身的接线已核对：PC `AssetViewer` 与移动端 `AssetPreview`
都绑定了 `@play` / `@pause`，两端的 `isTimed*` 判定差异见代码质量一节的观察项 1。

### 代码质量

通过。核心实现集中在两处且注释说明了「为什么」：

- `reportCurrentSession()` 只加一行 `syncStudyClock(now)`，并把「事件驱动 vs 时间驱动」的错配写在注释里，
  后续新增上报路径不会再次遗漏。
- `resolveRequiredSeconds()` 三级回退用常量 `MATERIAL_RULE_SOURCE_CUSTOM` 而非魔法字符串，
  `maybeCompleteItem()` 的 `requiredSeconds <= 0` 早返回带注释说明「0 表示未配置要求，不得据此判完成」。
- `CourseItemServiceImpl.updateMaterialRule()` 的留空补 `AUTO` 用常量 `DURATION_MODE_AUTO`，
  `buildDefaultMaterialRule()` 同步改用同一常量，两处口径不会漂移。

观察项（本期未改，建议后续处理）：

1. **两端「可计时媒体」判定口径不一致（低）**：PC 用 `mediaFormat === 'hls'`，
   移动端用 `assetType ∈ {VIDEO, AUDIO}`。正常转码路径下等价；只有「视频素材缺 HLS 派生」时 PC 视为非可计时、
   移动端视为可计时，而该路径下两端都放不了，影响有限。
2. **`durationMode` 取值域未校验（低）**：`updateMaterialRule` 只补空值，不校验取值范围，
   调用方传 `FOO` 会被原样落库；契约已标废弃，无功能影响。
3. **心跳 `endReason` 恒为 `USER_EXIT`（低）**：周期检查点与用户退出无法区分，
   用 `sts_course_study_session` 对账时容易误读。
4. **切换内容项时 `activeItem` 可能短暂指向旧快照对象（低）**：`selectItem()` 先 `await reportCurrentSession()`
   （内部会 `refreshTaskProgress()` 替换 `tasks.value`），随后把调用方传入的旧数组里的 `item` 赋给
   `activeItem.value`，新选中项可能短暂显示上一次刷新的进度。属既有实现，非本次引入。

## 环境与验收说明

- 验证在团队共享测试库（`19.191.192.146:8045/sts`）的 `enrollment 1` 上进行，产生了真实学习会话与进度数据；
  验证结束后已执行 `.workbuddy/e2e-out/threshold-restore.sql` 还原：
  快照回到 `CUSTOM / requiredSeconds=60 / minimumStudySeconds=30`，内容项与报名进度回到 `NOT_STARTED / 0`。
  验证期间产生的 `sts_course_study_session` 行（205～210）保留，不影响功能。
- 浏览器自动化使用本机 Chrome + 隔离目录内的 `puppeteer-core`；
  可见性验证必须使用有头模式（无头模式下 `visibilityState` 恒为 `visible`，无法真验）。
- 本轮 E2E 期间原 pc-web / 移动端 dev server 曾全部退出（端口不再监听），
  为完成移动端验证临时启动了 uni H5 dev server（落在 5173）并在验证后停止。
- 未改动生产环境、生产数据与数据库结构。
