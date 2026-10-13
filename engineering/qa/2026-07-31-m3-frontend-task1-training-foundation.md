# M3 前端 Task 1：培训基础配置 QA

- 日期：2026-07-31
- 范围：培训类型、培训标准、培训模板/分类、评价表单/不可变版本
- OpenSpec：`2026-07-29-complete-m3-training-execution-loop` Task 1

## 验证结果

- OpenAPI 结构化复审：通过；接口路径、字段、错误响应和乐观锁语义已核对。
- TDD：API、Contract Mock、页面和内部路由均先观察失败，再完成最小实现。
- Node 定向测试：2026-08-04 接入当前机构可引用资源后 34 个用例通过。
- ESLint：本次新增 API 和修改的模板工作台定向检查通过；全量 lint 仍被 M1/M2 工作区既有 1 个错误和 261 个警告阻塞。
- PC typecheck：通过。
- PC production build：通过。
- 双轴 Review：2026-08-04 结论为“需人工决策/需修改”；稳定版本与权限存在契约缺口，标准资源查询、加载竞态、上传类型校验和文件详情回显仍需修正。

## 2026-08-01 人工反馈修正

- 机构统一改用 `OrganizationSelect`；培训类型、标准、课程、考试和题库均改为 OpenAPI 数据列表选择。
- Task 1 主要表单补齐显式中文 `FormRules.message`，全局 Element Plus 继续使用 `zh-cn`。
- 培训类型图片和素材文件统一通过 `UnifiedFileUpload` 公共弹窗上传；用户点击“完成”后才把 `fileInfoId`、文件名和大小回显到原表单，关闭弹窗清空上传队列。
- 归档标准与归档模板只读；机构变化清空并重载下游选项；确认取消和校验拒绝不再产生未处理 Promise。

## 2026-08-04 可引用资源契约接入

- 模板工作台统一调用 `GET /api/training/reference-resources` 查询 COURSE、EXAM、QUESTION_BANK。
- 请求不提交 `organizationId`，当前机构由后端登录身份派生；M3 最小闭环不读取跨机构授权。
- 下拉项回显资源名称、编码和可执行状态，并保留接口返回的 COURSE/EXAM `versionId`。
- 接口权限为 `training:plan:list`，模板维护角色需要同时获得该权限。

## 当前阻塞

- 查询响应返回 `versionId`，但 `TrainingTemplateContentRequest/VO` 无稳定版本字段，模板保存会丢弃 COURSE/EXAM 版本。
- `training:plan:list` 不能自然覆盖仅有标准/模板权限的维护角色，需要后端明确组合授权或提供独立可引用资源权限。
- Task 2 还缺少计划内容版本快照回读、评价表单绑定回读、参训人名称/机构名称，以及计划机构与当前机构资源查询的一致上下文。
- 按人工决策，QUESTION_BANK 不在计划发布时冻结版本，由考试链在开考时冻结题目快照。

## 环境说明

- 后端 Maven 定向测试未能在当前终端启动：内网 Nexus `172.16.1.200:8081` 连接超时；不属于代码测试失败。
- 仓库当前 TypeScript 5.9 与 `ignoreDeprecations: "6.0"` 不兼容；验证时临时使用 `"5.0"`，验证后已恢复用户原配置。
- 当前系统默认 Node 16 不满足 ESLint 10 和 `structuredClone`；验证使用 Codex 工作区 Node 24.14.0。

## 非阻塞欠账

- 仓库中部分历史 OpenAPI schema 仍有泛化字段描述，未影响本次已使用字段。
