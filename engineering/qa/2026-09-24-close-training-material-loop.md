# 培训资料（影像/附件）闭环 QA

对应 Change：`2026-09-24-close-training-material-loop`（功能项 34/36）。
记录时间：2026-09-24。

## 范围

计划内培训资料的新增、编辑、删除与受控预览/下载；写入前的计划状态、阶段归属与文件可见性校验；
结束后只读。资料类型收敛为 `GENERAL` / `IMAGE` / `VIDEO` / `REWARD_PENALTY` / `EVALUATION`。

## 已验证（开发侧自动化，2026-09-24 实跑）

- 后端真实编译：`Compiling 1032 source files ... to target\classes` 后 `BUILD SUCCESS`（首次因并发构建抢占
  `target/classes` 报「写入 TrainingExecutionLinkMapper.class 出错」，重跑通过，非代码问题）。
- 定向单测：`TrainingAttachmentServiceImplTest` 8/8、`TrainingExecutionControllerTest` 7/7 全绿。
- MySQL IT：`TrainingAttachmentServiceIT` 5/5 全绿（`-P integration-test` + `TESTCONTAINERS_RYUK_DISABLED=true`），
  覆盖租户隔离、阶段归属、类型收敛、同计划同文件重复、结束只读且**计划版本不推进**。
- PC `vue-tsc --noEmit` 退出码 0；改动文件 eslint `--max-warnings=0` 退出码 0。
- `openspec validate 2026-09-24-close-training-material-loop --strict` 通过。

## 真实环境验收（2026-09-24 完成）

环境：本地后端 `20080`（`vpn` profile，连团队库与 MinIO），PC dev 独立端口 `5176`（`/api` 代理指向本地 `20080`），
无头 Chrome 153 CDP 直连。真实登录账号 `DEMO/admin`。

### 接口层（真实 HTTP，含 MinIO 真实落盘）

- 文件上传 `POST /api/system/files` → 200，`fileInfoId=41`，返回
  `objectName: DEMO/source/2026/09/<uuid>.png`、`fileUrl: minio-private://...` —— **对象存储真实写入成功**。
- 资料新增（`RUNNING` 计划 5 + 阶段 30 + `IMAGE`）→ 200；列表回读含名称/类型/关联阶段/文件/状态/乐观锁版本。
- **受控读取** `GET /api/training/attachments/{id}/file` → 200、`image/png`、170322 字节、
  PNG 魔数 `\x89PNG\r\n\x1a\n`，且**与源文件字节完全一致**。
- 记录不存在 → `404 / 8231 培训附件不存在`。
- **冻结（`ENDED`）后写入被拒**：plan 4 新增 → `409 / 8237 仅进行中的培训计划可维护培训资料`。
  删除路径 `delete()` 同样先过 `requireRunningPlan`（代码第 150 行），增/改/删三处共用同一校验。
- **跨计划阶段被拒**：plan 5 + 阶段 23（属 plan 3）/ 阶段 28（属 plan 4）/ 阶段 999999 →
  均 `400 / 8235 培训资料关联阶段不属于该培训计划`；阶段 29（属 plan 5）→ 200。
- **无效类型被拒**：`attachmentType='BAD'` → `400 / 8234 培训资料类型无效`。
- **文件不可见被拒**：`fileInfoId=99999999` → `400 / 8236 培训资料文件不存在或不可见`。
- **编辑与乐观锁**：`version=0` 更新 → 200，回读改名生效、`sortOrder` 2、版本 `0 → 1`；
  再用过期 `version=0` → `409 / 8238 培训资料版本冲突`。
- **删除（`RUNNING`）** → 200；删除后列表条数减少。
- **同计划同文件唯一键**：同一 `fileInfoId` 在同一计划下建第二条资料 → `409 / 8232 同计划下文件已存在`
  （与 `uk_plan_file_deleted` 及既有 IT 结论一致，非缺陷）。

### 浏览器渲染（真实管理员登录态）

- 培训计划工作台（`/admin/business-center/training/plan/5`）→「过程与结果」步骤 → **培训资料（1）** 页签：
  行渲染 `验收现场照片 | 图片 | 线下培训 | <说明> | 启用 | 预览 编辑 删除` ——
  **资料类型与关联阶段均为中文**，非英文枚举码。
- 点「预览」→ 弹窗标题**「培训资料预览」**，内含 `<img>` 且 `src` 为
  `blob:http://127.0.0.1:5176/...` —— 即**经受控端点取回字节后以 blob 渲染**，页面不持有可直连的文件地址。
- 全程无控制台错误、无 4xx/5xx。截图 `engineering/assets/2026-09-24-ui-training-material-preview.png`（证据已于 2026-09-28 清理）。

### 未覆盖项（如实记录）

1. **跨机构（跨租户）读取拒绝**：真实环境仅 `DEMO` 单租户，无法构造他机构资料；
   由 `TrainingAttachmentServiceIT`（5 例，含租户隔离）覆盖。
2. **视频类型的真实预览**：验收用的是一条 `IMAGE`（受控读取与预览链路已验）；`VIDEO` 走同一受控端点为
   既有 `AssetViewer` 分支，未单独造视频数据。
3. 无头环境下 `<a download>` 未落盘，「下载」按钮的落盘行为未在浏览器侧取得文件；
   下载链路以**受控端点返回的字节与 MIME**（170322 字节 `image/png`，与源文件逐字节相同）为证。

### 验收产生的数据

- 计划 5 保留 1 条 `IMAGE` 资料（`fileInfoId=49`，关联线下阶段 30）作为真实业务数据；
  验收过程中创建的其余资料已清理，清理后计划 5 资料数为 1。
- 上传的 5 个临时文件仅落在对象存储的 `DEMO/source/...` 路径下，未改动任何表结构。

## 双轴 Review

- **规格符合性**：类型收敛五类与功能项 36「影像、培训附件、奖惩评价、综合评价」逐项对应；
  受控端点按**记录**（而非任意文件 ID）读取；写入前校验计划属于当前机构且为 `RUNNING`、
  阶段属于该计划、文件对当前机构可见；结束后只读且可继续预览；不新增权限、不新增表。
  唯一与 spec 的显式偏差是**写入不推进计划版本**，理由（资料不参与综合结果，避免多文件上传互相冲突）
  已在 `tasks.md` 与 IT 用例中记录，属刻意取舍。
- **代码质量**：校验按 `requireRunningPlan → requireMaterialType → requireStage → requireVisibleFile` 顺序前置，
  错误码 8231～8238 与契约一致；`/file` 端点复用控制器内 `inlineFile`，未复制内联响应逻辑。
  `openFile` 刻意不校验计划状态，以支持结束后只读预览——这是 spec 要求而非疏漏。
  未发现本次改动引入的重复代码或未使用依赖。
