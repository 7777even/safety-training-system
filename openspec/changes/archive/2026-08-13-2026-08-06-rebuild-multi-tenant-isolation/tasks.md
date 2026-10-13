# Tasks：重构为多租户隔离体系

> 状态：**已完成（2026-08-07）**，Task 1～9 全部交付。
>
> 两条验收标准标记为**顺延**：Task 6 / Task 7 的「导出产物不含跨租户数据」。
> 档案导出与运营导出都只有建任务、没有执行器，属功能未开发而非隔离缺陷，无产物可验；
> 实现导出执行器时须在对应 Change 内补验。除此之外全部达成。
>
> 过程纠错留痕：Task 2 的「异步任务显式设置上下文」曾误标完成，
> 2026-08-07 复核 6 个异步入口后发现转码与分片清理两处不合规，已分别闭环并附测试证据。
>
> Owner：lims（仅后端；前端以 `docs/api/*.openapi.json` 为准另开）
>
> 依赖顺序：Task 1 → Task 2 → Task 3 → Task 4~7 可并行 → Task 8 → Task 9。
> Task 2 未完成前不得开始业务域接入，否则会形成第二套手写过滤条件。

---

## Task 1：建立租户主数据与开户能力

- 状态：已完成（2026-08-06）
- Owner：lims
- 输入：已确认的扁平租户模型；现有 `sts_org_organization` 表与机构 CRUD；`superAdmin` 判定实现
- 输出：租户表结构收敛；租户 CRUD 与查询；`REGULATOR` 租户建立；OpenAPI
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：机构接口改为租户接口，删除 `parentOrganizationId`，`orgType` enum 改 `ENTERPRISE`/`REGULATOR`，
  标注 `x-permission: system:tenant:*` 且仅 `superAdmin` 可调用
- 数据影响：删除 `parent_organization_id` 列与 `DEFAULT` 占位记录；删除 `sts_org_regulatory_relation` 表；
  建立唯一 `REGULATOR` 租户
- 验收标准：
  - [x] 租户仅 `ENTERPRISE`、`REGULATOR` 两类，无层级字段
  - [x] `org_code` 全局唯一，租户创建后 `org_code` 与 `org_type` 不可修改
  - [x] `REGULATOR` 租户全系统至多一个，业务 API 无法创建第二个
  - [x] 仅 `superAdmin` 可创建、停用租户；普通角色调用返回 HTTP 403
  - [x] 租户查询不再依赖 `DataScopeService`，`DEFAULT` 占位过滤逻辑一并删除
  - [x] `docs/api/org.openapi.json` 满足四条铁律
- 测试方式：先写失败的 Service/Controller 测试；MySQL IT 覆盖 `org_code` 唯一、`REGULATOR` 唯一性约束、
  层级字段删除后的持久化
- 备注：**开户初始化（默认部门、内置角色、管理员账号）移至 Task 3**——`us_sys_user.organization_id`
  由 Task 2 建立，Task 1 阶段尚无法写入归属新租户的账号

---

## Task 2：建立强制租户隔离

- 状态：已完成（2026-08-06；异步上下文传递一项 2026-08-07 复核后补齐）
- Owner：lims
- 输入：Task 1；design.md §2、§3 的上下文与表分类；MyBatis-Plus 插件配置现状
- 输出：`TenantContext`、`TenantContextFilter`、`TenantLineHandler` 与插件注册；豁免名单；异步上下文传递
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：无新增外部接口
- 数据影响：直接修改 `backend/sql/schema-*.sql` 建表语句，为 65 张表（61 张业务子表 + `us_sys_user`、
  `us_sys_file_info`、`us_sys_oper_log`、`us_sys_login_log`）加 `organization_id` 与索引；
  `us_sys_user` 唯一键改 `(organization_id, user_code, deleted)`；不写增量 ALTER 迁移
