// frontend/src/api/workorder.js
// 维修工单与人员调度 API 封装
// baseURL = '/api'（与 http.js 共享实例），Vite 代理把 '/api/workorder/*' 打到 8081
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

// ============ 跨服务：调 8080 现有 WorkOrderController PATCH /status ============
// 🟠 严重问题 #3 修正：前端拖拽改 status 必须走现有 8080 API，不走 8081
//    这是前端代理配置（vite.config.js）：'/api' → :8080，所以这里直接打 /work-orders/* 即可
export const patchWorkOrderStatus = (id, payload) => request.patch(`/work-orders/${id}/status`, payload)
