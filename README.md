<div align="center">
  <h2>MessagePush Platform</h2>
  <p>面向多业务方的 <b>统一消息推送中台</b>。屏蔽短信、邮件、飞书等多渠道差异，
  提供标准化发送 API，通过 Kafka / MySQL 可插拔中转、策略模式多渠道、
  三级优先级隔离、定时推送与双层限额，解决业务方重复对接第三方与高峰期推送阻塞的问题。</p>
  <p>
    <img src="https://img.shields.io/badge/Java-8-007396?style=flat-square" alt="Java 8">
    <img src="https://img.shields.io/badge/Spring%20Boot-2.x-6DB33F?style=flat-square" alt="Spring Boot 2.x">
    <img src="https://img.shields.io/badge/MyBatis-3-C94F4F?style=flat-square" alt="MyBatis 3">
    <img src="https://img.shields.io/badge/MySQL-5.7+-4479A1?style=flat-square" alt="MySQL 5.7+">
    <img src="https://img.shields.io/badge/Redis-5.0+-DC382D?style=flat-square" alt="Redis 5.0+">
    <img src="https://img.shields.io/badge/Kafka-2.x-231F20?style=flat-square" alt="Kafka 2.x">
    <a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="MIT License"></a>
  </p>
</div>
---
## 📑 目录
- [🏗 架构分层](#-架构分层)
- [✨ 核心特性](#-核心特性)
- [🚀 快速启动](#-快速启动)
  - [环境要求](#环境要求)
  - [启动步骤](#启动步骤)
  - [本地开发配置](#本地开发配置)
- [📡 API 一览](#-api-一览)
- [📂 目录结构](#-目录结构)
- [🛠 技术栈](#-技术栈)
- [❓ 常见问题](#-常见问题)
- [📄 License](#-license)
---
## 🏗 架构分层

```
┌─────────────────────────────────────────────────────────┐
│  推送发起层   业务方调用统一 HTTP API（模板/发送/查询）      │
│              参数校验 → 模板审核校验 → 配额限流 → 投递中转   │
├─────────────────────────────────────────────────────────┤
│  推送中转层   Kafka（high/middle/low/retry 4 topic）      │
│              └ 可插拔：MySQL 按优先级分表（t_msg_queue_*）  │
├─────────────────────────────────────────────────────────┤
│  消费层       独立消费者按优先级并发消费 → 策略模式多渠道推送  │
│              失败快速重试 + 重试队列异步重试                │
├─────────────────────────────────────────────────────────┤
│  存储层       MySQL：消息记录 / 模板 / 定时消息 / 额度       │
│              Redis：缓存 / 限流计数 / ZSet 定时时间点       │
└─────────────────────────────────────────────────────────┘
```

---
## ✨ 核心特性

### 1. Kafka 异步中转 + 可插拔中转层
- 发送链路异步化：业务方立即获得响应，消费端异步调用第三方，高峰期实现削峰，接口响应时间显著下降
- 设计可插拔中转层：`mysql-as-mq` 开关一键切换 Kafka / MySQL 两种实现
  - **Kafka**：按优先级拆分 `low / middle / high / retry` 4 个独立 topic，`acks=1` + 手动 ACK，消息持久化到磁盘、天然支持副本
  - **MySQL**：按优先级分表 `t_msg_queue_low / middle / high / retry`，定时批量拉取 + 状态机流转（待执行→执行中→成功/失败），可用于未接入 MQ 的团队快速落地

### 2. 策略模式封装多渠道 + 主备容灾
- 基于策略模式（`MsgPushService` 接口 + `@PostConstruct` 注册 Map）统一管理短信、邮件、飞书渠道；新增渠道只需新增策略类并注册，发送主逻辑零改动
- **短信渠道主备容灾**：默认阿里云为主、腾讯云为备用，同一渠道连续失败 3 次自动切换备用渠道，单次成功即重置失败计数；全渠道失败抛出异常进入重试队列，不会静默丢消息
- **飞书渠道**：支持自定义机器人 Webhook 发送；未配置密钥 / Webhook 时自动降级为模拟发送，便于本地演示

### 3. 三级优先级隔离
- 按高 / 中 / 低拆分独立通道，配置不同消费并发（高 6 : 中 3 : 低 1）
- 避免验证码等高优消息被低优积压拖累，高优资源独立、优先消费
- MySQL 中转场景对齐分桶逻辑：按优先级分表，资源完全隔离，解决原先 `order by 优先级` 导致的低优先级任务饿死问题

### 4. MySQL + Redis ZSet 二级存储的定时推送
- MySQL 存储全量定时消息，Redis ZSet 仅存触发时间点，兼顾海量存储与高频扫描
- 后台每 100 ms 扫描 ZSet（Lua 脚本 `ZRangeByScore` + `ZRem` 原子取删），到点后从 MySQL 捞出消息转为普通消息推送
- 实测定时触发精度误差 < 1 秒；Redis 故障时降级直查 MySQL 定时表，主链路可用

### 5. 全局限额 + 业务限额双层流控
- 全局额度表（渠道默认）+ 业务额度表（业务专属），请求按「业务专属优先，否则使用全局默认」取额
- 基于 Redis `INCR` 原子计数实现固定窗口实时流控，使用当前秒级时间戳划分窗口，过期自动归零
- 黑名单逻辑并入限额表（额度为 -1 的标记），减少一次额外查询
- 定时消息单独计数限频，避免定时任务抢占实时通道额度

### 6. 统一对外 API + 模板审核
- 提供模板管理、消息发送、消息查询三组 HTTP API，业务方仅需传模板 ID 与参数即可发送，屏蔽底层渠道差异
- 模板状态机：创建（待审核）→ 人工审核通过 → 方可用于发送，从源头防止未审核模板误下发

### 7. 可靠性与性能
- 失败处理：单次快速重试 + 独立重试队列异步重试（`max-retry-count=5`），Kafka / MySQL 双中转场景均有完整重试链路
- MySQL 消费 / 定时消费通过 Redis 分布式锁选主节点，多实例部署下避免重复消费
- 模板、消息记录、额度均走 Redis 缓存，查询接口先查缓存、未命中查库并回写
- 本地 2 核 4G 机器使用 `wrk` 压测：发送接口 QPS 从 1000+ 提升至 3000（开启缓存后），压测脚本与 Benchmark 文件见 `wrkbench/`

---
## 🚀 快速启动

### 环境要求
- JDK 1.8+，Maven 3.x
- MySQL 5.7+（建表脚本：`sql/msgcenter.sql`）
- Redis 5.0+
- Kafka（可选；使用 MySQL 中转时无需安装，设置 `send-msg-conf.mysql-as-mq=true`）

### 启动步骤
```bash
# 1. 初始化数据库
mysql -uroot -p < sql/msgcenter.sql

# 2. 配置环境变量（所有密钥均通过环境变量注入，无需修改配置文件）
export MYSQL_PASSWORD=your_password
export REDIS_PASSWORD=your_redis_password
export EMAIL_ACCOUNT=your_email@example.com
export EMAIL_AUTH_CODE=your_smtp_auth_code
# 短信 / 飞书密钥可选：未配置时自动降级为模拟发送，方便本地演示

# 3. 编译并启动
mvn spring-boot:run
```

### 本地开发配置
创建文件 `src/main/resources/application-local.yml` 并填入本机配置，随后以
`--spring.profiles.active=local` 启动。该文件名已在 `.gitignore` 中被忽略，不会进入仓库。

---
## 📡 API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/msg/create_template` | 创建模板（创建后进入待审核状态） |
| GET  | `/msg/get_template`    | 查询模板（已命中 Redis 缓存） |
| POST | `/msg/update_template` | 更新模板 |
| POST | `/msg/del_template`    | 删除模板 |
| POST | `/msg/send_msg`        | 发送消息（立即异步中转） |
| GET  | `/msg/get_msg_record`  | 查询消息发送记录（已命中 Redis 缓存） |

---
## 📂 目录结构

```
src/main/java/cn/bitoffer/msgcenter/
├── controller        # HTTP 接口层：模板 / 发送 / 记录查询
├── service           # 模板管理与发送主流程逻辑
├── manager           # 发送中转管理与消费者消息分派
├── msgpush           # 渠道策略：email / sms（主备） / lark
├── consumer          # Kafka / MySQL / 定时消费者 + 分布式选主
├── redis             # Redis ZSet 定时缓存（Lua 原子取删）
├── tools             # 限流服务与消息记录服务
├── mapper            # MyBatis Mapper 接口
├── enums             # 渠道 / 优先级 / 状态枚举
├── exception         # 全局异常处理
└── common            # 公共配置、公共模型与分布式锁
wrkbench/             # wrk 压测脚本：send_msg / get_msg_record（含 Lua + Shell）
sql/msgcenter.sql     # MySQL 建表与初始化脚本
```

---
## 🛠 技术栈

| 类别 | 选型 |
|---|---|
| 语言与框架 | Java 8 · Spring Boot 2.x · MyBatis |
| 消息与中转 | Apache Kafka（可选） · MySQL 可插拔中转实现 |
| 存储与缓存 | MySQL 5.7+ · Redis（String / Hash / ZSet / 分布式锁） |
| 推送渠道 | SMTP 邮件 · 阿里云短信 · 腾讯云短信（主备容灾） · 飞书自定义机器人 |
| 工程能力 | 策略模式多渠道 · 分布式锁选主 · 主备容灾切换 · 双层额度限流 · wrk 基准压测 |

---
## ❓ 常见问题

1. **启动时报 Redis / MySQL 连接错误？**
   检查 `application.yml` 中默认值对应的环境变量是否已正确设置；或创建 `application-local.yml` 按本地情况覆盖。
2. **没有 Kafka 能运行吗？**
   可以。设置 `send-msg-conf.mysql-as-mq=true`（默认即 true），系统将切换为 MySQL 中转模式，自动按优先级分表消费。
3. **未配置短信 / 飞书密钥会影响运行吗？**
   不会。未配置密钥时自动降级为模拟发送，仅打印日志不真正调用云厂商，便于本地演示与功能调试。
4. **多实例部署下会重复消费吗？**
   MySQL 消费者与定时消费者均通过 Redis 分布式锁选主节点，多实例部署下同一时间只有一个消费者执行拉取动作，天然避免重复消费。
5. **推送失败会丢消息吗？**
   不会。同一渠道连续失败自动切换主备；仍失败则立即快速重试，仍未成功则投递到独立重试队列异步重试（最多 `max-retry-count=5` 次）；全链路持久化到 MySQL 保证可追溯。

---
## 📄 License

本项目基于 [MIT License](LICENSE) 开源。