- 验收标准：
  - [x] SELECT / UPDATE / DELETE / INSERT 自动注入或填充 `organization_id`，含分页 `count` 语句
  - [x] 租户插件注册在分页插件之前
  - [x] 上下文为空时抛出错误，MUST NOT 退化为无条件全表查询
  - [x] A 类 126 张表全部参与隔离，豁免表精确等于 design §3.2 B 类 9 张，每张附理由
  - [x] 全部业务子表带租户列，不存在按子表 ID 绕过隔离的通道
  - [x] 请求结束清理 `ThreadLocal`；线程池复用不残留上一请求租户
  - [x] 异步任务、MQ 消费显式设置上下文后才执行业务
    —— 本条一度误标完成、2026-08-07 复核后改回未完成，同日闭环。全仓库 6 个异步/定时入口
    逐个核对并各有测试证据（不再只凭读代码推断）：
    - `OperLogEventListener`（`@Async`）：写 `us_sys_oper_log`（NOT NULL 租户列）。
      原本静默失败，改为实体显式赋值、平台级操作记 0；`AsyncLogTenantIT` 覆盖
    - `LoginLogEventListener`（`@Async`）：写 `us_sys_login_log`，发布前显式赋值，
      租户无法解析的失败登录记 0；`AsyncLogTenantIT` 覆盖
    - `CacheEvictEventListener`（`@Async`）：只发消息不碰 DB
    - `TranscodeTimeoutRecovery.recover`：只调 `fail()`，SQL 自带 `organizationId` 形参
    - `TranscodeScheduler.poll` → `TranscodeJobProcessor`：原本无上下文导致写派生违反非空约束、
      素材被吞成 FAILED（真库探针证实）。由 `2026-08-07-fix-transcode-tenant-context`
      Task 1 改为 `runWithTenant(job.getOrganizationId(), ...)` 包住处理全程；
      `TranscodeTenantContextIT` 覆盖，且已实测修复前该用例会红
    - `MultipartUploadServiceImpl.abortExpired`（`@Scheduled`）：原本靠上下文缺失时插件放行
      才跨租户生效，调用线程带租户时会被静默裁剪成单租户、漏清无报错。
      改为扫描 `callWithBypass` 显式声明、写回 `callWithTenant` 切回自身租户；
      `MultipartAbortExpiredTenantIT` 覆盖，且已实测旧实现下关键用例会红
- 测试方式：零 DB 覆盖 handler 解析与空上下文报错、上下文清理；MySQL IT 覆盖四类 SQL 的条件注入、
  分页 count、跨租户按 ID 直查直改直删无效（含子表）、主子表租户一致性、线程复用不串租户
- 实施补记：
  - `sts_common_resource_org_grant` 追加为第 9 张豁免表——跨租户授权记录同时属于授权方与被授权方，
    单一租户列会让被授权方读不到授予自己的记录（design §3.2）
  - `SysMenuMapper.selectAccessibleMenusByUserId` 方法级豁免——租户插件无法处理 `WITH RECURSIVE`
    的 CTE 别名，会注入不存在的列（design §3.3）
  - 登录链路（按工号查用户、读用户缓存）用 `TenantContext.callWithBypass` 旁路；
    Task 3 引入企业编码后改为按租户精确定位
  - 登录成功后 Sa-Token Session 写入租户身份，供 `TenantContextFilter` 绑定上下文

---

## Task 3：企业编码登录与租户内账号体系

- 状态：已完成（2026-08-06）
- Owner：lims
- 输入：Task 2；现有 `us_sys_auth_identity` 多渠道登录实现；密码双层哈希实现
- 输出：企业编码登录；`identifier` 复合值改造；删除机构成员表并归并档案字段；
  账号、部门、角色的租户一致性校验；租户开户初始化（自 Task 1 移入）
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：登录接口新增 `orgCode` 必填参数并统一失败话术；机构成员接口下线，其档案字段并入用户接口；
  用户、角色接口的租户一致性错误契约
- 数据影响：重写 `local` 凭证 `identifier` 为 `{orgCode}:{userCode}`；`us_sys_auth_identity` 表结构不变；
  删除 `sts_org_member`，`member_type`/`email`/`job_title`/`entry_date` 并入 `us_sys_user`；
  `sts_org_member_tag`、`sts_org_member_position` 外键改 `user_id`
