<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2 class="page-title">智能调度总览</h2>
        <p class="page-subtitle">从待派工单中自动匹配最合适的维修人员</p>
      </div>
      <div class="header-tools">
        <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <div class="dispatch-row">
      <!-- 左：待派工单 -->
      <div class="left-col glass-panel">
        <div class="col-head">
          <h3>待派工单</h3>
          <span class="count">{{ pendingOrders.length }}</span>
        </div>
        <div class="order-list">
          <div
            v-for="o in pendingOrders" :key="o.id"
            class="order-item"
            :class="{ active: currentOrderId === o.id }"
            @click="selectOrder(o)"
          >
            <div class="order-head">
              <span class="order-no">{{ o.orderNo }}</span>
              <span class="prio" :class="`prio-${(o.priority || 'HIGH').toLowerCase()}`">{{ priorityLabel(o.priority) }}</span>
            </div>
            <div class="order-title">
              <span class="emoji">{{ faultEmoji(o.faultType) }}</span>
              {{ o.title }}
            </div>
            <div class="order-device">{{ o.deviceName }} · {{ o.deviceCode }}</div>
          </div>
          <div v-if="!pendingOrders.length" class="empty-tip">暂无待派工单</div>
        </div>
      </div>

      <!-- 中：自动匹配 Top 3 -->
      <div class="middle-col">
        <div v-if="!currentOrder" class="placeholder glass-panel">
          <el-icon :size="40"><Aim /></el-icon>
          <p>从左侧选一个待派工单</p>
          <p class="sub">系统会按技能匹配 + 等级 + 负载推荐 Top 3</p>
        </div>

        <template v-else>
          <div class="match-head glass-panel">
            <div>
              <p class="match-mark">智能推荐</p>
              <h3>{{ currentOrder.orderNo }} · {{ currentOrder.title }}</h3>
              <div class="required-skills">
                <span>所需技能：</span>
                <span v-for="s in matchResult?.requiredSkills" :key="s" class="skill-chip" :class="`skill-${s}`">{{ s }}</span>
                <span v-if="!matchResult?.requiredSkills?.length" class="none">未知故障类型，无技能解析</span>
              </div>
            </div>
          </div>

          <div class="match-list">
            <div
              v-for="(c, idx) in matchResult?.candidates || []" :key="c.personnelId"
              class="match-card glass-panel"
              :style="{ animationDelay: (idx * 80) + 'ms' }"
            >
              <div class="match-rank">#{{ idx + 1 }}</div>
              <div class="match-avatar" :style="{ background: c.avatarColor || '#52c8ff' }">
                {{ (c.name || '?').charAt(0) }}
              </div>
              <div class="match-info">
                <div class="match-name-row">
                  <strong>{{ c.name }}</strong>
                  <span class="emp-no">{{ c.employeeNo }}</span>
                  <span class="level-badge" :class="`lvl-${skillLevelTone(c.skillLevel)}`">{{ skillLevelLabel(c.skillLevel) }}</span>
                </div>
                <div class="match-skills">
                  <span v-for="s in c.specializations" :key="s" class="skill-chip" :class="{ matched: c.matchedSkills?.includes(s), [`skill-${s}`]: true }">{{ s }}</span>
                </div>
                <div class="match-workload">
                  负载 {{ c.currentWorkload }}/{{ c.maxWorkload }} ({{ c.workloadRate }}%)
                </div>
              </div>
              <div class="match-score">
                <el-progress
                  type="circle"
                  :percentage="c.matchScore"
                  :width="64"
                  :stroke-width="6"
                  :color="scoreColor(c.matchScore)"
                />
                <span class="score-label">{{ c.matchScore }} 分</span>
              </div>
              <div class="match-action">
                <template v-if="c.currentWorkload >= c.maxWorkload">
                  <el-popconfirm
                    title="该人员已满载，强制指派将使其超负荷工作"
                    confirm-button-text="确认强制指派"
                    cancel-button-text="取消"
                    @confirm="assign(c)"
                  >
                    <template #reference>
                      <el-button type="warning" size="default">强制指派</el-button>
                    </template>
                  </el-popconfirm>
                </template>
                <el-button v-else type="primary" size="default" @click="assign(c)">指派</el-button>
              </div>
            </div>

            <!-- 无匹配人员时的兜底操作 -->
            <div v-if="matchResult && !matchResult.candidates?.length" class="fallback-panel">
              <el-alert
                title="未找到匹配的维修人员"
                :description="`当前没有在岗人员满足所需技能：${(matchResult.requiredSkills || []).join('、') || '未知'}。您可以：`"
                type="warning"
                show-icon
                :closable="false"
              />
              <div class="fallback-actions">
                <el-button type="primary" :icon="Aim" @click="showAllPersonnel">
                  查看全部在岗人员
                </el-button>
                <el-button @click="showAllPersonnel">
                  手动选择并指派
                </el-button>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- 右：在岗人员负载矩阵 -->
      <div class="right-col glass-panel">
        <div class="col-head">
          <h3>在岗负载</h3>
          <span class="count">{{ board.offDutyCount || 0 }} 离岗</span>
        </div>
        <div class="board">
          <div v-for="g in board.skillGroups || []" :key="g.skill" class="skill-group">
            <div class="skill-head">
              <span class="skill-chip" :class="`skill-${g.skill}`">{{ g.skill }}</span>
              <span class="group-meta">{{ g.personnelCount }} 人 · {{ g.avgWorkloadRate }}%</span>
            </div>
            <div class="group-bar">
              <div class="group-fill" :class="workloadClass(g.avgWorkloadRate)" :style="{ width: (g.avgWorkloadRate || 0) + '%' }" />
            </div>
            <div class="group-people">
              <div v-for="p in g.personnel" :key="p.id" class="person-mini" :title="`${p.name} (${p.currentWorkload}/${p.maxWorkload})`">
                <div class="mini-avatar" :style="{ background: p.avatarColor || '#52c8ff' }">
                  {{ (p.name || '?').charAt(0) }}
                </div>
                <span class="mini-name">{{ p.name }}</span>
                <span class="mini-load">{{ p.currentWorkload }}/{{ p.maxWorkload }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Aim } from '@element-plus/icons-vue'
