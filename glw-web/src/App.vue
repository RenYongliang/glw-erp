<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { RouterView, RouterLink, useRoute, useRouter } from 'vue-router'
import {
  DataAnalysis,
  Search,
  House,
  FolderOpened,
  Document,
  FullScreen,
  Fold,
  Expand,
  ArrowDown,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import UserMenu from './components/UserMenu.vue'
import { useAuthStore } from './stores/auth'
import { SESSION_KEY, LOGOUT_KEY } from './domain/authSession'
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
let sessionTimer: ReturnType<typeof setInterval> | undefined
async function checkSession() {
  try {
    await auth.ensureSession()
  } catch {
    /* Network failures retain the refresh session. */
  }
  auth.sync()
  if (!auth.isAuthenticated && !route.meta.public)
    router.replace({ name: 'login', query: { redirect: route.fullPath } })
}
function storageChanged(event: StorageEvent) {
  if (event.key === LOGOUT_KEY && event.newValue) {
    auth.logout(false)
    checkSession()
  } else if (event.key === SESSION_KEY || event.key === null) checkSession()
}
onMounted(() => {
  window.addEventListener('storage', storageChanged)
  window.addEventListener('focus', checkSession)
  sessionTimer = setInterval(checkSession, 15000)
})
onBeforeUnmount(() => {
  window.removeEventListener('storage', storageChanged)
  window.removeEventListener('focus', checkSession)
  clearInterval(sessionTimer)
})
watch(
  () => auth.isAuthenticated,
  (loggedIn) => {
    if (!loggedIn && !route.meta.public)
      router.replace({ name: 'login', query: { redirect: route.fullPath } })
  },
)
const boardMenus = [
  { title: '库存统计看板', path: '/boards/inventory' },
  { title: '销售订单看板', path: '/boards/sales' },
  { title: '采购订单看板', path: '/boards/purchase' },
]
const customerMenus = [
  { title: '客户', path: '/customers' },
  { title: '跟进记录', path: '/followups' },
]
const customerExpanded = ref(['/customers', '/followups'].includes(route.path))
const boardExpanded = ref(route.path.startsWith('/boards/'))
const pageTitle = computed(() => String(route.meta.title || '首页'))
watch(
  () => route.path,
  () => {
    if (route.path.startsWith('/boards/')) boardExpanded.value = true
    if (['/customers', '/followups'].includes(route.path)) customerExpanded.value = true
    workspace.value?.querySelector('.page-scroll')?.scrollTo({ top: 0 })
  },
)
const query = ref('')
const collapsed = ref(false)
const workspace = ref<HTMLElement>()
const menus = [
  '首页',
  '应用说明',
  '经营看板',
  '基础数据',
  '客户管理',
  '销售管理',
  '采购管理',
  '库存管理',
  '财务管理',
]
const filteredMenus = computed(() =>
  menus.filter(
    (item) =>
      item.includes(query.value.trim()) ||
      (item === '经营看板' && boardMenus.some((menu) => menu.title.includes(query.value.trim()))) ||
      (item === '客户管理' &&
        customerMenus.some((menu) => menu.title.includes(query.value.trim()))),
  ),
)
function navigate(name: string) {
  if (name === '首页') {
    router.push('/')
    workspace.value?.querySelector('.page-scroll')?.scrollTo({ top: 0, behavior: 'smooth' })
  } else if (name === '经营看板') {
    if (collapsed.value) collapsed.value = false
    boardExpanded.value = !boardExpanded.value
  } else if (name === '客户管理') {
    if (collapsed.value) collapsed.value = false
    customerExpanded.value = !customerExpanded.value
  } else ElMessage.info(`${name}将在后续阶段建设。`)
}
async function fullscreen() {
  try {
    if (document.fullscreenElement) await document.exitFullscreen()
    else await workspace.value?.requestFullscreen()
  } catch {
    ElMessage.warning('当前浏览器无法进入全屏模式')
  }
}
</script>

<template>
  <el-config-provider :locale="zhCn" size="default">
    <RouterView v-if="route.meta.public" />
    <div v-else-if="auth.user" class="app-shell">
      <header class="global-header">
        <div class="brand">
          <span class="brand-mark">G</span><strong>GLW 进销存</strong
          ><span class="edition">业务管理工作台</span>
        </div>
        <UserMenu />
      </header>
      <div class="shell-body">
        <aside class="sidebar" :class="{ collapsed }">
          <el-input
            v-if="!collapsed"
            v-model="query"
            class="menu-search"
            placeholder="输入名称来搜索"
            :prefix-icon="Search"
            clearable
            aria-label="搜索菜单"
          />
          <nav aria-label="主导航">
            <template v-for="name in filteredMenus" :key="name">
              <button
                class="nav-item"
                :class="{
                  active: name === '首页' && ['/', '/orders'].includes(route.path),
                }"
                :title="name"
                :aria-label="name"
                :aria-current="
                  name === '首页' && ['/', '/orders'].includes(route.path) ? 'page' : undefined
                "
                :aria-expanded="
                  name === '经营看板'
                    ? boardExpanded
                    : name === '客户管理'
                      ? customerExpanded
                      : undefined
                "
                @click="navigate(name)"
              >
                <el-icon :class="name === '首页' || name === '应用说明' ? 'nav-blue' : 'nav-folder'"
                  ><component
                    :is="name === '首页' ? House : name === '应用说明' ? Document : FolderOpened"
                /></el-icon>
                <span v-if="!collapsed"
                  >{{ name }}<span v-if="name === '应用说明'" class="tip-bulb">💡</span></span
                >
                <el-icon
                  v-if="['经营看板', '客户管理'].includes(name) && !collapsed"
                  class="board-menu-arrow"
                  :class="{
                    open: name === '经营看板' ? boardExpanded : customerExpanded,
                  }"
                  ><ArrowDown
                /></el-icon>
              </button>
              <div v-if="name === '经营看板' && (boardExpanded || query)" class="board-submenu">
                <RouterLink
                  v-for="menu in boardMenus.filter(
                    (item) => !query || '经营看板'.includes(query) || item.title.includes(query),
                  )"
                  :key="menu.path"
                  :to="menu.path"
                  class="nav-item"
                  :class="{ active: route.path === menu.path }"
                  :title="menu.title"
                  :aria-label="menu.title"
                  :aria-current="route.path === menu.path ? 'page' : undefined"
                  ><el-icon><DataAnalysis /></el-icon><span>{{ menu.title }}</span></RouterLink
                >
              </div>
              <div v-if="name === '客户管理' && (customerExpanded || query)" class="board-submenu">
                <RouterLink
                  v-for="menu in customerMenus.filter(
                    (item) => !query || '客户管理'.includes(query) || item.title.includes(query),
                  )"
                  :key="menu.path"
                  :to="menu.path"
                  class="nav-item"
                  :class="{ active: route.path === menu.path }"
                  :title="menu.title"
                  :aria-label="menu.title"
                  :aria-current="route.path === menu.path ? 'page' : undefined"
                  ><el-icon><Document /></el-icon><span>{{ menu.title }}</span></RouterLink
                >
              </div>
            </template>
            <p v-if="!filteredMenus.length" class="menu-empty">没有匹配的菜单</p>
          </nav>
          <button
            class="collapse-trigger"
            :aria-label="collapsed ? '展开菜单' : '收起菜单'"
            @click="collapsed = !collapsed"
          >
            <el-icon><component :is="collapsed ? Expand : Fold" /></el-icon
            ><span v-if="!collapsed">收起菜单</span>
          </button>
        </aside>
        <main ref="workspace" class="workspace">
          <div class="page-heading">
            <strong>{{ pageTitle }}</strong
            ><RouterLink v-if="['/', '/orders'].includes(route.path)" to="/">首页看板</RouterLink
            ><RouterLink v-if="['/', '/orders'].includes(route.path)" to="/orders"
              >首页表单</RouterLink
            ><span class="heading-divider" /><el-button
              text
              :icon="FullScreen"
              aria-label="切换全屏"
              title="切换全屏"
              @click="fullscreen"
            />
          </div>
          <div class="page-scroll"><RouterView :key="route.path" /></div>
        </main>
      </div>
    </div>
  </el-config-provider>
</template>
