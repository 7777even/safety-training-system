# 前台门户统一布局实现计划

> **面向 AI 代理的工作者：** 按 OpenSpec `2026-07-21-build-portal-layout` 的 Task 1、Task 2 顺序执行；每步完成后运行列出的验证命令并更新复选框。

**目标：** 将菜单驱动的 `/portal/*` 页面置于统一门户布局中，并为五个已配置入口建立可解析页面。

**架构：** 动态路由为所有 `/portal/` 菜单创建共享的 `PortalLayout` 父路由，菜单对应组件作为该父路由的子路由。`views/portal/index.vue` 只负责顶栏和内容插槽，首页内容移动到 `views/portal/home/index.vue`，其他入口保持无业务数据的占位页。

**技术栈：** Vue 3、TypeScript、Vue Router、Element Plus、Vite、Node.js test runner。

---

### Task 1：让前台菜单在门户布局中嵌套渲染

**文件：**

- 创建：`frontend/pc-web/tests/portal-layout-routing.test.mjs`
- 修改：`frontend/pc-web/src/router/admin-dynamic-routes.ts`
- 修改：`frontend/pc-web/src/views/portal/index.vue`

- [x] **步骤 1：编写失败的门户路由结构测试**

  使用源码断言验证路由文件为 `/portal/` 菜单使用门户布局，并验证布局文件在内容容器中使用 `RouterView`：

  ```js
  assert.match(routeSource, /PortalLayout/);
  assert.match(routeSource, /isPortalMenuPath/);
  assert.match(layoutSource, /<RouterView\s*\/>/);
  ```

- [x] **步骤 2：运行测试并确认失败**

  运行：`node --test frontend/pc-web/tests/portal-layout-routing.test.mjs`

  预期：失败，提示动态路由尚未包含门户父布局分支，且布局尚未包含 `RouterView`。

- [x] **步骤 3：实现门户父路由和布局内容插槽**

  在 `admin-dynamic-routes.ts` 中引入门户布局、识别 `/portal/` 前缀并按稳定父路由建立子路由；在 `portal/index.vue` 中保留顶栏和用户操作，仅将 `.portal-content` 改为：

  ```vue
  <section class="portal-content">
    <RouterView />
  </section>
  ```

- [x] **步骤 4：运行路由结构测试并确认通过**

  运行：`node --test frontend/pc-web/tests/portal-layout-routing.test.mjs`

  预期：通过。

### Task 2：创建五个菜单入口页面

**文件：**

- 创建：`frontend/pc-web/src/views/portal/home/index.vue`
- 创建：`frontend/pc-web/src/views/portal/class/index.vue`
- 创建：`frontend/pc-web/src/views/portal/exams/index.vue`
- 创建：`frontend/pc-web/src/views/portal/training/index.vue`
- 创建：`frontend/pc-web/src/views/portal/teaching-aids/index.vue`
- 修改：`frontend/pc-web/tests/portal-layout-routing.test.mjs`

- [x] **步骤 1：扩展失败测试以覆盖全部组件路径**

  为五个入口添加文件存在性断言，并断言四个非首页页面包含各自中文名称和“暂未开放”提示：

  ```js
  for (const pagePath of portalPagePaths) {
    assert.equal(existsSync(pagePath), true);
  }
  assert.match(classPageSource, /课程中心/);
  assert.match(classPageSource, /暂未开放/);
  ```

- [x] **步骤 2：运行测试并确认失败**

  运行：`node --test frontend/pc-web/tests/portal-layout-routing.test.mjs`

  预期：失败，提示五个组件文件尚不存在。

- [x] **步骤 3：建立菜单页面骨架**

  将既有首页静态概览迁移到 `portal/home/index.vue`；创建其余四个仅含标题和“暂未开放”提示的 scoped Less 页面，不添加接口请求、模拟数据或业务操作。

- [x] **步骤 4：运行页面测试及受影响 workspace 验证**

  运行：

  ```powershell
  node --test frontend/pc-web/tests/portal-layout-routing.test.mjs
  pnpm --filter @safety-training/pc-web run typecheck
  pnpm --filter @safety-training/pc-web run build
  ```

  预期：全部通过；五个菜单组件均可解析，且 Vite 构建无错误。

- [x] **步骤 5：审查变更范围**

  运行：`git diff --check && git diff -- frontend/pc-web/src/router/admin-dynamic-routes.ts frontend/pc-web/src/views/portal frontend/pc-web/tests/portal-layout-routing.test.mjs`

  预期：无空白错误，改动仅覆盖门户路由、布局、菜单页面和对应测试。