import { getWorkOrderList, autoMatch, assignWorkOrder, getDispatchBoard, listPersonnel } from '../api/workorder'
import { skillLevelLabel, skillLevelTone } from '../utils/skillLevel'
import { getFaultTypeMeta, getPriorityMeta } from '../utils/status'

const route = useRoute()

const pendingOrders = ref([])
const currentOrderId = ref(null)
const matchResult = ref(null)
const board = ref({})

const currentOrder = computed(() => pendingOrders.value.find(o => o.id === currentOrderId.value))

const faultEmoji = (f) => getFaultTypeMeta(f).emoji
const faultLabel = (f) => getFaultTypeMeta(f).label
const priorityLabel = (p) => getPriorityMeta(p).label

const scoreColor = (s) => {
  if (s >= 80) return [['#3bff9f', '#52c8ff']]
  if (s >= 60) return [['#52c8ff', '#ff9f43']]
  return [['#ff9f43', '#ff5d5d']]
}

const workloadClass = (rate) => {
  if (rate == null) return ''
  if (rate >= 80) return 'danger'
  if (rate >= 60) return 'warn'
  return 'ok'
}

const loadAll = async () => {
  const [list, b] = await Promise.all([
    getWorkOrderList({ page: 1, size: 100, status: 'PENDING' }),
    getDispatchBoard()
  ])
  pendingOrders.value = list.records || []
  board.value = b || {}
}

const selectOrder = async (o) => {
  currentOrderId.value = o.id
  try {
    const res = await autoMatch({ faultType: o.faultType, workOrderId: o.id, topN: 5 })
    matchResult.value = res
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '匹配推荐加载失败')
  }
}

