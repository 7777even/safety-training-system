# Design：安全教育培训系统模块化 MySQL 数据模型

## 1. 设计依据与状态

### 1.1 输入

- `docs/product/安全教育培训74条FR新需求描述对照表.md`：74 条 FR 的长期追踪记录，其中 9 条已确认延期，本期有效 FR 为 65 条。
- `docs/product/魔学院功能调研文档_排版优化终稿.md`：课程、题库、试卷、考试、证书、问卷和人脸核验细节。
- `docs/architecture/business-architecture.md`、`docs/architecture/domain-model.md`：领域边界与业务闭环。
- `docs/database/ddl-conventions.md`、`docs/database/logic-delete.md`：主键、审计、删除和索引铁律。
- `backend/sql/schema-system.sql`：现有 `us_sys_*` 系统管理表。

### 1.2 当前状态

本文件是 workHarness 阶段的物理模型设计，不是可执行 DDL。所有标记“暂定”的表或字段必须先获得产品/架构/权限负责人确认，
再由后续业务 Task 生成 API Contract、migration、Entity 和测试。

2026-07-21，需求方确认两轮设计评审（`design-review-issues.md` 问题一～七、`design-review-issues-round2.md`
R2-01～R2-12）全部问题关闭，
本数据模型设计评审通过。后续实施仍须按 `tasks.md` 逐 Task 确认执行，第 25 节列出的业务口径待确认项在对应 Task 开工前必须完成确认。

2026-07-21，经需求方确认，仿真和人脸识别能力延期，本期不设计、不实施、不验收；本设计不得增加相关表、字段、跨模块引用或隐含实现。

字段级设计已同步展开到 `ddl/` 下的 7 个模块化 DDL 草案。它们用于设计审查和本地建表验证，不是正式 migration，不得直接用于生产环境。

### 1.3 本地执行验证状态

2026-07-21，上一版 6 个 DDL 草案曾在本地 MySQL 8.0.45 创建 129 张表并通过注释、引擎和字符集复核。
本次按需求方决定删除 4 张重复培训组织/RBAC 表，新增 1 张系统数据权限关联表、课程发布版本表、问卷发布版本与发放实例表、综合成绩计算批次与来源表，并增加
2 个系统表目标结构变更；修订后共创建 131 张表。
2026-07-21 第二轮评审（R2-07）确认统一导入模型：删除题库导入批次/错误表和线下成绩导入批次表，新增公共导入批次/错误表；当前共创建
130 张表，其中 129 张培训业务表、1 张系统数据权限表。
上一版 MySQL 执行结论已失效，必须按“先执行 `backend/sql/schema-system.sql`，再执行
`ddl/00_system_permission_extension.sql` 和其余 DDL”的顺序重新验证。
2026-07-21 第二轮评审修复后已按该顺序在本地 MySQL 8.0.45 重新验证：8 个脚本零报错，共创建 143 张表（130 张建表 DDL 加 13
张既有系统表）；
试卷题目、填空答案、学习会话令牌、证书幂等与撤销后重发、考试人员上下文等唯一性用例全部符合预期，素材反向引用和导入批次新索引经
`EXPLAIN` 确认命中；验证记录见 `design-review-issues-round2.md`。

## 2. 模块边界与依赖

```text
技术账号身份（复用 us_sys_user / us_sys_auth_identity）
    ↓
系统部门、角色、功能权限与数据范围（us_sys_dept / role / menu / role_dept）
    ↓
机构与成员档案（sts_org_organization / sts_org_member）
    ├── 通用参与范围 / 授权 / 消息 / 积分
    ├── 素材 ──→ 课程 ──→ 开课与学习
    ├── 题库 ──→ 试卷 ──→ 考试 / 练习 ──→ 成绩
    └── 培训标准 / 模板 ──→ 培训计划 ──→ 计划内容 ──→ 课程 / 考试 / 线下阶段

计划内容 ──→ 各模块执行实例；参训人员 ──→ 各模块执行人员与结果

课程 / 考试 / 培训 ──→ 证书 / 问卷 / 综合评价
学习 / 成绩 / 培训事实 ──→ 培训档案 ──→ 监管预警
```

| 模块      | 表前缀                                |               新表数 | 核心职责                                 | 主要 FR                                            |
|---------|------------------------------------|------------------:|--------------------------------------|--------------------------------------------------|
| 系统技术支撑  | `us_sys_`                          |       0（直接复用 8 张） | 登录账号、认证、文件、字典、日志和系统参数                | FR-ENT-SYS-005、FR-LRN-001~002                    |
| 系统组织与权限 | `us_sys_`                          | 1（另扩展 2 张、复用 3 张） | 系统部门树、角色、用户角色、菜单功能权限和角色部门数据范围        | FR-ENT-SYS-002~004                               |
| 业务组织    | `sts_org_`                         |                 7 | 机构、成员档案、标签、岗位和监管关系                   | FR-ENT-SYS-002、FR-ENT-SYS-004、FR-REG-004         |
| 公共业务支撑  | `sts_common_`                      |                14 | 参与范围、授权、提醒、分享、积分、通用导入批次              | 多模块共用、FR-ENT-SYS-004、FR-ENT-TRN-006              |
| 素材      | `sts_asset_`                       |                 3 | 素材库、派生文件、转码                          | FR-ENT-CRS-001~002                               |
| 课程学习    | `sts_course_`                      |                18 | 课程、不可变发布版本、目录、知识点/题库编排、直播、开课、报名与学习过程 | FR-ENT-CRS-003~010、FR-LRN-005~007                |
| 题库      | `sts_question_`                    |                12 | 知识点、题库、题目知识点和版本化题目；批量导入使用公共导入批次      | FR-ENT-EXM-001~002                               |
| 试卷      | `sts_paper_`                       |                 5 | 固定、随机、自定义试卷及版本                       | FR-ENT-EXM-003~004                               |
| 考试练习    | `sts_exam_`                        |                15 | 考试配置、人员、作答、批阅、补考、防作弊                 | FR-ENT-EXM-005~008、FR-LRN-010~012                |
| 培训      | `sts_training_`                    |                19 | 标准、模板、计划、阶段、评价绑定、线下执行；成绩导入使用公共导入批次   | FR-ENT-TRN-001~007、FR-LRN-008~009、FR-REG-005~006 |
| 证书      | `sts_certificate_`                 |                 6 | 模板版本、规则、签发和撤销                        | FR-ENT-AUX-001~003                               |
| 问卷      | `sts_survey_`                      |                12 | 问卷设计、不可变发布版本、发放实例、作答和统计事实            | FR-ENT-AUX-004~005                               |
| 评价与归档   | `sts_evaluation_` / `sts_archive_` |                13 | 综合评价、不可变计算批次与来源、查课、个人/机构/期次档案        | FR-ENT-OPS-003~010                               |
| 监管预警    | `sts_regulation_`                  |                 5 | 规则、预警事实和处置流水                         | FR-REG-001~003、FR-REG-007                        |

合计新增 129 张培训业务表和 1 张系统数据权限关联表，共 130 张新表；另扩展 `us_sys_dept`、`us_sys_role` 并复用既有系统关系表。
人脸供应商和仿真协议相关能力已确认本期不实施，不建立对应表或跨模块引用；后续如需启用，必须创建独立 OpenSpec Change 重新设计。

## 3. 全局物理设计规则

### 3.1 数据库与命名

- MySQL 8.0，`InnoDB`，`utf8mb4_general_ci`，脚本头使用 `SET NAMES utf8mb4`。
- 新业务表命名 `sts_<module>_<entity>`；表名和字段统一小写下划线。
- 每张表必须有准确的中文表 `COMMENT`，每个字段必须有中文字段 `COMMENT`；状态、类型字段的注释同时列出允许值，禁止使用表名或字段名占位。
- 主键为 `{entity}_id BIGINT NOT NULL AUTO_INCREMENT`；引用只使用 ID，不使用名称。
- 业务码使用 `varchar(64)`，在所属机构内不可变；展示名称通常使用 `varchar(100)`。
- 启用/禁用使用 `status TINYINT`（0 禁用、1 正常）；业务生命周期使用有语义的 `varchar(32)` 字段，不使用 MySQL `ENUM`。
- 金额/分数使用 `decimal(10,2)`，比例使用 `decimal(7,4)`，学习/考试时长统一存秒 `int`，文件大小存字节 `bigint`。
- 富文本使用 `longtext`；JSON 只用于不可变快照或供应商扩展数据，不能代替稳定的一对多关系。

### 3.2 三类审计模板

下文“业务字段”不重复列出公共审计列，实际 DDL 必须按类别补齐。

| 类别     | 适用               | 公共字段                                                                                                                                   | 删除方式                |
|--------|------------------|----------------------------------------------------------------------------------------------------------------------------------------|---------------------|
| A 业务实体 | 会被就地修改的聚合/配置     | `create_by bigint`、`create_time datetime`、`update_by bigint`、`update_time datetime`、`deleted bigint default 0`、`version int default 0` | `deleted=主键ID` 逻辑删除 |
| B 关联关系 | 建立/解绑关系，不就地修改    | `create_by bigint`、`create_time datetime`                                                                                              | 物理删除，必须有业务唯一键       |
| C 日志流水 | 作答、会话、消息、事件、变更历史 | `create_by bigint`、`create_time datetime`                                                                                              | 只追加，不提供业务删除         |

题目、试卷、证书模板和评价表单的版本表是 C 类的受控例外：`published_status=DRAFT` 期间允许编辑本行及其子记录，发布后整体不可变；发布动作必须写入
`published_by`、`published_time` 留痕，旧版本归档迁移只允许由服务在发布新版本的同一事务内执行。课程和问卷版本表写入即发布，不适用该例外。

### 3.3 机构隔离

- 聚合根和高频事实表保存 `organization_id`，逻辑关联 `sts_org_organization`。
- 平台公共资源由 `org_type=PLATFORM` 的机构持有，不使用 `organization_id=0` 魔法值。
- 子表可从父聚合确定机构时不重复保存；答卷、学习会话、培训参与、消息、核验和归档等高频事实表冗余 `organization_id` 并建组合索引。
- `us_sys_dept.organization_id` 必须与成员及业务数据的 `organization_id` 一致；部门父子节点不得跨机构，成员所属部门以
  `us_sys_user.dept_id` 为准。
- 应用查询必须依次校验登录账号、有效机构成员、系统功能权限、角色数据范围、任务归属和资源授权。
- 数据范围支持 `ALL`、`ORGANIZATION`、`DEPT`、`DEPT_AND_CHILDREN`、`CUSTOM`、`SELF`；不支持字段表达式、SQL 条件或任意脚本。
- `CUSTOM` 通过 `us_sys_role_dept` 保存一个或多个部门，`include_children` 明确每个部门是否包含下级；组织树调整后按当前树动态生效，不冻结授权路径。
- 同一用户多个有效角色的数据范围取并集；为避免权限拼接扩大，只有授予当前功能权限的角色才参与本次数据范围合并。
- 数据权限必须由后端统一执行，覆盖列表、详情、统计、导出和异步任务；聚合统计必须先过滤基础事实再计算，不能先跨范围聚合后隐藏结果。

机构维度组合索引按实际查询入口设计：

| 查询入口               | 目标索引前缀                                            | 覆盖表                                                                                                                                                                  |
|--------------------|---------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 机构学习进度与学习流水        | `organization_id + progress_status/started_time`  | `sts_course_item_progress`、`sts_course_study_session`                                                                                                                |
| 机构考试人员、作答、待批阅与审计流水 | `organization_id + status/type + business_time`   | `sts_exam_candidate`、`sts_exam_attempt`、`sts_exam_answer`、`sts_exam_review_record`、`sts_exam_score_publish_log`、`sts_exam_anticheat_event`、`sts_exam_practice_reset` |
| 机构参训、签到和线下成绩       | `organization_id + status/scored_time`            | `sts_training_participant`、`sts_training_attendance`、`sts_training_offline_score`                                                                                    |
| 机构问卷参与和答卷          | `organization_id + status + submitted_time`       | `sts_survey_participant`、`sts_survey_response`                                                                                                                       |
| 题库、试卷、考试后台筛选       | `organization_id + category/status + create_time` | `sts_question_bank`、`sts_paper_paper`、`sts_exam_exam`                                                                                                                |

只随父聚合访问、没有机构维度列表/统计/清理入口的明细表不重复增加 `organization_id` 索引；正式 API 查询矩阵出现新入口时再补对应组合索引。

### 3.4 逻辑外键与一致性

- 本 Change 已确认不创建物理外键，与现有系统表保持一致；所有内部引用 `*_id`
  都是逻辑外键。作为高频关联或独立筛选条件的逻辑外键必须建立索引或处于组合索引最左前缀，低频文件引用和只随父聚合访问的子表引用可豁免。
- 同一聚合内的新增/更新/解绑使用本地事务；跨模块状态变化通过幂等应用服务或事件完成。
-

所有写入必须校验被引用记录存在、未删除且状态允许。普通业务引用必须与当前业务对象属于同一机构；培训类型、培训标准和培训模板允许在有效监管关系及显式资源授权下跨机构只读引用，并在培训计划发布时冻结完整来源快照，运行期不得依赖来源记录当前值。多态引用还必须按
`*_type` 校验目标表。

- 发布考试、开课、问卷和培训计划前执行整聚合引用检查；删除或停用前执行反向引用检查，被历史事实引用的内容只能停用/归档，不能物理清除。
- 跨模块失败必须回滚本地事务或记录可重试事件；定时孤儿巡检按逻辑外键清单检查不存在、已删除和跨机构引用并形成告警，不自动删除事实数据。
- 业务唯一键包含 `deleted`，例如 `uk_course_code_deleted(organization_id, course_code, deleted)`。
- 可空唯一键分两类处理：`sts_common_audience_rule.subject_id`、`sts_question_asset.option_key`、
  `sts_paper_draw_rule.difficulty/knowledge_point_id` 使用非空哨兵值后参与唯一键；`sts_org_member.employee_no`
  、模板参与范围的业务对象字段、非课程来源考试人员的课程字段允许多个 `NULL`，其唯一键只约束非空适用场景，并由服务校验条件字段完整性。
- 比例必须在 0～1 之间，分数/学时/数量/时长不得为负，结束时间不得早于开始时间，统计计数不得超过总数；本 DDL 草案不批量增加
  `CHECK`，上述约束统一由领域服务校验并在正式 migration 测试中覆盖，避免应用规则与数据库表达式形成两套口径。

### 3.5 版本、快照与统计

- 题目和试卷显式版本化；新编辑产生新版本，旧版本只读。
- 每次考试尝试保存实际题目、选项、分值和判分依据快照，答卷不依赖题库当前版本重放。
- 课程和问卷统一采用“工作草稿 + 不可变发布版本”：结构化主表供草稿编辑，每次正式发布生成完整 JSON 快照和校验哈希；发布版本只读。
- 课程开课固定 `course_version_id`；问卷每轮发布创建独立 `survey_instance_id`，参与人和答卷同时固定投放实例与
  `survey_version_id`。新版本只影响后续新开课或新投放，不改变既有执行事实。
- 统计指标从事实表计算；正式培训归档保存冻结快照和校验哈希，个人档案、机构档案和一期一档不各建一套重复事实表。

## 4. 系统层与业务层依赖边界

