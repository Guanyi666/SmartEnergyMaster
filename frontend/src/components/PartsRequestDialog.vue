<script setup>
import { computed, reactive, watch } from 'vue'

const model = defineModel({ type: Boolean, default: false })
const props = defineProps({
  parts: { type: Array, default: () => [] },
  submitting: Boolean
})
const emit = defineEmits(['submit'])
const quantities = reactive({})

const selectedItems = computed(() => props.parts
  .filter((part) => Number(quantities[part.id] || 0) > 0)
  .map((part) => ({ part, quantity: Number(quantities[part.id]) })))

watch(model, (open) => {
  if (!open) return
  Object.keys(quantities).forEach((key) => delete quantities[key])
})

const changeQuantity = (part, delta) => {
  const current = Number(quantities[part.id] || 0)
  quantities[part.id] = Math.min(part.quantity, Math.max(0, current + delta))
}
</script>

<template>
  <el-dialog v-model="model" title="申请配件" width="760px" append-to-body>
    <p class="dialog-note">可以同时选择多种配件，申请数量不能超过当前库存。</p>
    <div class="part-list">
      <div v-for="part in parts" :key="part.id" class="part-row">
        <div>
          <strong>{{ part.name }}</strong>
          <span>{{ part.spec || '无规格说明' }} · 当前库存 {{ part.quantity }} {{ part.unit }}</span>
        </div>
        <div class="quantity-actions">
          <el-button circle :disabled="!quantities[part.id]" @click="changeQuantity(part, -1)">－</el-button>
          <el-input-number v-model="quantities[part.id]" :min="0" :max="part.quantity" controls-position="right" />
          <el-button circle :disabled="Number(quantities[part.id] || 0) >= part.quantity" @click="changeQuantity(part, 1)">＋</el-button>
        </div>
      </div>
    </div>
    <template #footer>
      <span class="selection-count">已选择 {{ selectedItems.length }} 种配件</span>
      <el-button @click="model = false">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!selectedItems.length" @click="emit('submit', selectedItems)">
        提交申请
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.dialog-note,
.part-row span,
.selection-count {
  color: var(--text-secondary);
  font-size: 12px;
}

.part-list {
  display: grid;
  gap: 10px;
  max-height: 480px;
  overflow: auto;
}

.part-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px;
  border: 1px solid rgba(148, 163, 184, .18);
  border-radius: 10px;
}

.part-row > div:first-child {
  display: grid;
  gap: 5px;
}

.quantity-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.selection-count {
  margin-right: 12px;
}
</style>
