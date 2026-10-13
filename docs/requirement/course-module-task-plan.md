# 课程模块实施任务清单（推荐步进）

编制日期：2026-07-21
依据：

- `openspec/changes/2026-07-21-design-safety-training-mysql-schema/`：数据模型设计（2026-07-21 两轮评审通过），课程模块共 21
  张表（`ddl/02_asset_course.sql`）。
- `openspec/changes/2026-07-21-plan-course-capability/tasks.md`：课程能力业务 Task 1～5（按 FR 拆分，早于表设计）。
- `business-development-roadmap.md`：阶段 1（课程资源与制作）、阶段 2（开课、学习与直播）。

本清单把已评审的表模型映射为可执行的实施步进，仅覆盖课程模块；考试、培训等模块按数据模型 Change 的 `tasks.md` 顺序另行编写。

> **同步说明（2026-09-20）**：本文是按 2026-07-21 表模型编写的实施清单，保留当时口径；此后已确认的变化以 OpenSpec 与
> init 快照为准，阅读第 1、2 节时请注意差异：
>
> - `2026-09-20-simplify-course-item-learning-rule` 删除 `sts_course_item_material_rule`，课程模块表数由 21 张降为 20 张；
    > `sts_course_setting` 仅保留为【暂时隐藏】历史资产，不作为当前页面、API 或业务规则依据。学习时长收敛到
    > `sts_course_item.minimum_study_seconds`（ASSET > 0，其他类型为 0），倍速与文档水印只由课程级设置控制；对应步骤 2 的
    > 「机构课程设置」与步骤 3 的「材料规则」已不再实施。
> - `2026-09-17-close-course-learning-progress-loop` 起，内容项完成判定统一按客户端上报的有效学习时长，`duration_mode` 不再参与判定。

## 1. 范围：课程模块 21 张表分层

| 层              | 表                                                                                                                                                                                                                                             | 职责                                  |
|----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------|
| ① 素材底座         | `sts_asset_asset`、`sts_asset_derivative`、`sts_asset_transcode_job`                                                                                                                                                                            | 素材库、派生文件、转码任务（FR-ENT-CRS-001/002）   |
| ② 课程配置         | `sts_course_category`、`sts_course_setting`、`sts_course_course_category`                                                                                                                                                                       | 分类树、机构级默认配置（学时/水印/防作弊/转码通知）         |
| ③ 课程定义与编排（草稿态） | `sts_course_course`、`sts_course_chapter`、`sts_course_item`、`sts_course_item_knowledge`、`sts_course_item_question_bank`、`sts_course_item_material_rule`、`sts_course_item_video_question`、`sts_course_download_asset`、`sts_course_live_session` | 课程、章节树、内容项、材料规则、视频弹题、可下载素材、直播场次     |
| ④ 发布与开课        | `sts_course_version`、`sts_course_offering`、`sts_course_enrollment`                                                                                                                                                                            | 不可变发布版本、开课（绑版本 + 具体报名人员 + 必修/选修）、报名 |
| ⑤ 学习事实         | `sts_course_item_progress`、`sts_course_study_session`、`sts_course_evaluation`                                                                                                                                                                 | 内容项进度、学习会话流水、课程评价                   |

## 2. 硬前置（开工前必须满足）

1. **机构、成员与公共业务支撑已交付**：课程处处引用 `organization_id` 和角色数据范围；选人只接受当前机构全员、部门或指定人员，最终保存具体报名或开放报名白名单。
2. **文件存储与转码服务方案确认**（路线图"不得跳过的依赖决策"第 1 条）：存储介质、转码供应商、预览能力、大文件限制。
3. 各步进的业务口径确认项见每步"前置确认"，来源为设计文档 `design.md` 第 25 节；未确认不得进入对应编码任务。

## 3. 推荐步进（6 步）

每步都是完整小闭环：API Contract（`docs/api/`）→ migration → Entity/Service → 前后端实现 → 契约/权限/集成测试。

### 步骤 1：素材与转码

- **表**：① 层 3 张
- **FR**：FR-ENT-CRS-001、FR-ENT-CRS-002
- **对应既有 Task**：plan-course-capability Task 1
- **内容**：批量上传/下载/删除、在线预览、转码状态机（UPLOADED→PROCESSING→SUCCEEDED/FAILED）、派生文件、转码列表与通知开关
- **关键验收**：转码中素材不可下载/编辑/用于课程；素材批量删除前经 `idx_asset` 反向引用检查拦截被课程、题目引用的素材
- **前置确认**：文件存储与转码方案（硬前置 2）；转码失败处理方式（需规 CRS-002 待确认项）
- **说明**：独立性最强、能提前消化外部供应商风险，放第一步

### 步骤 2：课程分类与机构课程设置

- **表**：② 层 3 张
- **FR**：FR-ENT-CRS-003 的分类部分；调研文档 1.1.4 转码通知、系统级学习默认值
- **对应既有 Task**：plan-course-capability Task 2 的一部分
- **内容**：分类树 CRUD、机构级默认配置（每机构一条）；顺带在课程域接通 `sts_common_resource_admin_permission` 资源管理员授权
- **关键验收**：分类同层同名唯一；默认配置被步骤 3 的材料规则正确继承

### 步骤 3：课程定义与内容编排（草稿态）