| 系统表                                  | 本期复用或扩展方式                                                            | 约束                                         |
|--------------------------------------|----------------------------------------------------------------------|--------------------------------------------|
| `us_sys_user`                        | 登录主体、创建人及业务人员标识；`dept_id` 是当前主部门                                     | 一个用户本期只有一个主部门；人员扩展档案仍由 `sts_org_member` 保存 |
| `us_sys_auth_identity`               | 本地账号、微信等认证凭证                                                         | 微信解绑、数字验证规则待独立确认                           |
| `us_sys_dept`                        | 统一承载培训组织树；补充 `organization_id`、`dept_code`、`dept_type`、`description` | 父子部门必须属于同一机构；机构内部门编码唯一                     |
| `us_sys_role`                        | 统一承载系统及培训角色；补充机构归属、角色级别、内置标识、`data_scope` 和描述                        | 角色编码改为机构内唯一；角色只能分配给同机构用户                   |
| `us_sys_user_role`                   | 统一承载用户与角色多对多关系                                                       | 绑定时校验用户部门所属机构与角色机构一致                       |
| `us_sys_menu`                        | 菜单、按钮和 `perms` 功能权限点                                                 | 培训功能权限必须进入统一权限编码清单                         |
| `us_sys_role_menu`                   | 统一承载角色功能权限                                                           | 只回答“能否操作”，不能代替数据范围                         |
| `us_sys_role_dept`                   | 本期新增；保存 `CUSTOM` 角色的授权部门及是否包含下级                                      | 角色和部门必须属于同一机构；非 `CUSTOM` 角色不得保留关联行         |
| `us_sys_file_info`                   | 文件对象元数据；业务表只引用 `file_info_id`                                        | 素材转码与业务用途由 `sts_asset_*` 管理                |
| `us_sys_dict_*`                      | 可配置字典展示值                                                             | 核心状态机不依赖可删除字典项                             |
| `us_sys_oper_log`、`us_sys_login_log` | 系统操作和登录审计                                                            | 学习、考试等业务流水使用各自事实表                          |
| `us_sys_config`                      | 系统级通用参数                                                              | 不保存机构级培训业务配置                               |

不再创建 `sts_org_unit`、`sts_org_role`、`sts_org_member_role`、`sts_org_role_permission`
。系统层账号、部门、角色或权限停用后，培训业务访问必须随缓存失效立即撤销。

### 4.1 数据范围语义

| `data_scope`        | 数据边界                                                       | 部门来源                                                 |
|---------------------|------------------------------------------------------------|------------------------------------------------------|
| `ALL`               | 角色配置的平台全部机构数据                                              | 不限制机构或部门                                             |
| `ORGANIZATION`      | 当前角色所属机构全部数据                                               | `us_sys_role.organization_id`                        |
| `DEPT`              | 当前用户主部门数据                                                  | `us_sys_user.dept_id`                                |
| `DEPT_AND_CHILDREN` | 当前用户主部门及当前组织树下全部后代部门                                       | `us_sys_user.dept_id` + `us_sys_dept.ancestors`/递归查询 |
| `CUSTOM`            | `us_sys_role_dept` 明确授权的部门；每行由 `include_children` 决定是否包含下级 | `us_sys_role_dept.dept_id`                           |
| `SELF`              | 当前用户本人创建、负责、被分配或作为数据主体的数据                                  | 按业务资源声明的 owner/subject 字段                            |

账号有效且 `user_code` 大小写敏感地严格等于 `superAdmin` 时，直接绕过功能权限、机构边界和部门数据范围；该特权不依赖角色记录。除该账号外，所有用户均按角色自身功能权限和
`data_scope` 执行，角色编码、名称和 `role_level` 不产生隐含特权；角色配置为 `ALL` 时按 `ALL` 语义执行。

### 4.2 服务端权限计算

1. 校验当前账号及其主要机构 `sts_org_member` 均有效，并确定 `principal_organization_id`；`us_sys_user.dept_id`
   对应部门必须属于该主要机构。业务数据所属机构作为 `owner_organization_id` 独立处理。
2. 从 `us_sys_user_role` 读取当前机构内有效角色，从 `us_sys_role_menu`、`us_sys_menu.perms` 校验功能权限。
3. 根据授予当前功能权限的各角色 `data_scope` 生成允许机构、部门和本人条件，并取这些角色数据范围的并集；没有授予当前功能权限的角色不得贡献数据范围。
4. 人员、学习、考试、培训和档案事实按数据主体用户的部门过滤；课程、题库、试卷等管理资源按创建人部门过滤，再叠加
   `sts_common_resource_admin_permission`。
5. 老师任务归属、学员本人、参与名单和跨机构资源授权属于附加条件，不得因角色拥有菜单权限而跳过。学员可凭课程报名、考试人员或参训人员事实访问跨机构的本人任务，不要求成为主办机构成员；该授权只允许访问本人执行链。
6. 每个 API Contract 必须声明数据权限资源类型、主体/负责人字段和所需关联；无法确定归属的业务查询不得进入实现。
7. 列表、详情、统计、导出、批量操作和异步任务使用同一权限上下文；详情和批量 ID 必须防止绕过列表条件，统计必须在聚合前过滤。
8. 用户调岗、部门移动、角色范围和角色菜单变化后必须清理权限缓存；历史参与名单和归档快照不改写，但后续访问按最新授权判断。

## 5. 组织与机构模块（`sts_org_*`）

| 表                             | 类别 | 业务字段（不含公共审计列）                                                                                                                                                                                                                                                                                         | 关键约束与索引                                                                                                                                                                     |
|-------------------------------|----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sts_org_organization`        | A  | `organization_id bigint`，`org_code varchar(64)`，`org_name varchar(100)`，`org_type varchar(32)`（PLATFORM/PARK/ENTERPRISE/TRAINING_INSTITUTION），`parent_organization_id bigint null`，`contact_user_id bigint null`，`status tinyint`，`description varchar(500)`                                          | `uk_org_code_deleted(org_code,deleted)`；`idx_parent_type(parent_organization_id,org_type,status)`                                                                           |
| `sts_org_member`              | A  | `org_member_id bigint`，`organization_id bigint`，`user_id bigint`（关联 `us_sys_user`；部门由 `us_sys_user.dept_id` 确定），`member_type varchar(32)`（INTERNAL/EXTERNAL/REGULATOR），`employee_no varchar(64) null`，`email varchar(128) null`，`job_title varchar(100) null`，`entry_date date null`，`status tinyint` | `uk_org_user_deleted(organization_id,user_id,deleted)`；`uk_org_employee_deleted(organization_id,employee_no,deleted)`；`idx_org_user_status(organization_id,user_id,status)` |
| `sts_org_tag`                 | A  | `tag_id bigint`，`organization_id bigint`，`tag_name varchar(100)`，`description varchar(500)`，`status tinyint`                                                                                                                                                                                          | `uk_org_tag_deleted(organization_id,tag_name,deleted)`                                                                                                                      |
| `sts_org_member_tag`          | B  | `member_tag_id bigint`，`org_member_id bigint`，`tag_id bigint`                                                                                                                                                                                                                                         | `uk_member_tag(org_member_id,tag_id)`；`idx_tag_member(tag_id,org_member_id)`                                                                                                |
| `sts_org_position`            | A  | `position_id bigint`，`organization_id bigint`，`position_code varchar(64)`，`position_name varchar(100)`，`description varchar(500)`，`status tinyint`                                                                                                                                                    | `uk_org_position_code_deleted(organization_id,position_code,deleted)`；`idx_org_position_name(organization_id,position_name)`                                                |
| `sts_org_member_position`     | B  | `member_position_id bigint`，`org_member_id bigint`，`position_id bigint`，`is_primary tinyint`                                                                                                                                                                                                          | `uk_member_position(org_member_id,position_id)`；`idx_position_member(position_id,org_member_id)`                                                                            |
| `sts_org_regulatory_relation` | A  | `regulatory_relation_id bigint`，`regulator_org_id bigint`，`regulated_org_id bigint`，`relation_type varchar(32)`，`valid_from datetime null`，`valid_to datetime null`，`status tinyint`                                                                                                                  | `uk_regulator_org_deleted(regulator_org_id,regulated_org_id,deleted)`；`idx_regulated_status(regulated_org_id,status)`                                                       |

说明：`sts_org_member` 只保存培训业务扩展档案，不复制登录账号、姓名、部门或角色；姓名、手机号和主部门来自 `us_sys_user`，角色来自系统
RBAC。
一个普通账号只在 `us_sys_user.dept_id` 对应的主要机构内任职，并且只有一个主部门；角色必须与该部门的 `organization_id`
一致。本期不支持普通账号在多个机构分别拥有部门和角色，也不提供机构任职切换。`superAdmin` 的全局特权、监管人员通过
`sts_org_regulatory_relation` 访问被监管机构，以及 `sts_common_resource_org_grant` 的跨机构资源授权均为独立机制，不改变账号的主要机构归属。

## 6. 公共业务支撑模块（`sts_common_*`）

### 6.1 参与范围、授权和分享

| 表                                      | 类别 | 业务字段                                                                                                                                                                                                                                                                                                                                                                                                                                               | 关键约束与索引                                                                                                                                                |
|----------------------------------------|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sts_common_audience`                  | A  | `audience_id bigint`，`organization_id bigint`，`audience_name varchar(100)`，`audience_kind varchar(16)`（TEMPLATE/BOUND），`business_type varchar(32) null`（COURSE/OFFERING/LIVE/EXAM/TRAINING/SURVEY/SURVEY_INSTANCE），`business_id bigint null`，`purpose varchar(32)`（VISIBILITY/PARTICIPANT/REVIEWER/NOTIFICATION），`resolve_mode varchar(16)`（DYNAMIC/FROZEN），`is_internal_link_allowed tinyint`，`is_external_link_allowed tinyint`，`status tinyint` | `uk_business_purpose_deleted(organization_id,business_type,business_id,purpose,deleted)`；`idx_org_kind(organization_id,audience_kind,status)`          |
| `sts_common_audience_rule`             | B  | `audience_rule_id bigint`，`audience_id bigint`，`decision varchar(16)`（INCLUDE/EXCLUDE），`subject_type varchar(16)`（ALL/DEPT/TAG/POSITION/USER/AUDIENCE），`subject_id bigint not null default 0`（ALL 固定为 0，其他类型为正数 ID）                                                                                                                                                                                                                                | `uk_audience_rule(audience_id,decision,subject_type,subject_id)`；`idx_subject(subject_type,subject_id)`                                                |
| `sts_common_audience_member`           | B  | `audience_member_id bigint`，`audience_id bigint`，`user_id bigint`，`final_decision varchar(16)`，`source_rule_id bigint null`，`resolved_time datetime`                                                                                                                                                                                                                                                                                               | `uk_audience_user(audience_id,user_id)`；`idx_user_decision(user_id,final_decision)`                                                                    |
| `sts_common_resource_admin_permission` | B  | `admin_permission_id bigint`，`organization_id bigint`，`resource_type varchar(32)`，`resource_id bigint`，`admin_org_member_id bigint`，`permission_code varchar(16)`（EDIT/DELETE/STAT/ALL）                                                                                                                                                                                                                                                            | `uk_resource_admin_permission(resource_type,resource_id,admin_org_member_id,permission_code)`；`idx_admin_resource(admin_org_member_id,resource_type)`  |
| `sts_common_resource_org_grant`        | A  | `org_grant_id bigint`，`owner_organization_id bigint`，`resource_type varchar(32)`（COURSE/QUESTION_BANK/PAPER/EXAM/TRAINING_TYPE/TRAINING_STANDARD/TRAINING_TEMPLATE），`resource_id bigint`，`grantee_organization_id bigint`，`grant_level varchar(16)`（VIEW/USE/MANAGE），`valid_from datetime null`，`valid_to datetime null`，`status tinyint`                                                                                                          | `uk_resource_grantee_deleted(resource_type,resource_id,grantee_organization_id,deleted)`；`idx_grantee_status(grantee_organization_id,status,valid_to)` |
| `sts_common_share_link`                | A  | `share_link_id bigint`，`organization_id bigint`，`business_type varchar(32)`，`business_id bigint`，`link_token varchar(128)`，`link_mode varchar(16)`（PC/MOBILE/AUTO），`poster_file_id bigint null`，`valid_from datetime null`，`valid_to datetime null`，`status tinyint`                                                                                                                                                                               | `uk_link_token_deleted(link_token,deleted)`；`idx_business_status(business_type,business_id,status)`                                                    |

`audience_rule` 保存系统部门/标签/岗位等选择规则；当 `subject_type=ALL` 时 `subject_id=0`，其余类型必须使用正数 ID；当
`subject_type=DEPT` 时，`subject_id` 逻辑关联 `us_sys_dept.dept_id`。`audience_member`
保存解析结果。动态课程可见范围可重新解析，已发布考试和培训使用冻结结果。
排除规则优先于包含规则；该优先级必须在参与范围服务中测试。
问卷类的分享链接、提醒和消息一律以 `SURVEY_INSTANCE` 挂到投放实例，不挂问卷定义；直播场次的绑定参与范围使用
`business_type=LIVE`，业务对象为直播场次 ID。

### 6.2 提醒、站内消息和积分

| 表                              | 类别 | 业务字段                                                                                                                                                                                                                                                                                                                                                                               | 关键约束与索引                                                                                                                            |
|--------------------------------|----|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `sts_common_reminder_rule`     | A  | `reminder_rule_id bigint`，`organization_id bigint`，`business_type varchar(32)`，`business_id bigint`，`reminder_type varchar(32)`，`receiver_filter varchar(32)`，`receiver_audience_id bigint null`，`template_content varchar(1000)`，`start_time datetime null`，`end_time datetime null`，`interval_minutes int null`，`max_send_count int null`，`send_time time null`，`status tinyint` | `idx_business_type`；`idx_receiver_audience`；`idx_schedule`                                                                         |
| `sts_common_reminder_channel`  | B  | `reminder_channel_id bigint`，`reminder_rule_id bigint`，`channel_type varchar(16)`（APP/SMS/EMAIL）                                                                                                                                                                                                                                                                                   | `uk_rule_channel(reminder_rule_id,channel_type)`                                                                                   |
| `sts_common_message`           | A  | `message_id bigint`，`organization_id bigint`，`message_type varchar(32)`，`business_type varchar(32) null`，`business_id bigint null`，`title varchar(200)`，`content text`，`channel_type varchar(16)`，`planned_send_time datetime null`，`sent_time datetime null`，`send_status varchar(16)`，`provider_message_id varchar(128) null`                                                    | `idx_org_send(organization_id,send_status,planned_send_time)`；`idx_business(business_type,business_id)`                            |
| `sts_common_message_recipient` | A  | `message_recipient_id bigint`，`message_id bigint`，`user_id bigint`，`delivery_status varchar(16)`，`delivered_time datetime null`，`read_time datetime null`，`failure_reason varchar(500) null`                                                                                                                                                                                       | `uk_message_user_deleted(message_id,user_id,deleted)`；`idx_user_read(user_id,read_time,delivery_status)`                           |
| `sts_common_point_account`     | A  | `point_account_id bigint`，`organization_id bigint`，`user_id bigint`，`balance decimal(12,2)`，`status tinyint`                                                                                                                                                                                                                                                                       | `uk_org_user_deleted(organization_id,user_id,deleted)`                                                                             |
| `sts_common_point_ledger`      | C  | `point_ledger_id bigint`，`organization_id bigint`，`point_account_id bigint`，`business_type varchar(32)`，`business_id bigint`，`change_type varchar(32)`，`change_amount decimal(12,2)`，`balance_after decimal(12,2)`，`idempotency_key varchar(128)`，`occurred_time datetime`，`description varchar(500)`                                                                              | `uk_idempotency_key(idempotency_key)`；`idx_account_time(point_account_id,occurred_time)`；`idx_business(business_type,business_id)` |

