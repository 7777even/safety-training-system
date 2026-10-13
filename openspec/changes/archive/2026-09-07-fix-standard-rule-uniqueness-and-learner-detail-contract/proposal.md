# Proposal：培训标准基础规则唯一性与学员详情契约修正

> 状态：已于 2026-09-07 按联调缺口审核确认实施第 1、3 条。Owner：lims。

## 背景

管理端联调整理了培训标准与学员详情的两处缺口：

1. 培训标准的 `TOTAL_HOURS`、`ONLINE_RATIO` 前端已限制各只能一条，但替换规则和发布标准都不校验。直接调 API 可写入重复基础规则。
2. `TrainingLearnerParticipantDetailVO.participant` 与 `plan` 在 OpenAPI 中被写成字符串，后端实际返回对象；学员详情需要明确
   `plan.publishStatus` 必返。

第 2 条「给培训标准增加 trainingNature」和第 4 条评价表单绑定强化不在本次范围。

## 本次覆盖的功能项

| 序号 | 原文语义                            | 本次边界                               |
|----|---------------------------------|------------------------------------|
| 5  | 培训类型保存初培学时、复审学时和线上最高占比，作为计划规则依据 | 标准基础规则不得重复，避免同一标准出现多条总学时或线上占比      |
| 34 | 可设置培训全部内容并查看培训详情                | 标准规则作为计划编排输入时保持可判定                 |
| 48 | 学员按培训计划状态查看培训内容并学习              | 学员参训详情契约与真实对象结构一致，计划状态可读取          |
| 56 | 我的培训展示任务及结果                     | 学员详情返回参训人对象与计划对象，含 `publishStatus` |

## 目标

1. 替换规则和发布标准时，`TOTAL_HOURS`、`ONLINE_RATIO` 各最多一条；重复返回明确业务错误，不写脏数据、不发布。
2. 学员参训详情 OpenAPI 将 `participant`、`plan` 改为对象 schema，并声明 `plan.publishStatus` 必返。
3. `COURSE` / `EXAM` / `OFFLINE` 指定资源规则仍允许多条。

## 功能范围

- 培训标准 `replaceRules`、`publish` 的基础规则唯一性校验与错误码。
- `docs/api/training.openapi.json` 中标准规则接口说明、错误码，以及学员参训详情 schema / example。
- 对应零 DB 测试；本 Change 不改表结构。

## 非目标范围

- 不给培训标准增加 `trainingNature`；初培/复审仍只在培训计划上。
- 不强化评价表单 `bindEvaluationForms`。
- 不把基础规则唯一性做成数据库部分唯一索引（`COURSE`/`EXAM`/`OFFLINE` 同类型可多条）。
- 不要求标准必须配置 `TOTAL_HOURS` 或 `ONLINE_RATIO`。
- 不实施前端页面、Mock、联调或 UI；前端以 OpenAPI 为准另开。

## 前后端影响

仅后端；前端以 OpenAPI 为准另开。

## Impact

- 后端：`TrainingStandardServiceImpl` 替换规则与发布校验。
- API：新增业务码 `8016`；修正 `TrainingLearnerParticipantDetailVO`；`TrainingPlanVO.publishStatus` 标为必返。
- 数据：无表结构、无 migration。
- 兼容：替换规则/发布在重复基础规则时由静默接受改为 409/`8016`。学员详情 JSON 形状不变，仅契约对齐实际对象。

## 人工确认关卡

本 Change 修改 API Contract，属于 L4。确认同时表示接受：

1. 基础规则唯一性按「同一标准有效规则中该 `ruleType` 只能一条」计算，含禁用行，不含已软删行。
2. 学员详情继续返回现有 `TrainingParticipantVO` 与 `TrainingPlanVO`，不另造精简 VO；`publishStatus` 在 `TrainingPlanVO`
   上必返。
