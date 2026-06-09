# 维修工单与人员调度 API 文档

> 完整实现见 `C:\SmartEnergyMaster\doc\维修工单与人员调度-实施计划.md`
> 基础 URL: `http://localhost:8081/api/workorder`

---

## 1. 人员 CRUD

### GET `/api/workorder/personnel`
分页 + 筛选人员列表

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| pageNum | int | 否，默认 1 | 页码 |
| pageSize | int | 否，默认 20 | 每页条数 |
| specialization | string | 否 | 技能 contains 过滤（前端在内存中二次过滤） |
| skillLevel | string | 否 | JUNIOR/INTERMEDIATE/SENIOR/EXPERT |
| onDuty | bool | 否 | true=在岗, false=离岗 |

响应：
```json
{
  "records": [
    {
      "id": 1, "employeeNo": "E001", "name": "张工",
      "avatarColor": "#52c8ff",
      "specializations": ["电气", "自动化"],
      "skillLevel": "EXPERT", "certification": "高级工程师 / 15年",
      "currentWorkload": 2, "maxWorkload": 5,
      "isOnDuty": true, "workloadRate": 40,
      "createdAt": "2026-06-09T10:00:00", "updatedAt": "2026-06-09T10:00:00"
    }
  ],
  "total": 6, "size": 20, "current": 1
}
```

### GET `/api/workorder/personnel/{id}`
人员详情

### POST `/api/workorder/personnel`
新增人员

请求体：
```json
{
  "employeeNo": "E007",
  "name": "新员工",
  "specializations": ["电气"],
  "skillLevel": "JUNIOR",
  "maxWorkload": 3,
  "phone": "13800000007",
  "email": "x@x.com",
  "avatarColor": "#52c8ff",
  "certification": "..."
}
```

### PUT `/api/workorder/personnel/{id}`
编辑人员（employeeNo 不可改）

### DELETE `/api/workorder/personnel/{id}`
删除人员（currentWorkload 必须为 0，否则 409）

### PATCH `/api/workorder/personnel/{id}/duty?onDuty=true|false`
切岗

---

## 2. 工单只读

### GET `/api/workorder/orders?status=PENDING&pageNum=1&pageSize=20`
工单列表（JOIN 设备 + 活跃指派人）

`assigneeName` 来自 `workorder_assignment` JOIN，**不读 work_order.assignee 列**（🟠 脏写风险修正）

### GET `/api/workorder/orders/{id}`
工单详情（同上 JOIN）

### GET `/api/workorder/orders/{id}/assignments`
指派历史

---

## 3. 指派 / 释放 / 自动匹配

### POST `/api/workorder/orders/{id}/assign`
指派人员（事务内同步 8080 现有 API）

请求体：
```json
{
  "personnelId": 1,
  "role": "PRIMARY",  // PRIMARY | ASSIST
  "note": "现场已沟通"
}
```

错误码：
- 400 工单不存在 / 人员不存在
- 409 人员已离岗 / 已达最大负载 / 重复指派 / 8080 同步失败

### POST `/api/workorder/orders/{id}/release`
释放所有活跃指派

### GET `/api/workorder/orders/auto-match?faultType=MECHANICAL_JAM&workOrderId=1&topN=5`
自动匹配 Top N

响应：
```json
{
  "workOrderId": 1,
  "faultType": "MECHANICAL_JAM",
  "requiredSkills": ["机械", "液压"],
  "candidates": [
    {
      "personnelId": 2, "employeeNo": "E002", "name": "李工",
      "specializations": ["机械", "液压"], "skillLevel": "SENIOR",
      "currentWorkload": 0, "maxWorkload": 4, "workloadRate": 0,
      "matchScore": 88,
      "matchedSkills": ["机械", "液压"]
    }
  ]
}
```

评分公式：基础50 + 技能匹配(每+15, 最多30) + 等级bonus(0/5/10/15) - 负载率*20

---

## 4. 调度总览 / 看板

### GET `/api/workorder/dashboard/summary`
```json
{
  "totalOrders": 38, "pendingOrders": 12, "inProgressOrders": 8, "resolvedOrders": 18,
  "totalPersonnel": 6, "onDutyPersonnel": 5, "offDutyPersonnel": 1,
  "avgWorkloadRate": 67,
  "skillCoverage": { "电气": 3, "机械": 2, "液压": 2, "仪表": 2, "自动化": 3 }
}
```

### GET `/api/workorder/dispatch-board`
按技能分组的负载矩阵（前端"智能调度"右栏使用）

---

## 5. 错误响应统一格式

```json
{
  "timestamp": "2026-06-09T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "人员不存在: id=99"
}
```

- 400 IllegalArgumentException
- 409 IllegalStateException
- 500 其他

---

## 6. 跨服务调用约定

| 操作 | 谁来执行 | 通过 |
|---|---|---|
| 读 work_order | 新模块 | 直查 DB（只读） |
| 改 work_order.status | **现有 8080** | PATCH /api/work-orders/{id}/status |
| 改 work_order.assignee | **现有 8080** | PATCH /api/work-orders/{id}/status（body 携带 assignee） |
| 设备状态联动 | 现有 8080 | WorkOrderServiceImpl.syncDeviceStatusAfterOrderUpdate 自动 |
| 写 workorder_assignment | 新模块 | 直写新表 |
| 写 workorder_maintenance_personnel | 新模块 | 直写新表 |
