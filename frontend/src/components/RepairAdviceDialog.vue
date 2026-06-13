<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    :title="title"
    width="780px"
    top="5vh"
    :close-on-click-modal="false"
  >
    <div v-if="advice" class="advice-content">
      <div class="advice-summary glass-card">
        <div class="summary-row">
          <span class="muted">生成策略：</span>
          <el-tag :type="advice.strategy === 'LLM' ? 'success' : 'info'" effect="dark" size="small">
            {{ advice.strategy === 'LLM' ? '大模型增强' : '标准流程路径' }}
          </el-tag>
          <span class="muted" style="margin-left: 16px">整体置信度：</span>
          <el-progress
            :percentage="Math.round((advice.overallConfidence || 0) * 100)"
            :stroke-width="10"
            :color="confidenceColor(Math.round((advice.overallConfidence || 0) * 100))"
            style="width: 140px; display: inline-block; vertical-align: middle"
          />
          <span class="confidence-text">{{ Math.round((advice.overallConfidence || 0) * 100) }}%</span>
        </div>
        <p class="summary-text">{{ advice.summary || '—' }}</p>
      </div>

      <div v-if="advice.matchedSop" class="matched-sop glass-card">
        <div class="matched-header">
          <el-icon><Document /></el-icon>
          <strong>参考标准维修流程</strong>
          <el-tag size="small" effect="dark" style="margin-left: 8px">{{ formatSopCode(advice.matchedSop.sopCode) }}</el-tag>
          <el-tag size="small" type="info" effect="dark" style="margin-left: 6px">v{{ advice.matchedSop.version }}</el-tag>
          <el-tag size="small" type="warning" effect="dark" style="margin-left: 6px">预计 {{ advice.matchedSop.estimatedMinutes }} 分钟</el-tag>
        </div>
        <div class="matched-title">{{ advice.matchedSop.title }}</div>
        <p class="matched-summary">{{ advice.matchedSop.summary }}</p>
      </div>

      <h4 class="section-title">建议步骤（{{ advice.steps?.length || 0 }} 步）</h4>

      <div v-if="!advice.steps?.length" class="empty-tip">
        未生成任何步骤，请确认标准流程是否有具体步骤内容。
      </div>

      <ol class="advice-steps">
        <li v-for="step in advice.steps" :key="step.order" class="step-item" :class="{ 'ai-derived': step.aiDerived }">
          <div class="step-head">
            <span class="step-num">步骤 {{ step.order }}</span>
            <el-tag v-if="step.aiDerived" type="warning" size="small" effect="dark">智能推断</el-tag>
            <el-tag v-else type="success" size="small" effect="dark">
              来源 {{ formatSopCode(step.sourceSopCode) }} 步骤 {{ step.sourceStepIndex + 1 }}
            </el-tag>
            <el-progress
              v-if="step.confidence != null"
              :percentage="Math.round(step.confidence * 100)"
              :stroke-width="6"
              :color="confidenceColor(Math.round((advice.overallConfidence || 0) * 100))"
              style="width: 80px; display: inline-block; vertical-align: middle; margin-left: 8px"
            />
            <span class="confidence-text" style="font-size: 11px">{{ Math.round((step.confidence || 0) * 100) }}%</span>
          </div>
          <div class="step-action">{{ step.action }}</div>
          <div v-if="step.rationale" class="step-rationale muted">理由：{{ step.rationale }}</div>
        </li>
      </ol>
    </div>

    <template #footer>
      <el-button @click="$emit('update:visible', false)">关闭</el-button>
      <el-button type="primary" :loading="loading" @click="$emit('regenerate')">重新生成</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
// 简单函数不需要 computed
import { Document } from '@element-plus/icons-vue'
import { formatSopCode } from '../utils/dict'

const props = defineProps({
  visible: { type: Boolean, default: false },
  advice: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  title: { type: String, default: '智能维修建议' }
})

defineEmits(['update:visible', 'regenerate'])

const confidenceColor = (pct) => {
  if (pct >= 80) return '#3bff9f'
  if (pct >= 50) return '#ffb347'
  return '#ff5d5d'
}
</script>

<style scoped>
.advice-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.glass-card {
  background: rgba(15, 23, 42, 0.55);
  border: 1px solid rgba(148, 163, 184, 0.15);
  border-radius: 8px;
  padding: 14px 18px;
}

.advice-summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.summary-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.summary-text {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.matched-sop {
  border-left: 3px solid #52c8ff;
}

.matched-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #cbd5f5;
}

.matched-title {
  margin-top: 6px;
  font-weight: 600;
  color: #e2e8f0;
}

.matched-summary {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.section-title {
  margin: 4px 0 8px;
  color: #cbd5f5;
  font-size: 14px;
}

.empty-tip {
  padding: 20px;
  text-align: center;
  color: var(--text-secondary);
}

.advice-steps {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.step-item {
  background: rgba(15, 23, 42, 0.4);
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-left: 3px solid #3bff9f;
  border-radius: 6px;
  padding: 10px 14px;
}

.step-item.ai-derived {
  border-left-color: #ffb347;
  background: rgba(255, 179, 71, 0.04);
}

.step-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 13px;
  color: #cbd5f5;
}

.step-num {
  font-weight: 600;
  color: #e2e8f0;
}

.step-action {
  color: #e2e8f0;
  font-size: 14px;
  line-height: 1.6;
  margin-left: 4px;
}

.step-rationale {
  margin-top: 4px;
  font-size: 12px;
  margin-left: 4px;
}

.muted {
  color: var(--text-secondary);
}

.confidence-text {
  color: var(--text-secondary);
  font-size: 12px;
  margin-left: 4px;
}
</style>
