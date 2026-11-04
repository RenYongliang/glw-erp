<script setup lang="ts">
import { computed, ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
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
  Close,
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
const salesMenus = [
  { title: '销售订单', path: '/sales/orders' },
  { title: '产品出库', path: '/sales/shipments' },
]
const salesExpanded = ref(route.path.startsWith('/sales/'))
const financeMenus = [
  { title: '应收应付', path: '/finance/open-items' },
  { title: '对账单', path: '/finance/statements' },
  { title: '收付款', path: '/finance/payments' },
  { title: '结算档案', path: '/finance/profiles' },
  { title: '文档模板', path: '/finance/templates' },
]
const financeExpanded = ref(route.path.startsWith('/finance/'))
const purchaseMenus = [
  { title: '采购订单', path: '/purchase/orders' },
  { title: '产品入库', path: '/purchase/receipts' },
]
const supplierMenus = [
  { title: '供应商', path: '/suppliers' },
  { title: '供应商品牌', path: '/supplier-brands' },
]
const basicMenus = [
  { title: '产品信息查询', path: '/products/query' },
  { title: '品牌管理', path: '/brands' },
]
const basicExpanded = ref(
  route.path.startsWith('/products/') || route.path === '/brands',
)
const purchaseExpanded = ref(route.path.startsWith('/purchase/'))
const supplierExpanded = ref(route.path.startsWith('/supplier'))
const inventoryExpanded = ref(route.path.startsWith('/inventory/'))
const customerExpanded = ref(['/customers', '/followups'].includes(route.path))
const boardExpanded = ref(route.path.startsWith('/boards/'))
// FIFO: revisiting an existing page updates its URL without changing opening order.
const pageTabs = ref<{ path: string; url: string; title: string }[]>([])
const pageNavigation = ref<HTMLElement>()
async function closePageTab(path: string) {
  const index = pageTabs.value.findIndex(tab => tab.path === path)
  if (index < 0) return
  if (route.path === path) {
    const next = pageTabs.value[index + 1] || pageTabs.value[index - 1]
    const target = next?.url || '/'
    if (target !== route.fullPath) {
      const failure = await router.push(target)
      if (failure) return
    }
  }
  pageTabs.value = pageTabs.value.filter(tab => tab.path !== path)
}

watch(
  () => [route.fullPath, auth.isAuthenticated] as const,
  async () => {
    if (!auth.isAuthenticated || route.meta.public) {
      pageTabs.value = []
      return
    }
    if (!route.matched.length) return
    const title = String(route.meta.title || (route.path === '/orders' ? '首页表单' : '首页'))
    const existing = pageTabs.value.find(tab => tab.path === route.path)
    if (existing) {
      existing.url = route.fullPath
      existing.title = title
    } else {
      pageTabs.value.push({ path: route.path, url: route.fullPath, title })
      if (pageTabs.value.length > 5) pageTabs.value.shift()
    }
    await nextTick()
    pageNavigation.value?.querySelector('[aria-current="page"]')?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  },
  { immediate: true },
)
watch(
  () => route.path,
  () => {
    if (route.path.startsWith('/products/') || route.path === '/brands') {
      basicExpanded.value = true
    }
    if (route.path.startsWith('/sales/')) salesExpanded.value = true
    if (route.path.startsWith('/finance/')) financeExpanded.value = true
    if (route.path.startsWith('/purchase/')) purchaseExpanded.value = true
    if (route.path.startsWith('/supplier')) supplierExpanded.value = true
    if (route.path.startsWith('/inventory/')) inventoryExpanded.value = true
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
  '经营看板',
  '基础数据',
  '客户管理',
  '供应商管理',
  '销售管理',
  '采购管理',
  '库存管理',
  '财务管理',
]
const filteredMenus = computed(() =>
  menus.filter(
    (item) =>
      item.includes(query.value.trim()) ||
      (item === '财务管理' && financeMenus.some(menu => menu.title.includes(query.value.trim()))) ||
      (item === '库存管理' && '库存列表'.includes(query.value.trim())) ||
      (item === '销售管理' && salesMenus.some((menu) => menu.title.includes(query.value.trim()))) ||
      (item === '基础数据' &&
        basicMenus.some((menu) => menu.title.includes(query.value.trim()))) ||
      (item === '经营看板' && boardMenus.some((menu) => menu.title.includes(query.value.trim()))) ||
      (item === '采购管理' &&
        purchaseMenus.some((menu) => menu.title.includes(query.value.trim()))) ||
      (item === '供应商管理' &&
        supplierMenus.some((menu) => menu.title.includes(query.value.trim()))) ||
      (item === '客户管理' &&
        customerMenus.some((menu) => menu.title.includes(query.value.trim()))),
  ),
)
/** 菜单分组是否展开；未知分组不展开。 */
function menuExpanded(name: string) {
  if (name === '销售管理') return salesExpanded.value
  if (name === '基础数据') return basicExpanded.value
  if (name === '经营看板') return boardExpanded.value
  if (name === '客户管理') return customerExpanded.value
  if (name === '采购管理') return purchaseExpanded.value
  if (name === '供应商管理') return supplierExpanded.value
  if (name === '库存管理') return inventoryExpanded.value
  if (name === '财务管理') return financeExpanded.value
  return false
}
function navigate(name: string) {
  if (name === '首页') {
    router.push('/')
    workspace.value?.querySelector('.page-scroll')?.scrollTo({ top: 0, behavior: 'smooth' })
  } else if (name === '基础数据') {
    if (collapsed.value) collapsed.value = false
    basicExpanded.value = !basicExpanded.value
  } else if (name === '经营看板') {
    if (collapsed.value) collapsed.value = false
    boardExpanded.value = !boardExpanded.value
  } else if (name === '销售管理') {
    if (collapsed.value) collapsed.value = false
    salesExpanded.value = !salesExpanded.value
  } else if (name === '采购管理') {
    if (collapsed.value) collapsed.value = false
    purchaseExpanded.value = !purchaseExpanded.value
  } else if (name === '供应商管理') {
    if (collapsed.value) collapsed.value = false
    supplierExpanded.value = !supplierExpanded.value
  } else if (name === '客户管理') {
    if (collapsed.value) collapsed.value = false
    customerExpanded.value = !customerExpanded.value
  } else if (name === '财务管理') {
    if (collapsed.value) collapsed.value = false
    financeExpanded.value = !financeExpanded.value
  } else if (name === '库存管理') {
    if (collapsed.value) collapsed.value = false
    inventoryExpanded.value = !inventoryExpanded.value
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
                :aria-expanded="menuExpanded(name)"
                @click="navigate(name)"
              >
                <el-icon :class="name === '首页' ? 'nav-blue' : 'nav-folder'"
                  ><component
                    :is="name === '首页' ? House : FolderOpened"
                /></el-icon>
                <span v-if="!collapsed">{{ name }}</span>
                <el-icon
                  v-if="
                    ['经营看板', '客户管理', '采购管理', '供应商管理', '基础数据', '销售管理', '库存管理', '财务管理'].includes(name) &&
                    !collapsed
                  "
                  class="board-menu-arrow"
                  :class="{ open: menuExpanded(name) }"
                  ><ArrowDown
                /></el-icon>
              </button>
              <div v-if="name === '基础数据' && (basicExpanded || query)" class="board-submenu">
                <RouterLink
                  v-for="menu in basicMenus.filter(
                    (item) => !query || '基础数据'.includes(query) || item.title.includes(query),
                  )"
                  :key="menu.path"
                  :to="menu.path"
                  class="nav-item"
                  :class="{ active: route.path === menu.path }"
                  :title="menu.title"
                  :aria-label="menu.title"
                  :aria-current="route.path === menu.path ? 'page' : undefined"
                >
                  <el-icon><Document /></el-icon><span>{{ menu.title }}</span>
                </RouterLink>
              </div>
              <div v-if="name === '财务管理' && (financeExpanded || query)" class="board-submenu">
                <RouterLink v-for="menu in financeMenus.filter(item => !query || '财务管理'.includes(query) || item.title.includes(query))" :key="menu.path" :to="menu.path" class="nav-item" :class="{ active: route.path === menu.path }" :title="menu.title" :aria-label="menu.title" :aria-current="route.path === menu.path ? 'page' : undefined"><el-icon><Document /></el-icon><span>{{ menu.title }}</span></RouterLink>
              </div>
              <div v-if="name === '库存管理' && (inventoryExpanded || query)" class="board-submenu">
                <RouterLink to="/inventory/list" class="nav-item" :class="{ active: route.path === '/inventory/list' }"
                  title="库存列表" aria-label="库存列表" :aria-current="route.path === '/inventory/list' ? 'page' : undefined">
                  <el-icon><Document /></el-icon><span>库存列表</span>
                </RouterLink>
              </div>
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
              <div v-if="name === '销售管理' && (salesExpanded || query)" class="board-submenu">
                <RouterLink
                  v-for="menu in salesMenus.filter(
                    (item) => !query || '销售管理'.includes(query) || item.title.includes(query),
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
              <div v-if="name === '采购管理' && (purchaseExpanded || query)" class="board-submenu">
                <RouterLink
                  v-for="menu in purchaseMenus.filter(
                    (item) => !query || '采购管理'.includes(query) || item.title.includes(query),
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
              <div v-if="name === '供应商管理' && (supplierExpanded || query)" class="board-submenu">
                <RouterLink
                  v-for="menu in supplierMenus.filter(
                    (item) => !query || '供应商管理'.includes(query) || item.title.includes(query),
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
            <nav ref="pageNavigation" class="page-tabs" aria-label="已打开的页面">
              <div v-for="tab in pageTabs" :key="tab.path" class="page-tab-item" :class="{ 'is-active': route.path === tab.path }">
                <RouterLink
                  :to="tab.url"
                  class="page-tab"
                  :aria-current="route.path === tab.path ? 'page' : undefined"
                  :title="tab.title"
                >{{ tab.title }}</RouterLink>
                <button type="button" class="page-tab-close" :aria-label="'关闭' + tab.title" :title="'关闭' + tab.title" @click.stop="closePageTab(tab.path)">
                  <el-icon><Close /></el-icon>
                </button>
              </div>
            </nav>
            <span v-if="['/', '/orders'].includes(route.path)" class="home-page-links">
              <RouterLink to="/">首页看板</RouterLink>
              <RouterLink to="/orders">首页表单</RouterLink>
            </span>
            <span class="heading-divider" /><el-button
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

<style scoped>
.page-tabs {
  display: flex;
  align-items: stretch;
  align-self: stretch;
  flex: 1;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: thin;
}
.page-heading .page-tab {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  padding: 0 8px 0 16px;
  border-radius: 0;
  border-bottom: 2px solid transparent;
  white-space: nowrap;
  font-size: 14px;
}
.page-heading .page-tab:hover { color: #1687ff; background: #f3f8ff; }
.page-heading .page-tab-item.is-active { color: #1687ff; background: #e8f2ff; border-bottom-color: #1687ff; }
.page-tab-close:focus-visible, .page-tab:focus-visible { outline: 2px solid #1687ff; outline-offset: -3px; }
.page-tab-item { display: flex; align-items: center; flex-shrink: 0; border-bottom: 2px solid transparent; }
.page-tab-item .page-tab { height: 100%; border-bottom: 0; }
.page-tab-item.is-active .page-tab { color: #1687ff; background: transparent; }
.page-tab-close { display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px; padding: 0; margin-right: 8px; border: 0; border-radius: 50%; background: transparent; color: #8491a4; cursor: pointer; }
.page-tab-close:hover { color: #1687ff; background: #d7eaff; }
.home-page-links { display: flex; flex-shrink: 0; white-space: nowrap; }
.heading-divider { flex-shrink: 0; }
@media (max-width: 700px) {
  .page-heading .page-tab { padding: 0 10px; }
  .home-page-links a { padding: 5px; }
}
</style>
