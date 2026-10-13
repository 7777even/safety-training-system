# 移动端培训执行闭环契约同步 QA

## 自动化验证

在 `frontend/mobile-app` 执行：

```text
node --test tests/training-learner-api.test.mjs
```

结果：2 项通过，0 项失败。覆盖培训进入不传作答类型、移动端结果投影展示、学员专属线下场次接口及无签到文案。

```text
pnpm typecheck
pnpm build:h5
```

结果：均通过。H5 构建仅输出 uni-app 版本提示与 Sass legacy API 弃用提示，未影响构建完成。

## 回归说明

```text
node --test tests/*.test.mjs
```

结果：21 项通过、2 项失败。失败均来自 `tests/learner-course-learning.test.mjs` 的素材预览静态断言：`MaterialPreview.vue` 未包含音频预览样式，且 PDF 放大操作仍被注释。该组件及其测试不在本 Change 修改范围内；本次培训相关定向测试、类型检查和 H5 构建均通过。

## 待人工验收

当前环境未提供真实学员登录态，尚未完成浏览器或设备联调。需要验证：

1. 学员提交课程、考试或题库后返回培训详情，内容状态、成绩和获得学时按后端投影刷新。
2. 待阅卷、待发布成绩的考试或题库练习进入既有结果页，不重复创建答卷。
3. 线下页只显示本人场次、成绩、状态和获得学时；合格获得安排学时，不合格或未评分获得 0。