- 验收标准：
  - [x] 登录 = 企业编码 + 工号 + 密码，Session 写入 `organizationId` 与 `orgType`
  - [x] 企业编码不存在与密码错误返回同一话术，不泄露租户是否存在
  - [x] 停用租户、停用账号均不能登录
  - [x] 两个租户可存在同名 `user_code`；同租户内重复返回冲突
  - [x] 用户 `dept_id` 所属部门、绑定角色均与用户租户一致，跨租户返回 HTTP 400
  - [x] 登录链路显式豁免租户插件，且方法内自带租户条件，不产生全表查询
  - [x] `phone` 保持可空可重复，不参与登录
  - [x] `sts_org_member` 及其实体、Mapper、Service、Controller、契约全部删除，全仓库无残留引用
  - [x] 不再存在 `employee_no`，`user_code` 是唯一工号字段
  - [x] 按标签、按岗位圈人能力在改为 `user_id` 关联后行为不变
  - [x] 参与范围动态解析、选人组件筛选、考生档案快照、培训资源引用四处改造后回归通过
  - [x] 创建租户同时初始化默认部门、内置角色与管理员账号，全部归属新租户
  - [x] 停用租户后其账号不能登录
- 测试方式：零 DB 覆盖登录租户解析、统一话术、一致性校验分支、标签/岗位解析改造；
  MySQL IT 覆盖跨租户同名工号、同租户唯一键冲突、登录豁免后的真实 SQL 条件、
  `user_id` 关联下的标签与岗位圈人结果
- 实施补记：
  - 关联表连同表名一并更名：`sts_org_member_tag` → `sts_org_user_tag`、
    `sts_org_member_position` → `sts_org_user_position`，外键 `org_member_id` → `user_id`，
    唯一键改 `(user_id, tag_id)` / `(user_id, position_id)`
  - `CurrentOrganizationResolver` 依赖成员表，重建为 `com.sts.admin.tenant.CurrentTenantResolver`：
    先取请求上下文，`superAdmin` 旁路时回落到账号自身归属
  - 成员档案的写入能力并入用户 CRUD：`us_sys_user` 新增 `member_type`/`email`/`job_title`/`entry_date`，
    用户接口新增 `tagIds`/`positionIds`/`primaryPositionId` 绑定
  - 菜单种子与 migration 同步删除 `org:member:*` 5 项菜单

---

## Task 4：素材与课程域接入租户上下文

- 状态：已完成（2026-08-06）
- Owner：lims
- 输入：Task 2、Task 3；`DefaultOrganizationService` 在素材、课程域的调用点
- 输出：素材、课程、分类、编排、报名、学习进度、课程设置全部改由租户上下文供给归属
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：删除对外暴露的 `organizationId` 请求参数；其余签名不变
- 数据影响：无（子表租户列已在 Task 2 统一加完）
- 验收标准：
  - [x] 本域内 `defaultOrganizationService.getDefaultOrganizationId()` 调用点全部移除
  - [x] 业务代码不再手写 `eq(..., organizationId)`，归属由插件注入
  - [x] 创建的素材、课程、报名、进度归属当前租户
  - [x] 跨租户按 ID 访问素材、课程、章节、课程项一律拒绝
- 测试方式：零 DB 回归本域 Service/Controller；MySQL IT 覆盖跨租户访问拒绝（含章节、课程项等子表）
- 实施补记：
  - **发现并修复回归**：跨租户资源授权与自动隔离冲突——插件过滤掉其它租户资源后，
    "先查资源再校验授权"的授权分支永远走不到，跨租户共享静默失效。
    新增 `CrossTenantResourceReader` 两段式读取（design §4.4），已接入课程详情、
    课程内容项引用考试、培训类型/标准/模板引用三处
  - `AssetReferenceService.requireUsableAsset` 与 `CourseSnapshotAssembler.assembleAndValidate`
    等方法签名去掉 `organizationId` 形参，边界改由插件保证
  - `applyPublishedExamResult` 成绩发布回调用 `TenantContext.runWithTenant` 显式声明事件自带租户，
    为此给 `TenantContext` 补了无返回值的 `runWithTenant`
  - 零 DB 测试中归属断言移除：写入归属由插件填充，mock Mapper 场景不体现，
    该行为由 `TenantIsolationIT` 在真库覆盖

---

## Task 5：题库、试卷与考试域接入租户上下文

- 状态：已完成（2026-08-06）
- Owner：lims
- 输入：Task 2、Task 3；知识点与题库分类接口的 `@RequestParam organizationId`；`ExamAttemptQueryServiceImpl`
  中的 `DataScopeService` 调用
- 输出：知识点、题目、题库、试卷、考试、作答、判分、批阅全部改由租户上下文供给归属
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：`GET /api/question/knowledge-points/tree`、`GET /api/question/banks/categories` 等删除
  `organizationId` 参数
