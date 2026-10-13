# Design：隔离证书签发来源，建立三类独立达标触发

## 1. 真相源与边界

优先级依次为：

1. 本 Change 经人工确认后的 Proposal、Design、Tasks 与 Spec Delta；
2. 功能项 19 原文「证书可配置于理论考试、课程和培训，达到合格标准后，系统根据配置进行下发相应的证书」；
3. `openspec/specs/m4-minimum-closure/spec.md` 的证书最小闭环；
4. `docs/api/certificate.openapi.json`；
5. 当前证书、课程、考试和培训实现。

本 Change 只调整「来源语义、触发点、候选口径与来源展示」，不改模板设计、证书文件生成、
证书查询下载与权限模型。

## 2. 根因

### 2.1 来源 ID 两侧不同空间

`CertificateIssueRuleServiceImpl` 的候选、绑定校验与名称解析统一使用主数据 ID；
`CertificateAutoIssueServiceImpl.issueForCompletedContents` 则从 `TrainingExecutionLink` 取执行实例 ID。

实测（staging，只读）：

| 事实 | 值 | 说明 |
|---|---|---|
| `sts_training_execution_link`(plan_content 9, COURSE) | `execution_id=2` | 来自 `course_offering_id` |
| `sts_course_offering`(2) | `course_id=1`、`plan_content_id=9` | 计划 1 的开课实例 |
| 规则 1/4 `COURSE.source_id` | `2` | 指向独立课程 `sts_course_course.course_id=2` |
| `sts_training_execution_link`(plan_content 10, EXAM) | `execution_id=38` | 来自 `assessment_execution_id` |
| `sts_assessment_execution`(38) | `exam_id=33`（`TRN-E-10`，计划派生） | 培训计划 1 的派生考试 |
| 规则 2/5 `EXAM.source_id` | `38` | 指向独立考试 `sts_exam_exam.exam_id=38` |

两处均为**数值撞车**：`course_offering_id` 与 `course_id` 恰好同为 2，`assessment_execution_id`
与 `exam_id` 恰好同为 38。撞不上的数据一条都不会命中。

### 2.2 培训内内容被当作独立来源

`issueForCompletedContents` 遍历培训计划内的 `COURSE`/`EXAM` 内容项，完成即按对应类型触发。
这把「培训的课程/考试」与「独立的课程/考试」混到了一处，与功能项 19 的三类来源语义不符。

### 2.3 独立学习链路零触发

全仓检索：证书自动签发只有 `TrainingResultSyncServiceImpl` 三处调用；课程域（`com.sts.course`）、
考试域（`com.sts.exam`）源码无任何证书引用；`CertificateController` 无手工签发端点。
因此独立课程完成、独立考试通过拿不到证书。

## 3. 目标语义：三效触发

| 来源类型 | 达标事实 | 触发 ID |
|---|---|---|
| `COURSE` | 独立课程的学习状态达到 `COMPLETED` | `course_id` |
| `EXAM` | 独立考试的考生结论达到 `PASSED` | `exam_id` |
| `TRAINING` | 培训整体结果达到 `PASSED` | `training_plan_id` |

- 三者的「独立」口径：**不属于任何培训计划**。
- 培训计划内引用的课程与考试**只参与培训结果判定**，不再单独触发 `COURSE`/`EXAM` 证书。
- 课程内考核（章节测验等）归属课程，不触发 `EXAM` 证书。

## 4. 后端设计

### 4.1 自动签发服务归位证书域

现状 `com.sts.training.service.CertificateAutoIssueService` 位于培训域，若由课程域、考试域调用，
会形成 `course/exam → training` 的反向依赖。项目既有 `TrainingCourseExecutionPort` 端口已确立
「跨域通过域内端口，不反向直连」的边界，本 Change 遵守同一约束。

- 新增 `com.sts.certificate.service.CertificateAutoIssueService`，暴露通用入口：

  ```java
  void issueForSource(String sourceType, Long sourceId, Long userId, Long sourceResultId);
  ```

  职责：按 `sourceType + sourceId` 查启用且自动签发的规则，校验模板已启用且存在当前 PUBLISHED 版本，
  组装 `CertificateIssueRequest` 调用既有 `CertificateService.issue`。幂等由既有唯一键
  `uk_holder_source_deleted` 与 `CertificateService.issue` 的前置查询共同保证。
- 该服务只依赖证书域自身的 Mapper 与 `CertificateService`，不依赖课程、考试、培训任何实体。
- 删除培训域旧实现（含 `issueForTrainingPass`、`issueForCompletedContents`）。

