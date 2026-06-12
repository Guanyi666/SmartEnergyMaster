<script setup>
import { computed, onMounted, reactive, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import { listPersonnel, listTransferRequests, reviewTransferRequest } from '../api/workorder'

const requests = shallowRef([])
const personnel = shallowRef([])
const status = shallowRef('PENDING')
const dialogOpen = shallowRef(false)
const current = shallowRef(null)
const submitting = shallowRef(false)
const reviewForm = reactive({ approved: true, newPersonnelId: null, reviewNote: '' })

const visiblePersonnel = computed(() => personnel.value.filter((person) =>
  person.isOnDuty && person.id !== current.value?.requesterPersonnelId
))
const statusLabel = (value) => ({ PENDING: '待审批', APPROVED: '已接受', REJECTED: '已驳回' }[value] || '未知')
const statusType = (value) => ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[value] || 'info')

const load = async () => {
  const [requestList, personnelResult] = await Promise.all([
    listTransferRequests({ status: status.value || undefined }),
    listPersonnel({ pageNum: 1, pageSize: 200, onDuty: true })
  ])
  requests.value = requestList || []
  personnel.value = personnelResult.records || []
}

const openReview = (item, approved) => {
  current.value = item
  Object.assign(reviewForm, { approved, newPersonnelId: null, reviewNote: '' })
  dialogOpen.value = true
}

const submitReview = async () => {
  if (reviewForm.approved && !reviewForm.newPersonnelId) {
    ElMessage.warning('接受申请时必须重新指派人员')
    return
  }
  submitting.value = true
  try {
    await reviewTransferRequest(current.value.id, { ...reviewForm })
    ElMessage.success(reviewForm.approved ? '已接受申请并完成重新指派' : '已驳回转派申请')
    dialogOpen.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2 class="page-title">转派审批</h2>
        <p class="page-subtitle">审核维修工程师提交的转工单申请，接受后必须指派其他维修人员。</p>
      </div>
      <div class="header-actions">
        <el-select v-model="status" placeholder="审批状态" clearable @change="load">
          <el-option label="待审批" value="PENDING" />
          <el-option label="已接受" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <div class="request-grid">
      <article v-for="item in requests" :key="item.id" class="glass-panel request-card">
        <div class="request-heading">
          <div>
            <span>{{ item.workOrderNo }}</span>
            <h3>{{ item.workOrderTitle }}</h3>
          </div>
          <el-tag :type="statusType(item.status)" effect="dark">{{ statusLabel(item.status) }}</el-tag>
        </div>
        <p class="meta">{{ item.deviceName }} · 申请人 {{ item.requesterName }}（{{ item.requesterEmployeeNo }}）</p>
        <div class="reason"><strong>转派缘由</strong><p>{{ item.reason }}</p></div>
        <div v-if="item.status !== 'PENDING'" class="review-result">
          <span>审批人：{{ item.reviewerUsername || '无' }}</span>
          <span v-if="item.newPersonnelName">新指派人员：{{ item.newPersonnelName }}</span>
          <span>审批备注：{{ item.reviewNote || '无' }}</span>
        </div>
        <div v-else class="request-actions">
          <el-button type="danger" @click="openReview(item, false)">驳回</el-button>
          <el-button type="success" @click="openReview(item, true)">接受并重新指派</el-button>
        </div>
      </article>
      <el-empty v-if="!requests.length" description="暂无转派申请" />
    </div>

    <el-dialog v-model="dialogOpen" :title="reviewForm.approved ? '接受转派申请' : '驳回转派申请'" width="600px">
      <el-form label-width="110px">
        <el-form-item v-if="reviewForm.approved" label="重新指派人员" required>
          <el-select v-model="reviewForm.newPersonnelId" filterable placeholder="选择其他维修人员" style="width: 100%">
            <el-option
              v-for="person in visiblePersonnel"
              :key="person.id"
              :label="`${person.name}（${person.employeeNo}，当前负载 ${person.currentWorkload}/${person.maxWorkload}）`"
              :value="person.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="审批备注">
          <el-input v-model="reviewForm.reviewNote" type="textarea" :rows="4" placeholder="填写审批说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button :type="reviewForm.approved ? 'success' : 'danger'" :loading="submitting" @click="submitReview">
          确认提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-shell,
.request-grid {
  display: grid;
  gap: 16px;
}

.header-actions,
.request-heading,
.request-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.request-heading {
  justify-content: space-between;
}

.request-heading span,
.meta,
.review-result {
  color: var(--text-secondary);
  font-size: 12px;
}

.request-heading h3 {
  margin: 6px 0 0;
}

.request-card {
  padding: 18px;
}

.reason,
.review-result {
  margin-top: 14px;
  padding: 12px;
  border-radius: 10px;
  background: rgba(15, 23, 42, .55);
}

.reason p {
  margin: 7px 0 0;
  line-height: 1.7;
}

.review-result {
  display: grid;
  gap: 6px;
}

.request-actions {
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
