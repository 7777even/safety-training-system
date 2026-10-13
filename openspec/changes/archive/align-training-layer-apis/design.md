# Design：按分层职责收口培训接口

## Context

长期职责见 `docs/architecture/training-layer-responsibility.md`。表已经分层，本 Change 不改 DDL。

当前接口缺口：

1. `POST /api/training/plans` 只把 `sourceTemplateId` 存进计划，不复制阶段。调用方只能 `GET` 模板再 `PUT .../stages`
   ，和「标准带入模板」是同一种手搓粘贴。
2. `TrainingPlanPublishValidator` 只卡类型/标准的学时与线上占比，不卡 `COURSE`/`EXAM`/`OFFLINE` 指定资源，指定规则等于摆设。
3. 模板阶段契约把 `executionConfig` 写成题库/线下「必须填」，把开课/考试的本期参数提前压进方案层。
4. 不存在、也不应存在类型→标准、标准→模板的带入 API；前端自己拼出来才套娃。

开课/考试能力拆分（`unify-training-course-exam-capability`）保持：计划生成执行继续调用同一套 publish。本 Change 只收编排层接口。

## Goals / Non-Goals

**Goals:**

1. 每一层 HTTP 接口只写该层产物：类型尺子、标准口径、模板课表、计划本期。
2. 模板 → 计划的复制由服务端一次完成，成为唯一合法带入。
3. 计划发布预检把标准指定资源当硬缺口。
4. 无表结构变更。契约与零 DB 测试同步。

**Non-Goals:**

- 不改前端工作台、不删层、不锁大纲。
- 不合并 `publish` / `generate-executions` / `start`。
- 不删除 `sts_training_template_content.execution_config_json`。
- 不新增类型→标准、标准→模板接口。

## Decisions

### D1 不增加「带入」类接口，只收正复制

禁止新增例如 `apply-type-hours`、`apply-standard-rules-to-template`。标准规则保存后仍是规则行；模板阶段保存后仍是课表行。二者之间没有服务端转换。

调用方若继续自己把规则写成阶段，发布预检仍按计划内容检查指定资源，不承认「曾经带入过」。

### D2 创建计划带模板即复制课表

`POST /api/training/plans` 在插入草稿成功后：若 `sourceTemplateId` 非空，则：

- 模板必须存在、`PUBLISHED`、属于当前机构、`trainingTypeId` 与计划相同；否则 400/`8157`，计划不创建（同一事务回滚）。
- 将模板阶段与内容复制为计划阶段与内容，写入 `source_template_stage_id`。
- 复制内容引用、建议学时、`assessmentMode`；`executionConfig` 若模板有则原样拷到计划作默认值，没有则计划侧为空（发布时再按计划内容校验）。
- 不把模板的 `trainingStandardId` 写进计划；计划标准只来自请求里的 `trainingStandardId`（可空）。

`trainingStandardId`、`sourceTemplateId` 继续可空。空建计划没有阶段。

### D3 草稿覆盖复制用独立接口

`POST /api/training/plans/{trainingPlanId}/apply-template`

权限：`training:plan:edit`。仅 `DRAFT`。请求体：`version`（计划乐观锁）、`sourceTemplateId`。

- 非草稿：409/`8158`。
- 模板不合格：400/`8157`。
- 版本冲突：409/`8104`。
- 成功：覆盖保存来源模板 ID，删除并重建计划阶段/内容（与现有 `replaceStages` 同一套删除语义），复制规则同 D2。
- 空 `sourceTemplateId` 不提供；清空来源只走计划更新接口改字段，**不自动清空已改课表**。

计划 `PUT` 基本信息若只改 `sourceTemplateId`：**不复制课表**。复制必须走创建或 `apply-template`，避免「改一下下拉就覆盖」。

### D4 标准规则仍用现表，语义改为只检查

`PUT .../standards/{id}/rules` 保持现结构。`COURSE`/`EXAM`/`OFFLINE` 必须带可冻结的 `resourceId`
（考试须有已发布试卷版本，课程须有已发布课程版本），与现有资源校验对齐。

保存或发布标准时：启用的 `TOTAL_HOURS` 若存在，其值 MUST ≥
该标准所属类型的初培学时与复审学时中的较小正数？不对——标准没有性质字段。架构说不得低于类型对应基线。类型有两套学时。合理规则：

**暂定并收口为：** 启用的 `TOTAL_HOURS` MUST ≥ `max(类型.initialHours, 类型.reviewHours)` 中实际配置了的正数里的**较小者
**？那会允许低于初培。

功能项与领域模型：不得降低类型对应初培/复审基线。标准是类型级、不分初培/复审。因此：

**决定：** 启用的 `TOTAL_HOURS` MUST ≥ 类型 `initialHours` 与 `reviewHours` 中所有已配置正数的**最大值**。这样初培 80、复审
24 时，标准总学时不能低于 80，复审计划用该标准时适用最低学时仍取 max(类型复审, 标准总学时)=80，可能偏严。

