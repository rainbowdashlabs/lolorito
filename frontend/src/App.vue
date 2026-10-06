/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref } from 'vue'
import AppHeader from '@/components/layout/AppHeader.vue'
import AppFooter from '@/components/layout/AppFooter.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import BasketDrawer from '@/components/ffxiv/BasketDrawer.vue'
import ToastContainer from '@/components/layout/ToastContainer.vue'
import { useValuationStream } from '@/composables/useValuationStream'
// Side-effect import — registers the module-level cache-invalidation
// handler so a `model.refit` frame drops stale offers / plan results.
import '@/composables/valuationCacheInvalidator'

const basketOpen = ref(false)
// Mount the WS subscriber for the whole SPA lifetime.
useValuationStream()
</script>

<template>
  <AppHeader @open-basket="basketOpen = true" />
  <main class="flex-1">
    <ViewContent>
      <RouterView />
    </ViewContent>
  </main>
  <AppFooter />
  <BasketDrawer v-model="basketOpen" />
  <ToastContainer />
</template>