- 数据影响：无（子表租户列已在 Task 2 统一加完）
- 验收标准：
  - [x] 客户端不再能指定机构 ID，越权读取通道关闭
  - [x] `ExamAttemptQueryServiceImpl` 的 `DataScopeService` 依赖移除，改由插件隔离
  - [x] 跨租户按 ID 访问题目、试卷、考试、作答记录一律拒绝
  - [x] 组卷抽题、判分、批阅只命中本租户数据
- 测试方式：零 DB 回归本域；MySQL IT 覆盖抽题与判分的租户边界、子表按 ID 直查拒绝
- 实施补记：
  - **写侧越权面比原计划大**：不只是 `@RequestParam organizationId`，7 个请求体
    （`QuestionCreateRequest`、`QuestionImportRequest`、`BankCategoryCreateRequest`、
    `KnowledgePointCreateRequest`、`QuestionBankCreateRequest`、`QuestionUserStateUpsertRequest`、
    `PaperCreateRequest`、`ExamCreateRequest`）都带 `@NotNull organizationId`，
    等于客户端可以指定新建资源归属，一并删除
  - 查询 DTO 的 `organizationId` 全部删除：`QuestionQuery`、`QuestionBankQuery`、`PaperQuery`、
    `ExamQuery`、`ExamCandidateQuery`；对应 XML 里手写的 `AND x.organization_id = #{...}`
    换成注释，条件改由插件注入
  - `ExamAttemptQueryMapper.selectAttemptPage` 由 4 参收敛为 `(page, query)`，
    `ExamAttemptQueryServiceImpl` 只剩 Mapper 一个依赖
  - 抽题池（`PaperServiceImpl.resolveQuestions`）只按题库 ID 过滤，跨租户引用别家题库时
    池为空并由 6507「抽题不足」拦下，已由 IT 固化
  - 新增 IT：`QuestionPaperTenantBoundaryIT`（4 例）、`ExamTenantBoundaryIT`（4 例）
  - 契约同步：`question/paper/exam.openapi.json` 删除请求体与查询参数中的 `organizationId`
    及其 example，响应 VO 仍回显归属
- 验收证据：
  - 零 DB `mvn -pl sts-service-admin -am test`：521 通过、0 失败
  - 带 DB `mvn -pl sts-service-admin -am -Pintegration-test test`：147 通过、0 失败

---

## Task 6：培训、档案、证书与问卷评价域接入租户上下文

- 状态：已完成（2026-08-07；导出产物一项顺延，见验收标准）
- Owner：lims
- 输入：Task 2、Task 3；档案导出、证书规则接口的 `@RequestParam organizationId`
- 输出：培训标准、计划、执行、签到、成绩、问卷、评价、档案、证书全部改由租户上下文供给归属
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：档案导出任务、证书签发规则等接口删除 `organizationId` 参数
- 数据影响：无（子表租户列已在 Task 2 统一加完）
- 验收标准：
  - [x] 客户端不再能指定机构 ID
  - [x] 跨租户按 ID 访问培训计划、档案、证书、问卷一律拒绝
  - [x] 档案归档、证书签发只汇集本租户事实
  - [顺延] 异步导出任务显式设置租户上下文后执行，产物不含跨租户数据
    —— **不属于本 Change 范围，随导出功能一并交付**。
    `ArchiveServiceImpl.createExportJob` 只插入 `jobStatus = PENDING` 的任务行，
    全仓库没有任何消费者、调度器或 MQ 监听读取 `sts_archive_export_job`：
    导出执行器本身尚未开发，既没有"显式设置上下文"的代码可写，也没有产物可验。
    这是功能缺失，不是隔离缺陷——本域其余隔离要求均已达成并有 IT 覆盖。
    实现导出执行器时 MUST 同时满足本条，届时在对应 Change 内验收