更合理且与现发布逻辑一致：发布计划时 `applicableRequiredHours = max(类型按性质学时, 标准 TOTAL_HOURS)`。标准保存时只要求
`TOTAL_HOURS` ≥ 类型按**两套学时里较小的正数**（通常是复审），否则复审标准无法加严到「仍低于初培」——加严是「不得低于」，复审 24
的标准写成 30 是合法的。

**决定（与现发布逻辑兼容）：** 保存/发布标准时，启用 `TOTAL_HOURS` MUST ≥ 类型 `initialHours`、`reviewHours` 中已配置正数的*
*最小值**。不得为 0 或负数。低于该最小值返回 400/`8017`。计划发布仍按性质取类型学时再与标准取 max。

`ONLINE_RATIO` MUST ≤ 类型 `onlineMaxRatio`（若类型有值）。超过返回 400/`8017` 同一码或单独？用 `8018`：线上占比不得宽于类型上限。

### D5 模板保存/发布不卡执行配置、不卡标准覆盖

`PUT .../templates/{id}/stages`：继续校验内容类型、资源属于本机构（与现计划资源校验同级的最小校验：引用 ID 存在且类型匹配）。*
*不要求** `executionConfig` 完整。**不要求** 覆盖所选标准的指定课。

模板 `publish`：草稿、乐观锁、至少有一个阶段和一个内容（否则引用无意义）。缺则 400/`8042`。不引入对执行配置的 8117。

契约：`TrainingTemplateContentRequest.executionConfig` 说明改为「可选默认值，模板发布不校验齐全；计划发布才校验」。

### D6 发布预检增加指定资源缺口

`POST .../publish-precheck` 与 `POST .../publish` 使用同一套检查。计划绑定了已发布标准时，对每条启用的 `COURSE`/`EXAM`/
`OFFLINE` 规则：计划内容中必须存在匹配 `contentType` + `resourceId` 的项。缺一项加入 `issues`，`code=8156`，`ready=false`
。发布直接失败同一码。

无标准或标准无指定资源规则：不产生 8156。学时/占比逻辑保持现实现。

计划内容执行配置齐全、人员、冻结版本等现有规则不变。

### D7 错误码

| 码    | HTTP | 含义                         |
|------|------|----------------------------|
| 8017 | 400  | 标准总学时低于类型已配置学时的最小值         |
| 8018 | 400  | 标准线上占比宽于类型上限               |
| 8042 | 400  | 模板发布前须至少有一个阶段和一个内容         |
| 8156 | 400  | 计划缺少培训标准指定的课程、考试或线下内容      |
| 8157 | 400  | 来源模板未发布、不属于当前机构或与计划培训类型不一致 |
| 8158 | 409  | 仅草稿计划可从模板复制课表              |

不新增权限码。`apply-template` 使用 `training:plan:edit`。

### D8 契约写法

同步 `docs/api/training.openapi.json`：按 Controller 分类；每个改动的 operation 更新 `summary`/`description`
/example；新路径与新错误码进 description 与域错误码 schema。字段中文 description 写清「标准不生成课表」「模板执行配置可选」「创建/apply-template
才复制」。

## 调用关系（简化后）

```text
类型 GET/CRUD          只读写尺子
标准 rules + publish   只读写检查口径
模板 stages + publish  只读写课表方案
计划 POST(+template)   建草稿并可选复制课表
计划 apply-template    草稿覆盖复制课表
计划 PUT stages        本期改课表（可改，不回写模板）
计划 PUT contents 执行配置  本期开课/考试参数
计划 publish-precheck  尺子 + 口径 + 本期可执行性
计划 generate-executions   调用开课/考试能力（既有）
```

## Risks / Trade-offs

- [创建带模板失败回滚整单] → 与编码冲突同一事务，避免空计划挂着无效来源。
- [前端仍手搓标准进模板] → 后端不提供接口；预检按计划内容卡指定资源，手搓删掉会失败。
- [模板无执行配置，复制后计划发布失败] → 预期。执行参数在计划上补，符合分层。
- [标准 TOTAL_HOURS 相对两套学时取最小正数] → 复审加严仍可低于初培；计划发布按性质再取 max。若监管要求标准必须 ≥ 初培，需另确认。
- [apply-template 覆盖已改课表] → 必须显式调该接口；PUT 来源 ID 不覆盖。

## Migration Plan

1. 先扩预检与标准保存校验（行为收紧，无 DDL）。
2. 再实现创建复制与 apply-template。
3. 最后改模板发布/契约说明。
4. 回滚：去掉新路径与新校验即可，无表回滚。

存量：已手工粘贴的模板/计划数据保持；不清洗 `execution_config_json`。

## Open Questions

无。待人工确认后实施。

## 前后端影响

仅后端；前端以 OpenAPI 为准另开。
