<script setup>
import { reactive, shallowRef, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAgentConfig, updateAgentConfig, getAgentConfigs, updateSubAgentConfig, checkAllAgentsHealth } from '../api/agent'
import { readStoredSession } from '../utils/session'

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
const llm = reactive({ apiKey: '', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', model: 'qwen-plus', timeout: 30, temperature: 0.3, enableThinking: true, maxTokens: 2048 })
const retention = reactive({ sensorDays: 365, auditDays: 730, workOrderDays: 1825, autoArchive: true })

// ========== 多Agent API管理 ==========
const agentList = ref([])
const agentLoading = ref(false)
const agentSaving = ref({})
const agentChecking = ref(false)
const agentCheckResults = ref({})

const AGENT_LABELS = {
  orchestrator: '总管Agent (Orchestrator)',
  production_monitor: '生产监控Agent',
  fault_diagnosis: '故障诊断Agent',
  maintenance_dispatch: '维修调度Agent',
  energy_optimization: '能耗优化Agent',
  management_decision: '管理决策Agent',
  knowledge_agent: '知识管理Agent'
}

const MODEL_OPTIONS = [
  { label: 'qwen-plus (增强版)', value: 'qwen-plus' },
  { label: 'qwen-max (旗舰版)', value: 'qwen-max' },
  { label: 'qwq-plus (深度思考)', value: 'qwq-plus' }
]

async function loadAgentConfigs() {
  agentLoading.value = true
  try {
    const masterCfg = await getAgentConfig()
    const master = {
      key: 'orchestrator',
      name: AGENT_LABELS['orchestrator'],
      api_key: masterCfg.api_key || '****',
      model: masterCfg.model || 'qwen-plus',
      autonomy_level: masterCfg.auto_execute_level || 'L3',
      description: '系统总管，协调调度所有子Agent',
      _newKey: ''
    }
    let subAgents = []
    try {
      const resp = await getAgentConfigs()
      const agents = resp.agents || {}
      subAgents = Object.entries(agents).map(([key, cfg]) => ({
        key,
        name: AGENT_LABELS[key] || cfg.name,
        api_key: cfg.api_key || '****',
        model: cfg.model || 'qwen-plus',
        autonomy_level: cfg.autonomy_level || 'L1',
        description: cfg.description || '',
        _newKey: ''
      }))
    } catch { /* ignore */ }
    agentList.value = [master, ...subAgents]
  } catch {
    agentList.value = []
  } finally {
    agentLoading.value = false
  }
}

async function saveAgentConfig(agent) {
  const session = readStoredSession()
  const role = session?.user?.role || 'OPERATOR'
  if (role !== 'ADMIN') {
    ElMessage.warning('仅管理员可修改Agent API Key')
    return
  }
  agentSaving.value[agent.key] = true
  try {
    if (agent.key === 'orchestrator') {
      await updateAgentConfig({
        role,
        api_key: agent._newKey || undefined,
        model: agent.model
      })
    } else {
      const payload = { role }
      if (agent._newKey) payload.api_key = agent._newKey
      if (agent.model) payload.model = agent.model
      await updateSubAgentConfig(agent.key, payload)
    }
    agent._newKey = ''
    ElMessage.success(`${agent.name} 配置已保存`)
    await loadAgentConfigs()
  } catch (e) {
    ElMessage.error(`保存失败: ${e.message || e}`)
  } finally {
    agentSaving.value[agent.key] = false
  }
}

async function checkAgents() {
  agentChecking.value = true
  agentCheckResults.value = {}
  try {
    const result = await checkAllAgentsHealth()
    agentCheckResults.value = result
    const allOk = Object.values(result).every(r => r?.reachable)
    if (allOk) ElMessage.success('所有Agent API连通正常')
    else {
      const failed = Object.entries(result).filter(([, r]) => !r?.reachable).map(([k]) => AGENT_LABELS[k] || k)
      ElMessage.warning(`部分Agent不可达: ${failed.join(', ')}`)
    }
  } catch {
    ElMessage.error('Agent连通性检查失败，请确认Agent服务已启动')
  } finally {
    agentChecking.value = false
  }
}

onMounted(() => { loadAgentConfig(); loadAgentConfigs() })

/** 从 Agent 后端加载 LLM 配置 */
async function loadAgentConfig() {
  try {
    const cfg = await getAgentConfig()
    if (cfg && cfg.api_key) {
      llm.apiKey = cfg.api_key || ''
      llm.model = cfg.model || 'qwen-plus'
      llm.timeout = cfg.timeout || 30
      llm.temperature = cfg.temperature || 0.3
      llm.enableThinking = cfg.enable_thinking !== false
      llm.maxTokens = cfg.max_tokens || 2048
    }
  } catch (e) {
    // Agent 服务不可用时静默降级，使用本地存储的配置
    const saved = localStorage.getItem('smart-energy-config')
    if (saved) {
      try {
        const parsed = JSON.parse(saved)
        if (parsed.llm) {
          llm.apiKey = parsed.llm.apiKey || ''
          llm.model = parsed.llm.model || 'qwen-plus'
          llm.timeout = parsed.llm.timeout || 30
        }
      } catch { /* ignore */ }
    }
  }
}

const save = async () => {
  // 保存到本地
  localStorage.setItem('smart-energy-config', JSON.stringify({
    alertRows, prices,
    llm: { ...llm, apiKey: llm.apiKey ? 'configured' : '' },
    retention
  }))

  // 同步到 Agent 后端（仅ADMIN有效）
  const session = readStoredSession()
  const role = session?.user?.role || 'OPERATOR'
  try {
    await updateAgentConfig({
      role: role,
      api_key: llm.apiKey || undefined,
      model: llm.model,
      timeout: llm.timeout,
      temperature: llm.temperature,
      enable_thinking: llm.enableThinking,
      max_tokens: llm.maxTokens
    })
    ElMessage.success('配置已保存并同步到AI Agent服务')
  } catch (e) {
    if (e.message?.includes('403') || e.message?.includes('管理员')) {
      ElMessage.warning('配置已本地保存。API Key同步需要ADMIN权限。')
    } else {
      ElMessage.success('配置已本地保存（AI Agent服务未连接，下次连接时自动同步）')
    }
  }
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
          <el-form label-width="120px" class="form-narrow">
            <el-alert
              title="API Key 安全提示"
              description="API Key 首次运行时会自动配置到系统中。修改需要ADMIN权限，且会持久化到Agent服务配置文件。"
              type="info"
              :closable="false"
              show-icon
              style="margin-bottom: 18px;"
            />
            <el-form-item label="接口密钥(API Key)">
              <el-input v-model="llm.apiKey" type="password" show-password placeholder="sk-xxxxxxxx" />
            </el-form-item>
            <el-form-item label="API Base URL">
              <el-input v-model="llm.baseUrl" placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1" disabled />
              <div class="form-hint">阿里云百炼 DashScope 兼容模式</div>
            </el-form-item>
            <el-form-item label="模型选择">
              <el-select v-model="llm.model">
                <el-option label="通义千问增强版 (qwen-plus)" value="qwen-plus" />
                <el-option label="通义千问旗舰版 (qwen-max)" value="qwen-max" />
                <el-option label="通义千问深度思考 (qwq-plus)" value="qwq-plus" />
              </el-select>
            </el-form-item>
            <el-form-item label="生成温度">
              <el-slider v-model="llm.temperature" :min="0" :max="1" :step="0.1" show-input style="max-width: 320px;" />
              <div class="form-hint">越低越确定，越高越有创造性</div>
            </el-form-item>
            <el-form-item label="最大输出Token">
              <el-input-number v-model="llm.maxTokens" :min="256" :max="32768" :step="256" />
            </el-form-item>
            <el-form-item label="超时时间">
              <el-input-number v-model="llm.timeout" :min="5" :max="180" /> 秒
            </el-form-item>
            <el-form-item label="深度思考模式">
              <el-switch v-model="llm.enableThinking" />
              <div class="form-hint">启用后AI会展示推理过程（仅qwen-plus/max支持）</div>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="Agent API管理" name="agents">
          <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px;">
            <span style="font-size:13px;color:var(--el-text-color-secondary);">管理总管及6个专业子Agent的API Key和模型配置</span>
            <el-button size="small" :loading="agentChecking" @click="checkAgents" :icon="'Connection'">检测全部连通性</el-button>
          </div>
          <el-table :data="agentList" v-loading="agentLoading" stripe>
            <el-table-column prop="name" label="Agent名称" min-width="170">
              <template #default="{ row }">
                <div style="font-weight:600">{{ row.name }}</div>
                <div style="font-size:11px;color:var(--el-text-color-secondary)">{{ row.description }}</div>
              </template>
            </el-table-column>
            <el-table-column label="自主级别" width="100" align="center">
              <template #default="{ row }">
                <el-tag :color="row.autonomy_level === 'L3' ? '#00d4aa' : row.autonomy_level === 'L2' ? '#409eff' : '#e6a23c'"
                  style="color:#fff;border:none;font-weight:600;" size="small">
                  {{ row.autonomy_level }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="当前API Key" min-width="200">
              <template #default="{ row }">
                <code style="font-size:12px;background:rgba(0,0,0,0.08);padding:2px 8px;border-radius:4px;">{{ row.api_key }}</code>
                <span v-if="agentCheckResults[row.key]" style="margin-left:8px;">
                  <el-tag v-if="agentCheckResults[row.key].reachable" type="success" size="small">✅ 连通</el-tag>
                  <el-tag v-else type="danger" size="small">❌ {{ agentCheckResults[row.key].message?.slice(0, 20) }}</el-tag>
                </span>
              </template>
            </el-table-column>
            <el-table-column label="新API Key" min-width="220">
              <template #default="{ row }">
                <el-input v-model="row._newKey" type="password" show-password placeholder="输入新Key替换..." size="small" />
              </template>
            </el-table-column>
            <el-table-column label="模型" width="170">
              <template #default="{ row }">
                <el-select v-model="row.model" size="small">
                  <el-option v-for="m in MODEL_OPTIONS" :key="m.value" :label="m.label" :value="m.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" align="center">
              <template #default="{ row }">
                <el-button type="primary" size="small" :loading="agentSaving[row.key]" @click="saveAgentConfig(row)">
                  保存
                </el-button>
              </template>
            </el-table-column>
          </el-table>
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
.form-hint { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 4px; }
</style>
