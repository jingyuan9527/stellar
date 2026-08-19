<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { NLayout, NLayoutContent, NBackTop, NDrawer, NDrawerContent, NIcon } from 'naive-ui'
import { useIsMobile } from '@/composables/useBreakpoint'
import { useThemeStore } from '@/store/theme'
import { useAuthStore } from '@/store/auth'
import { useAiNotifyStore } from '@/store/aiNotify'
import { iconMap } from '@/utils/icons'
import LayoutSider from './components/LayoutSider.vue'
import LayoutHeader from './components/LayoutHeader.vue'
import LayoutTabs from './components/LayoutTabs.vue'
import LayoutMenu from './components/LayoutMenu.vue'
import PublicHeader from './components/PublicHeader.vue'
import ThemeDrawer from './components/ThemeDrawer.vue'

const isMobile = useIsMobile()
const themeStore = useThemeStore()
const authStore = useAuthStore()
const aiNotifyStore = useAiNotifyStore()

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

watch(() => authStore.isLogin, () => {
  aiNotifyStore.disconnect()
  aiNotifyStore.connect()
})

onMounted(() => {
  aiNotifyStore.connect()
})

onBeforeUnmount(() => {
  aiNotifyStore.disconnect()
})
</script>

<template>
  <NLayout position="absolute" has-sider>
    <!-- 游客态：独立顶栏 + 内容，无后台侧栏 -->
    <NLayout v-if="!authStore.isLogin" class="layout-main">
      <PublicHeader />
      <NLayoutContent
        class="layout-content public-content"
        :content-style="isMobile ? 'padding: 12px;' : 'padding: 16px;'"
        :native-scrollbar="false"
      >
        <RouterView v-slot="{ Component }">
          <component :is="Component" :key="refreshKey" />
        </RouterView>
        <NBackTop :right="isMobile ? 16 : 40" :bottom="isMobile ? 16 : 40" />
      </NLayoutContent>
    </NLayout>

    <!-- 登录态：完整后台壳 -->
    <template v-else>
      <LayoutSider v-if="!isMobile" />

      <NDrawer v-model:show="mobileSiderShow" placement="left" :width="240" class="mobile-sider">
        <NDrawerContent :native-scrollbar="false">
          <div class="mobile-logo">
            <NIcon size="26" :color="themeStore.primaryColor">
              <component :is="iconMap.grid" />
            </NIcon>
            <span class="mobile-logo-text">Stellar</span>
          </div>
          <LayoutMenu @select="mobileSiderShow = false" />
        </NDrawerContent>
      </NDrawer>

      <NLayout class="layout-main">
        <LayoutHeader @open-theme="themeDrawerShow = true" @toggle-sider="handleToggleSider" />
        <LayoutTabs v-if="authStore.isLogin" @refresh="handleRefresh" />
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
    </template>

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

.public-content {
  background: var(--c-bg);
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