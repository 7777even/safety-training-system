# M4 结果状态双端口径统一：`INCOMPLETE` → 「结果待补全」

日期：2026-09-28
类型：L3（学员/管理员可见的业务展示语义统一；不涉 API 字段、权限、数据库）
来源：`2026-09-28-dual-m4-real-environment-acceptance.md` 登记的「双端措辞差异」待确认项
决策：人工确认统一为「结果待补全」（非「结果不完整」，也非「未完成」）

## 1. 统一前分布（实测）

| 位置 | INCOMPLETE 文案 | 无结果兜底 |
|---|---|---|
| PC 管理端 档案统计 / 成果页 / 共享枚举 `enum-options` | 结果不完整 | — |
| PC 门户 学员培训班列表 | 未完成 | 待同步 |
| PC 管理端 培训计划执行面板 | 未完成 | 待同步（与过程态共用） |
| 移动端 培训班列表 | 未完成 | 待同步 |
| 移动端 我的培训档案 | 结果待补全 | 结果待同步 |
| 后端 档案 PDF 成品（`ArchiveLabels` / `ArchivePdfRenderer`） | 结果不完整 | — |
| `docs/api` 三个契约的 `resultStatus.description` | 未完成 | — |

另有一处自我矛盾：`pc-web/src/constants/enum-options.ts` 的注释自称「与门户培训班列表一致：
合格 / 不合格 / 结果不完整」，而门户实现写的是「未完成」，属遗留未收口。
**2026-09-28 复查补充**：该「自称一致」的声明式注释已**整行移除**（而非改词），理由见第 5 节；
该文件现只保留「分母只统计 PASSED/FAILED，INCOMPLETE 单列，三态必须穷举」这条领域规则。

## 2. 统一口径

- `INCOMPLETE` → **结果待补全**（前端、后端成品、契约、活规格全一致）
- 无结果（null）→ **结果待同步**（与「结果待补全」成对，避免与进行中的「未完成」混淆）
- `PASSED` / `FAILED` 仍为 **合格** / **不合格**（未改）

## 3. 改动清单

### 前端 pc-web
- `src/constants/enum-options.ts`：`TRAINING_RESULT_STATUS_OPTIONS` 的 INCOMPLETE label；
  并移除那句易过期的「与门户列表一致」跨文件一致性注释（只保留枚举自身的领域规则行）
- `src/views/admin/result/training-result/index.vue`：`RESULT_TAGS` label、统计标签、注释
- `src/views/admin/result/archive/components/ArchiveFactsSections.vue`：已完成 hint、统计 label、说明文
- `src/views/admin/result/archive/components/PlanArchivePanel.vue`：说明文
- `src/views/admin/result/archive/index.vue`：2 处说明文
- `src/views/admin/business-center/training/plan/components/PlanExecutionPanel.vue`：`resultStatusText` 映射
- `src/views/portal/training/index.vue`：`resultText` 映射 + 兜底

### 前端 mobile-app
- `src/pages/training/index.vue`：`resultText` 映射 + 兜底 + 注释
- `src/pages/archive/index.vue`：本就是目标口径，未改

### 后端
- `archive/util/ArchiveLabels.java`：`RESULT_STATUS` 的 `INCOMPLETE`
- `archive/service/ArchivePdfRenderer.java`：统计行标签
- `archive/util/ArchiveLabelsTest.java`：补 `INCOMPLETE` 断言
- `training/service/CompositeScoreServiceImplTest.java`、`training/service/CompositeScoreServiceIT.java`：`@DisplayName` 术语

### 契约与规格
- `docs/api/archive.openapi.json`：`missingItems` 说明、`incompleteCount` 描述
- `docs/api/training.openapi.json`：2 处 `resultStatus` 说明、`compositeScore` 说明、`incompleteCount` 描述
- `docs/api/regulation.openapi.json`：`resultStatus` 说明、`compositeScore` 说明
- `openspec/specs/m4-minimum-closure/spec.md`（活规格，6 处）
- 未归档 Change：`2026-09-21-implement-m4-pc-outcome-closure`（design/tasks）、
  `2026-09-24-close-training-material-scope-loop`（specs delta）、
  `2026-09-24-close-mobile-learner-outcome-loop`（design/tasks）、
  `2026-09-28-improve-m4-certificate-and-mobile-archive`（proposal/spec-delta/specs/tasks）

