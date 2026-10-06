/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import DashboardView from '@/views/DashboardView.vue'
import LoginView from '@/views/LoginView.vue'
import SettingsView from '@/views/SettingsView.vue'
import OffersView from '@/views/OffersView.vue'
import ItemDetailView from '@/views/ItemDetailView.vue'
import PlannerView from '@/views/PlannerView.vue'
import BasketView from '@/views/BasketView.vue'
import BasketsView from '@/views/BasketsView.vue'
import SharedBasketView from '@/views/SharedBasketView.vue'
import AlertsView from '@/views/AlertsView.vue'
import DesynthView from '@/views/DesynthView.vue'
import ShoppingView from '@/views/ShoppingView.vue'
import { useSession } from '@/composables/useSession'

const routes: RouteRecordRaw[] = [
  { path: '/', name: 'dashboard', component: DashboardView, meta: { requiresAuth: true } },
  { path: '/offers', name: 'offers', component: OffersView, meta: { requiresAuth: true } },
  { path: '/item/:id', name: 'item', component: ItemDetailView, meta: { requiresAuth: true } },
  { path: '/planner', name: 'planner', component: PlannerView, meta: { requiresAuth: true } },
  { path: '/settings', name: 'settings', component: SettingsView, meta: { requiresAuth: true } },
  { path: '/basket', name: 'basket', component: BasketView, meta: { requiresAuth: true } },
  { path: '/baskets', name: 'baskets', component: BasketsView, meta: { requiresAuth: true } },
  { path: '/alerts', name: 'alerts', component: AlertsView, meta: { requiresAuth: true } },
  { path: '/desynth', name: 'desynth', component: DesynthView, meta: { requiresAuth: true } },
  { path: '/shopping', name: 'shopping', component: ShoppingView, meta: { requiresAuth: true } },
  {
    path: '/baskets/shared/:token',
    name: 'baskets-shared',
    component: SharedBasketView,
    meta: { requiresAuth: false },
  },
  { path: '/login', name: 'login', component: LoginView, meta: { requiresAuth: false } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  const session = useSession()
  if (session.status.value === 'unknown') {
    await session.load()
  }
  const requiresAuth = to.meta.requiresAuth !== false
  if (requiresAuth && !session.isAuthenticated.value) {
    return { name: 'login', query: { next: to.fullPath } }
  }
  if (!requiresAuth && session.isAuthenticated.value && to.name === 'login') {
    return { name: 'dashboard' }
  }
})

export default router
