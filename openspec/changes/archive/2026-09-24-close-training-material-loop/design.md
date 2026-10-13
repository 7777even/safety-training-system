# Design：培训计划资料的统一挂载与受控读取

## 业务路径

管理员进入培训计划工作台 → 「过程与结果 / 培训资料」→ 选择资料类型（一般资料 / 图片 / 视频 / 奖惩佐证 / 评价佐证）→ 可选关联培训阶段 → 上传文件并填写名称与说明 → 服务端校验后保存 → 列表回读并可按记录预览原始文件 → 计划结束后只读 → 后续档案 Change 复用同一批资料作为「影像记录」与「附件」事实。

## 资料挂载层级（本 Change 的核心口径）

- 培训资料挂 **培训计划**（`trainingPlanId` 必填），并可**可选**关联到培训阶段（`planStageId` 可空）。
- 依据：功能项 36 的边界是"培训过程中"，功能项 34 把"附件资料"与"线上培训、线下培训"**并列为同级维度**，因此**不把资料绑死到线下培训阶段**；线下阶段只是最常见的关联对象。
- 现有 `TrainingAttachment`（`trainingPlanId` 必填 + `planStageId` 可空）**已支持该口径，无需改表**。

## 后端

- 新增 `GET /api/training/attachments/{trainingAttachmentId}/file`，沿用 `training:attachment:list`，按当前机构可见的附件记录定位 `fileInfoId` 并返回原始字节与内联文件名；无记录或文件不可读返回 404，**不提供按任意文件 ID 下载的入口**（对照奖惩 `/evidence` 的最小实现）。
- 写入路径校验：计划存在且属于当前机构、状态为 `RUNNING`；`planStageId` 若传入须属于该计划；`fileInfoId` 须为当前机构可访问的已上传文件。
- `attachmentType` 的服务端校验收敛为约定集合 `GENERAL/IMAGE/VIDEO/REWARD_PENALTY/EVALUATION`，非法值拒绝；该集合**契约 `enum` 与 DDL 列注释早已声明**，本次只补服务端校验，契约只补错误码说明，**既有数据不做迁移**。
- 更新沿用既有乐观锁字段；计划结束冻结后拒绝写入。

## PC 交互

- 恢复计划工作台第 5 步「过程与结果」中的**培训资料页签**（取消现有注释），按 `training:attachment:*` 权限展示新增、编辑、删除、预览；仅 `RUNNING` 可写，`ENDED` 只读。无独立菜单。
- 表单字段：资料类型（中文枚举）、资料名称、说明、可选关联阶段（从本计划阶段选择，**不手输 ID**）、文件（复用 `UnifiedFileUpload` 取得 `fileInfoId`）、排序、状态。
- 列表展示名称、类型中文、关联阶段、说明与文件大小；有文件时可预览/下载。图片与视频按原文件直接打开（浏览器原生能力），**不引入专用播放器**。
- 后端拒绝时展示中文业务消息并保留当前表单输入。

## 数据与契约影响

- **无新业务表、无新菜单。** 复用 `sts_training_attachment` 与既有 `training:attachment:*` 权限，不涉及 init 快照与增量迁移。
- 新增 1 个只读端点 `GET /api/training/attachments/{trainingAttachmentId}/file`，同步 `training.openapi.json` 的 Controller 分组、接口 summary/description、字段中文 description、请求/响应 example、错误码与 `x-permission`。
- 不顺带恢复已延期的签到、影像转码、媒体播放器、移动端上传；档案组装与档案文件格式由后续 Change 处理。
