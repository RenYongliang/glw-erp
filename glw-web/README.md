# GLW 进销存前端

基于 Vue 3、TypeScript、Vite、Element Plus 和 Pinia，包含登录、首页工作台、订单跟踪，以及销售和采购订单看板。

## 环境要求

- Node.js：`^22.18.0 || >=24.12.0`
- npm
- VS Code 建议安装 Vue - Official（`Vue.volar`）扩展，并禁用 Vetur。

## 本地启动

在项目根目录执行：

```powershell
npm.cmd install
npm.cmd run dev
```

打开终端显示的本地地址，按 `Ctrl+C` 停止服务。

## 检查与构建

```powershell
# TypeScript 类型检查
npm.cmd run type-check

# 类型检查并构建，产物输出到 dist
npm.cmd run build

# 本地预览构建产物
npm.cmd run preview
```

## 目录说明

- `src/views`：业务页面
- `src/components`：业务组件
- `src/router`：路由与登录守卫
- `src/stores`：状态管理
- `src/domain`：业务逻辑与模拟认证
- `src/data`：本地数据
- `src/assets`：页面样式
- `tests`：业务逻辑测试
- `docs`：业务文档

## 后端认证与 Token

默认调用 glw-backend 的登录接口，请同时启动后端（默认 mock profile，无需数据库/Redis，端口8080）。Vite 会把 `/api` 代理到后端。登录后自动携带 accessToken，过期自动使用 refreshToken 换发，退出撤销当前会话。

复制 `.env.example` 为 `.env.local` 可修改接口地址。纯前端静态演示设置 `VITE_AUTH_MODE=mock`，修改环境配置后重启 Vite。生产构建默认使用后端认证，需要为 `/api` 配置反向代理。

业务页面仍使用 mock 数据。请求层用法、凭据存储及刷新规则见 [认证说明](src/services/README.md)。
