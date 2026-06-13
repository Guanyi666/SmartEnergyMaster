<script setup>
import { computed, shallowRef } from 'vue'

const props = defineProps({
  recommended: { type: Object, default: null },
  selectedId: { type: Number, default: null },
  options: { type: Array, default: () => [] },
  disabled: Boolean
})

const emit = defineEmits(['accept', 'choose'])
const dialogOpen = shallowRef(false)
const keyword = shallowRef('')
const chosenId = shallowRef(null)
const selectedSop = computed(() => props.options.find((item) => item.id === props.selectedId))
const processSop = computed(() => selectedSop.value || props.recommended)
const visibleOptions = computed(() => {
  const query = keyword.value.trim()
  if (!query) return props.options
  return props.options.filter((item) => [item.title, item.sopCode, item.summary].some((value) => String(value || '').includes(query)))
})

const acceptRecommended = () => props.recommended && emit('accept', props.recommended.id)
const openChoose = () => {
  chosenId.value = props.selectedId
  dialogOpen.value = true
}
const confirmChoose = () => {
  if (!chosenId.value) return
  emit('choose', chosenId.value)
  dialogOpen.value = false
}
</script>

<template>
  <section class="sop-panel">
    <div class="sop-heading">
      <div>
        <h4>智能推荐维修流程</h4>
        <p>{{ recommended?.title || '暂无匹配的维修流程' }}</p>
      </div>
      <el-tag v-if="recommended && selectedId === recommended.id" type="success" effect="dark">已接受推荐</el-tag>
      <el-tag v-else-if="selectedId" type="warning" effect="dark">已选择其他流程</el-tag>
    </div>

    <div class="sop-actions">
      <el-button type="success" :disabled="disabled || !recommended || selectedId === recommended.id" @click="acceptRecommended">
        接受推荐
      </el-button>
      <el-button :disabled="disabled" @click="openChoose">不接受并选择其他流程</el-button>
    </div>

    <ol class="process-list">
      <li v-for="(step, index) in (processSop?.steps || [])" :key="index">{{ step }}</li>
      <li v-if="!processSop?.steps?.length">确认设备停机并执行标准故障排查流程</li>
    </ol>

    <el-dialog v-model="dialogOpen" title="搜索并选择维修流程" width="720px" append-to-body>
      <el-input v-model="keyword" clearable placeholder="输入流程名称、编号或简介" />
      <el-radio-group v-model="chosenId" class="sop-options">
        <el-radio v-for="item in visibleOptions" :key="item.id" :value="item.id" class="sop-option">
          <strong>{{ item.title }}</strong>
          <span>预计 {{ item.estimatedMinutes }} 分钟 · 版本 {{ item.version }}</span>
        </el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :disabled="!chosenId" @click="confirmChoose">确认选择</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.sop-panel {
  padding: 16px;
  border: 1px solid rgba(82, 200, 255, .17);
  border-radius: 14px;
  background: rgba(15, 23, 42, .55);
}

.sop-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.sop-heading h4,
.sop-heading p {
  margin: 0;
}

.sop-heading p {
  margin-top: 6px;
  color: var(--text-secondary);
}

.sop-actions {
  display: flex;
  gap: 8px;
  margin: 14px 0;
}

.process-list {
  display: grid;
  gap: 9px;
  margin: 0;
  padding-left: 22px;
  color: #dbeafe;
  line-height: 1.6;
}

.sop-options {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.sop-option {
  height: auto;
  margin: 0;
  padding: 12px;
  border: 1px solid rgba(148, 163, 184, .2);
  border-radius: 10px;
}

.sop-option :deep(.el-radio__label) {
  display: grid;
  gap: 5px;
  white-space: normal;
}

.sop-option span {
  color: var(--text-secondary);
}
</style>
