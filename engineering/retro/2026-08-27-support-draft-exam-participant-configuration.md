# Retro：支持草稿考试配置考生与阅卷人

日期：2026-08-27

## 做得好的地方

- 用草稿期按需物化唯一执行对象、发布时原地冻结快照，避免恢复 `examId` 第二套人员归属或改库。
- 真库 IT 及时抓住行锁 SQL 的错误物理表名，说明零 DB Mockito 无法替代落库验证。
- 开考和学员任务继续以 `Exam.publishStatus` 为权威，没有把 `execution_status=READY` 当成已发布。

## 问题与原因

- `@Select` 手写 SQL 必须写物理表名，不能套用逻辑实体名 `sts_exam`；首次真库运行才暴露。
- 类级 `@Transactional` 内再用默认 `REQUIRED` 的 `TransactionTemplate`，内层失败只标记回滚、不会立刻撤销已执行 SQL；MyBatis
  一级缓存还会继续返回更新后的快照，使回滚断言假失败。
- `when(mapper.updateById(any())).thenReturn(0)` 在 `delegatesTo` 真 Mapper 上会在 stub 阶段调用真实更新，必须改用
  `doReturn`。

## 改进

- 手写 `FOR UPDATE` 等原生 SQL 时对照 Entity `@TableName` 和 init 完整 SQL，并尽早用目标 IT 覆盖。
- 验证事务回滚时使用 `PROPAGATION_NESTED` savepoint，事后用 `JdbcTemplate` 读原始列，避开一级缓存。
- 对 MyBatis Mapper 的 Mockito 包装优先 `doReturn` / `doThrow`，不要让 stub 打到真实 SQL。
