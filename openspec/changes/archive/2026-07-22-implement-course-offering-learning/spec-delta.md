# Spec Delta：开课、报名与学习

## 新增

- 开课管理与发布，冻结 `course_version_id`。
- 指派/自助报名、选修退选、同开课同用户唯一。
- 学习会话幂等、进度汇总、完成规则（推荐默认 ALL_REQUIRED_ITEMS）。
- 完成后一报名一评价。
- 存在 DRAFT/PUBLISHED 开课时禁止课程 content-mode 切换。

## 推荐默认（已写入 design）

- 完成口径、退选规则、会话 end_reason、audience 暂不解析。