暂定：短信/邮件供应商回执字段和积分是否允许负数需独立确认。`point_account` 通过乐观锁更新，`point_ledger` 是余额审计真相源。
人脸供应商能力本期不实施，因此不建立人脸配置、档案、策略、核验或修订表。

### 6.3 通用导入批次（2026-07-21 需求方确认）

| 表                         | 类别 | 业务字段                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | 关键约束与索引                                                                                                                                                      |
|---------------------------|----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sts_common_import_batch` | A  | `import_batch_id bigint`，`organization_id bigint`，`business_type varchar(32)`（QUESTION/USER/TRAINING_PARTICIPANT/OFFLINE_SCORE），`business_id bigint not null default 0`（QUESTION 为题库 ID、TRAINING_PARTICIPANT 为培训计划 ID、OFFLINE_SCORE 为线下场次 ID、USER 为 0），`source_file_id bigint`，`template_type varchar(16)`（WORD/EXCEL），`import_status varchar(16)`，`total_rows int`，`success_rows int`，`failure_rows int`，`result_file_id bigint null`，`started_time datetime null`，`finished_time datetime null` | `idx_org_type_time(organization_id,business_type,create_time)`；`idx_business(business_type,business_id,create_time)`；`idx_status(import_status,create_time)` |
| `sts_common_import_error` | C  | `import_error_id bigint`，`import_batch_id bigint`，`row_no int`，`field_name varchar(64) null`，`error_code varchar(64)`，`error_message varchar(1000)`，`raw_data_json json null`                                                                                                                                                                                                                                                                                                                     | `idx_batch_row(import_batch_id,row_no)`                                                                                                                      |

题库题目（FR-ENT-EXM-002）、用户账号（FR-ENT-SYS-004）、参训人员（FR-ENT-TRN-006）和线下成绩（FR-ENT-TRN-005）四类批量导入统一使用本组表，
批次、逐行错误、重试和审计口径一致；不再各建专用导入批次表。导入服务按 `business_type` 校验目标对象存在且属于同一机构；
`sts_training_offline_score.import_batch_id` 逻辑关联本批次表。各类导入模板列定义和校验规则仍待产品确认。

## 7. 素材模块（`sts_asset_*`）

| 表                         | 类别 | 业务字段                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | 关键约束与索引                                                                                                                                                |
|---------------------------|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sts_asset_asset`         | A  | `asset_id bigint`，`organization_id bigint`，`asset_code varchar(64)`，`asset_name varchar(255)`，`asset_type varchar(32)`（DOCUMENT/IMAGE/AUDIO/VIDEO/RICH_TEXT/LINK/LIVE），`source_type varchar(16)`（UPLOAD/CREATED/EXTERNAL），`file_info_id bigint null`，`external_url varchar(1000) null`，`rich_content longtext null`，`mime_type varchar(128) null`，`file_size bigint null`，`page_count int null`，`duration_seconds int null`，`checksum varchar(128) null`，`process_status varchar(16)`（UPLOADED/PROCESSING/SUCCEEDED/FAILED），`status tinyint` | `uk_org_asset_code_deleted(organization_id,asset_code,deleted)`；`idx_org_type_status(organization_id,asset_type,status)`；`idx_file_info(file_info_id)` |
| `sts_asset_derivative`    | A  | `asset_derivative_id bigint`，`asset_id bigint`，`derivative_type varchar(32)`（PREVIEW/TRANSCODED/THUMBNAIL/HLS），`file_info_id bigint`，`duration_seconds int null`，`page_count int null`，`status tinyint`                                                                                                                                                                                                                                                                                                                                      | `uk_asset_type_deleted(asset_id,derivative_type,deleted)`；`idx_file(file_info_id)`                                                                     |
| `sts_asset_transcode_job` | A  | `transcode_job_id bigint`，`organization_id bigint`，`asset_id bigint`，`course_id bigint null`，`chapter_id bigint null`，`job_type varchar(32)`，`job_status varchar(16)`，`provider_job_id varchar(128) null`，`started_time datetime null`，`finished_time datetime null`，`retry_count int`，`failure_code varchar(64) null`，`failure_reason varchar(1000) null`                                                                                                                                                                                   | `idx_status_time(job_status,create_time)`；`idx_asset(asset_id,job_status)`；`idx_course_chapter(course_id,chapter_id)`                                  |

素材原文件仍存于 `us_sys_file_info`；`sts_asset_asset` 负责业务语义与处理状态。发布课程只能引用 `SUCCEEDED`
的需转码素材；链接、直播和富文本不要求转码。

## 8. 课程与学习模块（`sts_course_*`）

### 8.1 课程定义与内容编排

| 表                                | 类别 | 业务字段                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | 关键约束与索引                                                                                                                                                                         |
|----------------------------------|----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sts_course_category`            | A  | `course_category_id bigint`，`organization_id bigint`，`parent_id bigint not null default 0`，`ancestors varchar(1000)`，`category_name varchar(100)`，`description varchar(500)`，`sort_order int`，`status tinyint`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | `uk_org_parent_name_deleted(organization_id,parent_id,category_name,deleted)`；`idx_parent_sort(parent_id,sort_order)`                                                           |
| `sts_course_setting`             | A  | `course_setting_id bigint`，`organization_id bigint`，文档每页/图文/链接直播默认学习秒数、音视频默认完成比例、分类权限、断点续播、最后一页强制、全局禁止快进、离开页面暂停计时、转码通知等机构级默认配置，`status tinyint`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | `uk_org_deleted(organization_id,deleted)`，每机构一条                                                                                                                                 |
| `sts_course_course`              | A  | `course_id bigint`，`organization_id bigint`，`course_code varchar(64)`，`course_name varchar(100)`，`current_version_id bigint null`，`cover_file_id bigint null`，`introduction longtext null`，`teaching_objective longtext null`，`syllabus longtext null`，`assessment_method longtext null`，`content_mode varchar(16)`（SINGLE/MULTI_CHAPTER），`credit decimal(10,2)`，`point_mode varchar(16)`（NONE/PER_ITEM/COURSE），`point_value decimal(10,2)`，`source_download_mode varchar(16)`（NONE/ALL/PARTIAL），`is_speed_play_allowed tinyint`，`is_study_anticheat_enabled tinyint`，`is_study_description_enabled tinyint`，`is_watermark_enabled tinyint`，`publish_status varchar(16)`（DRAFT/PUBLISHED/OFFLINE），`published_time datetime null`，`status tinyint` | `uk_org_course_code_deleted(organization_id,course_code,deleted)`；`idx_current_version`；`idx_org_publish`；`idx_name`                                                            |
| `sts_course_version`             | C  | `course_version_id bigint`，`course_id bigint`，`version_no int`，`content_snapshot_json json`，`snapshot_hash varchar(128)`，`version_note varchar(500) null`，`published_by bigint`，`published_time datetime`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | `uk_course_version(course_id,version_no)`；`idx_course_publish_time(course_id,published_time)`                                                                                   |
| `sts_course_course_category`     | B  | `course_category_rel_id bigint`，`course_id bigint`，`course_category_id bigint`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | `uk_course_category(course_id,course_category_id)`；`idx_category_course(course_category_id,course_id)`                                                                          |
| `sts_course_chapter`             | A  | `chapter_id bigint`，`course_id bigint`，`parent_id bigint not null default 0`，`ancestors varchar(1000)`，`chapter_name varchar(100)`，`sort_order int`，`status tinyint`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | `uk_course_parent_name_deleted(course_id,parent_id,chapter_name,deleted)`；`idx_course_parent_sort(course_id,parent_id,sort_order)`                                              |
| `sts_course_item`                | A  | `course_item_id bigint`，`course_id bigint`，`chapter_id bigint`，`item_type varchar(24)`（ASSET/KNOWLEDGE_POINT/QUESTION_BANK/EXAM/PRACTICE/LIVE），`item_title varchar(255)`，`asset_id bigint null`，`exam_id bigint null`，`is_required tinyint`，`minimum_study_seconds int`，`sort_order int`，`is_locked_after_participation tinyint`，`status tinyint`                                                                                                                                                                                                                                                                                                                                                                                                 | `idx_chapter_sort(chapter_id,sort_order)`；`idx_course_type(course_id,item_type,status)`；`idx_asset(asset_id)`；应用校验不同类型仅允许对应引用字段或明细关系非空                                          |
| `sts_course_item_knowledge`      | B  | `item_knowledge_id bigint`，`course_item_id bigint`，`knowledge_point_id bigint`，`sort_order int`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | `uk_item_knowledge(course_item_id,knowledge_point_id)`；`idx_knowledge_item(knowledge_point_id,course_item_id)`                                                                  |
| `sts_course_item_question_bank`  | B  | `item_question_bank_id bigint`，`course_item_id bigint`，`question_bank_id bigint`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | `uk_item_question_bank(course_item_id)` 保证题库型内容项只有一个执行题库；`idx_bank_item(question_bank_id,course_item_id)`                                                                       |
| `sts_course_live_session`        | A  | `live_session_id bigint`，`organization_id bigint`，`course_item_id bigint`，`live_name varchar(150)`，`teacher_user_id bigint`，`live_url varchar(1000)`，`start_time datetime`，`end_time datetime`，`audience_id bigint null`，`live_status varchar(16)`，`status tinyint`                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | `uk_course_item_deleted(course_item_id,deleted)`；`idx_org_time(organization_id,start_time,end_time)`；`idx_teacher_time(teacher_user_id,start_time)`；`idx_audience(audience_id)` |
| `sts_course_item_material_rule`  | A  | `material_rule_id bigint`，`course_item_id bigint`，`duration_mode varchar(16)`（AUTO/MANUAL），`required_seconds int`，`is_fast_forward_allowed tinyint`，`is_speed_play_allowed tinyint`，`watermark_mode varchar(16)`（SYSTEM/CUSTOM/NONE），`watermark_color varchar(16) null`，`watermark_opacity decimal(7,4) null`，`aux_audio_asset_id bigint null`，`is_screen_leave_paused tinyint`                                                                                                                                                                                                                                                                                                                                                                   | `uk_course_item_deleted(course_item_id,deleted)`；`idx_aux_audio_asset(aux_audio_asset_id)`                                                                                      |
| `sts_course_item_video_question` | A  | `video_question_id bigint`，`course_item_id bigint`，`question_version_id bigint`，`popup_second int`，`wrong_replay_second int`，`sort_order int`，`status tinyint`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | `uk_item_question_time_deleted(course_item_id,question_version_id,popup_second,deleted)`；`idx_item_time(course_item_id,popup_second)`                                           |
| `sts_course_download_asset`      | A  | `course_download_asset_id bigint`，`course_id bigint`，`asset_id bigint`，`sort_order int`，`status tinyint`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | `uk_course_asset_deleted(course_id,asset_id,deleted)`；`idx_course_sort(course_id,sort_order)`                                                                                   |

`course_item` 是课程目录中的统一可排序节点。考试/练习参与后，相关 `exam_id`、`is_required` 不允许修改；该规则由应用服务根据考试尝试/学习事实校验。
`item_type=QUESTION_BANK/EXAM/PRACTICE` 时必须直接填写 `exam_id`，三种类型使用同一考试执行链，不增加课程专用考试或答卷表。每个
`QUESTION_BANK` 内容项只允许关联一个题库，并关联 `exam_type=BANK_PRACTICE` 且 `exam.question_bank_id`
与内容项题库一致的考试对象，以记录逐题答案、正确率和错题。同一个 `exam_id`
可以被多个课程内容项或开课使用，但每个学员按“课程报名 + 内容项”建立独立考试人员记录。
从多章节切换单章节时只移动 `chapter_id` 和排序，不复制素材或考试。

### 8.2 开课、报名与学习事实

| 表                          | 类别 | 业务字段                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | 关键约束与索引                                                                                                                                                                                                                         |
|----------------------------|----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sts_course_offering`      | A  | `course_offering_id bigint`，`organization_id bigint`，`offering_code varchar(64)`，`course_id bigint`，`course_version_id bigint null`，`plan_content_id bigint null`，`offering_name varchar(150)`，`start_time datetime null`，`end_time datetime null`，`course_type varchar(16)`（REQUIRED必修/ELECTIVE选修），`enrollment_type varchar(16)`（ASSIGNED/OPEN/LINK），`completion_rule varchar(32)`，`audience_id bigint null`，`is_self_enroll_allowed tinyint`，`is_external_user_allowed tinyint`，`publish_status varchar(16)`（DRAFT/PUBLISHED/ENDED/CANCELLED），`published_time datetime null`，`status tinyint` | `uk_org_offering_code_deleted(organization_id,offering_code,deleted)`；`idx_course_time`；`idx_course_version`；`idx_plan_content`；`idx_org_publish`                                                                               |
| `sts_course_enrollment`    | A  | `course_enrollment_id bigint`，`organization_id bigint`，`course_offering_id bigint`，`training_participant_id bigint null`，`user_id bigint`，`enrollment_source varchar(16)`（ASSIGN/SELF/LINK/IMPORT/TRAINING），`source_business_id bigint null`，`enrolled_time datetime`，`learning_status varchar(16)`（NOT_STARTED/LEARNING/COMPLETED/EXPIRED），`progress_percent decimal(5,2)`，`total_study_seconds int`，`credit_awarded decimal(10,2)`，`final_score decimal(10,2) null`，`completed_time datetime null`，`last_study_time datetime null`，`result_snapshot_json json null`                           | `uk_offering_user_deleted(course_offering_id,user_id,deleted)`；`idx_training_participant(training_participant_id)`；`idx_user_status(user_id,learning_status,last_study_time)`；`idx_org_status(organization_id,learning_status)` |
| `sts_course_item_progress` | A  | `item_progress_id bigint`，`organization_id bigint`，`course_enrollment_id bigint`，`course_item_id bigint`，`progress_status varchar(16)`，`study_seconds int`，`last_position_seconds int null`，`last_page_no int null`，`attempt_count int`，`best_score decimal(10,2) null`，`first_start_time datetime null`，`completed_time datetime null`，`last_study_time datetime null`                                                                                                                                                                                                                         | `uk_enrollment_item_deleted(course_enrollment_id,course_item_id,deleted)`；`idx_item_status(course_item_id,progress_status)`；`idx_org_status_time(organization_id,progress_status,last_study_time)`                              |
| `sts_course_study_session` | A  | `study_session_id bigint`，`organization_id bigint`，`course_enrollment_id bigint`，`course_item_id bigint`，`user_id bigint`，`device_type varchar(16)`，`started_time datetime`，`ended_time datetime null`，`valid_study_seconds int`，`start_position_seconds int null`，`end_position_seconds int null`，`start_page_no int null`，`end_page_no int null`，`end_reason varchar(32)`，`client_trace_id varchar(128)`                                                                                                                                                                                      | `uk_enrollment_trace_deleted(course_enrollment_id,client_trace_id,deleted)`；`idx_enrollment_time(course_enrollment_id,started_time)`；`idx_user_time(user_id,started_time)`；`idx_org_started_time(organization_id,started_time)` |
| `sts_course_evaluation`    | A  | `course_evaluation_id bigint`，`organization_id bigint`，`course_offering_id bigint`，`course_enrollment_id bigint`，`user_id bigint`，`rating decimal(3,1)`，`content varchar(2000) null`，`is_anonymous tinyint`，`submitted_time datetime`，`status tinyint`                                                                                                                                                                                                                                                                                                                                          | `uk_enrollment_deleted(course_enrollment_id,deleted)`；`idx_offering_time(course_offering_id,submitted_time)`                                                                                                                    |