// 无匹配人员时的兜底：显示全部在岗人员（不限技能匹配）
const showAllPersonnel = async () => {
  try {
    const res = await listPersonnel({ pageNum: 1, pageSize: 50, onDuty: true })
    const all = (res.records || []).map(p => ({
      personnelId: p.id,
      employeeNo: p.employeeNo,
      name: p.name,
      avatarColor: p.avatarColor,
      specializations: p.specializations || [],
      skillLevel: p.skillLevel,
      currentWorkload: p.currentWorkload || 0,
      maxWorkload: p.maxWorkload || 5,
      workloadRate: p.maxWorkload > 0 ? Math.round((p.currentWorkload || 0) * 100 / p.maxWorkload) : 0,
      matchScore: 0,
      matchedSkills: []
    }))
    // 已在工单上的排除
    const existing = matchResult.value?.candidates || []
    const existingIds = new Set(existing.map(c => c.personnelId))
    const fresh = all.filter(c => !existingIds.has(c.personnelId))
    matchResult.value = {
      workOrderId: currentOrderId.value,
      faultType: currentOrder.value?.faultType,
      requiredSkills: matchResult.value?.requiredSkills || [],
      candidates: [...existing, ...fresh]
    }
  } catch (e) {
    ElMessage.error('加载人员列表失败')
  }
}

const assign = async (c) => {
  try {
    await assignWorkOrder(currentOrderId.value, { personnelId: c.personnelId, role: 'PRIMARY' })
    ElMessage.success(`已指派 ${c.name} 处理 ${currentOrder.value.orderNo}`)
    await loadAll()
    // 重新匹配（可能状态变化导致推荐变化）
    if (currentOrderId.value) await selectOrder(currentOrder.value)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '指派失败')
  }
}

watch(() => route.query.workOrderId, (id) => {
  if (id) {
    loadAll().then(() => {
      const o = pendingOrders.value.find(o => o.id === Number(id))
      if (o) selectOrder(o)
    })
  }
}, { immediate: true })

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.page-shell { display: flex; flex-direction: column; gap: 16px; height: 100%; }

.page-header { display: flex; justify-content: space-between; align-items: flex-start; }
.page-title { margin: 0; font-size: 22px; font-weight: 600; color: #e0f2fe; }
.page-subtitle { margin: 4px 0 0; font-size: 12px; color: var(--text-secondary); }

.dispatch-row {
  display: grid;
  grid-template-columns: 280px 1fr 320px;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.left-col, .right-col { display: flex; flex-direction: column; min-height: 0; }

.col-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.15);
}