### 测试（新增口径锁定，防回退）
- `frontend/pc-web/tests/m4-outcome-closure.test.mjs`：新增 1 条，断言 7 个 PC 文件不再出现「结果不完整」
  或 `INCOMPLETE: '未完成'`，且各处 label / mapping 均为「结果待补全」
- `frontend/mobile-app/tests/learner-outcome.test.mjs`：新增 1 条，断言培训班列表与档案共用同一口径
- `frontend/pc-web/tests/m4-minimum-ui-closure.test.mjs`：测试名同步

## 4. 验证结果（真实执行）

| 项 | 命令 | 结果 |
|---|---|---|
| PC 类型检查 | `vue-tsc --noEmit`（pc-web） | 退出码 0 |
| 移动端类型检查 | `vue-tsc --noEmit`（mobile-app） | 退出码 0 |
| PC 改动文件 lint | `eslint <7 文件> --max-warnings=0` | 仅 `portal/training/index.vue:109:94 html-self-closing` 1 条 warning，**与 `git show HEAD` 基线逐条相同，属既有遗留** |
| 移动端 lint | `eslint src/pages/training/index.vue src/pages/archive/index.vue --max-warnings=0` | 退出码 0 |
| PC 静态测试 | `node --test tests/m4-outcome-closure.test.mjs` | **8/8 pass**（含新增 1 条） |
| 移动端静态测试 | `node --test tests/learner-outcome.test.mjs` | **4/4 pass**（含新增 1 条） |
| 后端单测 | `mvn -pl sts-service-admin test -Dtest=ArchiveLabelsTest,CompositeScoreServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false` | **5/5 pass**；首次输出 `Nothing to compile`，按项目规矩 `touch` 全部 java 后重跑得 `Compiling 1033 source files` + `Compiling 302 source files`，**此次才算真实编译并执行** |
| 规格校验 | `openspec validate <4 个 Change> --strict` | 4/4 valid |
| 差异检查 | `git diff --check` | 无空白错误 |
| 注释移除复验（2026-09-28） | `eslint src/constants/enum-options.ts --max-warnings=0`；`node --test tests/m4-outcome-closure.test.mjs` | lint 退出码 **0**；口径锁定测试 **8/8 pass** |

## 5. 边界与未覆盖

- **不改历史记录**：`openspec/changes/archive/**`、`engineering/qa/**`、`engineering/assets/**` 与进度表历史条目
  保留当时用词（它们记录当时界面的实际值，回溯改写等于篡改证据）。如要全局一致需另行确认。
  （**2026-09-28 补记**：`engineering/assets/**` 下的截图、结构化 JSON 与 PDF 样例已整体清理，只保留
  `certificate-bg/`；上述文档中的证据路径**文字保留**，仅在其后统一标注「证据已于 2026-09-28 清理」。）
- **未做浏览器复验**：本次为纯展示文案替换，由两端 typecheck + 定向 lint + 口径锁定测试覆盖；上一轮 M4
  真实环境验收的结论不受影响（改动与业务逻辑无耦合）。
- **已生成的档案 PDF 不受影响**：`ArchiveLabels` 只作用于生成时渲染，既有 PDF 内容不变，仅新生成的正本使用新词。
- **未改动**：`PASSED` / `FAILED` 中文；`PlanExecutionPanel` 过程态「未通过」、各处内容项「未完成」的正常用法
  （那是「内容没做完」，与结果状态是两个语义）；移动端证书页「签发日期待同步」（说的是日期不是结果）。
- **移除跨文件一致性声明注释（2026-09-28 复查）**：`enum-options.ts` 原注释用文字把本文件的文案绑定到
  门户培训班列表 / 成果页 / 档案页，一旦别处改动即成谎言——本次「注释写结果不完整、门户写未完成」的矛盾
  正是这样产生的。该口径已由 `m4-outcome-closure.test.mjs` 的反向断言锁定，注释再挂一致性声明只会重复过期，
  故整句移除，只陈述本枚举自身的领域规则。
