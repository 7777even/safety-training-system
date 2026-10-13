# Design：课程域测试覆盖补齐

## Context

现状盘点（实施前）：

**已有零 DB：** Category/Setting/Course/Chapter/Item/Publish/Offering/Enrollment/Learning/Asset/AssetReference 等 Service
单测；部分 Controller Web 切片。

**缺失零 DB：** `CourseDownloadAssetServiceImpl`、`CourseAssetReferenceServiceImpl`；Controller
切片缺口：Publish、Offering、Enrollment、Learning、DownloadAsset、AssetReference、Asset。

**已有带 DB IT：** CourseCategory/Setting/Course/Chapter MapperIT（唯一键 + 逻辑删除）。

**缺失带 DB IT：** Item/Live/Version/Download/Offering/Enrollment/StudySession/Evaluation/Asset 唯一键与关键链路；发布幂等、学习会话幂等可用
Mapper+轻量服务或纯 Mapper 验证约束。

## Goals / Non-Goals

**Goals:** 每个课程/素材核心 Service 有零 DB 单测；关键唯一键与幂等约束有 IT；Web 切片覆盖主要 Controller 成功/错误包装路径。

**Non-Goals:** 全链路 E2E 登录权限；前端；改业务代码（测出缺陷另提）。

## Decisions

### D1：零 DB 分层

- ServiceImplTest：纯 Mockito。
- ControllerWebSliceTest：standalone MockMvc + HttpApiExceptionHandler + ResultEasyTransAdvice（对齐现有切片）。

### D2：带 DB IT

- 继续 `AbstractMysqlIntegrationTest` + `CourseMapperItConfig`（或扩展 MapperScan 含 asset）。
- 优先测 **唯一索引 / 逻辑删除复用 / 1:1 约束**（与现有 IT 风格一致）。
- 对发布哈希幂等、会话 `client_trace_id` 幂等：可用 IT 插库后调 Mapper/薄服务验证。

### D3：Asset IT 配置

- 新增 `AssetMapperItConfig`：`@MapperScan` 含 `com.sts.asset.mapper`（及引用校验所需 course mapper 若测删除保护可仅测
  uk）。

### D4：对齐系统管理标准（Task 3）

系统管理标准层次：

1. MapperIT：insert 往返、like 过滤、分页拦截器、乐观锁（`SysUserMapperIT`）
2. LogicDeleteServiceIT：`BaseServiceImpl.removeById` → deleted=主键
3. *CrudPersistenceIT：真实 Service 真库 CRUD + 业务护栏（`MenuCrudPersistenceIT`）

课程域 Task 3 按上述三层补齐，不满足于「仅唯一键 MapperIT」。
