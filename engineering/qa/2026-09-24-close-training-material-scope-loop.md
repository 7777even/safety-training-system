# 培训资料范围收口与学员侧只读 QA

对应 Change：`2026-09-24-close-training-material-scope-loop`（功能项 34/36/44/48）。
记录时间：2026-09-24。

## 范围

培训资料**类型收口**（去掉 `REWARD_PENALTY`）、**状态口径统一**（档案与学员侧只取 `status = 1`）、
**奖惩佐证归位**（只走奖惩记录 `evidenceFileId`，档案奖惩表补佐证预览）、
**学员侧只读端点与两端呈现**（PC 门户 + 移动端，只预览不下载）；不新增权限、不新增业务表、不改综合分与证书签发语义。

## 一、开发侧自动化验证（2026-09-24 实跑）

### 后端

- **真实编译**：`Compiling 1033 source files with javac ... to target\classes`、
  `Compiling 302 source files ... to target\test-classes` → `BUILD SUCCESS`。
  （首次重编出现过一次 `写入 ... PaperVersionQuestionMapper.class 时出错` 的 **并发写 `target/classes` 冲突**，
  重跑即通过，非代码问题——与 2026-09-24 `close-training-material-loop` QA 记录的同类现象一致。）
- **定向单测 73/73 全绿**：
  `ArchiveControllerTest` 4、`ArchiveServiceImplTest` 12、`TrainingExecutionControllerTest` 7、
  `TrainingWorkflowControllerTest` 8、`TrainingAttachmentServiceImplTest` 10、`TrainingWorkflowServiceImplTest` 32。
- **Testcontainers MySQL IT 23/23 全绿**（`-P integration-test` + `TESTCONTAINERS_RYUK_DISABLED=true`）：
  `FlywayMigrationIT` 9（含 `V20260924180000` 迁移链路与 schema 版本）、`ArchiveServiceIT` 6（含「停用资料不进档案/统计」）、
  `TrainingAttachmentServiceIT` 5、`TrainingWorkflowServiceIT` 3（本人/他人、类型过滤、停用过滤、跨计划拒绝）。

### 前端

- PC：`vue-tsc --noEmit` 退出码 0；本 Change 6 个改动文件
  （`api/training-learner.ts`、`api/training-process.ts`、`constants/enum-options.ts`、
  `PlanProcessResultsPanel.vue`、`views/portal/training/detail.vue`、`ArchiveFactsSections.vue`）
  `eslint --max-warnings=0` 退出码 0；静态契约测试 `m4-minimum-ui-closure.test.mjs` **12/12**。
- 移动端：`vue-tsc --noEmit` 退出码 0；`src` 范围改动文件 `eslint --max-warnings=0` 退出码 0；
  `uni.js build -p h5` → `DONE Build complete.`；静态契约测试 `training-learner-api.test.mjs` **6/6**。
  - **如实记录**：`.mjs` 测试文件不在仓库 lint 口径内（两端的 `lint` 脚本都是 `eslint src`）。
    直接对 `tests/training-learner-api.test.mjs` 跑 ESLint 会报 `no-undef: 'URL' is not defined`，
    **HEAD 基线同样报 6 条**（`pc-web/tests/m4-minimum-ui-closure.test.mjs` 基线 7 条），
    本次因新增 2 行 `new URL(...)` 增至 8 条——**同一既有规则、随行数线性增加，非新增失败类别**。

### 规格

- `openspec validate 2026-09-24-close-training-material-scope-loop --strict` 通过；`git diff --check` 通过。

## 二、真实环境验收

环境：本地后端 `20080`（`vpn` profile → 团队库 `19.191.192.146:8045` / Redis `16379` / MinIO，**启动前 `unset *_proxy`**）、
PC dev 独立端口 `5190`（`/api` 代理到 `127.0.0.1:20080`）、移动端 H5 静态服务 `5191`（同源反代 `/api`）、
无头 Chrome CDP `9222` 直连（Node 22 内置 fetch/WebSocket，未用 playwright）。真实登录态：`DEMO/superAdmin`。

### 2.1 学员侧端点（真实 HTTP，13 组用例全部符合预期）

验收数据造在计划 3（`0920培训计划`，阶段 23/24）：M1 = `GENERAL`/PDF/启用、M2 = `GENERAL`/PNG/启用、
M3 = `IMAGE`/PNG/启用、M4 = `GENERAL`/PNG/停用。