- 测试方式：零 DB 回归本域；MySQL IT 覆盖归档与签发的租户边界、异步任务上下文、子表按 ID 直查拒绝
- 实施补记：
  - **修复一个真实越权漏洞**：`TrainingResourceAccessHelper.requireCrossOrgAccess(callerOrgId, ownerOrgId)`
    在两者相等时直接放行，而 `callerOrgId` 来自请求体的 `organizationId`——
    客户端只要把它填成资源属主的租户 ID，跨租户授权校验就被完全绕过。
    调用方身份改为 `TenantContext.requireTenantId()`（与 Task 4 `CourseServiceImpl` 一致），
    三个 `requireXxxForUse` 方法去掉 `callerOrgId` 形参
  - 写侧：18 个请求体删除 `organizationId`（培训 8、档案 2、证书 3、评价 3、问卷 2）
  - 读侧：11 个查询 DTO 删除 `organizationId` 及其 `wrapper.eq` 条件；
    2 个 `@RequestParam organizationId`（`GET /api/archive/export-jobs`、`GET /api/certificate/rules`）删除
  - `TrainingReferenceResourceMapper.xml` 的 3 处手写 `AND x.organization_id = #{organizationId}` 删除；
    Service 中"按 userId 反查主部门与租户归属"的补偿逻辑整段删除（与插件注入重复，属第二套过滤），
    `page(userId, request)` 收敛为 `page(request)`，`TrainingReferenceResourceServiceImpl`
    只剩 Mapper 一个依赖
  - `CertificateIssueRuleService.list()` 与 MyBatis-Plus `IRepository.list()` 签名冲突，改名 `listRules()`
  - 新增 IT：`TrainingTenantBoundaryIT`（5 例）、`ArchiveTenantBoundaryIT`（4 例）、
    `CertificateTenantBoundaryIT`（4 例）、`SurveyTenantBoundaryIT`（4 例）
  - 契约同步：`training`/`archive`/`certificate`/`survey`/`evaluation-form`/`evaluation-result`
    删除请求体字段、`required`、7 个查询参数及 example 中的 `organizationId`；
    顺带把因此变空的 12 个 `example: {}` 按真实 DTO 补齐，满足四条铁律；响应 VO 仍回显归属
  - 已确认的既有语义（非缺陷，已由 IT 固化）：
    - `sts_certificate_certificate.uk_certificate_no_deleted` 不含租户列——证书号对外出示，
      全平台唯一是刻意设计，跨租户重号会让核验失去意义
    - `sts_archive_training.uk_plan_deleted`、`sts_exam_attempt.uk_candidate_attempt_deleted`
      同理：计划 ID / 考试人员 ID 本就全局唯一，只属于一个租户
- 验收证据：
  - 零 DB `mvn -pl sts-service-admin -am test`：521 通过、0 失败
  - 带 DB `mvn -pl sts-service-admin -am -Pintegration-test test`：164 通过、0 失败（Task 5 后为 147）
- 遗留项：
  - 档案异步导出执行尚未实现（仅有建任务与查任务）。实现时 MUST 用
    `TenantContext.runWithTenant(job.getOrganizationId(), ...)` 显式声明租户后再执行，
    并补上"产物不含跨租户数据"的 IT，届时回填本 Task 第 4 条验收标准

---

## Task 7：运营工作台与统计接入租户上下文

- 状态：已完成（2026-08-07；导出产物一项顺延，见验收标准）
- Owner：lims
- 输入：Task 4~6；`OpsController` 全部 13 个接口的 `DefaultOrganizationService` 调用
- 输出：工作台待办、课程/考试/培训/结果统计、下钻、导出全部改由租户上下文供给归属
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：`OpsController` 各接口签名不变，归属改为服务端解析
- 数据影响：不新增列
- 验收标准：
  - [x] `OpsController` 与运营 Service 的 `DefaultOrganizationService` 调用点全部移除
  - [x] 统计先按租户过滤明细事实再汇总，MUST NOT 先汇总后过滤
  - [x] 工作台待办、下钻、导出与列表使用同一租户边界
  - [顺延] 导出任务产物不含跨租户数据
    —— **不属于本 Change 范围，随导出功能一并交付**。
    `OpsExportServiceImpl.create` 插入任务后立即置 `jobStatus = SUCCEEDED`、
    `resultFileId = 1L`，并没有真正生成文件，因此无产物可验。
    与 Task 6 的档案导出同一性质：功能缺失而非隔离缺陷。
    本域统计、下钻、列表的租户边界均已达成，由 `OpsStatsTenantBoundaryIT` 覆盖