培训下发课程时，`plan_content -> course_offering -> course_enrollment -> training_participant` 构成直接结果链，避免使用无法校验的泛化业务
ID 推断培训成绩。
`course_offering.plan_content_id` 仅记录“该开课因某培训计划内容创建”的溯源信息，不得作为归属、权限或统计判断依据。
`course_enrollment` 是学员课程报告的聚合结果，`item_progress` 是目录明细，`study_session` 是防篡改的过程流水。积分与证书通过通用积分流水和证书规则产生，
不直接修改课程定义。完成口径、过期状态和课程报告字段仍为暂定。
开课类型（FR-ENT-CRS-008、业务字典 8.4）与报名类型正交（2026-07-21 需求方确认）：`course_type` 表达必修/选修的修读性质，
`enrollment_type` 表达报名渠道；
UI 可按报名类型给默认值（指派默认必修、选课大厅默认选修），但存储始终显式。选修课的退选规则（FR-LRN-007）仍待产品确认。

课程结构化表始终表示当前工作草稿。首次发布及每次修改后再次发布时，事务内计算下一 `version_no`
，把课程基本信息、章节、内容项、素材/考试引用和学习规则写入 `sts_course_version.content_snapshot_json`，再更新
`current_version_id`。相同发布请求必须幂等；发布版本禁止更新或删除。开课从草稿进入 `PUBLISHED` 时绑定当时的
`current_version_id`，之后不得变更，学习展示和历史报告读取该版本快照。

## 9. 题库模块（`sts_question_*`）

| 表                                 | 类别 | 业务字段                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | 关键约束与索引                                                                                                                                                                                                                  |
|-----------------------------------|----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sts_question_knowledge_point`    | A  | `knowledge_point_id bigint`，`organization_id bigint`，`parent_id bigint not null default 0`，`knowledge_code varchar(64)`，`knowledge_name varchar(100)`，`ancestors varchar(1000)`，`sort_order int`，`status tinyint`                                                                                                                                                                                                                                                                                                 | `uk_org_code_deleted(organization_id,knowledge_code,deleted)`；`idx_parent_sort(parent_id,sort_order)`                                                                                                                    |
| `sts_question_bank_category`      | A  | `bank_category_id bigint`，`organization_id bigint`，`category_name varchar(100)`，`description varchar(500)`，`sort_order int`，`status tinyint`                                                                                                                                                                                                                                                                                                                                                                      | `uk_org_name_deleted(organization_id,category_name,deleted)`；`idx_org_sort(organization_id,sort_order)`                                                                                                                  |
| `sts_question_bank`               | A  | `question_bank_id bigint`，`organization_id bigint`，`bank_code varchar(64)`，`bank_name varchar(100)`，`bank_category_id bigint null`，`difficulty varchar(16)`（EASY/NORMAL/HARD/VERY_HARD），`question_order_mode varchar(16)`（TYPE/IMPORT），`archive_status varchar(16)`（ACTIVE/ARCHIVED），`status tinyint`                                                                                                                                                                                                             | `uk_org_bank_code_deleted(organization_id,bank_code,deleted)`；`idx_org_category_status(organization_id,bank_category_id,archive_status)`；`idx_org_status_time(organization_id,status,create_time)`；`idx_name(bank_name)` |
| `sts_question_bank_knowledge`     | B  | `bank_knowledge_id bigint`，`question_bank_id bigint`，`knowledge_point_id bigint`                                                                                                                                                                                                                                                                                                                                                                                                                                  | `uk_bank_knowledge(question_bank_id,knowledge_point_id)`；`idx_knowledge_bank(knowledge_point_id,question_bank_id)`                                                                                                       |
| `sts_question_question`           | A  | `question_id bigint`，`organization_id bigint`，`question_bank_id bigint`，`question_code varchar(64)`，`current_version_no int`，`archive_status varchar(16)`，`status tinyint`                                                                                                                                                                                                                                                                                                                                        | `uk_org_question_code_deleted(organization_id,question_code,deleted)`；`idx_bank_status(question_bank_id,status,archive_status)`                                                                                          |
| `sts_question_question_knowledge` | B  | `question_knowledge_id bigint`，`question_id bigint`，`knowledge_point_id bigint`                                                                                                                                                                                                                                                                                                                                                                                                                                   | `uk_question_knowledge(question_id,knowledge_point_id)`；`idx_knowledge_question(knowledge_point_id,question_id)`                                                                                                         |
| `sts_question_version`            | C  | `question_version_id bigint`，`question_id bigint`，`version_no int`，`question_type varchar(16)`（SINGLE/MULTIPLE/JUDGE/FILL/ESSAY），`stem longtext`，`standard_answer longtext null`，`analysis longtext null`，`difficulty varchar(16)`，`grading_mode varchar(16)`（AUTO/MANUAL/KEYWORD），`is_unordered_answer_allowed tinyint`，`is_case_ignored tinyint`，`is_full_half_width_ignored tinyint`，`published_status varchar(16)`，`published_by bigint null`，`published_time datetime null`，`version_note varchar(500) null` | `uk_question_version(question_id,version_no)`；`idx_type_status(question_type,published_status)`                                                                                                                          |
| `sts_question_option`             | C  | `question_option_id bigint`，`question_version_id bigint`，`option_key varchar(16)`，`option_content longtext`，`is_correct tinyint`，`sort_order int`                                                                                                                                                                                                                                                                                                                                                                 | `uk_version_key(question_version_id,option_key)`；`idx_version_sort(question_version_id,sort_order)`                                                                                                                      |
| `sts_question_blank_answer`       | C  | `blank_answer_id bigint`，`question_version_id bigint`，`blank_no int`，`answer_group_no int`，`answer_text varchar(1000)`，`sort_order int`                                                                                                                                                                                                                                                                                                                                                                           | `uk_version_blank_answer(question_version_id,blank_no,answer_group_no)`；`sort_order` 仅用于展示排序                                                                                                                             |
| `sts_question_keyword`            | C  | `question_keyword_id bigint`，`question_version_id bigint`，`keyword varchar(200)`，`weight decimal(7,4) null`，`sort_order int`                                                                                                                                                                                                                                                                                                                                                                                      | `uk_version_keyword(question_version_id,keyword)`                                                                                                                                                                        |
| `sts_question_asset`              | C  | `question_asset_id bigint`，`question_version_id bigint`，`asset_id bigint`，`asset_role varchar(16)`（STEM/OPTION/ANALYSIS），`option_key varchar(16) not null default ''`（非 OPTION 素材固定为空字符串），`sort_order int`                                                                                                                                                                                                                                                                                                        | `uk_version_asset_role(question_version_id,asset_id,asset_role,option_key)`；`idx_asset(asset_id)`                                                                                                                        |
| `sts_question_user_state`         | A  | `user_question_state_id bigint`，`organization_id bigint`，`user_id bigint`，`question_id bigint`，`is_favorite tinyint`，`wrong_count int`，`correct_count int`，`last_answer_time datetime null`，`last_wrong_time datetime null`，`last_exam_attempt_id bigint null`                                                                                                                                                                                                                                                    | `uk_org_user_question_deleted(organization_id,user_id,question_id,deleted)`；`idx_user_favorite(user_id,is_favorite)`；`idx_user_wrong(user_id,wrong_count)`；`idx_last_attempt(last_exam_attempt_id)`                      |

题目身份与版本分离：`question` 可停用，`question_version` 只追加。选择题正确项在 `question_option`，填空答案在
`blank_answer`，问答关键词在 `keyword`；
禁止把所有题型答案塞入一个可变 JSON。单选/多选 2~12 个选项、填空 1~12 项、问答最多 6 个关键词等规则由 API 和 Service 双重校验。

## 10. 试卷模块（`sts_paper_*`）

| 表                            | 类别 | 业务字段                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | 关键约束与索引                                                                                                                                                                                                                                                                                                                       |
|------------------------------|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sts_paper_category`         | A  | `paper_category_id bigint`，`organization_id bigint`，`category_name varchar(100)`，`description varchar(500)`，`sort_order int`，`status tinyint`                                                                                                                                                                                                                                                                                                                                          | `uk_org_name_deleted(organization_id,category_name,deleted)`；`idx_org_sort(organization_id,sort_order)`                                                                                                                                                                                                                       |
| `sts_paper_paper`            | A  | `paper_id bigint`，`organization_id bigint`，`paper_code varchar(64)`，`paper_name varchar(100)`，`paper_type varchar(16)`（FIXED/RANDOM/CUSTOM），`paper_category_id bigint null`，`cover_file_id bigint null`，`current_version_no int`，`archive_status varchar(16)`，`status tinyint`                                                                                                                                                                                                         | `uk_org_paper_code_deleted(organization_id,paper_code,deleted)`；`idx_org_type_category(organization_id,paper_type,paper_category_id,archive_status)`；`idx_org_category_status(organization_id,paper_category_id,archive_status,create_time)`；`idx_org_status_time(organization_id,status,create_time)`；`idx_name(paper_name)` |
| `sts_paper_version`          | C  | `paper_version_id bigint`，`paper_id bigint`，`version_no int`，`duration_seconds int`，`total_score decimal(10,2)`，`pass_ratio decimal(7,4)`，`pass_score decimal(10,2)`，`question_count int`，`question_order_mode varchar(16)`（TYPE/IMPORT/RANDOM），`multi_select_scoring varchar(16)`（ALL_OR_NOTHING/PARTIAL），`draw_setting_mode varchar(16)`（UNIFIED/PER_BANK），`published_status varchar(16)`，`published_by bigint null`，`published_time datetime null`，`version_note varchar(500) null` | `uk_paper_version(paper_id,version_no)`；`idx_status(published_status)`                                                                                                                                                                                                                                                        |
| `sts_paper_draw_rule`        | C  | `draw_rule_id bigint`，`paper_version_id bigint`，`question_bank_id bigint`，`question_type varchar(16)`，`difficulty varchar(16) not null default 'ALL'`，`knowledge_point_id bigint not null default 0`，`draw_count int`，`score_per_question decimal(10,2)`，`bank_weight decimal(7,4) null`，`sort_order int`                                                                                                                                                                              | `uk_version_bank_type_rule(paper_version_id,question_bank_id,question_type,difficulty,knowledge_point_id)`；`idx_version_sort(paper_version_id,sort_order)`                                                                                                                                                                    |
| `sts_paper_version_question` | C  | `paper_question_id bigint`，`paper_version_id bigint`，`question_id bigint`（冗余自题目版本），`question_version_id bigint`，`question_type varchar(16)`，`score decimal(10,2)`，`sort_order int`，`source_bank_id bigint`                                                                                                                                                                                                                                                                             | `uk_version_question(paper_version_id,question_id)` 保证同一试卷版本内题目唯一；`idx_question_version(question_version_id)`；`idx_version_type_sort(paper_version_id,question_type,sort_order)`                                                                                                                                              |

固定试卷和自定义试卷把确定题目写入 `paper_version_question`；随机试卷把抽题蓝图写入 `paper_draw_rule`，实际抽题结果在考试尝试中冻结。
同一试卷版本内按 `question_id` 去重，同一逻辑题目不得以不同题目版本重复入卷；随机抽题服务同样按 `question_id` 去重，保证与作答快照表
`uk_attempt_question(exam_attempt_id,question_id)` 的唯一性口径一致。
试卷编辑生成新 `paper_version`；考试引用明确版本而不是 `paper_id` 当前版本。`pass_score` 是发布时计算快照，避免比例精度变化。

## 11. 考试与练习模块（`sts_exam_*`）

### 11.1 考试定义、规则和人员

