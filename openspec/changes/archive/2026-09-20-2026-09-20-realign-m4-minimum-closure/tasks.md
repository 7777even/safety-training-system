# Tasks：按中山 50 重整 M4 最小闭环

> 状态：已归档。lims 人工确认「后端可交互即可」；前端不在本协作范围。Task 1～4、6 后端完成。Task 5 正式可下载档案仍需独立签到
> Change，不伪装空快照；按确认不阻塞本 Change 归档。Task 7 按后端口径关闭。

## Task 1：建立 M4 范围基线并收口超范围入口

- 状态：已完成（后端）
- [x] 盘点、进度纠正、超范围公开入口删除、SQL COMMENT、OpenAPI 同步
- Owner：lims（仅后端；前端以 OpenAPI 为准另开）
- 输入：功能项 19、35、39、40、42、43、44、50；本 Change Design 第 1～3、9～12 节。
- 输出：M4 能力/代码/API/SQL 盘点表；进度状态纠正；待删除代码和待标注 SQL 清单；调用方清单。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。前端 `evaluation-result.ts` 仍调用已删计算批次路径，由前端协作方同步。
- API 依赖：核对 survey、evaluation-result、archive、certificate OpenAPI 与全部 Controller。
- 数据影响：不删数据；新增 COMMENT migration 方案并同步 init SQL 方案。
- 验收标准：每个保留入口可追溯到功能项；每个删除入口有调用方结论；每个保留 SQL 对象归类为“内部保留”或“暂时隐藏”。
- 测试方式：OpenAPI/Controller 路径对比、菜单权限扫描、静态调用检索、OpenSpec strict、`git diff --check`。

## Task 2：完成问卷最小闭环

- 状态：已完成（后端）
- [x] 复制、双端预览、投放用途/可见性、填写链接与二维码内容、本人提交、统计权限、高级题型/绑定入口收口、OpenAPI 同步
- Owner：lims（仅后端；前端以 OpenAPI 为准另开）
- 输入：功能项 35、42；Design 第 4 节。
- 输出：问卷 CRUD/复制/双端预览/发起/二维码内容/填写/结果统计后端闭环与 `docs/api/survey.openapi.json`。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。管理端工作台、PC/移动填写页由前端协作方实施。
- API 依赖：已同步 `survey.openapi.json` 与 Controller。
- 数据影响：复用现有问卷表；投放快照内部使用；新增 `purpose`、`result_visibility`；高级字段按规则隐藏。
- 验收标准：后端覆盖 Design 4.1～4.4；扫码页与浏览器验收由前端另开。
- 测试方式：零 DB `SurveyControllerTest`、`SurveyServiceImplTest`、`SurveyInstanceServiceImplTest` 18 通过。MySQL IT 仍受既有
  Flyway 与 init 快照重复加列阻塞，未作为本 Task 完成证据。

## Task 3：完成综合评分与已结束培训结果

- 状态：已完成（后端）
- [x] 绑定综合评价表后按考试/课程/线下权重与奖惩自动刷新综合分；缺少来源不补零；奖惩 CUD 触发刷新；
  `GET /training/plans/{id}/ended-result`；OpenAPI 同步
- Owner：lims（仅后端；前端以 OpenAPI 为准另开）
- 输入：功能项 39、40；既有考试正式成绩和奖惩事实；Design 第 5 节。
- 输出：评分表、培训绑定、自动计算、已结束培训结果汇总与个人详情。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：已同步 `evaluation-form.openapi.json`、`evaluation-result.openapi.json` 和 `training.openapi.json`。
- 数据影响：参训人当前综合分为唯一读取口径；旧批次数据保留但隐藏。
- 验收标准：后端覆盖 Design 5.1～5.4；缺少来源时结果不完整且不补零。管理端页面与真实联调由前端另开。
- 测试方式：零 DB `CompositeScoreServiceImplTest`、`TrainingWorkflowServiceImplTest` ended-result；MySQL
  `CompositeScoreServiceIT` 2 通过。

## Task 4：完成专家查课最小闭环

