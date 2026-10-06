/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { createApp } from 'vue'
import App from '@/App.vue'
import router from '@/router'
import { installFontAwesome } from '@/plugins/fontawesome'
import { i18n, setLocale } from '@/i18n'
import { installTheme } from '@/plugins/theme'
import '@/style.css'

// Apply the persisted / detected locale immediately so <html lang> is right
// before anything renders.
setLocale(i18n.global.locale.value as never)
installTheme()

const app = createApp(App)
installFontAwesome(app)
app.use(router)
app.use(i18n)
app.mount('#app')