| 用例 | 期望 | 实测 |
|---|---|---|
| 本人列表 `self=6` | 仅 M1/M2 | 200，仅 2 条，含 `stageName`，**响应不含 `fileInfoId`** |
| M1 文件 | 200 `application/pdf` | 200 / `application/pdf` / 1927381 B / 魔数 `%PDF-1.6` / **md5 与源文件一致** `f155e00c...1e90` |
| M2 文件 | 200 `image/png` | 200 / `image/png` / 100697 B / PNG 魔数 / **md5 一致** `dd0e7e51...eab1` |
| M3（非 GENERAL）文件 | 404/8233 | 404 `{"code":8233,"message":"培训资料文件不存在或不可读取"}` |
| M4（停用 GENERAL）文件 | 404/8233 | 同上 |
| 他人参训 8（operator01）列表/文件 | 404/8155 | 404 `{"code":8155,"message":"培训任务对当前学员不可见"}` |
| 不存在参训 999999 | 404/8155 | 同上（**与存在但非本人返回一致，不泄漏存在性**） |
| 跨计划资料 7 | 404/8233 | 同上 |
| 停用 M1 后列表 | 收敛为仅 M2 | 200，仅 1 条 |
| 停用 M1 后读文件 | 404/8233 | 同上 |
| 改回启用后列表 | 恢复 M1+M2 | 200，2 条 |
| 新增 / 编辑为 `REWARD_PENALTY` | 400/8234 | 400 `{"code":8234,"message":"培训资料类型无效"}` |

两条 `Content-Disposition: inline; filename*=UTF-8''...` 均按记录内联返回，未下发可直连地址。

### 2.2 浏览器渲染（真实登录态）

- **移动端（H5 `5191`）**：`0920培训计划` / 进行中，区块「培训资料」`共 2 份`，两行
  `名称 | 阶段中文 | 说明 | 预览`，预览按钮 2 个，**页面无「下载」字样**（`hasDownloadWord: false`）；
  PDF 预览：`PdfPreview` 渲染出 canvas `370×523`，真实页内容；图片预览：`IMG` + `src=blob:http://127.0.0.1:5191/...`、
  `naturalWidth=1664`；页面错误收集 `[]`。
  截图 `engineering/assets/2026-09-24-ui-mobile-material-list.png`（证据已于 2026-09-28 清理）、`...-material-pdf-preview.png`、`...-material-image-preview.png`。
- **PC 门户（`/portal/training/6`）**：区块标题 `["培训内容","培训资料"]`，两行「在线预览」，
  `hasDownloadButton: false`；预览弹窗标题=资料名、1 个 canvas、`hasViewer: true`（`AssetViewer` 内 `PdfViewer` 按字节渲染）。
  截图 `...-ui-portal-material-list.png`、`...-ui-portal-material-preview.png`。
- **PC 档案（`/admin/business-center/training/plan/5/archive`）**：奖惩表列头
  `["类型","标题","分数变动","发生日期","说明","佐证"]`，行 `["奖励","优异表现","10","2026-09-23","线下实操表现优异，成绩突出","预览"]`；
  点「预览」弹窗标题「奖惩佐证预览」+ `img[src^=blob:]`。
  截图 `...-ui-archive-reward-evidence-column.png`、`...-ui-archive-reward-evidence-preview.png`。
- **PC 工作台（计划 5「过程与结果」→「培训资料」）**：「资料类型」下拉选项为
  **`["一般资料","图片","视频","评价佐证"]`**，`hasRewardPenalty: false`。
  截图 `...-ui-pc-material-type-options.png`。

**学员端「停用资料不可见」的页面侧证据**（计划 3 / 阶段 23 / 本人参训记录 6）：
造 2 条 `GENERAL` 资料——S1 启用、S2 停用——逐态渲染两端页面：

| 状态 | 移动端区块事实 | PC 门户区块事实 |
|---|---|---|
| S1 启用 / S2 停用 | `hasBlock: true`，`cardCount: 1`，仅 `停用态验证-启用资料`，`hasDownloadWord: false` | 同上（`cardCount: 1`，仅 S1） |
| S1、S2 全停用 | **`hasBlock: false`，`cardCount: 0`，`previewButtons: 0`** | 同上 |

即：停用资料**在页面上一律不出现**，且全不可见时**两端都完全不渲染资料区块**（不是渲染空表格），
与 spec 的「无可见资料的空态」Scenario 一致。
截图 `engineering/assets/2026-09-24-ui-mobile-material-absent.png`（证据已于 2026-09-28 清理）、`...-ui-portal-material-absent.png`（两态原始事实 `stop-state-enabled.json` / `stop-state-disabled.json`）。

### 2.3 管理端 UI 全链路（本次新增，9 步真实点击，计划 5）

补上 Task 6 验收标准 1「真实管理员完成 **上传资料 → 停用 → 档案不含 → 改回启用 → 档案含**」，全程用真实点击 + 真实文件输入
（CDP `DOM.setFileInputFiles` 塞入 599 B 的最小 PDF），逐步核对 DOM 事实：

