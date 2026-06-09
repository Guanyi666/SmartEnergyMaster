---
name: project-8080-8081-sync-pitfalls
description: 8080↔8081 双端口同步时 5 个连环坑，跨层修复必须全做
metadata:
  type: project
---

跨端口同步老字段（work_order.assignee）时的坑，缺一不可：

1. **8081 `release(Long)` 要幂等** — 没活跃指派时也要 PATCH 8080 清空 assignee（之前直接抛 IllegalStateException）
2. **8080 PATCH 端点用 Map 接收** — `WorkOrderStatusRequest` 强类型 DTO 配合 `StringUtils.hasText(null)==false` 会让客户端发的 `null` 被静默忽略。要新增 `assigneeProvided` boolean 标记
3. **8080 `WorkOrder.assignee` 字段加 `@TableField(updateStrategy = FieldStrategy.IGNORED)`** — 否则 MyBatis-Plus 默认 `NOT_NULL`，`setAssignee(null)` 后 `updateById` 不写 SQL
4. **8081 `WorkOrderClient` 用专用 INCLUDE_NULL ObjectMapper** — `application.yml` 全局配了 `spring.jackson.default-property-inclusion: non_null`，默认 RestTemplate 会把 null 字段从 JSON 里剔除。专用 Mapper 强制 `Include.ALWAYS` 才能让 8080 收到 `"assignee":null`
5. **数据库一致性兜底** — 写 `cleanup-stale-assignments.sql`：把所有未 released 的指派 mark 成 released、清空 PENDING 工单的 8080 assignee、重置 personnel.current_workload

**Why:** 见 [[release-returns-empty-when-workorder-released-history]]

**How to apply:** 改任何 PATCH 同步前先查 application.yml 是否有 `default-property-inclusion: non_null`，否则 8080 永远收不到 null。