- **表**：③ 层 9 张
- **FR**：FR-ENT-CRS-003、004、005（非仿真、非考试部分）、010（直播配置侧）
- **对应既有 Task**：plan-course-capability Task 2、Task 5 的配置部分
- **内容**：课程基本信息、无限级章节树、内容项编排与排序、材料规则（学时/快进/水印/弹题）、可下载素材、直播场次配置。*
  *本步内容项只开放 `ASSET/KNOWLEDGE_POINT/LIVE` 三类**；`QUESTION_BANK/EXAM/PRACTICE` 在 API 枚举中预留但禁用（步骤 6 回接）
- **关键验收**：章节树与排序稳定；单/多章节切换只移动 `chapter_id` 不复制内容；只能引用转码成功的素材
- **前置确认**：课程目录无限级是否最终保留、单/多章节切换规则（design.md §25 第 2 条）

### 步骤 4：课程发布版本

- **表**：`sts_course_version` + `sts_course_course.current_version_id` 流转
- **FR**：支撑 CRS-006/008/009 的历史稳定性（评审决策：不可变发布版本）
- **对应既有 Task**：**plan-course-capability 现有 5 个 Task 未覆盖，需补充**
- **内容**：发布时事务内生成递增 `version_no` + 完整 `content_snapshot_json` + `snapshot_hash`，更新 `current_version_id`
  ；重复发布幂等；发布版本只读
- **关键验收**：发布后修改草稿再发布产生新版本，旧版本内容逐字节不变；并发/重复发布幂等（设计文档 §22 版本测试）
- **说明**：这是课程模块的质量关键——快照完整性决定所有历史回放，建议独立成步并写足测试

### 步骤 5：开课、报名与学习事实

- **表**：④ 层 `sts_course_offering`、`sts_course_enrollment` + ⑤ 层 3 张
- **FR**：FR-ENT-CRS-006（非仿真）、007、008、009；FR-LRN-003～007 课程部分
- **对应既有 Task**：plan-course-capability Task 3、Task 4
- **内容**：开课绑定发布版本、必修/选修（`course_type`）、具体人员指派或开放报名白名单、选课大厅、内容项进度、学习会话流水（按报名幂等令牌）、完成规则计算、课程评价、管理端学习详情
- **关键验收**：开课发布后 `course_version_id` 不可变；同开课同用户唯一报名；学习会话跨用户令牌互不干扰；数据范围（部门/本人）在列表、详情、统计、导出一致生效
- **前置确认**：学习完成口径——文档页时长、音视频完成比例、离开页面计时、课程过期与完成重算（design.md §25 第 3
  条）；选课开放条件与退选规则（FR-LRN-007，选修课）；学习详情展示字段（CRS-009）
- **说明**：本步是最大的一步，可再按"授权开课 → 学员报名与学习 → 详情与评价"切三个子批次交付

### 步骤 6：考试类内容项回接（依赖题库/考试模块）

- **表**：复用 `sts_course_item`、`sts_course_item_question_bank`（已建），打通 `sts_exam_candidate` 课程上下文
- **FR**：FR-ENT-CRS-005/006 的题库、测验部分
- **对应既有 Task**：**plan-course-capability 现有 Task 未覆盖**；对应数据模型 Change tasks.md Task 2 的考试内容项验收标准
- **前置**：数据模型 Change 的 Task 3（题库/试卷）、Task 4（考试执行链）已交付
- **内容**：放开 `QUESTION_BANK/EXAM/PRACTICE` 内容项类型；题库项绑定唯一题库并关联同源 `BANK_PRACTICE` 考试；按"课程报名 +
  内容项 → `course_item_progress` → `exam_candidate`（`context_type=COURSE`）"链路幂等创建考试人员；进度表回读次数/最佳分/最终成绩
- **关键验收**：同一考试用于不同开课/内容项时执行记录互不覆盖；课程报告只统计本课程上下文成绩；练习重置只作用于当前考试人员

## 4. 与既有文档的差异说明

- plan-course-capability 的 Task 1～5 制定于表设计之前，缺少**步骤 4（发布版本）**和**步骤 6（考试内容项回接）**两个环节；建议在该
  Change 确认时按本清单补充或直接采用本清单作为 Task 细化基线。
- 路线图阶段 1 ≈ 步骤 1～4，阶段 2 ≈ 步骤 5（含直播学习入口），阶段 3（理论考试）完成后执行步骤 6。
- 直播在表设计中属于课程内容项（`sts_course_live_session` 1:1 绑定内容项，培训直投直播本期不做）；plan-course-capability
  Task 5 的直播实施应并入步骤 3（配置）与步骤 5（学员入口与学习记录），不再单列。

## 5. 待确认项汇总（进入对应步骤前关闭）

| 确认项                         | 阻塞步骤 | 来源                            |
|-----------------------------|------|-------------------------------|
| 文件存储、转码服务、预览与大文件限制          | 1    | 路线图依赖决策                       |
| 转码失败状态及处理方式                 | 1    | FR-ENT-CRS-002                |
| 课程目录无限级保留、单/多章节切换规则         | 3    | design.md §25.2               |
| 学习完成口径（页时长/完成比例/离页计时/过期/重算） | 5    | design.md §25.3               |
| 选课开放条件与选修退选规则               | 5    | FR-LRN-007、R2-10 决策           |
| 学习详情展示字段                    | 5    | FR-ENT-CRS-009                |
| 直播提供方、链接访问与有效时长记录规则         | 3、5  | plan-course-capability Task 5 |
