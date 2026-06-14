<script setup>
import { reactive, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'

const tab = shallowRef('alerts')
const alertRows = reactive([
  { type: '电弧炉', temperature: 1050, vibration: 18, pressure: 55, enabled: true },
  { type: '循环水泵', temperature: 85, vibration: 5, pressure: 70, enabled: true },
  { type: '空压机', temperature: 100, vibration: 8, pressure: 90, enabled: true },
  { type: '钢包精炼炉', temperature: 980, vibration: 15, pressure: 60, enabled: true },
  { type: '连铸机', temperature: 650, vibration: 12, pressure: 65, enabled: true },
  { type: '除尘系统', temperature: 90, vibration: 10, pressure: 50, enabled: false }
])
const prices = reactive([
  { name: '尖峰', range: '7-8月19:00-21:00 / 1/12月18:00-20:00', price: 1.25 },
  { name: '峰', range: '08:00-11:00 / 18:00-23:00', price: 0.95 },
  { name: '平', range: '06:00-08:00 / 11:00-18:00', price: 0.60 },
  { name: '谷', range: '23:00-次日06:00', price: 0.32 },
  { name: '深谷', range: '节假日 00:00-06:00', price: 0.22 }
])
const llm = reactive({ apiKey: '', model: 'qwen-plus', timeout: 30 })
const retention = reactive({ sensorDays: 365, auditDays: 730, workOrderDays: 1825, autoArchive: true })
const save = () => {
  localStorage.setItem('smart-energy-config', JSON.stringify({ alertRows, prices, llm: { ...llm, apiKey: llm.apiKey ? 'configured' : '' }, retention }))
  ElMessage.success('配置已保存')
}
</script>

<template>
  <div class="page-shell">
    <div class="page-header"><div><h2 class="page-title">系统配置</h2><p class="page-subtitle">管理告警、电价、模型和数据生命周期策略。</p></div><el-button type="primary" @click="save">保存配置</el-button></div>
    <section class="glass-panel config-panel">
      <el-tabs v-model="tab">
        <el-tab-pane label="告警阈值配置" name="alerts">
          <el-table :data="alertRows">
            <el-table-column prop="type" label="设备类型" min-width="130" />
            <el-table-column label="温度阈值 ℃"><template #default="{ row }"><el-input-number v-model="row.temperature" :min="0" /></template></el-table-column>
            <el-table-column label="振动阈值（毫米/秒）"><template #default="{ row }"><el-input-number v-model="row.vibration" :min="0" /></template></el-table-column>
            <el-table-column label="压力下限（千帕）"><template #default="{ row }"><el-input-number v-model="row.pressure" :min="0" /></template></el-table-column>
            <el-table-column label="启用" width="90"><template #default="{ row }"><el-switch v-model="row.enabled" /></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="电价时段配置" name="prices">
          <el-table :data="prices"><el-table-column prop="name" label="时段" width="100" /><el-table-column label="时间范围"><template #default="{ row }"><el-input v-model="row.range" /></template></el-table-column><el-table-column label="单价（元/千瓦时）"><template #default="{ row }"><el-input-number v-model="row.price" :min="0" :precision="2" :step=".01" /></template></el-table-column></el-table>
        </el-tab-pane>
        <el-tab-pane label="大模型参数配置" name="llm">
          <el-form label-width="100px" class="form-narrow"><el-form-item label="接口密钥"><el-input v-model="llm.apiKey" type="password" show-password /></el-form-item><el-form-item label="模型"><el-select v-model="llm.model"><el-option label="通义千问增强版" value="qwen-plus" /><el-option label="通义千问旗舰版" value="qwen-max" /></el-select></el-form-item><el-form-item label="超时时间"><el-input-number v-model="llm.timeout" :min="5" :max="180" /> 秒</el-form-item></el-form>
        </el-tab-pane>
        <el-tab-pane label="数据保留策略" name="retention">
          <el-form label-width="150px" class="form-narrow"><el-form-item label="传感器数据"><el-input-number v-model="retention.sensorDays" :min="30" /> 天</el-form-item><el-form-item label="审计日志"><el-input-number v-model="retention.auditDays" :min="180" /> 天</el-form-item><el-form-item label="维修工单"><el-input-number v-model="retention.workOrderDays" :min="365" /> 天</el-form-item><el-form-item label="自动归档"><el-switch v-model="retention.autoArchive" /></el-form-item></el-form>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<style scoped>
.config-panel { padding: 20px; }
.form-narrow { max-width: 620px; padding: 22px 0; }
</style>