### 4.2 培训域：只保留培训结果触发

`TrainingResultSyncServiceImpl`：

- 删除 `issueForCompletedContents` 在 `synchronizeParticipantContent`（现 138 行）与
  `synchronizeParticipant`（现 152 行）的调用。
- `freezePlan`（现 203 行）改为调用
  `issueForSource("TRAINING", participant.getTrainingPlanId(), participant.getUserId(), participant.getTrainingParticipantId())`。

### 4.3 课程域：新增独立课程完成触发

`CourseLearningServiceImpl` 在汇总学习进度并写回 `CourseEnrollment` 处（现 806–823 行）：

- 仅在以下条件同时成立时触发：
  1. 本次调用使 `learningStatus` **由非 COMPLETED 变为 COMPLETED**（首次达标，避免重复调用）；
  2. 该开课**不属于培训计划**（`offering.getPlanContentId() == null`，与既有
     `refreshTrainingProjection` 的独立课程判定一致）。
- 触发参数：`sourceType="COURSE"`、`sourceId=offering.getCourseId()`、`userId=enrollment.getUserId()`、
  `sourceResultId=enrollment.getCourseEnrollmentId()`。
- 达标口径：`COMPLETED` 即达标，不设及格线（人工确认结论）。

### 4.4 考试域：新增独立考试通过触发

`ExamAttemptServiceImpl` 有两个「考生最终结论落定」的私有收口方法：

- `updateCandidateAfterPublish`（人工发布 / 培训考试自动发布路径，现 886 行）；
- `updateCandidateAfterEffective`（课程考核结果自动生效路径，现 701 行）。

在**两个方法末尾统一埋点**，一次覆盖全部成绩发布入口（含 358、424、520、869 等调用点），
避免逐个入口埋点造成遗漏。筛选条件：

1. `"ASSIGN".equals(candidate.getAssignmentSource())` —— 独立指派考生；`COURSE`、`TRAINING`
   两类在数据上分别对应课程考核与培训考试，均被排除；
2. 考试本身非培训派生：`exam.planContentId == null`（`exam` 由
   `assessmentExecutionId → AssessmentExecution → Exam` 取得）；
3. 结论为 `"PASSED".equals(candidate.getCandidateStatus())`。

触发参数：`sourceType="EXAM"`、`sourceId=exam.getExamId()`、`userId=candidate.getUserId()`、
`sourceResultId=candidate.getExamCandidateId()`。

该埋点与既有 `notifyTrainingProjection`（现 1325 行，按 `trainingParticipantId` 过滤并投影给培训域）
互为镜像：一个只处理培训考生，一个只处理独立考生，互不重叠。

### 4.5 候选口径对齐

`CertificateIssueRuleServiceImpl.examCandidates` 增加 `plan_content_id IS NULL`：

- 排除培训派生考试，实测候选由 16 条收敛为 10 条（原 6 条 `TRN-E-xx` 派生考试不再出现）。
- 课程主数据无派生标记（培训开课是 `sts_course_offering` 上的 `plan_content_id`），规则侧本就只选
  独立课程主数据，无需额外过滤。
- 课程考核所用考试实测不在 `ONLINE + PUBLISHED` 候选中（`assignment_source='COURSE'` 关联的考试
  未进入该集合），无需额外排除。

### 4.6 同一来源仅允许一条启用规则

归档证书设计 8.2 要求「一个来源对象只能选择一个启用模板」，但既有唯一键
`uk_source_rule_deleted(source_type, source_id, certificate_template_id, deleted)` 把模板纳入唯一性，
允许同一来源绑定多个模板。实测 staging 曾并存 `COURSE/2 → 模板 3` 与 `COURSE/2 → 模板 5` 两条启用规则，
同一学员达标会命中两条规则、按不同模板签发两张证书，与设计语义冲突。

收口方式（**不改 DDL**）：在 `CertificateIssueRuleServiceImpl` 增加应用层唯一性校验。

- 语义：同一 `source_type + source_id`，在**启用态（`status=1`）且未删除**的规则中 MUST 至多一条。
- `create`：请求为启用态时，先查该来源是否已存在启用规则，存在则拒绝（新错误码 9418，HTTP 409）。
- `update`：请求为启用态时，按同一条件查询并**排除自身** `certificateRuleId`。
- 禁用态（`status=0`）不参与约束，允许同一来源保留多条禁用规则作为历史；替换模板须「先禁用、再启用」。
- 保留既有唯一键，继续拦截「同来源 + 同模板」的完全重复（错误码 9412）。

