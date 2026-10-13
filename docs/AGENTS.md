# 项目文档知识库规范

`docs/` 是长期项目知识库，记录跨版本有效的共识：

- `architecture/`：系统架构、模块边界、技术决策和演进；后端横切（鉴权、API 标准、缓存、审计等）见同目录 `backend-*`
  与专项文档。考试补考前后端协作说明见 `architecture/exam-retake-collaboration.md`（契约仍以 `api/exam.openapi.json` 为准）。
  开课与考试各是一套能力、独立管理与培训生成两处调用，见
  `architecture/training-course-exam-capability.md`（实施以已确认 OpenSpec 为准）。
  培训类型/标准/模板/计划每一层只做一层的事，见
  `architecture/training-layer-responsibility.md`（实施以已确认 OpenSpec 为准）。
  封面写入、按业务对象受控读取、学员列表 `coverUrl` 的设计见
  `architecture/cover-access-protocol.md`。
- `api/`：机器可读的 JSON API 契约：`system-admin.openapi.json`（系统管理）、`course.openapi.json`（课程域）、
  `asset.openapi.json`（素材域）、`org.openapi.json`（机构成员/标签/岗位）、`person-selection.openapi.json`（统一人员选择）、
  `question.openapi.json`（题库/知识点/导入）、
  `paper.openapi.json`（试卷组卷）、`exam.openapi.json`（考试作答/批阅/成绩）、
  `training.openapi.json`（培训类型/标准/模板/计划/执行）、
  `evaluation-form.openapi.json`（评价表单不可变版本）、
  `survey.openapi.json`（问卷）、`evaluation-result.openapi.json`（评价记录/综合计算）、
  `archive.openapi.json`（培训档案）、`certificate.openapi.json`（证书）、
  `ops.openapi.json`（运营工作台/统计/导出）、`regulation.openapi.json`（园区监管预警）。编写规范见
  `architecture/openapi-contract-writing-guide.md`；运行期 Result/分页见 `architecture/api-standard.md`。其他接口协作约定与
  技术设计归档至 `architecture/`。
- `requirement/`：已确认业务背景、范围、角色和需求；系统管理能力见 `system-admin-module.md`。
- `database/`：概念模型与 DDL/逻辑删除等持久化规约。
- `deployment/`：环境准备、部署、配置和运维说明；系统初始化见 `admin-init.md`。

QA、发布检查、复盘不放在 `docs/`，分别放在 `engineering/qa/`、`engineering/ship/`、`engineering/retro/`。未确定事项必须标“暂定”；架构或接口变更须与 OpenSpec 同步。

---

功能完成后：

必须检查：

API是否更新

架构是否变化

部署是否变化

---

短期开发记录：

放：

engineering

不要放：

docs
