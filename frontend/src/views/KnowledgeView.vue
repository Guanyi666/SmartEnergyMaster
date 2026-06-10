<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2 class="page-title">维修知识体系</h2>
        <p class="page-subtitle">SOP 标准操作规程、维修案例与知识图谱的统一入口，沉淀维修经验供工程师查阅。</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="knowledge-tabs">
      <!-- Tab 1: SOP -->
      <el-tab-pane label="SOP 标准操作规程" name="sop">
        <div class="glass-panel filter-panel">
          <el-form inline @submit.prevent>
            <el-form-item label="设备类型">
              <el-select v-model="sopFilter.deviceType" clearable placeholder="全部" style="width: 180px">
                <el-option v-for="t in deviceTypes" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="故障类型">
              <el-input v-model="sopFilter.faultType" clearable placeholder="如 机械卡涩" style="width: 200px" />
            </el-form-item>
            <el-form-item label="关键词">
              <el-input v-model="sopFilter.keyword" clearable placeholder="搜索标题/内容" style="width: 220px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadSops">查询</el-button>
              <el-button type="success" @click="openSopDialog()">+ 新增 SOP</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="glass-panel table-panel section-spacer">
          <el-table :data="sops" @row-click="openSop">
            <el-table-column label="SOP 编号" min-width="240">
            <template #default="{ row }">{{ formatSopCode(row.sopCode) }}</template>
          </el-table-column>
          <el-table-column label="设备类型" min-width="100">
            <template #default="{ row }">{{ deviceLabel(row.deviceType) }}</template>
          </el-table-column>
          <el-table-column label="故障类型" min-width="140">
            <template #default="{ row }">{{ faultLabel(row.faultType) }}</template>
          </el-table-column>
            <el-table-column prop="title" label="标题" min-width="220" />
            <el-table-column prop="estimatedMinutes" label="预计耗时" width="110">
              <template #default="{ row }">{{ row.estimatedMinutes }} min</template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="70" />
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag :type="row.isActive ? 'success' : 'info'" size="small">{{ row.isActive ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click.stop="openSopDialog(row)">编辑</el-button>
                <el-button link type="danger" @click.stop="handleDeleteSop(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!sops.length" class="empty-tip">暂无 SOP 数据，点击"+ 新增 SOP"开始登记。</div>
        </div>
      </el-tab-pane>

      <!-- Tab 2: Cases -->
      <el-tab-pane label="维修案例" name="case">
        <div class="glass-panel filter-panel">
          <el-form inline @submit.prevent>
            <el-form-item label="设备类型">
              <el-select v-model="caseFilter.deviceType" clearable placeholder="全部" style="width: 180px">
                <el-option v-for="t in deviceTypes" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="故障类型">
              <el-input v-model="caseFilter.faultType" clearable placeholder="如 机械卡涩" style="width: 200px" />
            </el-form-item>
            <el-form-item label="关键词">
              <el-input v-model="caseFilter.keyword" clearable placeholder="搜索标题/根因/关键词" style="width: 240px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadCases">查询</el-button>
            <el-button type="success" @click="openCaseDialog()">+ 新增案例</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="glass-panel table-panel section-spacer">
          <el-table :data="cases" @row-click="openCase">
            <el-table-column prop="caseCode" label="案例编号" min-width="160" />
            <el-table-column label="设备类型" min-width="100">
              <template #default="{ row }">{{ deviceLabel(row.deviceType) }}</template>
            </el-table-column>
            <el-table-column label="故障类型" min-width="140">
              <template #default="{ row }">{{ faultLabel(row.faultType) }}</template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="260" />
            <el-table-column prop="technician" label="维修人员" min-width="100" />
            <el-table-column prop="durationMinutes" label="耗时" width="100">
              <template #default="{ row }">{{ row.durationMinutes || '--' }} min</template>
            </el-table-column>
            <el-table-column prop="occurredAt" label="发生时间" min-width="170" />
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click.stop="openCaseDialog(row)">编辑</el-button>
                <el-button link type="danger" @click.stop="handleDeleteCase(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!cases.length" class="empty-tip">暂无维修案例。</div>
        </div>
      </el-tab-pane>

      <!-- Tab 3: Graph -->
      <el-tab-pane label="知识图谱" name="graph">
        <div class="glass-panel filter-panel">
          <el-form inline @submit.prevent>
            <el-form-item label="中心节点">
              <el-input v-model="graphCenter" clearable placeholder="如 电弧炉，留空显示全图" style="width: 260px" />
            </el-form-item>
            <el-form-item label="BFS 深度">
              <el-select v-model="graphDepth" style="width: 120px">
                <el-option :value="1" label="1 跳" />
                <el-option :value="2" label="2 跳" />
                <el-option :value="3" label="3 跳" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadGraph">加载图谱</el-button>
              <el-button @click="resetGraph">显示全图</el-button>
            </el-form-item>
          </el-form>
          <div class="legend-row">
            <span class="legend-item"><i class="dot" style="background:#52c8ff"></i>设备类型</span>
            <span class="legend-item"><i class="dot" style="background:#ff9f43"></i>故障类型</span>
            <span class="legend-item"><i class="dot" style="background:#3bff9f"></i>SOP</span>
            <span class="legend-item"><i class="dot" style="background:#c084fc"></i>案例</span>
            <span class="legend-item"><i class="dot" style="background:#f472b6"></i>根因</span>
            <span class="legend-tip muted">支持鼠标拖拽、滚轮缩放、节点悬停查看详情</span>
          </div>
        </div>
        <div class="glass-panel section-spacer">
          <KnowledgeGraph v-if="activeTab === 'graph'" :graph-data="graphData" height="560px" />
          <div v-if="!graphData.nodes.length" class="empty-tip">暂无图谱数据，请先灌入 SOP/案例。</div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- SOP 详情弹窗（只读） -->
    <el-dialog v-model="sopDialog" :title="selectedSop ? selectedSop.title : 'SOP 详情'" width="780px" top="6vh">
      <div v-if="selectedSop" class="detail-content">
        <div class="detail-meta">
          <el-tag>{{ selectedSop.sopCode }}</el-tag>
          <el-tag type="info">{{ selectedSop.deviceType }}</el-tag>
          <el-tag type="warning">{{ selectedSop.faultType }}</el-tag>
          <el-tag type="success">预计 {{ selectedSop.estimatedMinutes }} 分钟</el-tag>
          <el-tag>v{{ selectedSop.version }}</el-tag>
        </div>
        <p class="detail-summary">{{ selectedSop.summary }}</p>
        <h4>操作步骤</h4>
        <ol class="step-list">
          <li v-for="(step, i) in jsonToArray(selectedSop.steps)" :key="i">{{ step }}</li>
        </ol>
        <el-row :gutter="16" class="meta-row">
          <el-col :span="8">
            <h4>所需技能</h4>
            <div class="tag-cluster">
              <el-tag v-for="(s, i) in jsonToArray(selectedSop.requiredSkills)" :key="i" type="info" size="small">{{ s }}</el-tag>
            </div>
          </el-col>
          <el-col :span="8">
            <h4>所需工具</h4>
            <div class="tag-cluster">
              <el-tag v-for="(t, i) in jsonToArray(selectedSop.requiredTools)" :key="i" size="small">{{ t }}</el-tag>
            </div>
          </el-col>
          <el-col :span="8">
            <h4>所需备件</h4>
            <div class="tag-cluster">
              <el-tag v-for="(p, i) in jsonToArray(selectedSop.requiredParts)" :key="i" type="success" size="small">{{ p }}</el-tag>
            </div>
          </el-col>
        </el-row>
        <h4>详细说明</h4>
        <pre class="markdown-body">{{ selectedSop.content }}</pre>
      </div>
    </el-dialog>

    <!-- Case 详情弹窗（只读） -->
    <el-dialog v-model="caseDialog" :title="selectedCase ? selectedCase.title : '案例详情'" width="780px" top="6vh">
      <div v-if="selectedCase" class="detail-content">
        <div class="detail-meta">
          <el-tag>{{ selectedCase.caseCode }}</el-tag>
          <el-tag type="info">{{ selectedCase.deviceType }}</el-tag>
          <el-tag type="warning">{{ selectedCase.faultType }}</el-tag>
          <el-tag v-if="selectedCase.technician">处理人 {{ selectedCase.technician }}</el-tag>
          <el-tag v-if="selectedCase.durationMinutes" type="success">耗时 {{ selectedCase.durationMinutes }} min</el-tag>
        </div>
        <h4>故障现象</h4>
        <p>{{ selectedCase.faultSymptom || '—' }}</p>
        <h4>根因分析</h4>
        <p>{{ selectedCase.rootCause || '—' }}</p>
        <h4>维修过程</h4>
        <pre class="markdown-body">{{ selectedCase.repairProcess || '—' }}</pre>
        <h4>维修结果</h4>
        <p>{{ selectedCase.repairResult || '—' }}</p>
        <h4>关键词</h4>
        <div class="tag-cluster">
          <el-tag v-for="(k, i) in splitKeywords(selectedCase.keywords)" :key="i" size="small">{{ k }}</el-tag>
        </div>
      </div>
    </el-dialog>

    <!-- SOP 新增/编辑弹窗（CRUD） -->
    <el-dialog v-model="sopFormDialog" :title="sopForm.id ? '编辑 SOP' : '新增 SOP'" width="860px" top="5vh">
      <el-form :model="sopForm" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="SOP 编号">
              <el-input v-model="sopForm.sopCode" :disabled="Boolean(sopForm.id)" placeholder="如 SOP-电弧炉-机械卡涩-001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备类型">
              <el-select v-model="sopForm.deviceType" placeholder="选择">
                <el-option v-for="t in deviceTypes" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="故障类型">
              <el-input v-model="sopForm.faultType" placeholder="如 机械卡涩" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预计耗时(分)">
              <el-input-number v-model="sopForm.estimatedMinutes" :min="1" :step="10" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="标题">
              <el-input v-model="sopForm.title" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="简介">
              <el-input v-model="sopForm.summary" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="详细说明">
              <el-input v-model="sopForm.content" type="textarea" :rows="5" placeholder="Markdown 格式" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="操作步骤">
              <el-input v-model="sopForm.steps" type="textarea" :rows="4" placeholder="每行一步" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所需技能">
              <el-input v-model="sopForm.requiredSkills" type="textarea" :rows="4" placeholder="每行一项" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所需工具">
              <el-input v-model="sopForm.requiredTools" type="textarea" :rows="4" placeholder="每行一项" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所需备件">
              <el-input v-model="sopForm.requiredParts" type="textarea" :rows="4" placeholder="每行一项" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用">
              <el-switch v-model="sopForm.isActive" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="sopFormDialog = false">取消</el-button>
        <el-button type="primary" @click="submitSop">保存</el-button>
      </template>
    </el-dialog>
    <!-- Case 新增/编辑弹窗（CRUD） -->
    <el-dialog v-model="caseFormDialog" :title="caseForm.id ? '编辑案例' : '新增案例'" width="860px" top="5vh">
      <el-form :model="caseForm" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="案例编号">
              <el-input v-model="caseForm.caseCode" :disabled="Boolean(caseForm.id)" placeholder="如 RC-2025-001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备类型">
              <el-select v-model="caseForm.deviceType" placeholder="选择">
                <el-option v-for="t in deviceTypes" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="故障类型">
              <el-input v-model="caseForm.faultType" placeholder="如 机械卡涩" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="维修人员">
              <el-input v-model="caseForm.technician" placeholder="如 张工" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="标题">
              <el-input v-model="caseForm.title" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="故障现象">
              <el-input v-model="caseForm.faultSymptom" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="根因分析">
              <el-input v-model="caseForm.rootCause" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="维修过程">
              <el-input v-model="caseForm.repairProcess" type="textarea" :rows="4" placeholder="Markdown 格式" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="维修结果">
              <el-input v-model="caseForm.repairResult" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="耗时(分)">
              <el-input-number v-model="caseForm.durationMinutes" :min="0" :step="10" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联工单 ID">
              <el-input-number v-model="caseForm.relatedWorkOrderId" :min="0" :step="1" placeholder="可选" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发生时间">
              <el-date-picker v-model="caseForm.occurredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择时间" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="关键词">
              <el-input v-model="caseForm.keywords" placeholder="多个关键词用逗号分隔" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="caseFormDialog = false">取消</el-button>
        <el-button type="primary" @click="submitCase">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import KnowledgeGraph from '../components/KnowledgeGraph.vue'
import { DEVICE_TYPE_OPTIONS, deviceLabel, faultLabel, formatSopCode, resolveDeviceCode, resolveFaultCode } from '../utils/dict'
import { createCase, createSop, deleteCase, deleteSop, getKnowledgeGraph, listCases, listSops, updateCase, updateSop } from '../api'

const props = defineProps({ defaultTab: { type: String, default: 'sop' } })
const activeTab = ref(props.defaultTab)
const deviceTypes = DEVICE_TYPE_OPTIONS

const sopFilter = reactive({ deviceType: '', faultType: '', keyword: '' })
const caseFilter = reactive({ deviceType: '', faultType: '', keyword: '' })
const graphCenter = ref('')
const graphDepth = ref(2)

const sops = ref([])
const cases = ref([])
const graphData = ref({ nodes: [], links: [] })

const sopDialog = ref(false)
const caseDialog = ref(false)
const sopFormDialog = ref(false)
const selectedSop = ref(null)
const selectedCase = ref(null)
const sopForm = reactive({
  id: null,
  sopCode: '',
  deviceType: 'ARC_FURNACE',
  faultType: '',
  title: '',
  summary: '',
  content: '',
  steps: '',
  requiredSkills: '',
  requiredTools: '',
  requiredParts: '',
  estimatedMinutes: 60,
  isActive: true,
  createdBy: 'admin'
})

const caseFormDialog = ref(false)
const caseForm = reactive({
  id: null,
  caseCode: '',
  title: '',
  deviceType: 'ARC_FURNACE',
  faultType: '',
  faultSymptom: '',
  rootCause: '',
  repairProcess: '',
  repairResult: '',
  durationMinutes: 60,
  technician: '',
  keywords: '',
  relatedWorkOrderId: null,
  occurredAt: null
})

const arrayToText = (val) => {
  if (!val) return ''
  if (Array.isArray(val)) return val.join('\n')
  if (typeof val === 'string') {
    try { const arr = JSON.parse(val); return Array.isArray(arr) ? arr.join('\n') : val } catch { return val }
  }
  return ''
}

const textToArray = (text) => {
  if (!text) return []
  return text.split('\n').map((s) => s.trim()).filter(Boolean)
}

const loadSops = async () => {
  sops.value = await listSops({
    deviceType: sopFilter.deviceType || undefined,
    faultType: resolveFaultCode(sopFilter.faultType) || undefined,
    keyword: sopFilter.keyword || undefined
  })
}

const loadCases = async () => {
  cases.value = await listCases({
    deviceType: caseFilter.deviceType || undefined,
    faultType: resolveFaultCode(caseFilter.faultType) || undefined,
    keyword: caseFilter.keyword || undefined
  })
}


const loadGraph = async () => {
  const params = { depth: graphDepth.value }
  if (graphCenter.value) params.center = resolveDeviceCode(graphCenter.value) || graphCenter.value
  graphData.value = await getKnowledgeGraph(params)
}

const resetGraph = async () => {
  graphCenter.value = ''
  await loadGraph()
}

const refreshAll = async () => {
  await Promise.all([loadSops(), loadCases(), loadGraph()])
}

const openSop = (row) => {
  selectedSop.value = row
  sopDialog.value = true
}

const openCase = (row) => {
  selectedCase.value = row
  caseDialog.value = true
}

const openSopDialog = (row) => {
  if (row && row.id) {
    Object.assign(sopForm, {
      id: row.id,
      sopCode: row.sopCode,
      deviceType: row.deviceType,
      faultType: row.faultType,
      title: row.title,
      summary: row.summary || '',
      content: row.content || '',
      steps: arrayToText(row.steps),
      requiredSkills: arrayToText(row.requiredSkills),
      requiredTools: arrayToText(row.requiredTools),
      requiredParts: arrayToText(row.requiredParts),
      estimatedMinutes: row.estimatedMinutes || 60,
      isActive: row.isActive !== false,
      createdBy: 'admin'
    })
  } else {
    Object.assign(sopForm, {
      id: null,
      sopCode: '',
      deviceType: 'ARC_FURNACE',
      faultType: '',
      title: '',
      summary: '',
      content: '',
      steps: '',
      requiredSkills: '',
      requiredTools: '',
      requiredParts: '',
      estimatedMinutes: 60,
      isActive: true,
      createdBy: 'admin'
    })
  }
  sopFormDialog.value = true
}

const submitSop = async () => {
  if (!sopForm.sopCode || !sopForm.title || !sopForm.faultType || !sopForm.content) {
    ElMessage.warning('SOP 编号/标题/故障类型/内容必填')
    return
  }
  const payload = {
    sopCode: sopForm.sopCode,
    deviceType: sopForm.deviceType,
    faultType: resolveFaultCode(sopForm.faultType),
    title: sopForm.title,
    summary: sopForm.summary,
    content: sopForm.content,
    steps: textToArray(sopForm.steps),
    requiredSkills: textToArray(sopForm.requiredSkills),
    requiredTools: textToArray(sopForm.requiredTools),
    requiredParts: textToArray(sopForm.requiredParts),
    estimatedMinutes: sopForm.estimatedMinutes,
    isActive: sopForm.isActive,
    createdBy: sopForm.createdBy
  }
  try {
    if (sopForm.id) {
      await updateSop(sopForm.id, payload)
      ElMessage.success('SOP 已更新（版本号 +1）')
    } else {
      await createSop(payload)
      ElMessage.success('SOP 已创建')
    }
    sopFormDialog.value = false
    await loadSops()
  } catch (err) {
    ElMessage.error('保存失败：' + ((err && err.response && err.response.data && err.response.data.message) || (err && err.message) || '未知错误'))
  }
}

const handleDeleteSop = async (row) => {
  try {
    await ElMessageBox.confirm('确认删除 SOP ' + row.sopCode + ' ？关联工单仍会保留，但不会再自动匹配。', '删除确认', { type: 'warning' })
    await deleteSop(row.id)
    ElMessage.success('SOP 已删除')
    await loadSops()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error('删除失败：' + ((err && err.response && err.response.data && err.response.data.message) || (err && err.message) || '未知错误'))
    }
  }
}

const openCaseDialog = (row) => {
  if (row && row.id) {
    Object.assign(caseForm, {
      id: row.id,
      caseCode: row.caseCode,
      title: row.title,
      deviceType: row.deviceType || 'ARC_FURNACE',
      faultType: row.faultType || '',
      faultSymptom: row.faultSymptom || '',
      rootCause: row.rootCause || '',
      repairProcess: row.repairProcess || '',
      repairResult: row.repairResult || '',
      durationMinutes: row.durationMinutes || 0,
      technician: row.technician || '',
      keywords: row.keywords || '',
      relatedWorkOrderId: row.relatedWorkOrderId || null,
      occurredAt: row.occurredAt || null
    })
  } else {
    Object.assign(caseForm, {
      id: null,
      caseCode: '',
      title: '',
      deviceType: 'ARC_FURNACE',
      faultType: '',
      faultSymptom: '',
      rootCause: '',
      repairProcess: '',
      repairResult: '',
      durationMinutes: 60,
      technician: '',
      keywords: '',
      relatedWorkOrderId: null,
      occurredAt: null
    })
  }
  caseFormDialog.value = true
}

const submitCase = async () => {
  if (!caseForm.caseCode || !caseForm.title || !caseForm.faultType) {
    ElMessage.warning('案例编号/标题/故障类型必填')
    return
  }
  const payload = {
    caseCode: caseForm.caseCode,
    title: caseForm.title,
    deviceType: caseForm.deviceType,
    faultType: resolveFaultCode(caseForm.faultType),
    faultSymptom: caseForm.faultSymptom,
    rootCause: caseForm.rootCause,
    repairProcess: caseForm.repairProcess,
    repairResult: caseForm.repairResult,
    durationMinutes: caseForm.durationMinutes,
    technician: caseForm.technician,
    keywords: caseForm.keywords,
    relatedWorkOrderId: caseForm.relatedWorkOrderId || null,
    occurredAt: caseForm.occurredAt
  }
  try {
    if (caseForm.id) {
      await updateCase(caseForm.id, payload)
      ElMessage.success('案例已更新')
    } else {
      await createCase(payload)
      ElMessage.success('案例已创建')
    }
    caseFormDialog.value = false
    await loadCases()
  } catch (err) {
    ElMessage.error('保存失败：' + ((err && err.response && err.response.data && err.response.data.message) || (err && err.message) || '未知错误'))
  }
}

const handleDeleteCase = async (row) => {
  try {
    await ElMessageBox.confirm('确认删除案例 ' + row.caseCode + ' ？', '删除确认', { type: 'warning' })
    await deleteCase(row.id)
    ElMessage.success('案例已删除')
    await loadCases()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error('删除失败：' + ((err && err.response && err.response.data && err.response.data.message) || (err && err.message) || '未知错误'))
    }
  }
}