| 步骤 | 实测事实 |
|---|---|
| 基线 | 培训资料表 1 行：`验收现场照片 / 图片 / 线下培训 / 启用` |
| 上传 | 「上传培训资料」→「上传文件」→ 队列项 `ui-chain-material.pdf 1 KB · **上传成功**` → 点「完成」回到填单态，`资料文件` 项变为 `重新上传 / 预览文件`（`fileInfoId` 已回填） |
| 保存 | 表格新增 `UI链路验收资料-制度文件 / **一般资料** / 未关联阶段 / 启用` |
| 档案（启用） | 「附件清单 `共 1 条`」含 `UI链路验收资料-制度文件 / 一般资料`；「影像记录 `共 1 条`」仍为原 `验收现场照片` |
| 停用 | 编辑弹窗状态开关由 `is-checked` → 未选中，保存后表格状态列变 **`停用`** |
| 档案（停用） | 「附件清单 **`共 0 条`**」，该资料**不再出现** |
| 改回启用 | 再次编辑，开关回到选中，保存 |
| 档案（启用） | 「附件清单 `共 1 条`」**重新包含**该资料 |
| 清理 | UI「删除」+ 确认，表格回到基线 1 行 |

档案前后两侧截图 `engineering/assets/2026-09-24-ui-chain-archive-material-on.png`（证据已于 2026-09-28 清理） / `...-off.png`。
链路原始证据 `ui-chain.json`（含每一步的 DOM 取值）。

## 三、未覆盖项（如实记录）

1. **跨租户（跨机构）拒绝**：真实环境只有 `DEMO` 单租户，无法构造他机构资料与参训；
   由 `TrainingAttachmentServiceIT`（租户隔离）与 `TrainingWorkflowServiceIT` 覆盖。
2. **移动端原生（App/小程序）分支**：只验了 H5 产物；`// #ifdef H5` 之外的 `buildMaterialPreviewSource`
   回退分支（非 H5 直接使用受控地址）**未在真机验证**。
3. **「学员端看不到停用资料」的页面侧证据已补**（见 2.2 末表）：仅覆盖「停用资料不出现」与「全不可见时不渲染区块」两点；
   **停用态下的文件读取 404/8233** 仍只有接口层证据（页面侧停用资料没有入口可点）。
   管理端的停用态页面侧证据另见 2.3。
4. **移动端图片预览截图的素材来源**：验收用的「一般资料/图片」文件本身是一张 PC 页面截图，
   因此图片预览截图看起来像"门户截图"，判定依据是 `IMG` + `blob:` + `naturalWidth=1664`，与素材内容无关。
5. **`VIDEO` / `EVALUATION` 资料的呈现**：学员侧一律不返回（已由接口与 IT 断言覆盖），
   管理端两类资料的预览未单独造数据（走同一受控端点与 `AssetViewer` 分支）。
6. **管理端档案的「下载」按钮未改动**：`ArchiveFactsSections` 的影像记录/附件清单仍保留「预览 + 下载」，
   由 `training:attachment:list` 门控。本 Change 的「不提供下载」只约束**学员端**，管理端档案下载是既有权能，
   属**刻意保留**，不是遗漏。

## 四、验收产生的数据

- 计划 5 的 1 条 `IMAGE` 资料（`fileInfoId=49`）为既有业务数据，未改动。
- 本次 UI 链路新建的 1 条资料经 UI 删除（软删，`training_attachment_id=16`，`deleted=16`，应用不可见），
  其上传文件 `file_id=57` 作为对象存储孤件保留——与既有验收残留（id 8/9/12～15）同类。
- 学员侧验收造的 4 条资料（12/13/14/15）已删除，学员侧列表回到 `[]`；未改动任何表结构与权限种子。
- 本次「停用态」补证造的 2 条资料（`training_attachment_id` 17/18）经 API 删除（软删，应用不可见），
  对应上传文件 `file_id` 58/59 作为对象存储孤件保留；学员侧列表回到 `[]`。

## 五、双轴 Review

- **规格符合性**：三份 spec 的 8 个 Requirement 逐条有证据——类型收敛（8234 实测）、
  佐证只走奖惩记录（档案奖惩「佐证」列 + `evidenceFileId` 预览）、启用资料口径（档案三视角 + 归档登记 + 学员侧同一过滤）、
  学员侧只读/只本人/只 GENERAL/只启用（13 组用例）、不返回文件主键（列表响应实测无 `fileInfoId`）、
  学员侧只预览不下载（两端 `hasDownload*: false`）、无可见资料不渲染区块（`v-if="materials.length"`）。
  **权限模型零变更**：沿用 `training:learner:task`，未向学员发放 `training:attachment:list`。
- **代码质量**：学员侧端点顺序为「本人 → 计划可见 → 资料属于该计划且 GENERAL 且启用」，任一不满足即拒绝，
  且**他人参训与不存在参训返回同一 8155**，不泄漏存在性；文件读取复用受控内联响应，未复制第二套读取逻辑；
  前端移动端因学员侧不下发文件名而**按字节头判定格式**（与后端按内容分流一致），
  预览层与既有 `PdfPreview` / `AssetViewer` 复用而非新引播放器；
  资料区块空态不渲染，失败只清空数组不打整页错误态。未发现本次引入的重复代码或未使用依赖。
