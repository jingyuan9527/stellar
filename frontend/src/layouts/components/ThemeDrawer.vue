<script setup lang="ts">
import { NDrawer, NDrawerContent, NSwitch, NColorPicker } from 'naive-ui'
import { useThemeStore } from '@/store/theme'

const props = defineProps<{ show: boolean }>()
const emit = defineEmits<{ 'update:show': [value: boolean] }>()

const themeStore = useThemeStore()

const show = computed({
  get: () => props.show,
  set: (v: boolean) => emit('update:show', v),
})

const presetColors = ['#18a058', '#2080f0', '#f0a020', '#d03050', '#8a2be2', '#008080']
</script>

<template>
  <NDrawer v-model:show="show" :width="320" placement="right">
    <NDrawerContent title="主题配置" closable>
      <div class="theme-section">
        <div class="theme-row">
          <span>暗黑模式</span>
          <NSwitch :value="themeStore.darkMode" @update:value="themeStore.toggleDarkMode" />
        </div>
        <div class="theme-row">
          <span>折叠侧栏</span>
          <NSwitch
            :value="themeStore.siderCollapsed"
            @update:value="themeStore.toggleSiderCollapsed"
          />
        </div>
      </div>

      <div class="theme-section">
        <div class="section-title">主题色</div>
        <div class="color-swatches">
          <div
            v-for="color in presetColors"
            :key="color"
            class="swatch"
            :class="{ active: themeStore.primaryColor === color }"
            :style="{ background: color }"
            @click="themeStore.setPrimaryColor(color)"
          />
        </div>
        <div class="color-picker">
          <NColorPicker
            :value="themeStore.primaryColor"
            :show-preview="true"
            @update:value="themeStore.setPrimaryColor"
          />
        </div>
      </div>
    </NDrawerContent>
  </NDrawer>
</template>

<style scoped>
.theme-section {
  margin-bottom: 24px;
}

.theme-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
}

.color-swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.swatch {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  cursor: pointer;
  border: 2px solid transparent;
  transition: transform 0.15s;
}

.swatch:hover {
  transform: scale(1.1);
}

.swatch.active {
  border-color: var(--n-text-color, #333);
}
</style>