- 测试方式：零 DB 回归运营域；MySQL IT 覆盖多租户造数下的统计数值正确性与下钻边界
- 实施补记：
  - **范围超出原计划**：`DefaultOrganizationService` 的调用点不止 `OpsController` 13 处，
    还有 `RegulationController` 17 处与 `AssetMediaAccessService` 1 处（后者是 Task 4 的漏网）。
    Task 9 要求"全仓库无残留引用"，且这三处症状相同，故一并关闭；
    改完后 `DefaultOrganizationService` 引用数为 0，类本身留给 Task 9 删除
  - **纠正一处此前的判断**：该类硬编码解析并永久缓存 `DEMO` 租户，最初以为是
    "所有租户都看到 DEMO 的数据"的越权。实际不是——Task 2 上线插件后，这类查询会同时带上
    手写的 `= DEMO` 与插件注入的 `= 当前租户`，两条件互斥，**非 DEMO 租户得到的是空结果**。
    所以它是可用性缺陷（其它租户工作台、统计、监管概览、媒体播放鉴权一律为空/404），
    不是数据泄露。已在 IT 注释中按此表述
  - `RegulationOverviewServiceImpl.overview` 是真正的跨租户读：按被监管企业统计培训计划，
    插件会注入监管租户条件导致恒为 0。改为 `TenantContext.callWithTenant(regulatedOrgId, ...)`
    逐租户统计；预警本身归属监管租户，仍由插件注入。
    **该接口的 `REGULATOR` 身份校验与访问审计属于 Task 8，本 Task 未做**
  - `OpsWorkbenchServiceImpl` 三个 `xxxOrgWrapper(organizationId)` 改名 `xxxWrapper()`，只留业务条件
  - 新增 IT：`OpsStatsTenantBoundaryIT`（4 例）。为覆盖统计数值，
    `OpsMapperItConfig` 的 `@MapperScan` 增加 exam / training / evaluation 三个包
  - **用例有效性已实证**：IT 基类默认绑定 DEMO 租户，若在 DEMO 下断言，旧实现同样通过。
    因此用例统一切到 `OPS-OTHER` 租户断言；临时还原旧实现验证过 4 例中 3 例确实失败。
    第 4 例（双向回归护栏）新旧都通过，已在注释中说明它防的是"反向串租户"而非本次缺陷
- 验收证据：
  - 零 DB `mvn -pl sts-service-admin -am test`：521 通过、0 失败
  - 带 DB `mvn -pl sts-service-admin -am -Pintegration-test test`：168 通过、0 失败（Task 6 后为 164）
  - 契约无需改动：`ops.openapi.json`、`regulation.openapi.json` 从未暴露 `organizationId`，
    归属一直是服务端解析
- 遗留项：
  - 运营导出与档案导出都只有"建任务"没有"执行"，产物相关验收标准无从验证。
    实现异步执行时两处一并补：显式 `runWithTenant` + 产物租户边界 IT

---

## Task 8：监管平台跨租户查看能力

- 状态：已完成（2026-08-07）
- Owner：lims
- 输入：Task 1~7；design §4 的切换视角与聚合通道设计
- 输出：`X-Target-Tenant-Id` 切换视角；跨租户聚合只读通道；监管审计日志；监管角色种子权限
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：新增 `X-Target-Tenant-Id` 头定义与 403 错误契约；新增跨租户聚合统计接口
- 数据影响：不新增业务列；监管访问审计写入操作日志
- 验收标准：
  - [x] `REGULATOR` 用户可切换到任意企业租户读取业务明细
  - [x] 非 `REGULATOR` 用户提交该头返回 HTTP 403，MUST NOT 静默忽略后按自身租户执行
  - [x] 目标租户不存在返回稳定错误且不泄露其它租户信息
  - [x] 切换视角的写操作被拒绝（监管为只读）
  - [x] 跨租户聚合通道仅只读、仅 `REGULATOR` 可用、位于独立包路径
  - [x] 每次跨租户访问记录监管人、目标租户与资源的审计日志
  - [x] 监管角色种子权限不含任何业务写权限，并由测试断言
- 测试方式：零 DB 覆盖身份校验与 403 分支；MySQL IT 覆盖切换视角前后可见性变化、聚合通道多租户分组、
  写操作拒绝
