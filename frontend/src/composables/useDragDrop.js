// frontend/src/composables/useDragDrop.js
// 拖拽状态封装（vuedraggable 兼容）
import { ref } from 'vue'

export function useDragDrop() {
  const dragging = ref(false)
  const draggedItem = ref(null)
  const targetColumn = ref(null)

  const onDragStart = (item, column) => {
    dragging.value = true
    draggedItem.value = item
    targetColumn.value = null
  }

  const onDragEnter = (column) => {
    if (dragging.value) targetColumn.value = column
  }

  const onDragEnd = () => {
    dragging.value = false
    draggedItem.value = null
    targetColumn.value = null
  }

  return {
    dragging,
    draggedItem,
    targetColumn,
    onDragStart,
    onDragEnter,
    onDragEnd
  }
}
