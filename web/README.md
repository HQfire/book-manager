# book-manager-web

Book Manager 前端页面，Vue 3 + Vite，简洁浅色后台风格。

## 前置条件

- 后端 `book-manager` 已在 `http://127.0.0.1:8080` 启动；
- Node.js 18+（本机验证版本 22.23）。

## 启动

```
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`。

Vite 开发服务器已配置代理：`/api/**` 与 `/actuator/**` 转发到 `http://127.0.0.1:8080`，前端无需单独处理跨域。

## 常用脚本

| 命令 | 说明 |
|---|---|
| `npm run dev` | 启动开发服务器 |
| `npm run build` | 生产构建，输出到 `dist/` |
| `npm run preview` | 预览生产构建 |

## 结构

```
src
├── api.js              # fetch 封装、token 读写、统一解包 Result
├── i18n.js             # 中英文案、当前语言与切换逻辑
├── App.vue             # 按登录态切换登录页 / 图书管理页
├── components
│   ├── AuthView.vue    # 登录 / 注册
│   ├── BookView.vue    # 图书列表、搜索、新增、编辑、删除、分页
│   └── SettingsModal.vue   # 设置弹窗（通用 / 系统 / 接口）
└── style.css           # 浅色后台基础样式
```

## 设置弹窗

- 页面右上角「设置」按钮打开双栏设置弹窗（左菜单 + 右内容），参考常见后台设置：
  - 左侧：菜单搜索、当前账号卡片、通用 / 系统 / 接口 菜单；
  - **通用**：中文 / English 语言切换；
  - **系统**：应用版本、前后端地址、技术栈，以及后端 Actuator 健康检查状态；
  - **接口**：当前后端接口清单（模块、方法、路径）；
- 语言选择存入 `localStorage`，刷新后保留；
- 新增文案统一在 `i18n.js` 的 `zh` / `en` 两个对象中维护。

## 登录态

- 登录成功后 token 与用户名存入 `localStorage`；
- 后续请求在 `Authorization: Bearer <token>` 头中携带；
- 后端返回 401 时前端清除本地登录态，回到登录页。
