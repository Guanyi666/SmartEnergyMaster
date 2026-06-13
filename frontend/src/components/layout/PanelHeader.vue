<template>
  <div class="panel-header">
    <div class="panel-header-text">
      <h3 class="panel-title" :class="`panel-title--${size}`">
        {{ title }}
      </h3>
      <p v-if="subtitle" class="panel-subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.toolbar" class="panel-toolbar">
      <slot name="toolbar" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  size: { type: String, default: 'md' }
})
</script>

<style scoped>
.panel-header-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
</style>