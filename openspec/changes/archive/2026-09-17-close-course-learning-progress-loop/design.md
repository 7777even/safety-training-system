# Design：课程学习进度上报闭环与完成阈值生效

## Context

链路现状（实测 + 源码交叉验证）：

| 环节 | 位置 | 现状 |
|---|---|---|
| 有效秒数累加 | `syncStudyClock()` | 只在可见性变化 / 媒体事件 / 切换内容项时调用 |
| 有效秒数取值 | `reportCurrentSession()` | 直接读 `accumulatedStudySeconds` 后清零，取值前不结算 |
| 心跳 | `setInterval(..., 30000)` | 以 `USER_EXIT` 为结束原因上报，不触发结算 |
| 阈值解析 | `CourseLearningServiceImpl.resolveRequiredSeconds()` | 只读材料规则 `requiredSeconds`，缺失回退 0 |
| 完成判定 | `maybeCompleteItem()` | 仅 `itemType == ASSET`；`studySeconds >= 阈值` |
| 内容项门槛 | `ItemSnapshot.minimumStudySeconds` | 快照有值，无任何消费点 |
| 时长模式 | `MaterialRuleSnapshot.durationMode` | 快照有值，无任何消费点 |

客户端计时模型（两端一致）：

```
activeTrackingStartedAt  —— 当前活跃区间起点；不可见或媒体暂停时置 0
accumulatedStudySeconds  —— 已结算但未上报的秒数
syncStudyClock(now)      —— 把 [activeTrackingStartedAt, now] 结算进累计值，并把起点重置为 now
reportCurrentSession()   —— 上报 accumulatedStudySeconds 并清零
```

缺陷根因：`syncStudyClock()` 的调用点全部是「事件驱动」，而心跳是「时间驱动」。
纯文档类内容项没有媒体事件，用户不切标签页时中间没有任何调用点，心跳读到的是空累计值。

## Decisions

### D1：上报前强制结算，而不是缩短心跳间隔

在 `reportCurrentSession()` 取值前调用 `syncStudyClock(now)`，两端各一处。

- 收益：所有上报路径（心跳、切换内容项、页面卸载、媒体自然结束）统一先结算，不需要逐点补调用；
  结算点与上报点重合后，`accumulatedStudySeconds` 清零与区间起点重置天然衔接，不会重复计数或漏计。
- 保持心跳 30 秒不变：缩短间隔会增加请求量而不解决根因（事件缺失）。
- 被否决的方案：只在心跳回调里补一次 `syncStudyClock()`。可行但把「结算」这件事散落在调用点，
  后续新增上报路径会再次漏掉，口径无法收敛。

### D2：完成阈值三级解析（材料规则自定义值 → 内容项门槛 → 材料规则默认值）

```
自定义材料规则 required_seconds > 0   → 用它
否则 内容项 minimum_study_seconds > 0 → 用它
否则 材料规则默认 required_seconds > 0 → 用它
否则 0（视为无时长要求）
```

判定「自定义」用快照里的 `materialRule.source`：

- `CUSTOM`：管理员在材料规则面板显式保存过，视为对该内容项完成要求的表态，覆盖内容项门槛。
- `DEFAULT`：内容项没有自定义规则，快照里的值来自机构默认（`buildDefaultMaterialRule` 取
  `course_setting.default_document_page_seconds`，无配置时 15 秒），只是兜底展示值，不构成表态，
  因此让位给管理员在内容项上填的 `minimumStudySeconds`。

这一顺序即「内容项门槛 + 材料规则覆盖」的落地：管理员在材料规则里显式配了就用材料规则，
没配就用内容项门槛，都没配才回退机构默认。

关于第三档：保留机构默认兜底是必要的——它是现有行为，直接删除会让未配置的内容项阈值变为 0，
重新落回「首次上报即完成」的旧缺陷。

### D3：「时长模式」不再作为可配置项

功能项 67 只要求「设置学习时长」，全文没有模式选择的要求；`durationMode` 也没有任何消费点。
因此按「砍掉设置、统一自动上报」处理：

- 管理端 `CourseItemInspector.vue` 移除「时长模式」单选，材料规则面板只保留秒数与规则开关。
- 后端 `MaterialRuleUpdateRequest.durationMode` 去掉 `@NotBlank`；`updateMaterialRule()` 在字段为空时
  补 `AUTO` 落库，避免 `duration_mode NOT NULL` 约束失败。
- `MaterialRuleUpdateRequest.durationMode` 从契约 `required` 中移除，`description` 标注已废弃；
  `MaterialRuleVO.durationMode` 保留返回（兼容既有消费方）并标注已废弃。
- `course_item_material_rule.duration_mode` 列保留、不加 migration：本 Change 不改结构，列继续按
  `AUTO` 写入，避免为一个已废弃字段引入一次不可回退的 DDL。

### D4：契约只修描述与示例，不改结构

| 字段 | 现状 | 改为 |
|---|---|---|
| `CourseItemProgressVO.attemptCount` | 「作答次数」 | 「学习会话次数（每次有效上报累加一次）」 |
| `MaterialRuleUpdateRequest.durationMode` | 必填、示例 `"FIXED"` | 可选、示例 `"AUTO"`、描述标注已废弃 |
| `MaterialRuleVO.durationMode` | 示例 `"FIXED"` | 示例 `"AUTO"`、描述标注已废弃 |
| `minimumStudySeconds`（3 处） | 「最低学习秒数」 | 补齐「作为内容项完成门槛；ASSET 配置自定义材料规则时由材料规则要求秒数覆盖」 |

不改字段名、类型、路径、错误码，不改任何 Request/Response 结构，属纯描述修正。

## Risks

1. **历史数据阈值变化**：`minimumStudySeconds` 开始生效后，管理员此前填过该字段的内容项会收紧完成条件，
   已有报名可能出现「原本已完成、阈值调高」的展示差异。评估：完成状态只增不减（`maybeCompleteItem`
   不降级），已 COMPLETED 的行不受影响；仅未完成项可能更晚完成。属预期收敛，不是回归。
2. **快照字段来源**：判定依赖快照 `materialRule.source`。历史快照由同一份
   `toMaterialRuleSnapshot()` 生成，`source` 一直在写入（`CUSTOM` / `DEFAULT`），无需回填。
   若个别快照 `source` 为空（例如测试里手写的快照），按「非自定义」处理，落到第三档材料规则默认值，
   最终阈值与改动前的单档行为一致，不会突变。
3. **两端需同步改**：PC 与移动端计时结构一致，只改一端会造成跨端学时口径不一致（功能项 74 明确要求
   不同终端学习情况可同步），因此两端必须同批修改。
4. **真实联调缺口**：本次验证手段为定向单测、typecheck、lint 与构建，未起真实后端 + 数据库跑端到端。
   有效秒数需在真实浏览器会话中观察，属遗留项。

## Migration Plan

无数据库变更，无需 migration。上线后新上报的学习会话立即按新口径结算；历史会话的
`study_seconds` 保持不变，不做回填。
