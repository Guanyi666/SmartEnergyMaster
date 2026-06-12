// frontend/src/api/workorder.js
// 维修工单与人员调度 API 封装
// 所有 /api/* 请求统一通过 Vite 代理打到后端 8080（workorder 模块已合并到 backend）
import request from './http'

// ============ 人员 CRUD ============
export const listPersonnel = (params) => request.get('/workorder/personnel', { params })
export const getPersonnel = (id) => request.get(`/workorder/personnel/${id}`)
export const createPersonnel = (payload) => request.post('/workorder/personnel', payload)
export const updatePersonnel = (id, payload) => request.put(`/workorder/personnel/${id}`, payload)
export const deletePersonnel = (id) => request.delete(`/workorder/personnel/${id}`)
export const toggleDuty = (id, onDuty) => request.patch(`/workorder/personnel/${id}/duty`, null, { params: { onDuty } })

// ============ 工单只读 ============
export const getWorkOrderList = (params) => request.get('/workorder/orders', { params })
export const getWorkOrderDetail = (id) => request.get(`/workorder/orders/${id}`)
export const getWorkOrderAssignments = (id) => request.get(`/workorder/orders/${id}/assignments`)

// ============ 工单指派/释放/替换/批量/自动匹配 ============
export const assignWorkOrder = (id, payload) => request.post(`/workorder/orders/${id}/assign`, payload)
export const batchAssignWorkOrder = (id, payload) => request.post(`/workorder/orders/${id}/batch-assign`, payload)
export const releaseWorkOrder = (id) => request.post(`/workorder/orders/${id}/release`)
export const releaseOneAssignment = (id, personnelId) =>
    request.post(`/workorder/orders/${id}/assignments/${personnelId}/release`)
export const replaceAssignment = (id, personnelId, payload) =>
    request.post(`/workorder/orders/${id}/assignments/${personnelId}/replace`, payload)
export const autoMatch = (params) => request.get('/workorder/orders/auto-match', { params })

// ============ 调度总览 / 看板 ============
export const getDispatchSummary = () => request.get('/workorder/dashboard/summary')
export const getDispatchBoard = () => request.get('/workorder/dispatch-board')

// ============ 工单状态更新（调用主 WorkOrderController PATCH /status）============
// 所有 API 统一通过 Vite 代理打到 8080，直接调用 /work-orders/* 即可
export const patchWorkOrderStatus = (id, payload) => request.patch(`/work-orders/${id}/status`, payload)
export const patchWorkOrderSop = (id, sopId) => request.patch(`/work-orders/${id}/sop`, { sopId })
export const createTransferRequest = (id, payload) => request.post(`/work-orders/${id}/transfer-requests`, payload)
export const listTransferRequests = (params) => request.get('/work-orders/transfer-requests', { params })
export const reviewTransferRequest = (id, payload) => request.patch(`/work-orders/transfer-requests/${id}/review`, payload)

// 🆕 手动创建工单（操作员在维修指挥中心对话框提交 → POST /work-orders）
export const createWorkOrder = (payload) => request.post('/work-orders', payload)