| 表                    | 类别 | 业务字段                                                                                                                                                                                                                                                                                                                                                  | 关键约束与索引                                                                                                                                                                                                                                 |
|----------------------|----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sts_exam_category`  | A  | `exam_category_id bigint`，`organization_id bigint`，`category_name varchar(100)`，`description varchar(500)`，`sort_order int`，`status tinyint`                                                                                                                                                                                                          | `uk_org_name_deleted(organization_id,category_name,deleted)`；`idx_org_sort(organization_id,sort_order)`                                                                                                                                 |
| `sts_exam_exam`      | A  | `exam_id bigint`，`organization_id bigint`，`exam_code varchar(64)`，`exam_name varchar(100)`，`exam_type varchar(24)`（ONLINE/OFFLINE/SPECIAL_PRACTICE/BANK_PRACTICE），`exam_category_id bigint null`，`paper_version_id bigint null`，`question_bank_id bigint null`，`plan_content_id bigint null`，`responsible_user_id bigint null`，开放时间、时长、及格线、学分、须知与发布状态 | `uk_org_exam_code_deleted(organization_id,exam_code,deleted)`；`idx_org_type_status`；`idx_org_category_status`；`idx_org_status_time`；`idx_paper_version`；`idx_question_bank`；`idx_plan_content`；`idx_responsible_user`；发布时按考试类型校验试卷或题库引用 |
| `sts_exam_policy`    | A  | `exam_policy_id bigint`，`exam_id bigint`，`result_notify_audience_id bigint null`，水印、端侧准入、切屏/分屏、无操作、最短答题、补考、积分、查看答案与排行榜策略；布尔字段统一使用 `is_*`                                                                                                                                                                                                              | `uk_exam_deleted(exam_id,deleted)`；`idx_result_notify_audience(result_notify_audience_id)`                                                                                                                                              |
| `sts_exam_candidate` | A  | `exam_candidate_id bigint`，`organization_id bigint`，`exam_id bigint`，`user_id bigint`，课程/培训来源 ID，`context_type varchar(16)`（STANDALONE/COURSE/TRAINING），`context_id bigint`，`assignment_source varchar(16)`，`source_business_id bigint`，`candidate_profile_snapshot_json json`，`profile_snapshot_time datetime`，考试人员状态、次数、最佳/最终成绩、最终尝试与发布时间           | `uk_exam_user_context_deleted(exam_id,user_id,context_type,context_id,deleted)`；`uk_course_enrollment_item_deleted(course_enrollment_id,course_item_id,deleted)`；上下文、课程、培训、用户、状态与机构时间索引                                                 |
| `sts_exam_reviewer`  | B  | `exam_reviewer_id bigint`，`exam_id bigint`，`reviewer_user_id bigint`，`assignment_source varchar(16)`                                                                                                                                                                                                                                                  | `uk_exam_reviewer(exam_id,reviewer_user_id)`；`idx_reviewer_exam(reviewer_user_id,exam_id)`                                                                                                                                              |

线上考试和专项练习发布时必须绑定明确 `paper_version_id`；题库练习可以绑定 `question_bank_id`
，开始作答时由尝试记录冻结试卷来源、总分、合格分、时长、题序、多选计分参数和实际题目快照。线下结果型尝试使用
`paper_source_type=OFFLINE_RESULT`，不要求虚构正式试卷版本。
培训下发考试时，`plan_content -> exam -> exam_candidate -> training_participant` 构成直接结果链。
`exam.plan_content_id` 仅记录“该考试因某培训计划内容创建”的溯源信息；复用既有考试时保持 NULL，不得作为归属、权限或统计判断依据。培训执行链一律以
`plan_content.resource_id` 正向引用和考试人员 `context_type/context_id` 为准。
课程内题库/考试/练习按 `course_enrollment + course_item -> course_item_progress -> exam_candidate -> exam_attempt`
构成直接结果链。独立考试固定使用 `context_type=STANDALONE, context_id=0`；课程使用
`context_type=COURSE, context_id=course_item_progress_id`；培训使用 `context_type=TRAINING, context_id=plan_content_id`
，并由 `training_participant_id` 确定参训身份。唯一键已包含 `user_id`，因此同一参训人在两个计划内容项执行同一考试时仍会生成互不覆盖的考试人员记录。
`assignment_source` 只记录分配渠道，不参与考试人员业务身份。不同开课或内容项各自拥有考试人员记录，补考、练习重做和重置只在该考试人员下生效。
考试分配时必须冻结用户账号、姓名、机构、部门路径、工号、岗位和职务快照；历史名单、部门统计和导出读取快照，当前用户表只用于账号有效性和最新资料查询。
考试开始后 `exam_type`、`paper_version_id`、开始时间和时长锁定；结束后整场考试不可编辑。
考试结果通知不再使用“直属负责人”开关。管理员直接选择一个或多个接收用户，保存为 `purpose=NOTIFICATION`、
`resolve_mode=FROZEN` 的参与范围，并由 `result_notify_audience_id` 绑定；该用途只接受 `subject_type=USER`
的直接选人规则，未选择时不推送。成绩发布后只为范围内用户生成 `sts_common_message_recipient`，不从部门、角色或岗位推导接收人。

暂定：调研文档同时在试卷和考试配置时长、及格线。本设计以“考试发布时复制试卷值，只有明确选择覆盖时才写考试值”为候选策略，
是否允许覆盖以及已有关联时的优先级必须由产品确认。

### 11.2 作答快照、答案和批阅

| 表                            | 类别 | 业务字段                                                                                                                                                                                                                                                                                                                                                                                                                             | 关键约束与索引                                                                                                                                                                                             |
|------------------------------|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sts_exam_attempt`           | A  | `exam_attempt_id bigint`，机构、考试、考试人员和用户 ID，`paper_version_id bigint null`，`paper_source_type varchar(24)`，`paper_source_id bigint`，总分/合格分/时长/题序/多选计分快照，尝试序号、类型、状态、时间、分数、通过结果、设备环境和幂等令牌                                                                                                                                                                                                                                            | `uk_candidate_attempt_deleted(exam_candidate_id,attempt_no,deleted)`；`uk_candidate_token_deleted(exam_candidate_id,client_attempt_token,deleted)`；考试、用户、机构状态时间索引                                    |
| `sts_exam_attempt_question`  | C  | `attempt_question_id bigint`，`organization_id bigint`，`exam_attempt_id bigint`，`question_id bigint`，`question_version_id bigint`，`question_type varchar(16)`，`stem_snapshot longtext`，`analysis_snapshot longtext null`，`grading_mode varchar(16)`，`score decimal(10,2)`，`correct_answer_snapshot_json json null`，`sort_order int`，`source_bank_id bigint`                                                                       | `uk_attempt_question(exam_attempt_id,question_id)`；`idx_attempt_type_sort(exam_attempt_id,question_type,sort_order)`；`idx_question_version(question_version_id)`                                    |
| `sts_exam_attempt_option`    | C  | `attempt_option_id bigint`，`attempt_question_id bigint`，`option_key varchar(16)`，`option_content_snapshot longtext`，`is_correct_snapshot tinyint`，`sort_order int`                                                                                                                                                                                                                                                               | `uk_attempt_question_key(attempt_question_id,option_key)`；`idx_question_sort(attempt_question_id,sort_order)`                                                                                       |
| `sts_exam_answer`            | A  | `exam_answer_id bigint`，`organization_id bigint`，`exam_attempt_id bigint`，`attempt_question_id bigint`，`answer_text longtext null`，`is_answered tinyint`，`auto_score decimal(10,2) null`，`manual_score decimal(10,2) null`，`final_score decimal(10,2)`，`is_correct tinyint null`，`grading_status varchar(16)`（UNANSWERED/AUTO_GRADED/PENDING_MANUAL/MANUAL_GRADED），`answered_time datetime null`，`last_saved_time datetime null` | `uk_attempt_question_deleted(exam_attempt_id,attempt_question_id,deleted)`；`idx_attempt_grading(exam_attempt_id,grading_status)`；`idx_org_grading_time(organization_id,grading_status,update_time)` |
| `sts_exam_answer_item`       | A  | `answer_item_id bigint`，`exam_answer_id bigint`，`item_type varchar(16)`（OPTION/BLANK），`item_key varchar(32)`，`item_value longtext`，`sort_order int`                                                                                                                                                                                                                                                                              | `uk_answer_type_key_deleted(exam_answer_id,item_type,item_key,deleted)`；`idx_answer_sort(exam_answer_id,sort_order)`                                                                                |
| `sts_exam_answer_asset`      | B  | `answer_asset_id bigint`，`exam_answer_id bigint`，`file_info_id bigint`，`sort_order int`                                                                                                                                                                                                                                                                                                                                          | `uk_answer_file(exam_answer_id,file_info_id)`                                                                                                                                                       |
| `sts_exam_review_record`     | C  | `review_record_id bigint`，`organization_id bigint`，`exam_attempt_id bigint`，`exam_answer_id bigint`，`reviewer_user_id bigint`，`before_score decimal(10,2) null`，`after_score decimal(10,2)`，`review_comment varchar(2000) null`，`review_action varchar(16)`（SCORE/RESCORE），`reviewed_time datetime`                                                                                                                              | `idx_attempt_time(exam_attempt_id,reviewed_time)`；`idx_reviewer_time(reviewer_user_id,reviewed_time)`；`idx_org_reviewed_time(organization_id,reviewed_time)`                                        |
| `sts_exam_score_publish_log` | C  | `score_publish_log_id bigint`，`organization_id bigint`，`exam_id bigint`，`exam_attempt_id bigint`，`published_by bigint`，`published_time datetime`，`publish_type varchar(16)`（SINGLE/BATCH），`score_snapshot decimal(10,2)`，`result_snapshot varchar(16)`                                                                                                                                                                           | `idx_exam_time(exam_id,published_time)`；`idx_attempt(exam_attempt_id)`；`idx_org_publish_time(organization_id,published_time)`                                                                       |

`attempt_question` 和 `attempt_option` 是作答时生成的不可变快照；`exam_answer` 在作答阶段可自动保存，选择项和填空项结构化保存于
`answer_item`，
交卷后由状态机禁止学员修改。
每次人工评分/改分写一条 `review_record`，不能覆盖旧评分记录。发布成绩写独立日志，并以幂等检查避免重复积分、证书和通知。

### 11.3 防作弊事件和练习重置

| 表                          | 类别 | 业务字段                                                                                                                                                                                                                                                                                                                                 | 关键约束与索引                                                                                                                                              |
|----------------------------|----|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sts_exam_anticheat_event` | C  | `anticheat_event_id bigint`，`organization_id bigint`，`exam_attempt_id bigint`，`event_type varchar(32)`（PAGE_SWITCH/SPLIT_SCREEN/INACTIVITY/COPY/REFRESH/DEVTOOL/TIMEOUT），`event_time datetime`，`event_count int`，`action_taken varchar(32)`（RECORD/WARN/FORCE_SUBMIT），`evidence_file_id bigint null`，`client_payload_json json null` | `idx_attempt_time`；`idx_event_type`；`idx_org_event_time`；`idx_evidence_file`                                                                         |
| `sts_exam_practice_reset`  | C  | `practice_reset_id bigint`，`organization_id bigint`，`exam_id bigint`，`exam_candidate_id bigint`，`user_id bigint`，`reset_scope varchar(16)`（ALL/WRONG/UNFINISHED），`reset_time datetime`，`reason varchar(500) null`                                                                                                                    | `idx_candidate_time(exam_candidate_id,reset_time)`；`idx_exam_user_time(exam_id,user_id,reset_time)`；`idx_org_reset_time(organization_id,reset_time)` |

清除练习记录不物理删除答卷，而是在具体 `exam_candidate_id` 下新增 reset 水位；学员端只隐藏该执行上下文最近 reset
之前的练习事实，不影响其他课程、培训或独立练习。收藏状态仍是用户全局题目状态，不随单个练习重置清除。错题分析限定首次正式考试时，通过
`attempt_type=FIRST` 计算。
人脸核验本期不实施，考试防作弊事件不保留人脸事件类型或人脸核验引用。

### 11.4 考试状态与成绩口径

```text
候选人：NOT_STARTED → IN_PROGRESS → SUBMITTED
                              ├→ PENDING_REVIEW → PENDING_PUBLISH
                              └──────────────────→ PENDING_PUBLISH
PENDING_PUBLISH → PASSED / FAILED
开放期结束仍无有效尝试 → ABSENT

尝试：CREATED → ANSWERING → SUBMITTED → PENDING_REVIEW? → PENDING_PUBLISH → PUBLISHED
```

- 客观题交卷后自动评分；存在人工题则进入 `PENDING_REVIEW`。
- 人工题评分完成进入 `PENDING_PUBLISH`；发布后才更新候选人的正式结果并触发积分、证书和消息。
- 补考每次都是新的 `exam_attempt`，`attempt_no` 递增；候选人最终成绩由 `retake_score_mode` 计算。
- 线下考试暂定只创建候选人和结果型尝试，不生成逐题答卷；导入模板和冲突处理规则需单独确认。

## 12. 仿真能力（本期不实施）

本期不实现仿真资源、安装包、仿真试卷、分配、参与、尝试和步骤结果，不创建任何 `sts_sim_*` 表。
FR-ENT-EXM-009~016 延期处理；后续启动时必须先确认运行、认证、计分、回调、幂等、断线续传和安装包安全协议，并创建独立 OpenSpec
Change。

## 13. 培训标准、模板与执行模块（`sts_training_*`）

### 13.1 培训标准与模板

| 表                                | 类别 | 业务字段                                                                                                                                                                                                                                                                                                                                      | 关键约束与索引                                                                                                                                      |
|----------------------------------|----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `sts_training_type`              | A  | `training_type_id bigint`，`organization_id bigint`，`type_code varchar(64)`，`type_name varchar(100)`，`work_type varchar(100) null`，`tag varchar(64) null`，`image_file_id bigint null`，`initial_hours decimal(8,2) null`，`review_hours decimal(8,2) null`，`online_max_ratio decimal(7,4) null`，`status tinyint`，`description varchar(1000)` | `uk_org_code_deleted(organization_id,type_code,deleted)`；`idx_org_work_status(organization_id,work_type,status)`                             |
| `sts_training_standard`          | A  | `training_standard_id bigint`，`organization_id bigint`，`standard_code varchar(64)`，`standard_name varchar(100)`，`training_type_id bigint`，`standard_version varchar(32)`，`valid_from date null`，`valid_to date null`，`publish_status varchar(16)`，`status tinyint`                                                                        | `uk_org_code_version_deleted(organization_id,standard_code,standard_version,deleted)`；`idx_type_valid(training_type_id,valid_from,valid_to)` |
| `sts_training_standard_rule`     | A  | `standard_rule_id bigint`，`training_standard_id bigint`，`rule_type varchar(32)`（TOTAL_HOURS/ONLINE_RATIO/COURSE/EXAM/OFFLINE），`required_value decimal(12,4) null`，`unit varchar(16) null`，`resource_type varchar(32) null`，`resource_id bigint null`，`sort_order int`，`status tinyint`，`description varchar(1000)`                        | `idx_standard_type(training_standard_id,rule_type,sort_order)`                                                                               |
| `sts_training_template_category` | A  | `template_category_id bigint`，`organization_id bigint`，`parent_id bigint not null default 0`，`ancestors varchar(1000)`，`category_name varchar(100)`，`sort_order int`，`status tinyint`                                                                                                                                                     | `uk_org_parent_name_deleted(organization_id,parent_id,category_name,deleted)`；`idx_parent_sort(parent_id,sort_order)`                        |
| `sts_training_template`          | A  | `training_template_id bigint`，`organization_id bigint`，`template_code varchar(64)`，`template_name varchar(100)`，`template_category_id bigint null`，`training_type_id bigint`，`training_standard_id bigint null`，`description longtext null`，`publish_status varchar(16)`，`status tinyint`                                                 | `uk_org_code_deleted(organization_id,template_code,deleted)`；`idx_type_status(training_type_id,publish_status)`                              |
| `sts_training_template_stage`    | A  | `template_stage_id bigint`，`training_template_id bigint`，`stage_name varchar(100)`，`stage_type varchar(16)`（ONLINE/OFFLINE/MIXED），`required_hours decimal(8,2) null`，`sort_order int`，`status tinyint`                                                                                                                                    | `uk_template_stage_name_deleted(training_template_id,stage_name,deleted)`；`idx_template_sort(training_template_id,sort_order)`               |
| `sts_training_template_content`  | A  | `template_content_id bigint`，`template_stage_id bigint`，`content_type varchar(24)`（COURSE/EXAM/QUESTION_BANK/OFFLINE），`resource_id bigint null`，`content_name varchar(150)`，`is_required tinyint`，`required_hours decimal(8,2) null`，`sort_order int`，`status tinyint`                                                                    | `idx_stage_sort(template_stage_id,sort_order)`；`idx_resource(content_type,resource_id)`                                                      |

`sts_training_type` 已能承载 FR-REG-005 要求的培训类型字段。需求方确认在实施阶段自行提供并内置不少于 25
种正式类型；当前数据模型不为具体清单增加新表或字段，也不在设计阶段生成猜测性 seed。正式初始化必须使用稳定 `type_code`
并保证幂等，目标 `organization_id` 通过机构业务编码解析，不硬编码自增 ID。

模板只作为创建实际培训计划的输入。引用模板后复制为计划阶段和内容，后续模板修改不得回写已创建计划。

### 13.2 培训计划、参与和线下执行

| 表                                     | 类别 | 业务字段                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | 关键约束与索引                                                                                                                                                                                                                                                     |
|---------------------------------------|----|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sts_training_plan`                   | A  | `training_plan_id bigint`，`organization_id bigint`，`plan_code varchar(64)`，`plan_name varchar(150)`，`training_type_id bigint`，`training_standard_id bigint null`，`source_template_id bigint null`，`source_owner_organization_id bigint null`，培训类型/标准/模板快照 JSON 与哈希，`manager_user_id bigint null`，起止时间、参与范围、地点、描述与发布状态                                                                                                                                                                                                                                                       | `uk_org_plan_code_deleted`；机构状态时间、来源机构、培训类型、标准、来源模板、负责人和参与范围索引                                                                                                                                                                                              |
| `sts_training_plan_organization`      | B  | `plan_organization_id bigint`，`training_plan_id bigint`，`participant_organization_id bigint`，`organization_role varchar(16)`（ORGANIZER/PARTICIPANT）                                                                                                                                                                                                                                                                                                                                                                                                                         | `uk_plan_org(training_plan_id,participant_organization_id,organization_role)`；`idx_org_plan(participant_organization_id,training_plan_id)`                                                                                                                  |
| `sts_training_plan_stage`             | A  | `plan_stage_id bigint`，`training_plan_id bigint`，`source_template_stage_id bigint null`，`stage_name varchar(100)`，`stage_type varchar(16)`，`start_time datetime null`，`end_time datetime null`，`location varchar(255) null`，`required_hours decimal(8,2) null`，`sort_order int`，`stage_status varchar(16)`                                                                                                                                                                                                                                                                  | `idx_plan_sort(training_plan_id,sort_order)`；`idx_stage_time(start_time,end_time)`                                                                                                                                                                          |
| `sts_training_plan_content`           | A  | `plan_content_id bigint`，`plan_stage_id bigint`，`content_type varchar(24)`，`resource_id bigint null`，`content_name varchar(150)`，`is_required tinyint`，`is_scored tinyint`，`score_weight decimal(7,4) null`，`score_aggregation_mode varchar(24) null`（AVERAGE/WEIGHTED_AVERAGE），`required_hours decimal(8,2) null`，`sort_order int`，`content_status varchar(16)`                                                                                                                                                                                                            | `idx_stage_sort(plan_stage_id,sort_order)`；`idx_resource(content_type,resource_id)`                                                                                                                                                                         |
| `sts_training_plan_evaluation_form`   | A  | `plan_evaluation_form_id bigint`，`training_plan_id bigint`，`form_version_id bigint`，`usage_type varchar(24)`（COMPOSITE/EXPERT_INSPECTION/PARTICIPANT/COURSE_QUALITY），`is_required tinyint`，`status tinyint`                                                                                                                                                                                                                                                                                                                                                                 | `uk_plan_usage_deleted(training_plan_id,usage_type,deleted)` 保证一个计划每种用途只绑定一个固定版本；`idx_form_plan`                                                                                                                                                            |
| `sts_training_participant`            | A  | `training_participant_id bigint`，`organization_id bigint`，`training_plan_id bigint`，`user_id bigint`，`participant_org_id bigint`，`audience_id bigint null`，`join_source varchar(16)`（ASSIGN/IMPORT/QR），`joined_time datetime`，`participant_status varchar(24)`，`online_hours decimal(8,2)`，`offline_hours decimal(8,2)`，`course_score decimal(10,2) null`，`exam_score decimal(10,2) null`，`offline_score decimal(10,2) null`，`composite_score decimal(10,2) null`，`official_calculation_run_id bigint null`，`result_status varchar(16) null`，`completed_time datetime null` | `uk_plan_user_deleted(training_plan_id,user_id,deleted)`；用户、计划机构、机构状态时间和正式计算批次索引                                                                                                                                                                            |
| `sts_training_attachment`             | A  | `training_attachment_id bigint`，`training_plan_id bigint`，`plan_stage_id bigint null`，`attachment_type varchar(24)`（GENERAL/IMAGE/VIDEO/REWARD_PENALTY/EVALUATION），`file_info_id bigint`，`attachment_name varchar(255)`，`description varchar(1000) null`，`sort_order int`，`status tinyint`                                                                                                                                                                                                                                                                                  | `uk_plan_file_deleted`；`idx_plan_type`；`idx_stage`                                                                                                                                                                                                          |
| `sts_training_offline_session`        | A  | `offline_session_id bigint`，`training_plan_id bigint`，`plan_stage_id bigint`，`plan_content_id bigint null`，`session_name varchar(150)`，`teacher_user_id bigint null`，地点、起止与签到时间，`attendance_rule varchar(32)`，`session_status varchar(16)`，`status tinyint`                                                                                                                                                                                                                                                                                                                 | `idx_stage_time`；`idx_teacher_time`；`idx_plan_content`                                                                                                                                                                                                      |
| `sts_training_attendance`             | A  | `attendance_id bigint`，`organization_id bigint`，`offline_session_id bigint`，`training_participant_id bigint`，`user_id bigint`，`attendance_status varchar(16)`（PRESENT/LATE/LEAVE/ABSENT），`sign_in_time datetime null`，`sign_out_time datetime null`，`sign_method varchar(16)`，`evidence_json json null`，`confirmed_by bigint null`，`confirmed_time datetime null`                                                                                                                                                                                                           | `uk_session_participant_deleted(offline_session_id,training_participant_id,deleted)`；`idx_user_time(user_id,sign_in_time)`；`idx_session_status(offline_session_id,attendance_status)`；`idx_org_status_time(organization_id,attendance_status,sign_in_time)` |
| `sts_training_offline_score`          | A  | `offline_score_id bigint`，`organization_id bigint`，`training_participant_id bigint`，`offline_session_id bigint`，`score_name varchar(100)`，`score decimal(10,2)`，`max_score decimal(10,2)`，`score_source varchar(16)`（MANUAL/IMPORT），`import_batch_id bigint null`，`scored_by bigint null`，`scored_time datetime`                                                                                                                                                                                                                                                            | `uk_participant_session_name_deleted(training_participant_id,offline_session_id,score_name,deleted)`；`idx_session_score(offline_session_id,score)`；`idx_import_batch(import_batch_id)`；`idx_org_scored_time(organization_id,scored_time)`                   |
| `sts_training_reward_penalty`         | A  | `reward_penalty_id bigint`，`organization_id bigint`，`training_plan_id bigint`，`training_participant_id bigint`，`record_type varchar(16)`（REWARD/PENALTY），`title varchar(200)`，`description longtext`，`score_change decimal(10,2) null`，`occurred_date date`，`evidence_file_id bigint null`，`status tinyint`                                                                                                                                                                                                                                                                 | `idx_participant_date(training_participant_id,occurred_date)`；`idx_plan_type(training_plan_id,record_type)`                                                                                                                                                 |
| `sts_training_participant_evaluation` | A  | `participant_evaluation_id bigint`，`organization_id bigint`，`training_plan_id bigint`，`training_participant_id bigint`，`evaluator_user_id bigint`，`evaluation_record_id bigint null`，`evaluation_type varchar(24)`，`score decimal(10,2) null`，`content longtext null`，`evaluated_time datetime`，`status tinyint`                                                                                                                                                                                                                                                            | `idx_participant_type`；`idx_plan_time`；`idx_evaluation_record`                                                                                                                                                                                              |

