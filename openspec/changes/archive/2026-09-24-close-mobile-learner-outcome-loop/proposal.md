# Proposal：补齐移动端（员工学习端）M4 成果闭环

## 状态

- **已人工确认（2026-09-24）。** 本 Change 由「M4 闭环只在 PC 端完成，移动端未补充」提出。
- 本 Change 只做**学员本人**的成果视角（查看证书、我的培训档案），**不含**移动端管理端能力
  （档案维护、归档、企业档案），也不改动 PC 端已交付的任何页面与接口语义。

## 决策记录：不向学员开放 `archive:training:list`

2026-09-24 曾评估「在菜单权限中把既有节点 `archive:training:list`（`us_sys_menu.menu_id = 9720`）
授予学员角色，让移动端直接导出个人档案 PDF」的方案，**经人工确认否决**，改为维持本 Change 原边界
（移动端只做在线查看，不导出档案 PDF）。否决依据：

- `archive:training:list` 是**管理端跨人员**权限，当前共门控 6 个端点，且服务端**不校验"本人"**：
  `POST /archive/trainings/create-from-plan`（另一权限 `archive:training:create`）、
  `GET /archive/trainings/{trainingPlanId}`、`GET /archive/trainings/{trainingPlanId}/file`、
  `GET /archive/enterprises`、`GET /archive/persons/{userId}`、`GET /archive/persons/{userId}/file`。
- `ArchiveServiceImpl.getPerson(Long userId)` 直接信任入参、无登录人收口，学员传入他人主键即可读到
  对方全部已结束培训的成绩、综合评价、奖惩与证书入口数据；单期端点还会返回**全员**成绩与奖惩。
  租户隔离只把范围收敛到同租户，**同企业内互相可见仍属越权**。
- 若要支持学员导出自己的档案 PDF，正确做法是新增**本人收口**端点（服务端按登录人取 `userId`、
  不接受 `userId` 入参）并配套本人权限节点，属 L4 权限模型变更，须单独立项，不在本 Change 内实施。
- 学员侧 "查看自身学习档案" 由本 Change 的「我的培训档案」（功能项 57，复用
  `GET /api/training/learner/tasks`）承载，不依赖 `archive:*` 任何端点。
- 功能项依据：`docs/product/功能项.csv` 中山 **50**「个人中心：支持个人信息完善、**查看证书**、绑定微信、修改密码等操作」、
  **57**「**我的培训档案**：展示学员的培训任务统计，统计不同培训类型下培训任务的数量，查看培训结果，
  支持查看每个培训下的课程考试记录」；辅以 **52**「学员的个人资料……实现一人一档，**个人可在自己的账户下查看自身学习档案**」。

## 现状与问题

- 移动端（`frontend/mobile-app`，uni-app）现有 15 个页面：登录、首页、课程、考试、培训、个人中心。
  **全仓不存在任何证书或档案相关代码**（`grep -rln "certificate\|档案\|archive" src/` 零命中），
  `src/api/` 只有 `asset`、`auth`、`learner-course`、`learner-exam`、`request`、`training-learner` 六个模块。
- 个人中心（`src/pages/profile/index.vue`）只有三个入口：我的课程、我的考试、我的培训。
  **没有「查看证书」，也没有「我的培训档案」**——功能项 50 与 57 的移动端口径未交付。
- 后端其实**已具备**学员侧数据来源，缺的是移动端的消费：
  - `GET /api/certificate/my-certificates` 与 `GET /api/certificate/my-certificates/{id}/file`，
    权限为 `certificate:cert:list` **OR** `training:learner:task`（`SaMode.OR`），学员身份可访问。
  - `GET /api/training/learner/tasks`（权限 `training:learner:task`）返回学员全部培训任务，
    含 `trainingTypeId`、`planName`、`publishStatus`、`participantStatus`、`resultStatus`、起止时间。
- **唯一契约缺口**：`TrainingLearnerTaskVO` 有 `trainingTypeId` 但**没有 `trainingTypeName`**，
  移动端因此无法按「不同培训类型」分组并显示中文类型名，而功能项 57 明确要求按培训类型统计。
- 移动端「每期培训下的课程考试记录」**已存在**：`src/pages/training/detail.vue` 已展示课程/理论考试/
  题库练习/线下场次的内容项与结果入口。本 Change 不复做，只把它接进「我的培训档案」的期次入口。

## 目标与边界

1. 个人中心新增「我的证书」入口，学员可查看本人证书列表并在线查看、下载原件。
2. 个人中心新增「我的培训档案」入口，按培训类型汇总培训任务数量，展示每期的状态与培训结果，
   并可进入该期查看课程考试记录（复用既有 `training/detail`）。
3. 后端仅为「我的培训档案」补一个展示字段 `trainingTypeName`，**不新增端点、不新增权限、不新增表**。
4. 移动端遵守自身协作规范：**禁止复制 PC 页面结构与密集表格**，按触控、小屏与弱网设计；
   不引入 Element Plus。
5. 不触碰 PC 端已交付的档案/证书页面与 `archive.*` 管理端端点；**不给学员开放档案管理端能力**。

## 为什么不做移动端「个人档案 PDF 导出」

- 功能项 44(1) 的「个人档案详情页、导出档案记录」属于**企业/培训机构端（PC 管理端）**口径，已在
  `2026-09-24-close-training-archive-loop` 交付。
- 其数据来自 `GET /api/archive/persons/{userId}`，权限是 `archive:training:list`（管理端权限），
  学员身份不可访问；若要给学员开放，需新增「本人档案」端点并重新界定权限模型——**属 L4 权限变更**，
  经人工确认后不在本 Change 实施（依据见上「决策记录」）。学员侧的学习档案由功能项 57 的
  「我的培训档案」承载。

## 实施顺序

- Task 1：后端 `TrainingLearnerTaskVO` 补 `trainingTypeName` 并同步 OpenAPI 与测试。
- Task 2：移动端「我的证书」页与个人中心入口（复用既有证书端点与受控文件读取）。
- Task 3：移动端「我的培训档案」页与个人中心入口（按类型统计、结果、期次入口）。
- Task 4：纵向验收、双轴 Review、QA、Retro 与状态同步。