defineExpose({ openSop, openSopDialog })

const jsonToArray = (value) => {
  if (!value) return []
  if (Array.isArray(value)) return value
  if (typeof value === 'string') {
    try { const arr = JSON.parse(value); return Array.isArray(arr) ? arr : [] } catch { return [] }
  }
  return []
}

const splitKeywords = (raw) => {
  if (!raw) return []
  return String(raw).split(/[,，\s]+/).filter(Boolean)
}

onMounted(refreshAll)
</script>

<style scoped>
.knowledge-tabs {
  background: transparent;
}
.knowledge-tabs :deep(.el-tabs__item) {
  color: var(--text-secondary);
  font-size: 15px;
}
.knowledge-tabs :deep(.el-tabs__item.is-active) {
  color: var(--brand-primary, #52c8ff);
}
.filter-panel {
  padding: 18px 20px;
}
.table-panel {
  padding: 18px 20px;
}
.empty-tip {
  padding: 32px 0;
  text-align: center;
  color: var(--text-secondary);
}
.detail-content h4 {
  margin: 18px 0 8px;
  color: #cbd5f5;
}
.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.detail-summary {
  margin: 12px 0 0;
  color: var(--text-secondary);
  line-height: 1.7;
}
.step-list {
  padding-left: 22px;
  line-height: 1.9;
  color: #e2e8f0;
}
.tag-cluster {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.markdown-body {
  background: rgba(15, 23, 42, 0.6);
  padding: 14px;
  border-radius: 8px;
  white-space: pre-wrap;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13px;
  color: #cbd5f5;
  margin: 0;
}
.meta-row {
  margin-top: 12px;
}
.legend-row {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  padding-top: 4px;
  font-size: 13px;
  color: #cbd5f5;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.legend-item .dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}
.legend-tip {
  font-size: 12px;
  margin-left: auto;
}
.section-spacer {
  margin-top: 16px;
}
</style>