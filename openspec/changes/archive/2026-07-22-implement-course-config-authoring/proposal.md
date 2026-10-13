# Proposal：实施课程配置与课程编排（草稿态）能力

## 背景

数据模型 Change `2026-07-21-design-safety-training-mysql-schema` 已于 2026-07-21 通过两轮评审，课程模块 21 张表结构冻结。
`docs/requirement/course-module-task-plan.md` 将课程模块拆为 6 个步进，本 Change 承接其中**步骤 2（课程配置）和步骤
3（课程定义与内容编排·草稿态）**，
把已评审的表结构落为正式 migration、API Contract、Entity/Service 和前后端功能。

## 目标

- 交付课程分类树、机构级课程默认配置能力（② 层 3 张表）。
- 交付课程基本信息、无限级章节树、内容项编排、材料规则、可下载素材和直播场次配置能力（③ 层 9 张表），全部处于草稿态。
- 建立 FR-ENT-CRS-003/004/005（非仿真、非考试部分）/010（配置侧）到 API Contract、migration 和测试的追踪链路。

## 用户价值

企业/培训机构管理员可以维护课程分类和机构学习默认配置，创建课程、编排章节与内容、配置材料学习规则和直播场次，
为后续发布版本（步骤 4）和开课学习（步骤 5）提供完整的课程草稿。

## 功能范围

- 课程分类树的增删改查与排序（机构内同层同名唯一）。
- 机构统一课程设置（每机构一条：文档页时长、音视频完成比例、断点续播、防快进、离页暂停、转码通知等默认值）。
- 课程创建与基本信息维护（概况、教学目标、大纲、考核方式、封面、学分、积分模式、单/多章节模式）。
- 无限级章节树维护、排序与单/多章节切换。
- 内容项编排：本 Change 仅开放 `ASSET`（素材）与 `LIVE`（直播）两类；材料规则（学时模式、快进/倍速、水印、离页暂停）；课程可下载素材清单。
- 直播场次配置（名称、讲师、链接、起止时间；1:1 绑定直播内容项）。
- 课程与分类的多对多关联。

## 非目标范围

- 素材上传、转码与素材库管理（步骤 1，独立 Task；本 Change 仅逻辑引用 `sts_asset_asset`）。
- 课程发布版本、开课、报名、学习事实与课程评价（步骤 4/5）。
- `KNOWLEDGE_POINT`、`QUESTION_BANK`、`EXAM`、`PRACTICE` 内容项与视频弹题：依赖题库/考试模块，本 Change 建表但能力禁用（步骤 6
  回接）。
- 完整的机构成员、参与范围、角色数据范围（数据模型 Change Task 1）；本 Change 仅落实最小机构支撑（见 design 依赖边界）。
- 仿真、人脸相关能力（已确认延期）。

## 决策状态

- 已确认（2026-07-21，架构）：数据模型 Task 1 尚未实施，本 Change 采用"最小机构支撑"方案 A——随本 Change 建
  `sts_org_organization` 并受控初始化默认机构，完整机构/成员/数据范围能力仍归 Task 1。
- 已确认（2026-07-21，架构）：本项目不拆微服务；课程域作为 `com.sts.course.*` 独立顶级包（与 `com.sts.admin` 平级）并入
  `sts-service-admin` 同一进程同一 JAR，不新建 pom、启动类、端口或服务注册；`AdminApplication` 的 `@ComponentScan`/
  `@MapperScan` 及 `HttpApiExceptionHandler`/`ResultEasyTransAdvice` 的 `basePackages` 均已登记 `com.sts.course`。详见
  `docs/architecture/backend-module-boundary.md`。
- 待确认（产品，阻塞 Task 2）：课程目录无限级是否最终保留；单/多章节切换的具体规则（design.md §25.2）。
- 待确认（产品/架构，阻塞 Task 3 直播部分）：直播提供方、链接访问规则与有效性校验。
- 待确认（产品，不阻塞本 Change）：学习完成口径、退选规则等属于步骤 5 的确认项，本 Change 不涉及。
