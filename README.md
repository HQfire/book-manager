# book-manager

个人图书管理系统后端 API + 前端页面（学习用 MVP）。

## 技术栈

- Java 17
- Spring Boot 4.1.2
- MyBatis-Plus 3.5.18（`mybatis-plus-spring-boot4-starter`）
- MySQL 9.5
- Spring Data Redis（Lettuce）本地缓存
- JJWT 0.12.6（无状态 JWT 登录态）
- Spring Security Crypto（BCrypt 加盐哈希）
- Spring Boot Actuator（健康检查）
- SpringDoc OpenAPI / Swagger UI
- Lombok、Spring Boot Validation
- 前端：Vue 3 + Vite（独立工程 `../book-manager-web`）

## 环境要求

- JDK 17
- Maven 3.9+
- 本地 MySQL 服务，已创建数据库 `book_manager`（建表脚本见 `db/schema.sql`）
- 本地 Redis，默认 `127.0.0.1:6379`；开发用免安装 Redis 位于 `D:\tools\redis`，启动：

```
D:\tools\redis\redis-server.exe --port 6379 --bind 127.0.0.1
```

## 启动后端

1. 启动 MySQL 与 Redis；
2. 创建数据库并执行 `db/schema.sql`；
3. 复制 `src/main/resources/application-example.properties` 为 `application.properties`，填写本机 MySQL 用户名与密码（`application.properties` 已被 `.gitignore` 排除，不提交密码）；
4. 启动：

```
mvn spring-boot:run
```

服务默认监听 `http://127.0.0.1:8080`。

- 健康检查：`http://127.0.0.1:8080/actuator/health`
- 接口文档（Swagger UI）：`http://127.0.0.1:8080/swagger-ui.html`
- OpenAPI JSON：`http://127.0.0.1:8080/v3/api-docs`

## 启动前端

前端为独立工程，位于 `D:\Code\book-manager-web`：

```
cd D:\Code\book-manager-web
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`，Vite 代理 `/api/**` 到后端 8080。

## 接口清单

### 认证（无需 token）

| 方法 | 路径 | 说明 | 请求体 |
|---|---|---|---|
| POST | `/api/auth/register` | 注册 | `{"username": "...", "password": "..."}` |
| POST | `/api/auth/login` | 登录，返回 JWT | `{"username": "...", "password": "..."}` |

登录成功返回：

```json
{ "code": 200, "message": "OK", "data": { "token": "...", "id": 1, "username": "..." } }
```

### 图书（需 token）

请求头携带：`Authorization: Bearer <token>`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/books` | 新增图书 |
| PUT | `/api/books/{id}` | 编辑图书 |
| DELETE | `/api/books/{id}` | 删除图书 |
| GET | `/api/books/{id}` | 图书详情（走 Redis 缓存） |
| GET | `/api/books/page` | 分页条件查询，参数：`title`、`author`、`pageNum`、`pageSize` |

当前登录用户由 token 解析得到，请求体与查询参数不再传入 `userId`；图书不属于当前用户时返回 403。

统一返回结构：

```json
{ "code": 200, "message": "OK", "data": {} }
```

业务错误码：400 参数校验失败、401 未登录或 token 无效、403 无权限、404 不存在、409 用户名重复。

## 缓存策略

- 仅图书详情 `GET /api/books/{id}` 走 Redis，key 为 `book:detail:{id}`，TTL 30 分钟；
- 图书更新、删除后主动删除对应缓存；分页列表不缓存；
- 未命中（404）不写入缓存。

## 目录结构

```
src/main/java/org/example/bookmanager
├── common      # 统一返回、分页结果、业务异常、全局异常处理
├── config      # 拦截器注册、鉴权拦截器
├── controller  # AuthController、BookController、HelloController
├── dto         # 请求与响应对象
├── entity      # UserAccount、Book
├── mapper      # MyBatis-Plus Mapper
├── service     # 业务接口与实现
└── util        # 密码哈希、JWT 工具
```

## 当前边界（未实现，避免在简历中夸大）

- 鉴权为轻量拦截器 + JJWT，未引入完整 Spring Security 过滤器链；
- 无 token 刷新机制，过期后需重新登录；
- 分页为手写 `LIMIT` 条件查询，未使用 MP 分页插件；
- 缓存仅本地单实例 Redis，未做集群、二级缓存与缓存穿透防护；
- 前端为本地 Vite 开发工程，未打包部署到服务器；
- 未接入消息队列、分布式部署与云端上线。