- 实施补记：
  - **先修了一个阻断性既有缺陷**：`us_sys_oper_log.organization_id` 为 NOT NULL、靠租户插件在
    INSERT 时注入，而 `OperLogEventListener` 是 `@Async`——ThreadLocal 不跨线程，插件不注入该列，
    插入必然违反非空约束，异常又被监听器 `catch` 吞掉只打 warn。
    即 **Task 2 之后所有操作日志写入都在静默失败**。已用真库探针证实（`DataIntegrityViolationException`）。
    审计日志复用该管道，不修则本 Task 的审计验收无从谈起。
    修法沿用 `SysLoginLog` 已有约定：实体补 `organizationId`，在请求线程内取值显式写入，
    平台级操作（superAdmin）记 0。新增 `OperLogTenantIT` 固化，防止再被去掉
  - 新增 `RegulatorViewResolver`：判定能否切换、切到哪个租户。
    非 `REGULATOR`（含企业租户、租户类型缺失）4061；切换视角下的非 GET/HEAD/OPTIONS 4062；
    目标不存在/已停用/不是企业租户统一 4063 同话术；取值非数字 4064
  - `TenantContextFilter` 接入切换视角。**过滤器早于 DispatcherServlet，抛出的异常不经过
    `@RestControllerAdvice`**，因此在过滤器内自行写出统一 `Result` 响应，否则 403 会变成裸 500
  - 未登录、`superAdmin`、会话无租户三种情况提交该头一律拒绝，MUST NOT 静默忽略。
    `superAdmin` 也拒绝：它已是全局旁路，再切视角只会让审计口径混乱
  - 新增 `RegulatorAccessAuditor`：审计日志归属**监管租户**而非被查企业，
    这样监管访问轨迹留在监管侧，被查企业看不到，也不污染企业自己的操作日志
  - 跨租户聚合通道置于独立包 `com.sts.regulator`：Mapper 方法 `@InterceptorIgnore(tenantLine = "true")`，
    XML 内每个子查询自带 `organization_id` 分组，以企业租户为主表左连，零数据租户也出行；
    Service 入口自校验 `REGULATOR`（4065）。这是全系统唯一无租户条件读业务表的地方
  - 监管角色权限集 `RegulatorRolePermissions`（design §4.5 的那道断言）：
    `regulation:*`（监管自身域，含写，写的是监管自己的数据）+ `regulator:aggregate:view`
    + 8 个业务域**只读**权限码（供切换视角下钻）。开户时若为 `REGULATOR` 按此集合精确绑定角色菜单。
      菜单种子新增 `regulator:aggregate:view`
  - 自查纠错：`WRITE_ACTION` 正则最初把 `view` 误列为写动作，会把 `regulation:overview:view`
    判成写权限；`BUSINESS_READ_ONLY` 里的 `archive:archive:list` 是凭印象写的、菜单中并不存在，
    已核对全部 240 个权限码后改为 `archive:training:list`
- 验收证据：
  - 零 DB `mvn -pl sts-service-admin -am test`：531 通过、0 失败（Task 7 后为 521）
  - 带 DB `mvn -pl sts-service-admin -am -Pintegration-test test`：179 通过、0 失败（Task 7 后为 168）
  - 新增零 DB：`RegulatorViewResolverTest`（6 例）、`RegulatorRolePermissionsTest`（4 例）
  - 新增 IT：`RegulatorViewSwitchIT`（3 例）、`RegulatorAggregateMapperIT`（4 例）、
    `OperLogTenantIT`（3 例）
  - 契约：新增 `docs/api/regulator.openapi.json`，含 `X-Target-Tenant-Id` 头定义、
    4061~4065 错误约束与聚合接口；业务接口契约不变（切换视角不改变任何业务接口签名）
- 遗留项：
  - 切换视角的准入判定已由零 DB 全覆盖，但"过滤器整链路 + 真实 Sa-Token 会话"的端到端用例未写，
    需要 Web 层集成测试脚手架，本仓库现有两层测试均不覆盖该形态
  - `RegulationOverviewServiceImpl.overview`（Task 7 改为逐租户 `callWithTenant`）尚未接入
    本 Task 的 `REGULATOR` 身份校验与审计；它走的是自己的接口而非切换视角通道，
    建议 Task 9 统一收口到 `com.sts.regulator` 或补同等护栏

---

## Task 9：废弃结构清理与契约同步

