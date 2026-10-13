# Design：专家查课表最小闭环

## 类型与绑定边界

`COMPOSITE` 仅可使用 `usageType=COMPOSITE` 绑定，`EXPERT_INSPECTION` 仅可使用 `usageType=EXPERT_INSPECTION` 绑定；每种用途继续复用既有“每计划每用途一条有效绑定”的约束。其余用途不在本 Change 扩展。

专家检查版本仅可包含 `SCORE`、`TEXT`：分数项必须有正数满分，文本项不得有满分/权重；项目不允许评分规则；分数项满分总和必须精确为 100。发布后的版本为记录渲染与校验的唯一快照。

## 接口

### 提交专家查课记录

`POST /api/evaluation/expert-inspections/submit` 要求同时具备 `evaluation:record:add`、`evaluation:record:submit`。请求仅含 `trainingPlanId`、`planContentId`、`formVersionId`、可选 `summary` 和逐项目答案，不接收评价人。服务在单事务内创建 `targetType=TRAINING_PLAN_CONTENT`、状态为 `SUBMITTED` 的记录及明细，并由登录态写入评价人。

服务端依次校验：计划机构可见且状态 `RUNNING`；内容属于计划且为 `COURSE`；版本已发布、主体启用且类型为 `EXPERT_INSPECTION`；版本已按专家检查用途绑定计划；所有必填项目均有正确类型答案；分数在 `[0,maxScore]`；文本不传分数、分数不传文本；分数满分/实际计算均与 100 分规则一致。数据库唯一索引作为并发兜底，重复记录返回冲突。

### 计划内记录状态

`GET /api/training/plans/{trainingPlanId}/expert-inspections` 使用计划查看权限，返回当前计划全部 `COURSE` 内容及对应专家检查绑定版本的记录状态、记录 ID、版本、评价人、提交时间、总分和意见摘要。记录详情继续使用既有 `GET /api/evaluation/records/{id}`。

## PC 展示

- 综合评价保持现有来源卡与综合计算提示。
- 专家检查工作台使用“检查项目配置”，只显示项目名称、编码、类型、满分（仅分数项）、必填和排序；发布页显示总分 100 的只读校验；版本页显示“专家查课项目快照”。
- 计划评价步骤分离“综合评价”与“专家查课表”绑定区。专家绑定展示冻结摘要，不展示来源卡。
- 人员与执行步骤不常驻显示专家查课表。仅当计划为 `RUNNING`、已绑定专家检查版本且用户拥有计划查看权限时，“下一步操作”显示“专家查课”入口；点击后在弹窗读取当前计划课程及其提交状态。
- 弹窗内只有执行中且拥有既有评价记录新增、提交权限的用户可填写；已提交记录继续只读查看。

## 兼容与错误边界

已有综合表单、绑定与计算接口不改变。旧专家检查版本如含选择项、评分规则或满分不为 100，不能被新校验发布/绑定/提交流程使用；不自动修正历史数据。
