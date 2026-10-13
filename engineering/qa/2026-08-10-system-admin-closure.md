# 系统管理与验收数据收口 QA 记录

## 范围

- 系统用户成员扩展字段录入与真实接口字段提交。
- 标签、岗位、部门、角色的租户上下文。
- 监管聚合入口、目标租户只读请求头和会话清理。
- 已删除机构成员、监管关系菜单/Mock/组件残留清理。
- superAdmin 验收数据文档。

## 验证结果

| 验证项         | 命令/方式                                                                                  | 结果               |
|-------------|----------------------------------------------------------------------------------------|------------------|
| 定向闭环测试      | `node --test frontend/pc-web/tests/system-admin-closure.test.mjs`                      | 3/3 通过           |
| PC Web 类型检查 | `pnpm --filter @safety-training/pc-web exec vue-tsc --noEmit --ignoreDeprecations 5.0` | 通过               |
| PC Web 构建   | `pnpm --filter @safety-training/pc-web build`                                          | 通过               |
| 旧能力残留搜索     | `rg` 检查源码及初始化菜单                                                                        | 未发现运行时代码、菜单或组件残留 |
| 差异检查        | `git diff --check`                                                                     | 通过；仅有换行符转换提示     |

## 未通过/既有问题

`pnpm --filter @safety-training/pc-web lint` 仍因仓库既有 `src/mock/learner-course.ts:214` 的 `_data` 未使用错误失败，同时报告大量既有
Vue 格式警告。本轮未修改该无关课程 Mock。

## 真实数据说明

后端服务未在本地启动，本轮未直接写入数据库。可按 `docs/requirement/superadmin-validation-data.md` 使用 superAdmin
页面录入租户、标签、岗位、部门、用户和角色，录入后再执行业务链路验收。