- 状态：已完成（后端）
- [x] 评价表单产品入口仅 COMPOSITE/EXPERT_INSPECTION；专家提交与计划内只读查询保留；OpenAPI 已覆盖
- Owner：lims（仅后端；前端以 OpenAPI 为准另开）
- 输入：功能项 43；Design 第 6 节。
- 输出：查课表管理、培训引用、专家提交和管理员查看。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：评价表单、培训绑定和专家查课契约已对齐。
- 数据影响：复用评价表单/记录表；非查课类型默认列表隐藏。
- 验收标准：后端覆盖 Design 6.1～6.2；管理员—专家页面链路由前端另开。
- 测试方式：零 DB `EvaluationFormServiceImplTest` 拒绝非产品类型；`EvaluationResultControllerTest` 专家入口可达。

## Task 5：补齐签到前置并完成三级档案

- 状态：后端可交互已交付并按人工确认归档；正式可下载档案仍需独立签到 Change
- [x] 仅 ENDED 可归档；缺少 ATTENDANCE 返回 409/9204 且不写空快照；一期/企业/个人查询返回 missingItems；
  `archive.openapi.json` 已重写
- [ ] 独立签到 Change 提供最小签到事实后，才能生成正式档案与真实下载（残留，不阻塞本 Change 归档）
- Owner：lims（仅后端；前端以 OpenAPI 为准另开）
- 输入：功能项 44；已结束培训事实；另行确认的最小签到 Change；Design 第 7 节。
- 输出：个人档案、企业档案、一期一档查询；正式归档与下载仍被签到阻塞。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：已重写 `archive.openapi.json`，删除版本历史/任务中心公开路径。
- 数据影响：复用现有档案表；不删历史版本；不写伪装空签到快照。
- 验收标准：Design 7.7 因签到事实缺失未满足，本 Task 不得完成。
- 测试方式：零 DB `ArchiveServiceImplTest` 断言非 ENDED 与 ATTENDANCE 缺失拒绝；MySQL `ArchiveServiceIT` 3 通过。

## Task 6：完成证书设计、自动签发和学员查看

- 状态：已完成（后端）
- [x] 达标自动签发真实 PNG 文件；幂等；模板示例预览；管理员查询下载；本人 my-certificates；OpenAPI 同步
- Owner：lims（仅后端；前端以 OpenAPI 为准另开）
- 输入：功能项 19、50；课程/考试/培训通过事实；Design 第 8 节。
- 输出：模板设计预览、来源配置、达标自动签发、真实文件、管理员和本人查看下载。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：已补全 `certificate.openapi.json`，删除任意手工签发、撤销和重发路径。
- 数据影响：复用模板/元素/规则/证书表；撤销和有效期相关字段保留并标注；禁止占位文件 ID。
- 验收标准：后端覆盖 Design 8.1～8.4；PC/移动页面与三来源 E2E 由前端另开。
- 测试方式：零 DB `CertificateServiceImplTest` 幂等与真实 fileId。

## Task 7：M4 联调、Review、QA 与状态关闭

- 状态：已完成（lims 后端口径；2026-09-20 人工确认归档）
- [x] 回填 tasks.md、进度表、QA；Controller 与 OpenAPI 对齐（后端范围）
- [x] 按人工确认「后端可交互即可」归档本 Change；前端页面与五条真实角色链路不在 lims 范围
- Owner：lims（仅后端；前端以 OpenAPI 为准另开）
- 输入：Task 1～6 后端证据；Task 5 正式档案文件残留已记录。
- 输出：后端 OpenAPI、QA、进度表；本 Change 按后端口径归档。
- 前后端影响：仅后端；前端以 OpenAPI 为准另开。
- API 依赖：Controller 与 survey/evaluation-result/archive/certificate/training OpenAPI 对齐。
- 数据影响：init SQL、COMMENT migration 保持 Task 1～2 已同步状态。
- 验收标准：后端可交互 + OpenAPI 对齐即可归档；不得用空签到快照伪装正式档案。
- 测试方式：定向零 DB 与相关 MySQL IT；不以无关全量测试替代。

