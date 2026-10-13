# Retro：开课与考试各是一套核心能力（Task 1～4）

## 做得好的地方

- 发布规则写在对象字段上（开课是否已有版本、考试是否已有 `planContentId`），培训生成只作为调用方，避免再写一套状态机。
- 开课 5068 继续走「草稿保留 + 失败项带 offeringId」，与独立开课缺阅卷人同一码。
- 学员三个入口按来源查询，没有给核心发布打补丁。
- 管理列表把 `planContentId` 当字段而不是排除条件，独立对象与培训对象共用同一分页。

## 问题

- 先前战术修复把培训开课直接插入为已发布，5068 卡在报名前；设计上应卡在发布。
- 考试侧 `ensureTraining` 与 `refreshStandalone` 并行，培训 Adapter 自己 `updateById` 成 PUBLISHED。
- 零 DB 断言 `LambdaQueryWrapper.getSqlSegment()` 必须先 `TableInfoHelper.initTableInfo`，否则会报 lambda cache 缺失。

## 原因

- 培训生成曾被当成独立生命周期，而不是开课/考试能力的第二个调用方。

## 改进

- 跨域生成只调用目标域 Service 的 create/publish/人员接口；Adapter 不得旁路发布状态。
- 入口查询与对象列表分开写：管理列表是能力对象全集，学员入口才按来源过滤。
- 需要检查 wrapper SQL 片段时，沿用 `CourseLearnerReadingServiceImplTest` 的 TableInfo 初始化，而不是把过滤语义下放到 IT。