不把 `status` 并入数据库唯一键的原因：那会让「两条禁用规则」互相冲突，且改索引属 DDL 变更，
超出本 Change 的既定边界（不改结构、不动 `sts.init.schema-version`）。

### 4.7 存量规则核对

收口前用只读 SQL 核对共享库：`uk_source_rule_deleted` 的重复组合（`COURSE/2`、`EXAM/38`）中，
旧的两条（`certificate_rule_id=1/2`）已逻辑删除，生效的 `status=1 AND deleted=0` 组合**无重复**。
因此本次收口**不需要清理历史规则数据**，新约束对存量数据零影响。

## 5. 前端设计

签发规则页（`frontend/pc-web/src/views/admin/result/certificate/rules/index.vue`）与来源字典
（`constants/enum-options.ts`）：

1. 来源类型标签改为可自解释的三分口径：**独立课程 / 独立理论考试 / 培训计划**，并在表单中给出
   一句归属说明（如「培训计划内的课程与考试随培训结果发证，不单独配置」），避免管理员误配。
2. 候选下拉按当前选中类型隔离加载（既有行为），补齐「来源编码 + 名称 + 状态」展示，
   让相同名称的课程/考试可区分。
3. 列表的「来源类型」与「签发来源」两列合并展示为「独立课程 · xxx（CODE）」，类型与对象不再分离，
   减少读表歧义；来源无法解析时保留编号兜底。
4. `api/m4-outcome.ts` 的 `CertificateRuleSourceCandidate` 与 `docs/api/certificate.openapi.json`
   同步候选语义说明（不含培训派生对象），字段结构不变。

## 6. 脏数据清理

已由撞车产生的历史证书（实测 `certificate_id=3` 为 `COURSE/2`、`certificate_id=4` 为 `EXAM/38`）：

- 清理前先按「`source_id` 不等于对应主数据实际用途」逐条核对并列表确认；
- 采用与既有 `deleted` 列一致的**逻辑删除**，不物理删除，保留可追溯性；
- 不触碰证书文件对象，不修改 `sts_certificate_issue_rule` 配置本身（规则是否重配由业务决定）；
- 清理脚本作为定向 SQL 记录在 Change 内，执行前人工确认；不写入 Flyway migration。

## 7. 测试设计

### 7.1 零 DB Red → Green

- 证书域 `CertificateAutoIssueService`：三类 sourceType 各自命中启用规则；无规则、模板未启用、
  模板无 PUBLISHED 版本时跳过；幂等重复调用不产生第二条。
- 培训域：培训内 `COURSE`/`EXAM` 内容完成**不再**调用签发；培训整体通过仍触发 `TRAINING`。
- 课程域：首次由非 COMPLETED 变 COMPLETED 且开课无 `planContentId` 时触发，参数为 `course_id`；
  培训开课（`planContentId` 非空）不触发；重复进入已完成状态不重复触发。
- 考试域：`ASSIGN` 且考试非派生且 `PASSED` 时触发，参数为 `exam_id`；`COURSE`、`TRAINING`
  考生与派生考试不触发；发布与自动生效两条路径都能触发。
- 候选查询：派生考试不出现在 `EXAM` 候选中。

### 7.2 MySQL IT

- 独立课程完成 → 真实落库证书且 `source_id = course_id`；
- 独立考试通过 → 真实落库证书且 `source_id = exam_id`；
- 培训计划通过 → `TRAINING` 证书，且计划内课程/考试完成不再产生 `COURSE`/`EXAM` 证书；
- 同一来源重复同步不重复签发（唯一键幂等）。

### 7.3 契约与关闭

- Controller-operation、DTO/VO 与 `certificate.openapi.json` 保持一致；
- 前端 `vue-tsc --noEmit`、定向 `eslint --max-warnings=0`、受影响端 build 通过；
- 后端受影响模块定向编译与测试通过；
- `openspec validate 2026-09-24-separate-certificate-issue-sources --strict`、`git diff --check` 通过；
- 规格符合性与代码质量双轴 Review；更新 tasks、进度表、QA 与 Retro。

## 8. 兼容性与风险

- **既有撞车配置行为改变**：原先靠数值巧合出证的 `COURSE/2`、`EXAM/38` 规则在本次改造后不再由
  培训链路触发；若业务确需该课程/考试发证，应由独立学习链路自然触发。
- **历史证书不改写**：除本次定向清理的撞车产物外，其余历史证书一律不动，符合「规则禁用/编辑不撤销
  历史证书」的既有设计。
- **无 DDL、无权限变更**：不新增表、列、索引与权限节点，不需要 migration，不需要改
  `sts.init.schema-version`。