计划内容通过 `content_type + resource_id` 编排已发布的课程、考试、题库或线下资源；发布服务必须按类型验证引用。
培训直投直播本期不实施（2026-07-21 需求方确认）：计划和模板内容不提供 LIVE 类型，直播仅通过课程内容项执行；后续启用需独立
OpenSpec Change 补执行通道。课程、考试和线下执行表通过
`plan_content_id` 绑定内容，通过各自人员表的 `training_participant_id` 绑定参训人，结果可以稳定回写培训汇总。扫码加入仍须建立
`us_sys_user` 和机构成员后才能生成参训人员。

企业或培训机构可在有效监管关系和 `sts_common_resource_org_grant`
授权下引用园区所属的培训类型、培训标准和培训模板，其中园区是资源所有方、企业或培训机构是被授权使用方。创建或发布计划时必须同时校验授权级别、有效期和资源状态，并把来源园区以及类型、标准、模板的业务快照与哈希冻结到计划；计划运行、归档和审计读取冻结快照，不因授权到期或来源资源后续修改而改变。未获授权的跨机构引用必须拒绝，同机构引用继续遵循默认逻辑外键规则。

签到方式、迟到阈值、有效定位/二维码规则和培训状态值为暂定。综合成绩本期支持课程/考试计划内容权重、同类型平均或加权平均、来源类型权重与基础加减分，高级公式作为后续升级项。
`evidence_json` 仅保存签到设备/定位原始证据，稳定字段必须在规则确认后结构化。

## 14. 证书模块（`sts_certificate_*`）

| 表                                  | 类别 | 业务字段                                                                                                                                                                                                                                                                                          | 关键约束与索引                                                                           |
|------------------------------------|----|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| `sts_certificate_template`         | A  | `certificate_template_id bigint`，`organization_id bigint`，`template_code varchar(64)`，`template_name varchar(100)`，`current_version_no int`，`status tinyint`，`description varchar(1000)`                                                                                                      | `uk_org_code_deleted`；`idx_org_status`                                            |
| `sts_certificate_template_version` | C  | `template_version_id bigint`，`certificate_template_id bigint`，`version_no int`，`background_file_id bigint`，`width_px int`，`height_px int`，`published_status varchar(16)`（DRAFT/PUBLISHED/ARCHIVED），`published_by bigint null`，`published_time datetime null`，`version_note varchar(500) null` | `uk_template_version(certificate_template_id,version_no)`；`idx_background_file`   |
| `sts_certificate_template_element` | A  | `template_element_id bigint`，`template_version_id bigint`，`element_type varchar(16)`（TEXT/IMAGE/VARIABLE），坐标、尺寸、字体、变量、内容、顺序与状态                                                                                                                                                                | `idx_template_sort(template_version_id,sort_order)`；`idx_variable(variable_code)` |
| `sts_certificate_issue_rule`       | A  | `certificate_rule_id bigint`，`organization_id bigint`，`rule_name varchar(100)`，`certificate_template_id bigint`，`source_type varchar(16)`（COURSE/EXAM/TRAINING），`source_id bigint`，条件、领取说明、`is_auto_issue tinyint`、有效期与状态                                                                     | `uk_source_rule_deleted`；`idx_source_status`                                      |
| `sts_certificate_certificate`      | A  | `certificate_id bigint`，`organization_id bigint`，`certificate_no varchar(128)`，`certificate_rule_id bigint null`，`certificate_template_id bigint`，`template_version_id bigint`，`holder_user_id bigint`，来源、来源结果、签发/有效期、文件、内容快照、哈希与状态                                                           | 证书号、持有人来源幂等唯一键；持有人状态、模板版本与签发规则索引                                                  |
| `sts_certificate_revocation`       | C  | `certificate_revocation_id bigint`，`organization_id bigint`，`certificate_id bigint`，`revoked_by bigint`，`revoked_time datetime`，`reason varchar(1000)`，`before_status varchar(16)`，`after_status varchar(16)`                                                                                 | `idx_certificate_time(certificate_id,revoked_time)`                               |

证书模板元素变量的完整白名单、证书编号生成、签章、防伪验证、有效期和撤销权限均为暂定。课程中途新增证书规则时，
对已完成人员的补发通过扫描合格事实并使用幂等唯一键实现，不能直接更新课程学习记录。

来源粒度与重发规则（2026-07-21 需求方确认，采用简单规则）：签发规则的 `source_id` 为业务定义对象——COURSE 为课程 ID、EXAM 为考试
ID、TRAINING 为培训计划 ID；
证书实例的 `source_id` 为执行实例——COURSE 为开课 ID、EXAM 为考试 ID、TRAINING 为培训计划 ID，`source_result_id`
分别为课程报名、考试人员和参训人员 ID。
年度复训通过新培训计划或新开课自然签发新证书，不建设过期自动重发机制；证书 `valid_to` 到期只迁移为 EXPIRED 状态。
同一（持有人、来源、模板）最多一张未删除证书；已撤销证书仍占用幂等唯一键，确需重发时由管理员先逻辑删除已撤销证书再重新签发，撤销与删除动作均留痕。
本期不引入签发序号或自动重发，撤销后重发均为人工动作。

## 15. 问卷模块（`sts_survey_*`）

| 表                           | 类别 | 业务字段                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | 关键约束与索引                                                                                                                           |
|-----------------------------|----|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `sts_survey_survey`         | A  | `survey_id bigint`，`organization_id bigint`，`survey_code varchar(64)`，`survey_name varchar(100)`，`current_version_id bigint null`，`survey_type varchar(32)`，`cover_file_id bigint null`，`description longtext null`，`closing_text longtext null`，`is_anonymous tinyint`，`content_status varchar(16)`（DRAFT/PUBLISHED/OFFLINE），`status tinyint`                                                                                                                                                                                                               | `uk_org_code_deleted(organization_id,survey_code,deleted)`；`idx_current_version`；`idx_org_content_status`；`idx_name`              |
| `sts_survey_version`        | C  | `survey_version_id bigint`，`survey_id bigint`，`version_no int`，`content_snapshot_json json`，`snapshot_hash varchar(128)`，`version_note varchar(500) null`，`published_by bigint`，`published_time datetime`                                                                                                                                                                                                                                                                                                                                                    | `uk_survey_version(survey_id,version_no)`；`idx_survey_publish_time(survey_id,published_time)`                                     |
| `sts_survey_question`       | A  | `survey_question_id bigint`，`survey_id bigint`，`question_type varchar(24)`（SINGLE/MULTIPLE/SINGLE_FILL/MULTI_FILL/SORT/RATING/SCORE/UPLOAD/TEXT），`question_text varchar(300)`，`is_required tinyint`，`is_other_allowed tinyint`，`is_description_allowed tinyint`，`data_type varchar(16) null`（DATE/TIME/PHONE/EMAIL/TEXT/GENDER/AGE_RANGE），`max_star int null`，`max_upload_count int null`，`sort_order int`，`status tinyint`                                                                                                                                  | `idx_survey_sort(survey_id,sort_order)`；`idx_survey_type(survey_id,question_type)`                                                |
| `sts_survey_option`         | A  | `survey_option_id bigint`，`survey_question_id bigint`，`option_key varchar(16)`，`option_content varchar(1000)`，`sort_order int`，`status tinyint`                                                                                                                                                                                                                                                                                                                                                                                                              | `uk_question_key_deleted(survey_question_id,option_key,deleted)`；`idx_question_sort(survey_question_id,sort_order)`               |
| `sts_survey_sub_item`       | A  | `survey_sub_item_id bigint`，`survey_question_id bigint`，`item_name varchar(300)`，`data_type varchar(16) null`，`max_star int null`，`sort_order int`，`status tinyint`                                                                                                                                                                                                                                                                                                                                                                                          | `idx_question_sort(survey_question_id,sort_order)`                                                                                |
| `sts_survey_target_binding` | A  | `survey_binding_id bigint`，`survey_id bigint`，`source_type varchar(16)`（COURSE/EXAM/TRAINING/ORGANIZATION），`source_id bigint`，`is_available_after_completion tinyint`，`status tinyint`                                                                                                                                                                                                                                                                                                                                                                       | `uk_survey_source_deleted(survey_id,source_type,source_id,deleted)`；`idx_source(source_type,source_id,status)`                    |
| `sts_survey_instance`       | A  | `survey_instance_id bigint`，`organization_id bigint`，`instance_code varchar(64)`，`instance_name varchar(150)`，`survey_id bigint`，`survey_version_id bigint`，`survey_binding_id bigint null`，`source_type varchar(24)`，`source_id bigint`，`audience_id bigint null`（BOUND/FROZEN），`result_view_audience_id bigint null`（BOUND/FROZEN），`publish_mode varchar(16)`（IMMEDIATE/SCHEDULED），计划/实际发布时间，`start_time datetime null`，`end_time datetime null`，`is_anonymous_snapshot tinyint`，`instance_status varchar(16)`（DRAFT/SCHEDULED/PUBLISHED/CLOSED/WITHDRAWN） | `uk_org_instance_code_deleted`；问卷状态、来源状态、受众状态时间索引                                                                                 |
| `sts_survey_participant`    | A  | `survey_participant_id bigint`，`organization_id bigint`，`survey_instance_id bigint`，`survey_id bigint`，`survey_version_id bigint`，`user_id bigint`，`audience_id bigint null`，`participant_status varchar(16)`（PENDING/DRAFT/SUBMITTED/EXPIRED），`first_open_time datetime null`，`submitted_time datetime null`                                                                                                                                                                                                                                                | `uk_instance_user_deleted(survey_instance_id,user_id,deleted)`；用户、实例、问卷、版本和机构状态时间索引                                               |
| `sts_survey_response`       | A  | `survey_response_id bigint`，`organization_id bigint`，`survey_instance_id bigint`，`survey_id bigint`，`survey_version_id bigint`，`survey_participant_id bigint`，`user_id bigint`，`is_anonymous tinyint`，`response_status varchar(16)`（DRAFT/SUBMITTED），`started_time datetime`，`submitted_time datetime null`，`client_ip varchar(128) null`，`device_type varchar(16) null`                                                                                                                                                                                     | `uk_participant_deleted(survey_participant_id,deleted)`；实例、问卷、版本和机构提交时间索引                                                         |
| `sts_survey_answer`         | A  | `survey_answer_id bigint`，`survey_response_id bigint`，`survey_question_id bigint`，`survey_sub_item_id bigint null`，`sub_item_no int not null default 0`，`answer_text longtext null`，`numeric_value decimal(10,2) null`，`date_value date null`，`time_value time null`，`other_text varchar(1000) null`，`description_text varchar(2000) null`，`sort_value int null`                                                                                                                                                                                             | `uk_response_question_slot_deleted(survey_response_id,survey_question_id,sub_item_no,deleted)`；`idx_question(survey_question_id)` |
| `sts_survey_answer_option`  | B  | `answer_option_id bigint`，`survey_answer_id bigint`，`survey_option_id bigint`，`sort_order int null`                                                                                                                                                                                                                                                                                                                                                                                                                                                          | `uk_answer_option(survey_answer_id,survey_option_id)`；`idx_option_answer(survey_option_id,survey_answer_id)`                      |
| `sts_survey_answer_asset`   | B  | `answer_asset_id bigint`，`survey_answer_id bigint`，`file_info_id bigint`，`sort_order int`                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | `uk_answer_file(survey_answer_id,file_info_id)`                                                                                   |

