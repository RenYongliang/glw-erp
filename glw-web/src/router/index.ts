import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { safeRedirect } from '../domain/mockAuth'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/customers',
      name: 'customers',
      component: () => import('../views/CustomerManagementView.vue'),
      props: { kind: 'customers' },
      meta: { title: '客户' },
    },
    {
      path: '/followups',
      name: 'followups',
      component: () => import('../views/CustomerManagementView.vue'),
      props: { kind: 'followups' },
      meta: { title: '跟进记录' },
    },
    {
      path: '/customers',
      name: 'customers',
      component: () => import('../views/CustomerManagementView.vue'),
      props: { kind: 'customers' },
      meta: { title: '客户' },
    },
    {
      path: '/followups',
      name: 'followups',
      component: () => import('../views/CustomerManagementView.vue'),
      props: { kind: 'followups' },
      meta: { title: '跟进记录' },
    },
    {
      path: '/boards/inventory',
      name: 'inventory-board',
      component: () => import('../views/InventoryBoardView.vue'),
      meta: { title: '库存统计看板' },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { public: true, title: '登录工作台' },
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
    {
      path: '/boards/sales',
      name: 'sales-board',
      component: () => import('../views/OrderBoardView.vue'),
      props: { kind: 'sales' },
      meta: { title: '销售订单看板' },
    },
    {
      path: '/boards/purchase',
      name: 'purchase-board',
      component: () => import('../views/OrderBoardView.vue'),
      props: { kind: 'purchase' },
      meta: { title: '采购订单看板' },
    },
    {
      path: '/orders',
      name: 'orders',
      component: () => import('../components/OrderTracking.vue'),
    },
    { path: '/', name: 'home', component: HomeView },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  try {
    await auth.ensureSession()
  } catch {
    /* Retry transient failures on the next request. */
  }
  auth.sync()
  if (!to.meta.public && !auth.isAuthenticated)
    return { name: 'login', query: { redirect: to.fullPath }, replace: true }
  if (to.name === 'login' && auth.isAuthenticated)
    return { path: safeRedirect(to.query.redirect), replace: true }
})
router.afterEach((to) => {
  document.title = `${String(to.meta.title || '工作台')} · GLW 进销存`
})
export default router
