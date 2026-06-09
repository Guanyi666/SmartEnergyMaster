# Epic 11：缓存 / 限流 / 分布式锁 — 模块说明

> **上线日期**：2026-06-09  
> **涉及包**：`annotation/` `aspect/` `cache/` `exception/` `config/RedisConfig.java`  
> **依赖**：Redis 7（`deploy/docker-compose.yml`）

---

## 一、场景与问题

| 场景 | 原有问题 | 新增能力 |
|------|---------|----------|
| 前端大屏 5s 轮询 + 传感器高频上报 | 每次请求都查 DB，3 台设备 × 5s 轮询 → 每分钟 36 次重复 DB 查询 | **Redis 缓存**：热点数据缓存 5~30s，DB 查询量下降 90%+ |
| 登录接口无防护 | 可被脚本暴力爆破密码 | **IP 限流**：同一 IP 60s 内最多 3 次登录尝试，超限返回 HTTP 429 |
| 传感器并发上报同一设备 | 两个数据泵实例同时上报同一设备，可能导致数据覆盖或重复故障工单 | **分布式锁**：按设备编码加锁，同一设备同时只处理一个请求，冲突返回 HTTP 423 |
| 缓存与数据库不一致 | 新数据写入后缓存未更新，大屏展示过期数据 | **主动失效**：写操作后立即删除对应缓存 key，下次读取自动回源 |

---

## 二、模块职责

```
annotation/   — 声明式注解（@RateLimit / @DistributedLock），零侵入标注
aspect/       — AOP 切面实现（Lua 原子脚本 + SpEL 动态 key）
cache/        — 缓存抽象（读穿透 + 主动失效 + 缓存击穿保护）
exception/    — LockAcquireException(423) / RateLimitException(429)
config/       — RedisTemplate 序列化配置（JSON + JavaTimeModule）
```

---

## 三、遵循的设计标准

| 标准 | 应用 |
|------|------|
| **Fall-open（故障开放）** | Redis 不可用时缓存/限流/锁全部降级放行，不阻断业务 |
| **Lua 原子操作** | 限流 ZSET 滑动窗口 + 分布式锁解锁（比对 token 再删）均在 Redis 服务端单次原子执行 |
| **Token 锁所有权** | 解锁脚本比对 UUID token，只有加锁者才能释放，杜绝误删他人锁 |
| **TTL 防死锁** | 锁自动过期（leaseMillis），持锁线程崩溃不产生永久死锁 |
| **SpEL 动态 key** | 限流/锁的粒度通过方法入参动态计算（如 `#sensorDataDTO.deviceCode`），支持 `p0/a0/形参名` 三种写法 |
| **读穿透（Read-through）** | `getOrLoad(key, ttl, loader)`：缓存命中直接返回，未命中自动回源 DB 并回填缓存 |
| **缓存击穿保护** | 并发 miss 时用 Redis SET NX 互斥锁，仅一个线程回源重建，其余等待后重试 |
| **面向接口编程** | `CacheService` 接口抽象，底层可切换 Redis / Caffeine / 内存，业务代码无感知 |

---

## 四、注解使用速查

### @RateLimit — 接口限流

```java
// 登录防爆破：同一 IP 60s 内最多 3 次
@RateLimit(name = "login", limit = 3, window = 60, dimension = RateLimit.Dimension.IP,
           message = "登录尝试过于频繁，请 1 分钟后再试")

// 传感器上报：同一设备 1s 内最多 100 次
@RateLimit(name = "upload", limit = 100, window = 1, dimension = RateLimit.Dimension.SPEL,
           key = "#sensorDataDTO.deviceCode", message = "设备上报频率超限")
```

### @DistributedLock — 分布式锁

```java
// 传感器上报：按设备编码加锁，200ms 内重试，锁 3s 自动过期
@DistributedLock(name = "upload", key = "#sensorDataDTO.deviceCode",
                 waitMillis = 200, leaseMillis = 3000)
```

### CacheService — 缓存读写

```java
// 读穿透
SensorData data = cacheService.getOrLoad(
    CacheKeys.deviceLatest(deviceCode),      // key: device:latest:EAF-01
    CacheKeys.DEVICE_LATEST_TTL,             // TTL: 30s
    () -> sensorDataMapper.selectOne(...));   // 回源 loader

// 写后失效
cacheService.evict(CacheKeys.deviceLatest(deviceCode));
```

---

## 五、配置要点

- **Redis 连接**：`application.yml` → `spring.data.redis`（localhost:6379，Lettuce 连接池）
- **序列化**：key=String，value=JSON（`GenericJackson2JsonRedisSerializer` + `JavaTimeModule`），兼容 `OffsetDateTime` 等 Java 8 时间类型
- **容器**：`docker-compose.yml` 已包含 `redis:7-alpine`，`docker compose up -d` 自动启动