问卷“匿名”只影响展示和统计脱敏，内部仍保存 `user_id` 以防重复提交和满足审计；任何面向管理员的查询不得在匿名问卷中返回用户身份。
问卷结构化表表示当前工作草稿。每次正式发布先生成不可变 `sts_survey_version` 完整快照，再创建独立的 `sts_survey_instance`
投放实例；同一版本可以面向不同业务来源、受众和时间窗口重复投放。发布服务必须以
`business_type=SURVEY_INSTANCE, business_id=survey_instance_id` 把本次参与范围解析/复制成 `purpose=PARTICIPANT`、
`audience_kind=BOUND`、`resolve_mode=FROZEN` 的专用范围，把结果可见范围解析/复制成同样冻结的 `purpose=VISIBILITY`
范围，再保存到实例；实例不得直接引用可变 `DYNAMIC` 范围。参与人、答卷和统计都必须固定到投放实例，唯一性按“投放实例 +
用户”控制。问卷定义的匿名设置是默认值，投放实例保存本次匿名策略快照；发布后的结构编辑和范围变化只影响下一版本或新投放。

## 16. 综合评价与专家查课（`sts_evaluation_*`）

| 表                                   | 类别 | 业务字段                                                                                                                                                                                                                                                                                                                                                                      | 关键约束与索引                                                                                                              |
|-------------------------------------|----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| `sts_evaluation_form`               | A  | `evaluation_form_id bigint`，`organization_id bigint`，`form_code varchar(64)`，`form_name varchar(100)`，`form_type varchar(24)`（COMPOSITE/EXPERT_INSPECTION），`current_version_no int`，`status tinyint`，`description varchar(1000)`                                                                                                                                          | `uk_org_code_deleted(organization_id,form_code,deleted)`；`idx_org_type(organization_id,form_type,status)`            |
| `sts_evaluation_form_version`       | C  | `form_version_id bigint`，`evaluation_form_id bigint`，`version_no int`，`total_score decimal(10,2)`，`published_status varchar(16)`，`published_by bigint null`，`published_time datetime null`，`version_note varchar(500) null`                                                                                                                                               | `uk_form_version(evaluation_form_id,version_no)`                                                                     |
| `sts_evaluation_item`               | C  | `evaluation_item_id bigint`，`form_version_id bigint`，`item_code varchar(64)`，`item_name varchar(200)`，`item_type varchar(16)`（SCORE/TEXT/CHOICE），`max_score decimal(10,2) null`，`weight decimal(7,4) null`，`sort_order int`，`is_required tinyint`                                                                                                                         | `uk_version_code(form_version_id,item_code)`；`idx_version_sort(form_version_id,sort_order)`                          |
| `sts_evaluation_score_rule`         | C  | `score_rule_id bigint`，`form_version_id bigint`，`evaluation_item_id bigint null`，`calculation_type varchar(16)`（WEIGHT/ADJUSTMENT），`source_type varchar(24)`（COURSE/EXAM/OFFLINE/REWARD_PENALTY/MANUAL），`score_change decimal(10,2) null`，`weight decimal(7,4) null`，`sort_order int`，`rule_description varchar(1000)`                                                    | `idx_version_source(form_version_id,calculation_type,source_type,sort_order)`                                        |
| `sts_evaluation_record`             | A  | `evaluation_record_id bigint`，`organization_id bigint`，`form_version_id bigint`，`target_type varchar(24)`（PARTICIPANT/COURSE/TRAINING_PLAN/ORGANIZATION），`target_id bigint`，`evaluator_user_id bigint null`，`total_score decimal(10,2) null`，`record_status varchar(16)`（DRAFT/SUBMITTED/CALCULATED），`submitted_time datetime null`，`summary longtext null`               | `idx_target(target_type,target_id,record_status)`；`idx_evaluator_time(evaluator_user_id,submitted_time)`             |
| `sts_evaluation_record_item`        | A  | `record_item_id bigint`，`evaluation_record_id bigint`，`evaluation_item_id bigint`，`score decimal(10,2) null`，`text_value longtext null`，`option_value varchar(200) null`，`calculation_source_json json null`                                                                                                                                                              | `uk_record_item_deleted(evaluation_record_id,evaluation_item_id,deleted)`；`idx_item_score(evaluation_item_id,score)` |
| `sts_evaluation_calculation_run`    | A  | `calculation_run_id bigint`，`organization_id bigint`，`training_plan_id bigint`，`training_participant_id bigint`，`form_version_id bigint`，`run_no int`，`supersedes_run_id bigint null`，`calculation_status varchar(16)`（CALCULATED/CONFIRMED/SUPERSEDED），`official_marker tinyint null`，课程/考试/线下/调整/综合成绩，规则快照与哈希，计算及确认人和时间                                                 | `uk_participant_form_run_deleted`；`uk_participant_official_deleted` 保证一个参训人跨表单版本最多只有一个正式批次；计划状态和上代批次索引               |
| `sts_evaluation_calculation_source` | C  | `calculation_source_id bigint`，`calculation_run_id bigint`，`source_type varchar(24)`，`source_record_type varchar(32)`，`source_record_id bigint`，`plan_content_id bigint`，`source_score decimal(10,2)`，`normalized_score decimal(10,2) null`，`weight decimal(7,4) null`，`aggregation_mode varchar(24) null`，`intermediate_score decimal(10,2)`，`source_snapshot_json json` | `uk_run_source(calculation_run_id,source_record_type,source_record_id,plan_content_id)`；来源记录和批次类型索引                  |

专家查课使用 `form_type=EXPERT_INSPECTION`，`target_type=COURSE` 或 `TRAINING_PLAN`；综合素质表使用 `COMPOSITE` 并以参训人员为目标。

综合成绩采用“计划内容聚合 + 来源类型权重”的基础设计：

- `sts_training_plan_content` 对每个计分课程/考试分别保存 `is_scored`、`score_weight` 和 `score_aggregation_mode`
  ；非课程/考试内容本期不得启用 `is_scored`。
- 同一培训计划内同类型计分内容必须使用一致聚合方式。本期只支持 `AVERAGE` 和 `WEIGHTED_AVERAGE`：前者等权平均，后者要求每项权重大于
  0 且同类型权重合计为 1。
- 课程内容读取对应 `sts_course_enrollment.final_score`，考试内容读取对应 `sts_exam_candidate.final_score`
  ；补考采用考试策略已确定的正式最终成绩，不在综合成绩服务中再次选择考试尝试。聚合前统一换算为百分制，考试按正式得分除以对应试卷总分换算。
- 只有正式课程/考试成绩才参与聚合；任一必需计分内容尚无正式成绩时，综合成绩保持未计算或不完整，不自动按 0 分处理。
- 聚合后得到 `course_score`、`exam_score`。`calculation_type=WEIGHT` 再配置课程、考试、线下成绩等来源类型对
  `composite_score` 的基础权重；`calculation_type=ADJUSTMENT` 配置奖惩或人工调整的基础加减分。
- 表单版本发布前由应用校验：来源类型权重规则必须填写 `weight` 且启用权重合计为 1，加减分规则必须填写 `score_change`
  ，两类规则不得混用无关字段。
- 培训计划通过 `sts_training_plan_evaluation_form` 绑定固定表单版本；计划发布后计分内容、权重和聚合方式锁定。每次计算创建不可变
  `sts_evaluation_calculation_run`，规则快照及哈希固定本次算法输入；课程报名、考试人员、线下成绩和奖惩等每项贡献写入
  `sts_evaluation_calculation_source`，保留来源分数、标准化结果、权重、聚合方式和中间值。
- 只有 `CONFIRMED` 且 `official_marker=1` 的批次是正式结果。重算必须新增批次，在同一事务中把旧正式批次改为 `SUPERSEDED`
  并清空其标记，再确认新批次；批次一经写入，规则、来源、分项和结果字段不得修改，只允许上述确认/替代状态迁移，已确认或已替代批次不得逻辑删除。
  `sts_training_participant.official_calculation_run_id` 指向正式批次，汇总成绩只是查询投影，不是唯一审计真相源。
- 本期不支持缺勤直接不合格、单项不合格否决、封顶保底、条件组合或自定义公式表达式。

上述高级能力明确作为后续升级项，启用前必须创建新的 OpenSpec Change 扩展规则模型和计算服务。本期不得把可执行脚本、SQL
或任意公式字符串保存到评分规则表。

## 17. 分析与培训档案（`sts_archive_*`）

### 17.1 统计真相源

- 考试成绩列表和考情分析来源：`sts_exam_candidate`、`sts_exam_attempt`、`sts_exam_attempt_question`、`sts_exam_answer`
  和防作弊事件流水。
- 课程统计来源：`sts_course_enrollment`、`sts_course_item_progress`、`sts_course_study_session`。
- 培训统计来源：`sts_training_participant`、阶段内容、签到、线下成绩、课程结果和考试结果。
- 平台、操作系统、浏览器统计来源于考试尝试；错题 TOP5 来源于首次尝试题目和答案快照。
- 在口径未确认前不创建 `*_statistics` 汇总真相表。数据量证明需要后，可通过独立 OpenSpec 增加物化汇总或离线数仓。

### 17.2 归档表

