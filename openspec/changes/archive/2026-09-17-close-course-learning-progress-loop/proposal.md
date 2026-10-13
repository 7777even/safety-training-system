# Proposal：课程学习进度上报闭环与完成阈值生效

## Why

核查「课程 → 学员学习进度」链路后发现：管理端配的阈值字段有两处不生效，学员端的有效学习时长
在多数场景下恒为 0，导致进度既不可信也无法闭环。

- **有效学习秒数恒为 0**：两端 `reportCurrentSession()` 直接读取 `accumulatedStudySeconds` 再清零，
  但累加函数 `syncStudyClock()` 只在可见性变化、媒体播放/暂停、切换内容项时被调用。纯文档类内容项
  只要用户不切标签页、不碰媒体控件，30 秒心跳上报的有效秒数就恒为 0（实测：某会话持续 8 分 23 秒、
  有效秒数为 0）。这直接击穿「学习记录 / 学习进度 / 学时」的口径。
- **内容项「最低学习秒数」`minimumStudySeconds` 是死字段**：只出现在 DTO / Entity / 快照 / VO / DDL，
  后端没有任何业务逻辑读它。管理员填 30 秒或不填，对完成判定零影响。
- **MaterialRule「时长模式」`durationMode` 是死字段**：全后端仅在构造默认规则时写入常量 `AUTO`、
  在快照组装时原样透传，进度上报与完成判定从不读取。管理端的「自动 / 手动」单选对系统没有任何作用。
- **阈值缺失时回退为 0，首次上报即判为已完成**：`resolveRequiredSeconds()` 在材料规则缺失或
  `requiredSeconds` 为空时返回 0，`maybeCompleteItem()` 随即判定 `study_seconds >= 0` 成立。
- **契约描述与实现不符**：`attemptCount` 的 description 写「作答次数」，实现语义是「学习会话次数」；
  `MaterialRuleUpdateRequest` / `MaterialRuleVO` 的示例值出现 `"FIXED"`，不在自身 enum `[AUTO, MANUAL]` 内。

`openspec/specs/` 下没有覆盖学习会话上报与内容项完成判定的能力规格，上述规则只散落在已归档 change 的
design 里，缺少可追溯的正式约束，是字段长期失效没有被发现的直接原因。

## What Changes

- 客户端 MUST 在每一次上报检查点前结算活跃区间，而不是只依赖可见性变化、媒体事件与内容项切换：
  两端在 `reportCurrentSession()` 取值前先 `syncStudyClock()`。
- ASSET 内容项完成阈值改为三级解析：自定义材料规则「要求学习秒数」→ 内容项「最低学习秒数」→
  材料规则默认值；任一档给出大于 0 的值即停止回退，全为 0 时 MUST NOT 当作「立即完成」。
- 「时长模式」不再是可配置项：功能项只要求「设置学习时长」，未要求模式选择。管理端移除该单选，
  后端不再要求该字段非空、留空按 `AUTO` 落库，MUST NOT 因缺失该字段拒绝请求。
- 修正 `docs/api/course.openapi.json`：`attemptCount` 描述改为学习会话次数、`durationMode` 示例值
  与 enum 对齐并标注废弃、`minimumStudySeconds` 描述补齐与材料规则的关系。
- **新增能力规格** `course-learning-progress`，把学习会话上报、有效时长结算、完成阈值与进度重算
  固化为可追溯的 MUST 约束。

## Capabilities

### New Capabilities

- `course-learning-progress`：课程学习会话上报、有效学习时长结算、内容项完成阈值解析与报名进度重算。

### Modified Capabilities

- 无。课程完成规则（`ALL_REQUIRED_ITEMS` / `ALL_ITEMS`）、学分授予、培训学时投影与本 Change 无关。

## Impact

- 后端：`CourseLearningServiceImpl`（阈值解析）、`CourseItemServiceImpl`（材料规则落库兜底）、
  `MaterialRuleUpdateRequest`（放开 `durationMode` 必填）。
- 契约：`docs/api/course.openapi.json`（3 处字段描述与示例）。
- 前端：`frontend/pc-web/src/views/portal/class/learning.vue`、
  `frontend/mobile-app/src/pages/course/learning.vue`（心跳前结算）、
  `frontend/pc-web/.../CourseItemInspector.vue`（移除时长模式单选）、
  `frontend/pc-web/src/api/course-operations.ts`（类型标注）。
- 不变：数据库结构与 migration、权限码、接口路径与错误码、课程完成规则、学分、培训投影。
- **非 BREAKING**：`durationMode` 由必填改为可选属放宽，老调用方继续传值仍被接受。

## 功能项追溯

- **功能项 67**：「已上传的课件可预览、修改课件名称、**设置学习时长**、添加课件试题。」
  —— 阈值字段必须真实生效；功能项未要求「时长模式」，故移除该设置。
- **功能项 74**：「主要是实现用户在线学习，包含**学习记录、学习进度**等功能……不同终端的学习进度、
  学时等员工学习情况可实时同步。」
- **功能项 76**：「学习过程中暂停或退出系统时，系统**保存当时学时**，重新进入系统后可接续上次学习记录。」
- **功能项 77**：「监控所有培训记录……可了解各学员学习的课程、**时长、次数、进度**。」

## 目标

1. 文档类与媒体类内容项的有效学习秒数都能真实反映活跃学习时长，不再恒为 0。
2. 管理员在内容项或材料规则上配置的学习时长要求都真实参与完成判定。
3. 完成阈值缺失时行为可预期，不再出现「首次上报即完成」。
4. 管理端不再暴露未实现的「时长模式」设置，消除「配了就生效」的误解。
5. OpenAPI 对 `attemptCount`、`durationMode`、`minimumStudySeconds` 的描述与实现一致。

## 非目标范围

- 不实现防快进、倍速限制、离页暂停计时、水印四个材料规则字段的客户端消费（功能项 75 属延期项）。
- 不实现 LIVE 内容项完成路径：直播参与事实尚未定义，本期 LIVE 仍不按学习时长自动完成。
- 不改课程完成规则、学分授予（`creditAwarded` 仍恒为 0）、培训学时投影。
- 不改学习进度表的列展示口径（文档类内容项「最近位置(秒) / 结束位置(秒)」仍按既有逻辑渲染）。
- 不改数据库结构、不加 migration；`duration_mode` 列保留并继续按默认值落库。
- 不引入做题次数、跳过检测等新口径。
