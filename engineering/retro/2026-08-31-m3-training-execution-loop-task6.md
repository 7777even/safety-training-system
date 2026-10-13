# Retro：M3 培训执行闭环 Task 6

## 做得好的地方

- 用失败测试先钉住 PUBLISHED 直接 end 会走进同步/冻结，避免只改文案、状态机仍放行。
- 培训标准学时和个人结论拆开测：发布看计划 `requiredHours`，结束看必修终态，含考试的混合计划不再被学时口径误杀。
- 混合 IT 走真实发布、生成、start、同步和 end，而不是再堆 Mapper 断言；第一次打通 MySQL JSON 列对快照 hash 的改写。

## 问题

- Task 3～5 的零 DB 与单表 IT 全绿，但 `end` 仍接受 PUBLISHED，且没有一条四类内容真库纵向用例。
- `policy_snapshot_json` / `resource_snapshot_json` 是 MySQL JSON 类型，会排序键并吃掉 BigDecimal 尾零；按原始字符串算
  SHA-256 在落库后再读会 7063/8142。零 DB 用内存字符串测不到。

## 原因

- 结束门禁测试用 PUBLISHED 计划覆盖进行中 Attempt，把「已发布也可结束」固化成了合法路径。
- 快照 hash 按写入时的 Jackson 文本计算，没有按类型化文档做与存储层无关的规范化。

## 改进

- 生命周期测试按真实状态机选夹具：in-progress 用 RUNNING，PUBLISHED 直接 end 必须单独断言 8163 且零副作用。
- 对 JSON 列上的完整性摘要：解析为对象（或稳定排序的 Map）后再序列化计算；数字字段去掉尾零。写入和校验共用同一套规范化。
- 跨域闭环不要用「各层全绿」代替最少一条真实混合计划 IT。
- Task 6 全量关环数字：Admin 零 DB 847/1 跳过、Admin MySQL IT 208（含混合 IT 2）、Admin-init IT 1（baseline `20260831180000`）。
