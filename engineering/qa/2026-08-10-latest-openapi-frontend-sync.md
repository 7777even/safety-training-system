# 最新 OpenAPI 前端同步 QA 记录

## 验证结果

- `node --test tests/latest-openapi-sync.test.mjs tests/system-admin-closure.test.mjs`：6/6 通过。
- 受影响 API、Mock、组件文件 ESLint：通过。
- `pnpm --filter @safety-training/pc-web exec vue-tsc --noEmit --ignoreDeprecations 5.0`：通过。
- `pnpm --filter @safety-training/pc-web build`：通过。
- `git diff --check`：通过，仅有换行符转换提示。

## 本轮范围

- 用户部门空值和人员选择器字段同步。
- 题库、试卷、考试、评价表单请求租户字段清理。
- 素材 `original` 下载-only 展示。

## 未执行

- 未运行 PC Web 全量 lint；仓库仍存在课程 Mock 的既有 `_data` 未使用错误。
- 未执行后端服务联调；实际数据隔离仍需启动后端后验收。