| 表                         | 类别 | 业务字段                                                                                                                                                                                                                                                                                                                                                                                                                            | 关键约束与索引                                                                                                                                                                      |
|---------------------------|----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sts_archive_training`    | A  | `training_archive_id bigint`，`organization_id bigint`，`archive_code varchar(64)`，`training_plan_id bigint`，`current_version_no int`，`archive_status varchar(16)`（DRAFT/SEALED/SUPERSEDED），`sealed_by bigint null`，`sealed_time datetime null`，`status tinyint`                                                                                                                                                                  | `uk_org_archive_code_deleted(organization_id,archive_code,deleted)`；`uk_plan_deleted(training_plan_id,deleted)`；`idx_org_status(organization_id,archive_status)`             |
| `sts_archive_version`     | C  | `archive_version_id bigint`，`training_archive_id bigint`，`version_no int`，`plan_snapshot_json json`，`organization_snapshot_json json`，`summary_snapshot_json json`，`snapshot_hash varchar(128)`，`generated_time datetime`，`generated_by bigint`，`version_reason varchar(500)`                                                                                                                                                   | `uk_archive_version(training_archive_id,version_no)`；`idx_generated_time(generated_time)`                                                                                    |
| `sts_archive_participant` | C  | `archive_participant_id bigint`，`archive_version_id bigint`，`training_participant_id bigint`，`user_id bigint`，`participant_org_id bigint`，`member_snapshot_json json`，`attendance_snapshot_json json`，`learning_snapshot_json json`，`exam_snapshot_json json`，`evaluation_snapshot_json json`，`reward_penalty_snapshot_json json`，`certificate_snapshot_json json`，`final_score decimal(10,2) null`，`result_status varchar(16)` | `uk_version_participant(archive_version_id,training_participant_id)`；`idx_user_version(user_id,archive_version_id)`；`idx_org_version(participant_org_id,archive_version_id)` |
| `sts_archive_asset`       | C  | `archive_asset_id bigint`，`archive_version_id bigint`，`source_attachment_id bigint null`，`asset_type varchar(24)`，`file_info_id bigint`，`file_name_snapshot varchar(255)`，`checksum varchar(128) null`，`sort_order int`                                                                                                                                                                                                         | `uk_version_file(archive_version_id,file_info_id)`；`idx_version_type(archive_version_id,asset_type)`                                                                         |
| `sts_archive_export_job`  | A  | `archive_export_job_id bigint`，`organization_id bigint`，`archive_version_id bigint null`，`export_scope varchar(16)`（PERSON/ORGANIZATION/PERIOD），`scope_id bigint`，`export_format varchar(16)`，`job_status varchar(16)`，`result_file_id bigint null`，`failure_reason varchar(1000) null`，`requested_by bigint`，`finished_time datetime null`                                                                                     | `idx_requester_time(requested_by,create_time)`；`idx_status_time(job_status,create_time)`                                                                                     |

个人档案通过 `archive_participant.user_id` 聚合，企业/机构档案通过 `participant_org_id` 聚合，一期一档直接展示一个
`archive_version`。
“更新档案”必须生成新版本并保留旧版本，不覆盖已封存快照。JSON 在这里用于冻结跨模块历史展示，不作为日常查询与业务规则真相源。

## 18. 园区监管与预警模块（`sts_regulation_*`）

| 表                                  | 类别 | 业务字段                                                                                                                                                                                                                                                                                                                                                                                                                                        | 关键约束与索引                                                                                                   |
|------------------------------------|----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `sts_regulation_warning_rule`      | A  | `warning_rule_id bigint`，`organization_id bigint`，`rule_code varchar(64)`，`rule_name varchar(100)`，`warning_type varchar(32)`，`training_type_id bigint null`，`warning_level varchar(16)`，`valid_from datetime null`，`valid_to datetime null`，`status tinyint`，`description varchar(1000)`                                                                                                                                                   | `uk_org_code_deleted(organization_id,rule_code,deleted)`；`idx_type_status(warning_type,status)`           |
| `sts_regulation_warning_condition` | A  | `warning_condition_id bigint`，`warning_rule_id bigint`，`metric_code varchar(64)`，`operator varchar(16)`（LT/LE/EQ/GE/GT），`threshold_value decimal(12,4)`，`unit varchar(16) null`，`sort_order int`，`status tinyint`                                                                                                                                                                                                                           | `uk_rule_metric_deleted(warning_rule_id,metric_code,deleted)`；`idx_rule_sort(warning_rule_id,sort_order)` |
| `sts_regulation_warning`           | A  | `warning_id bigint`，`organization_id bigint`，`warning_rule_id bigint`，`regulated_org_id bigint`，`training_plan_id bigint null`，`detection_cycle varchar(64)`，`dedup_key varchar(128)`，`warning_level varchar(16)`，`warning_title varchar(200)`，`warning_status varchar(16)`（OPEN/VIEWED/PROCESSING/CLOSED/IGNORED），`detected_time datetime`，`first_viewed_time datetime null`，`closed_time datetime null`，`current_assignee_id bigint null` | `uk_org_dedup_deleted(organization_id,dedup_key,deleted)`；监管机构状态、规则时间和当前处理人索引                             |
| `sts_regulation_warning_evidence`  | C  | `warning_evidence_id bigint`，`warning_id bigint`，`evidence_key varchar(128)`，`metric_code varchar(64)`，`actual_value decimal(12,4) null`，`threshold_value decimal(12,4) null`，`unit varchar(16) null`，`source_type varchar(24)`，`source_id bigint`，`evidence_snapshot_json json null`                                                                                                                                                       | `uk_warning_evidence(warning_id,evidence_key)`；`idx_warning_metric`；`idx_source`                          |
| `sts_regulation_warning_action`    | C  | `warning_action_id bigint`，`warning_id bigint`，`action_type varchar(16)`（VIEW/ASSIGN/COMMENT/CLOSE/IGNORE/REOPEN），`action_by bigint`，`action_time datetime`，`before_status varchar(16) null`，`after_status varchar(16) null`，`action_content varchar(2000) null`                                                                                                                                                                            | `idx_warning_time(warning_id,action_time)`；`idx_actor_time(action_by,action_time)`                        |

园区数据总览和企业学习情况统计均从机构关系、培训、课程、考试和归档事实聚合，不另建重复明细表。
预警规则当前只设计透明的指标比较，不允许保存可执行脚本。检测服务必须按确定性规则生成 `detection_cycle` 和 `dedup_key`
（至少覆盖规则、被监管机构、可选培训计划及检测周期），并以唯一键幂等写入；证据按指标、来源记录和来源事实版本生成 `evidence_key`
，同一扫描重试不得重复追加主单或相同证据，新来源事实可追加新证据。跨周期是否延续或新建由后续确认的预警口径决定。预警等级、通知、处置
SLA 和关闭权限仍为暂定。

## 19. 关键关系与基数

| 起点         | 关系     | 终点            | 基数与约束                                           |
|------------|--------|---------------|-------------------------------------------------|
| 机构         | 包含     | 部门、成员、业务资源    | 1:N；所有根资源必须有机构                                  |
| 机构         | 监管     | 机构            | M:N；由 `regulatory_relation` 表达有效期               |
| 课程         | 包含     | 发布版本、章节、课程项   | 1:N；发布版本不可变，章节树内排序唯一由应用保证                       |
| 课程项        | 编排     | 知识点、题库、直播     | M:N/1:1/1:1；题库型内容项的题库必须与 `BANK_PRACTICE` 考试来源一致 |
| 课程         | 发布为    | 开课            | 1:N；开课固定绑定一个课程发布版本                              |
| 开课         | 分配给    | 学员报名          | 1:N；一个开课同一用户最多一条有效报名                            |
| 题库         | 包含     | 题目            | 1:N；题目有多个不可变版本                                  |
| 题目         | 关联     | 知识点           | M:N；用于抽题和错题分析                                   |
| 试卷         | 包含     | 试卷版本          | 1:N；考试绑定具体版本                                    |
| 考试         | 分配给    | 考试人员          | 1:N；一个考试在同一业务上下文中同一用户最多一条有效人员记录，分配来源只作追溯        |
| 考试人员       | 产生     | 考试尝试          | 1:N；首次与补考均独立                                    |
| 考试尝试       | 冻结     | 题目/选项/答案      | 1:N；交卷后学员答案不可改                                  |
| 培训类型/标准/模板 | 实例化    | 培训计划          | 1:N；同机构直接引用，跨机构须授权并冻结来源快照，复制后不回写                |
| 培训计划       | 包含     | 阶段、内容、参训人员    | 1:N；计划结束后可归档                                    |
| 培训计划内容     | 生成     | 课程开课、考试、线下场次  | 1:N；执行实例直接保存 `plan_content_id`                  |
| 参训人员       | 参与     | 课程报名、考试人员     | 1:N；执行人员直接保存 `training_participant_id`          |
| 培训计划       | 使用     | 评价表单版本        | M:N；通过用途区分综合、专家、人员和课程质量评价                       |
| 课程/考试/培训   | 满足规则生成 | 证书            | 1:N；幂等唯一键防重复签发                                  |
| 证书模板       | 包含     | 模板版本、模板元素     | 1:N；元素和已签发证书绑定固定版本                              |
| 问卷         | 产生     | 发布版本、投放实例     | 1:N；同一版本可重复投放，不同实例独立控制范围和时间                     |
| 问卷投放实例     | 产生     | 参与人、答卷、答案     | 1:N；参与人和答卷固定实例与版本，匿名只影响展示                       |
| 参训人员       | 产生     | 综合成绩计算批次及来源明细 | 1:N；每次重算追加批次，跨表单版本最多一个正式批次                      |
| 培训计划       | 封存为    | 培训档案版本        | 1:N；旧版本不可覆盖                                     |
| 监管规则       | 触发     | 预警及证据         | 1:N；处置只追加流水                                     |

## 20. API 与权限设计边界

本 Change 不新增 API。每个实施 Task 必须先在 `docs/api/` 建立对应 OpenAPI/JSON Contract，包含 URL、Method、请求、响应、错误码、权限和示例，
再实施前后端。不得根据本表设计让前端自行推导接口。

权限校验顺序为：

1. 通过 `us_sys_user` / `us_sys_auth_identity` 校验技术账号已经登录且可用。
2. 通过 `sts_org_member` 校验用户在当前 `organization_id` 下具有有效业务成员身份。
3. 通过 `us_sys_user_role`、`us_sys_role`、`us_sys_role_menu` 和 `us_sys_menu.perms` 校验当前机构内的业务功能权限。
4. 通过 `us_sys_role.data_scope` 与可选的 `us_sys_role_dept` 计算机构、部门或本人数据范围，并把范围条件加入业务查询。
5. 管理员、老师和学生均不因角色名称或 `role_level` 自动获得数据；老师任务、学生本人数据和监管关系必须由明确业务关系或数据范围决定。
6. 对单个课程、题库、试卷、考试、培训类型、培训标准和培训模板等资源叠加创建者或 `sts_common_resource_admin_permission`
   校验；跨机构读取/使用叠加 `sts_common_resource_org_grant`
   的级别和有效期。创建计划等写操作按“当前操作人的机构和权限”鉴权，来源资源所属机构仅用于校验授权及冻结快照，不能替代操作者机构。
7. 学员侧继续校验 `audience_member`、报名、考试人员或参训人员事实，避免仅凭角色读取他人记录。

当前实现到部门节点的数据范围，不实现按业务字段、标签条件、SQL 或任意表达式配置权限，也不硬编码角色 ID；`role_level`
只用于角色分类，不直接授予权限。
除账号编码严格等于 `superAdmin` 的硬编码特权外，不按“企业创建者”或任何角色名称隐式放权。普通账号不支持跨机构任职或切换机构角色；监管人员只能通过明确的监管关系访问被监管机构数据。

## 21. Migration 与实现顺序（确认后）

1. 系统组织权限与公共支撑：先回填并扩展系统部门/角色，建立 `us_sys_role_dept`、机构成员和参与范围，再实现统一功能权限与数据权限组件。
2. 素材与课程：先素材/转码，再课程定义、开课和学习事实。
3. 题库、试卷与考试：先版本化内容，再考试配置、答卷快照和批阅。
4. 培训：接入课程/考试已发布资源，再实现线下事实；本期不接入仿真。
5. 证书、问卷和评价：只绑定已存在的业务结果。
6. 归档和监管：最后基于稳定事实生成归档版本、统计查询和预警。

每个业务 Task 必须同时完成：`database/migration`、Entity/Mapper、API Contract、Mock/调用方、自动化测试、Review、QA 和 Retro。
正式 migration 命名 `V<yyyyMMddHHmmss>__<lower_snake_case_description>.sql`，每个 migration 只表达一个可说明变更。

## 22. 数据质量与测试设计

- DDL 静态检查：所有表单列自增主键、A/B/C 审计列正确、业务唯一键和 `deleted` 组合正确、字符集与引擎正确。
- Migration 测试：Testcontainers MySQL 空库按序执行；验证回滚方案、重复执行失败可诊断、索引存在。
- 唯一性测试：机构业务码、同一考试业务上下文的候选人、同一问卷投放实例参与人、同一正式成绩计算批次、同一周期预警、同一开课报名、版本号和证书幂等键等并发写入只能成功一次；不同考试上下文、不同问卷投放实例必须允许同一用户参与。
- 版本测试：编辑题目/试卷创建新版本，旧考试答卷内容和成绩保持不变。
- 状态机测试：课程、考试、培训、问卷和归档只允许合法状态转换；发布/交卷/批阅/签发具备幂等性。
- 数据隔离测试：不同机构相同业务码按约束处理；无授权不能跨机构查询；企业或培训机构引用园区培训类型/标准/模板时必须验证监管关系和资源授权，授权到期后不能新建引用，但已发布计划仍按冻结快照运行和归档。
- 数据权限测试：六类 `data_scope`、自定义部门是否包含下级、多角色合并、部门移动和用户调岗均覆盖；列表、详情、统计、导出和异步任务结果一致。
- 越权测试：构造跨部门 ID、批量 ID、导出和统计请求，确认不能绕过数据范围；权限缓存变更后及时失效。
- 历史追溯测试：候选人档案、无试卷版本的题库练习/线下考试执行参数、练习重置、综合成绩重算、补考、改分、证书撤销、档案更新和预警处置均保留旧记录。
- 统计对账：列表人数、参与人数、答卷数、首次考试错题和最终成绩从同一事实集计算，避免页面口径各自实现。

## 23. 性能与容量策略

- 高频索引优先覆盖“机构 + 状态 + 时间”“父 ID + 状态/排序”“用户 + 状态/时间”，避免只对低选择性布尔字段建单列索引。
- `study_session`、`exam_attempt_question`、`exam_answer`、`anticheat_event`、`message_recipient` 是预期大表；
  首期先使用组合索引，不在缺少数据量和保留期时提前分区。
- 列表统计禁止逐行回表；通过批量聚合查询或后续经 OpenSpec 确认的物化汇总解决，不能在主表维护易失真的冗余计数。
- 文件内容不存数据库，只保存 `us_sys_file_info` / `sts_asset_asset` 引用和必要校验哈希。
- 数据保留、冷归档、分区键和脱敏策略涉及合规，需独立确认后实施。

## 24. 需求追踪

| FR 分组              | 落表模块                | 说明                                             |
|--------------------|---------------------|------------------------------------------------|
| FR-ENT-CRS-001~010 | 素材、课程、公共参与范围        | 覆盖资源、转码、非仿真内容编排、开课、学习与直播链接；CRS-005～006 的仿真部分延期 |
| FR-ENT-EXM-001~008 | 题库、试卷、考试            | 覆盖版本、组卷、人员、作答、批阅和成绩                            |
| FR-ENT-EXM-009~016 | 本期不落表               | 仿真协议和数据模型延期到独立 OpenSpec Change                 |
| FR-ENT-TRN-001~007 | 培训                  | 覆盖类型/标准/非仿真模板/计划/线下/参与/结果；TRN-002 的仿真部分延期      |
| FR-ENT-AUX-001~005 | 证书、问卷               | 覆盖模板、签发、问卷设计与反馈                                |
| FR-ENT-OPS-001~010 | 考试/课程/培训事实、评价、归档    | 统计来自事实；个人/机构/期次共用归档                            |
| FR-ENT-SYS-001~005 | 系统技术账号、组织与权限        | 工作台为查询投影；部门、角色、功能权限和数据范围统一使用扩展后的系统 RBAC        |
| FR-ENT-SYS-006     | 本期不落表               | 人脸识别能力已确认延期，本期不设计、不实施、不验收                      |
| FR-LRN-001~013     | 现有身份、课程、考试、培训、证书、消息 | 学员状态由分配和执行事实投影                                 |
| FR-REG-001~007     | 组织监管关系、培训标准、归档、预警   | 数据大屏与统计不复制明细真相源                                |

## 25. 必须确认的业务/架构决策

本次设计复审已经确认以下边界：考试候选人按业务上下文唯一且分配来源仅作追溯；候选人保存人员档案快照；题库练习和线下考试允许无固定试卷版本，但尝试必须冻结执行参数和来源；课程题库项通过题库练习考试实例执行；练习重置限定到考试人员；问卷定义、版本和投放实例分离；企业或培训机构通过监管关系和显式资源授权引用园区培训类型/标准/模板并在计划冻结快照；综合成绩按不可变计算批次和来源明细留痕；预警按检测周期确定性去重。这些边界不再列为待确认项。

以下任何一项进入对应 Task 前仍未确认时，必须暂停，不能由 AI 自行选择：

1. **机构与权限**：已确认培训组织、角色和功能权限统一使用系统部门/RBAC，并补充六类角色数据范围；同一用户多个角色的数据范围取授予当前功能权限之角色的并集；账号编码严格等于
   `superAdmin` 时硬编码绕过权限，其他角色按自身配置执行；一个普通账号只属于一个主要机构和主部门，不支持跨机构任职。平台公共资源和跨机构授权级别仍待确认。
2. **课程目录**：无限级目录是否最终保留，以及单/多章节切换规则；已发布课程内容变更已确定通过新版本处理，不改写既有开课和学习记录。
3. **学习完成**：文档页时长、音视频完成百分比、离开页面计时、必修考试/练习、课程过期和完成重算口径。
4. **考试类型**：线上/线下/专项练习/题库练习的最终枚举，以及“练习模式仅错题”等限制；题库练习允许无固定试卷版本并在尝试中冻结执行参数已经确认。
5. **试卷与考试冲突**：时长、及格线、题目顺序、多选评分到底由试卷固定还是允许考试覆盖；无论取值来源如何，开始作答时冻结到尝试已经确认。
6. **补考与成绩**：可补考次数、范围、最终成绩取最高或最后、成绩发布前后的可见性和改分权限。
7. **外部人员**：链接/二维码临时注册字段、账号归属、数据保留与隐私授权。
8. **培训与签到**：培训/阶段状态机、签到方式和有效性、扫码加入、线下成绩导入冲突；综合成绩已确认按计划课程/考试内容配置权重，并在同类型内采用平均或加权平均，缺勤否决、封顶保底和复杂公式待后续升级。
9. **证书**：变量白名单、编号、签章、防伪、有效期与纸质领取规则仍待确认；来源粒度（规则挂业务定义、证书挂执行实例）和撤销后重发（先逻辑删除旧证再人工重发）已按简单规则确认，见第
   14 节。
10. **统计与归档**：所有指标口径、归档时点、档案更新权限、保留期和下载范围。
11. **监管预警**：规则来源、等级、通知、处置 SLA、关闭/忽略权限和导出口径。
12. **数据库策略**：已确认采用逻辑外键并按机构查询入口建立组合索引；数据库时区、数据保留与大表分区阈值仍待确认。

## 26. 风险与取舍

- 全系统范围较大。本设计建立统一骨架，但实施仍必须按 `tasks.md` 的业务能力逐个确认和交付，不能一次生成全部 migration。
- 通用参与范围和资源授权减少了课程/考试/培训重复表，但采用 `business_type + business_id` 会失去物理外键；通过发布校验、逻辑索引和跨模块集成测试补偿。
- 考试人员、答卷、培训计划来源、综合成绩计算和归档保留快照或不可变明细会增加存储，但这是保证历史成绩、审计和监管可追溯的必要取舍。
- 不提前建立统计汇总表可避免口径未确认时产生多个真相源；高并发报表性能需在真实数据量下另行设计。
- 人脸和仿真已从本期表设计中移除；证书签章和监管处置仍只保留已确认字段，避免用任意 JSON 猜测核心业务规则。
