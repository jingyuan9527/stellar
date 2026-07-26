<script setup lang="ts">
import { computed } from 'vue'
import { NCard, NDrawer, NDrawerContent } from 'naive-ui'
import { useIsMobile } from '@/composables/useBreakpoint'

const props = withDefaults(defineProps<{
  asideTitle?: string
  historyTitle?: string
  drawerTitle?: string
  drawerOpen?: boolean
  drawerWidth?: number
}>(), {
  asideTitle: '生成配置',
  historyTitle: '生成历史',
  drawerTitle: '历史详情',
  drawerOpen: false,
  drawerWidth: 560,
})

const emit = defineEmits<{ 'update:drawerOpen': [boolean] }>()

const isMobile = useIsMobile()
const resolvedWidth = computed(() => (isMobile.value ? '100%' : props.drawerWidth))
</script>

<template>
  <div class="ai-gen-layout">
    <aside class="ai-gen-aside">
      <NCard :title="asideTitle" :bordered="false">
        <slot name="aside" />
      </NCard>
    </aside>
    <main class="ai-gen-main">
      <NCard v-if="$slots.history" :title="historyTitle" :bordered="false">
        <template v-if="$slots['history-extra']" #header-extra>
          <slot name="history-extra" />
        </template>
        <slot name="history" />
      </NCard>
    </main>
    <NDrawer
      :show="drawerOpen"
      :width="resolvedWidth"
      placement="right"
      @update:show="(v: boolean) => emit('update:drawerOpen', v)"
    >
      <NDrawerContent :title="drawerTitle" :native-scrollbar="false" closable>
        <slot name="drawer" />
      </NDrawerContent>
    </NDrawer>
  </div>
</template>

<style scoped>
.ai-gen-layout {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 16px;
  align-items: start;
}

.ai-gen-aside {
  position: sticky;
  top: 8px;
  max-height: calc(100vh - 16px);
  overflow-y: auto;
}

.ai-gen-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

@media (max-width: 1023px) {
  .ai-gen-layout {
    grid-template-columns: 1fr;
  }

  .ai-gen-aside {
    position: static;
    max-height: none;
    overflow-y: visible;
  }
}
</style>
