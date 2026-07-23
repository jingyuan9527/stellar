<script setup lang="ts">
import { ref, watch } from 'vue'
import { NLayout, NLayoutContent, NBackTop, NDrawer, NDrawerContent, NIcon } from 'naive-ui'
import { useIsMobile } from '@/composables/useBreakpoint'
import { useThemeStore } from '@/store/theme'
import { iconMap } from '@/utils/icons'
import LayoutSider from './components/LayoutSider.vue'
import LayoutHeader from './components/LayoutHeader.vue'
import LayoutTabs from './components/LayoutTabs.vue'
import LayoutMenu from './components/LayoutMenu.vue'
import ThemeDrawer from './components/ThemeDrawer.vue'

const isMobile = useIsMobile()
const themeStore = useThemeStore()

const themeDrawerShow = ref(false)
const refreshKey = ref(0)
const mobileSiderShow = ref(false)

function handleToggleSider() {
  if (isMobile.value) {
    mobileSiderShow.value = true
  } else {
    themeStore.toggleSiderCollapsed()
  }
}

function handleRefresh() {
  refreshKey.value++
}

watch(isMobile, (mobile) => {
  if (!mobile) {
    mobileSiderShow.value = false
  }
})
</script>

<template>
  <NLayout position="absolute" has-sider>
    <LayoutSider v-if="!isMobile" />

    <NDrawer v-model:show="mobileSiderShow" placement="left" :width="240" class="mobile-sider">
      <NDrawerContent :native-scrollbar="false">
        <div class="mobile-logo">
          <NIcon size="26" color="#18a058">
            <component :is="iconMap.grid" />
          </NIcon>
          <span class="mobile-logo-text">Stellar Admin</span>
        </div>
        <LayoutMenu @select="mobileSiderShow = false" />
      </NDrawerContent>
    </NDrawer>

    <NLayout class="layout-main">
      <LayoutHeader @open-theme="themeDrawerShow = true" @toggle-sider="handleToggleSider" />
      <LayoutTabs @refresh="handleRefresh" />
      <NLayoutContent
        class="layout-content"
        :content-style="isMobile ? 'padding: 12px;' : 'padding: 16px;'"
        :native-scrollbar="false"
      >
        <RouterView v-slot="{ Component }">
          <component :is="Component" :key="refreshKey" />
        </RouterView>
        <NBackTop :right="isMobile ? 16 : 40" :bottom="isMobile ? 16 : 40" />
      </NLayoutContent>
    </NLayout>
    <ThemeDrawer v-model:show="themeDrawerShow" />
  </NLayout>
</template>

<style scoped>
.layout-main {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.layout-content {
  flex: 1;
  min-height: 0;
}

.mobile-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 56px;
  padding: 0 18px;
  border-bottom: 1px solid var(--n-border-color, rgba(0, 0, 0, 0.06));
}

.mobile-logo-text {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.5px;
}
</style>