- 状态：已完成（2026-08-07）
- Owner：lims
- 输入：Task 1~8 全部完成
- 输出：删除数据范围体系与默认机构占位；schema、种子、契约与工程记录同步
- 前后端影响：仅后端；前端以 OpenAPI 为准另开
- API 依赖：角色接口删除 `dataScope`、`customDepts` 及 schema；业务码 2030/2031/2032 移出契约
- 数据影响：删除 `us_sys_role.data_scope` 列与 `us_sys_role_dept` 表；`backend/sql/` 与 migration 一致
- 验收标准：
  - [x] `DataScopeService`、`DataScopeServiceImpl`、`DataScopeContext` 及其测试全部删除
  - [x] `DefaultOrganizationService` 删除，全仓库无残留引用
  - [x] `us_sys_role_dept`、`sts_org_regulatory_relation`、`data_scope` 在 migration 与开发期 schema 中一致删除
  - [x] `sts_org_organization` 主表定义从 `schema-course.sql` 迁出到 `schema-org.sql`，`Organization`
    实体与 Mapper 从 `com.sts.course` 迁至 `com.sts.org`
  - [x] `docs/api/` 全部受影响契约同步且满足四条铁律
  - [x] `openspec/changes/2026-08-06-enforce-organization-hierarchy-data-scope` 已废止或删除
  - [x] 进度表与路线图记录同步
- 测试方式：全量零 DB（`mvn -pl sts-service-admin -am test`）与全量 IT
  （`mvn -pl sts-service-admin -am -Pintegration-test test`）通过；契约校验通过
- 实施补记：
  - 删除范围：`com.sts.admin.datascope` 整包（含测试）、`DefaultOrganizationService`、
    `SysRoleDept`/`SysRoleDeptMapper`/`RoleDeptScopeRequest`，以及 `SysRoleServiceImpl` 中的
    `bindCustomDepts`/`validateDataScope`/`normalizeDataScope` 与业务码 2030/2031/2032
  - schema：`us_sys_role.data_scope` 列、`idx_org_data_scope` 索引、`us_sys_role_dept` 表删除；
    `sts_org_organization` 及租户种子行从 `schema-course.sql` 迁至 `schema-org.sql`，
    IT 初始化顺序相应改为 org 先于 course（全部 schema 无外键，仅此一处 INSERT，故顺序安全）
  - `Organization` / `OrganizationMapper` 迁至 `com.sts.org`；15 个 IT 配置的 `@MapperScan`
    补上 `com.sts.org.mapper`；11 个原同包引用的课程 IT 补显式 import
  - **补上 Task 8 的遗留项**：抽出 `RegulatorIdentityGuard`，
    `RegulationOverviewServiceImpl.overview`（逐租户 `callWithTenant` 统计，属跨租户读）
    与聚合通道现在共用同一道 `REGULATOR` 护栏，新增 `RegulatorIdentityGuardTest`（4 例）
  - 契约：`system-admin.openapi.json` 删除 `dataScope`/`customDepts` 字段、`required`、example
    与 `RoleDeptScopeRequest` schema，2030/2031/2032 移出错误码表，
    `RoleDataScopeBusinessErrorCodes` 更名 `RoleBusinessErrorCodes`；`org.openapi.json` 描述同步
  - 文档：`docs/requirement/role-workflows.md` 成文于改造前，仍描述机构树/数据范围/监管关系，
    已在 §2 加口径变更说明并修正 5 处指向已删表的描述
  - 旧 Change `2026-08-06-enforce-organization-hierarchy-data-scope` 从未确认实施，
    加废止说明后移入 `openspec/changes/archive/`
  - 进度表新增 BASE-05 行；BASE-02 的遗留阻塞项「课程域仍可用 DefaultOrganizationService」随之消解
  - **三处自造错误，均由测试captured**：删字段时留下孤儿 `@NotBlank("数据范围不能为空")`，
    它转而作用到 `description`（默认 `""`）导致角色更新 400；migration IT 的 INSERT 删了列名没删值，
    触发列数不匹配；`@MapperScan("a", "b")` 缺花括号编译失败
- 验收证据：
  - 零 DB `mvn -pl sts-service-admin -am test`：533 通过、0 失败
  - 带 DB `mvn -pl sts-service-admin -am -Pintegration-test test`：179 通过、0 失败
  - 终检：全仓库 `DataScopeService|DefaultOrganizationService|us_sys_role_dept|data_scope|
    sts_org_regulatory_relation|SysRoleDept` 在代码、schema、种子与契约中均无残留