.col-head h3 { margin: 0; font-size: 14px; color: #e0f2fe; }

.count {
  font-size: 11px;
  padding: 2px 10px;
  background: rgba(82, 200, 255, 0.18);
  color: #52c8ff;
  border-radius: 10px;
}

.order-list { flex: 1; overflow-y: auto; padding: 10px; }

.order-item {
  padding: 10px 12px;
  border-radius: 8px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.order-item:hover { background: rgba(82, 200, 255, 0.06); }
.order-item.active {
  background: rgba(82, 200, 255, 0.12);
  border-color: rgba(82, 200, 255, 0.4);
}

.order-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.order-no {
  font-family: 'SF Mono', Consolas, monospace;
  font-size: 11px;
  color: var(--text-secondary);
}

.prio {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 8px;
}
.prio.prio-critical { background: rgba(167, 139, 250, 0.2); color: #a78bfa; }
.prio.prio-high     { background: rgba(255, 159, 67, 0.2); color: #ff9f43; }

.order-title {
  font-size: 13px;
  color: #e0f2fe;
  margin-bottom: 2px;
}

.order-device {
  font-size: 11px;
  color: var(--text-secondary);
}

.middle-col {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  gap: 8px;
}

.placeholder .sub { font-size: 12px; opacity: 0.7; }

.match-head {
  padding: 16px 20px;
}

.match-mark {
  margin: 0 0 4px;
  font-size: 11px;
  letter-spacing: 3px;
  color: var(--text-secondary);
}

.match-head h3 {
  margin: 0 0 8px;
  font-size: 15px;
  color: #e0f2fe;
}

.required-skills {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
  font-size: 12px;
  color: var(--text-secondary);
}

.skill-chip {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 8px;
  border: 1px solid;
}

.skill-chip.skill-电气   { background: rgba(255, 159, 67, 0.15); border-color: rgba(255, 159, 67, 0.4); color: #ff9f43; }
.skill-chip.skill-机械   { background: rgba(82, 200, 255, 0.15); border-color: rgba(82, 200, 255, 0.4); color: #52c8ff; }
.skill-chip.skill-液压   { background: rgba(59, 255, 159, 0.15); border-color: rgba(59, 255, 159, 0.4); color: #3bff9f; }
.skill-chip.skill-仪表   { background: rgba(167, 139, 250, 0.15); border-color: rgba(167, 139, 250, 0.4); color: #a78bfa; }
.skill-chip.skill-自动化 { background: rgba(244, 114, 182, 0.15); border-color: rgba(244, 114, 182, 0.4); color: #f472b6; }

.skill-chip.matched {
  background: rgba(59, 255, 159, 0.25);
  border-color: rgba(59, 255, 159, 0.6);
  font-weight: 600;
  box-shadow: 0 0 8px rgba(59, 255, 159, 0.3);
}

.none { color: rgba(148, 163, 184, 0.5); font-style: italic; }

.match-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
  flex: 1;
  padding-right: 4px;
}

.match-card {
  display: grid;
  grid-template-columns: 36px 56px 1fr 80px 100px;
  gap: 14px;
  align-items: center;
  padding: 14px 18px;
  animation: slideUp 0.4s ease forwards;
  opacity: 0;
  transform: translateY(8px);
}

@keyframes slideUp {
  to { opacity: 1; transform: translateY(0); }
}

.match-rank {
  font-size: 22px;
  font-weight: 700;
  background: linear-gradient(135deg, #52c8ff, #a78bfa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  text-align: center;
}

.match-avatar {
  width: 50px;
  height: 50px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.match-info { min-width: 0; }

.match-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.match-name-row strong { color: #e0f2fe; font-size: 15px; }

.emp-no {
  font-family: 'SF Mono', Consolas, monospace;
  font-size: 11px;
  color: rgba(82, 200, 255, 0.85);
}

.level-badge {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 6px;
  letter-spacing: 0.5px;
}
.level-badge.lvl-junior        { background: rgba(148, 163, 184, 0.2); color: #cbd5f5; }
.level-badge.lvl-intermediate  { background: rgba(82, 200, 255, 0.2); color: #52c8ff; }
.level-badge.lvl-senior        { background: rgba(255, 159, 67, 0.2); color: #ff9f43; }
.level-badge.lvl-expert        { background: rgba(167, 139, 250, 0.2); color: #a78bfa; }

.match-skills { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 4px; }
.match-workload { font-size: 11px; color: var(--text-secondary); }

.match-score {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.score-label {
  font-size: 11px;
  color: var(--text-secondary);
  font-family: 'SF Mono', Consolas, monospace;
}

.match-action { display: flex; justify-content: center; }

.board { flex: 1; overflow-y: auto; padding: 10px; }

.skill-group {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px dashed rgba(148, 163, 184, 0.15);
}

.skill-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.group-meta { font-size: 11px; color: var(--text-secondary); }

.group-bar {
  height: 4px;
  background: rgba(148, 163, 184, 0.15);
  border-radius: 2px;
  overflow: hidden;
  margin-bottom: 8px;
}

.group-fill { height: 100%; transition: width 0.4s ease; }
.group-fill.ok     { background: linear-gradient(90deg, #3bff9f, #52c8ff); }
.group-fill.warn   { background: linear-gradient(90deg, #ff9f43, #ff5d5d); }
.group-fill.danger { background: linear-gradient(90deg, #ff5d5d, #a78bfa); }

.group-people { display: flex; flex-direction: column; gap: 4px; }

.person-mini {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  border-radius: 6px;
  background: rgba(15, 23, 42, 0.5);
}

.person-mini:hover { background: rgba(82, 200, 255, 0.08); }

.mini-avatar {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  display: grid;
  place-items: center;
  font-size: 11px;
  font-weight: 700;
  color: #0f172a;
  flex-shrink: 0;
}

.mini-name { flex: 1; font-size: 12px; color: #e0f2fe; }
.mini-load { font-size: 10px; color: var(--text-secondary); font-family: 'SF Mono', Consolas, monospace; }

.empty-tip {
  padding: 30px 0;
  text-align: center;
  color: rgba(148, 163, 184, 0.5);
  font-size: 12px;
}

/* 无匹配人员兜底面板 */
.fallback-panel {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.fallback-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}

@media (max-width: 1200px) {
  .dispatch-row { grid-template-columns: 1fr; }
}
</style>
