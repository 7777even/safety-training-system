# Design：移动端学员成果闭环

## 一、复用既有契约（零新端点）

| 能力 | 复用端点 | 权限 |
|---|---|---|
| 我的证书列表 | `GET /api/certificate/my-certificates` | `certificate:cert:list` **OR** `training:learner:task` |
| 证书原件在线查看/下载 | `GET /api/certificate/my-certificates/{certificateId}/file` | 同上 |
| 我的培训档案（任务与结果） | `GET /api/training/learner/tasks` | `training:learner:task` |
| 每期课程考试记录 | 既有 `pages/training/detail`（`/training/learner/participants/{id}` 等） | `training:learner:task` |

- `/certificate/my-certificates` 的权限是 `SaMode.OR`，学员凭 `training:learner:task` 即可访问，
  **无需新增权限**；服务端已按登录人收口（仅本人），移动端不传人员主键。
- 本 Change **不新增业务表、不新增权限节点、不改数据库结构**。

## 二、唯一的后端改动：培训类型名称

- 问题：`TrainingLearnerTaskVO` 有 `trainingTypeId` 无 `trainingTypeName`，前端无法按类型分组显示中文名。
- 方案：在 `TrainingWorkflowController` 的 `listLearnerTasks` 路径上，用既有 `TrainingTypeMapper`
  对本次返回的 `trainingTypeId` 集合**批量**查询名称并回填（避免逐条查询）。
- 同步：`docs/api/training.openapi.json` 的 `TrainingLearnerTaskVO` 增加 `trainingTypeName` 字段
  （中文 `description` + example）。
- 备选及未采纳：让移动端另调培训类型主数据接口自行映射——移动端当前没有该接口模块，
  且会把"类型名称"这件展示事实散到两个请求里，弱网下容易出现名称缺失。

## 三、移动端页面设计

### 我的证书（`pages/certificate/index`）

- 列表：卡片式，每张展示证书编号、来源类型中文、签发时间；不做 PC 的密集表格。
- 操作：点击「在线查看」在线预览（受控端点取 Blob/URL）；原件下载走受控端点按证书 ID 读取，不在列表暴露文件地址。
- 空态与失败：区分「暂无证书」与「加载失败」，失败可重试。

### 我的培训档案（`pages/archive/index`）

- 顶部：按培训类型汇总的任务数量（类型名 + 数量），用 chip / 分组头表达，不做图表。
- 列表：按类型分组，任务项展示计划名称、培训期间、进行状态、培训结果（合格/不合格/结果待补全/待同步），
  点击进入既有 `pages/training/detail`。
- 类型名称缺失时回退为「未分类」，不渲染空白。

### 个人中心入口

- 在 `taskEntries` 之外新增成果分组，沿用既有页面栈跳转；**不改变**既有三项的 tabBar 语义。

## 四、边界与风险

- 移动端只做**学员本人**视角；管理端档案能力一律不下放，学员不能读他人数据。
- 证书与培训任务的中文口径 MUST 与 PC 及既有字典一致（来源类型三分：独立课程 / 独立理论考试 / 培训计划；
  结果状态：合格 / 不合格 / 结果待补全）。
- 移动端 `api/request.ts` 只封装通用能力，真实接口字段以 `docs/api` 为准，不自行定义业务字段与错误码。
- 风险：学员端培训类型主数据若被停用，历史任务的类型名可能查不到——按「未分类」兜底，不报错。
