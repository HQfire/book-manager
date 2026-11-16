# book-manager

个人图书 / 笔记管理系统后端 API（学习用 MVP）。

## 技术栈

- Java 17
- Spring Boot 4.1.2
- MyBatis-Plus 3.5.18（`mybatis-plus-spring-boot4-starter`）
- MySQL 9.5
- MySQL Connector/J 9.7
- Lombok、Spring Boot Validation

## 环境要求

- JDK 17
- Maven 3.9+
- 本地 MySQL 服务，已创建数据库 `book_manager`（建表脚本见 `db/schema.sql`）

## 启动

1. 创建数据库并执行 `db/schema.sql`；
2. 复制 `src/main/resources/application-example.properties` 为 `application.properties`，填写本机 MySQL 用户名与密码（`application.properties` 已被 `.gitignore` 排除，不提交密码）；
3. 启动：

```
mvn spring-boot:run
```

服务默认监听 `http://127.0.0.1:8080`。

## 接口清单

### 认证

| 方法 | 路径 | 说明 | 请求体 |
|---|---|---|---|
| POST | `/api/auth/register` | 注册 | `{"username": "...", "password": "..."}` |
| POST | `/api/auth/login` | 登录校验 | `{"username": "...", "password": "..."}` |

### 图书

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/books` | 新增图书 |
| PUT | `/api/books/{id}` | 编辑图书 |
| DELETE | `/api/books/{id}` | 删除图书 |
| GET | `/api/books/{id}` | 图书详情 |
| GET | `/api/books/page` | 分页条件查询，参数：`userId`、`title`、`author`、`pageNum`、`pageSize` |

统一返回结构：

```json
{ "code": 200, "message": "OK", "data": {} }
```

## 目录结构

```
src/main/java/org/example/bookmanager
├── common      # 统一返回、分页结果、业务异常、全局异常处理
├── config
├── controller  # AuthController、BookController
├── dto         # 请求与响应对象
├── entity      # UserAccount、Book
├── mapper      # MyBatis-Plus Mapper
├── service     # 业务接口与实现
└── util        # 密码哈希工具
```

## 当前边界（未实现，避免在简历中夸大）

- 无前端页面；
- 未实现 token / 会话登录态，`userId` 由请求传入；
- 密码使用 SHA-256 无盐哈希，未引入 Spring Security；
- 分页为手写 `LIMIT` 条件查询，未使用 MP 分页插件；
- 未接入缓存、消息队列、分布式部署与云端上线。
