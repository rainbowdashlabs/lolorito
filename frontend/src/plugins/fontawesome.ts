/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { App } from 'vue'
import { library, config } from '@fortawesome/fontawesome-svg-core'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import '@fortawesome/fontawesome-svg-core/styles.css'

import {
  faBars,
  faCoins,
  faHome,
  faBoxes,
  faRoute,
  faGear,
  faArrowRight,
  faArrowRightFromBracket,
  faCircleCheck,
  faCircleXmark,
  faCircleInfo,
  faTriangleExclamation,
  faSpinner,
  faXmark,
  faCheck,
  faTrash,
  faMagnifyingGlass,
  faBasketShopping,
  faHammer,
  faWrench,
  faPlus,
  faPrint,
  faCopy,
  faLocationDot,
  faFlagCheckered,
  faBookmark,
  faRotate,
  faShieldHalved,
  faCartShopping,
} from '@fortawesome/free-solid-svg-icons'
import { faDiscord, faGithub } from '@fortawesome/free-brands-svg-icons'

config.autoAddCss = false

library.add(
  faBars,
  faCoins,
  faHome,
  faBoxes,
  faRoute,
  faGear,
  faArrowRight,
  faArrowRightFromBracket,
  faCircleCheck,
  faCircleXmark,
  faCircleInfo,
  faTriangleExclamation,
  faSpinner,
  faXmark,
  faCheck,
  faTrash,
  faMagnifyingGlass,
  faBasketShopping,
  faHammer,
  faWrench,
  faPlus,
  faPrint,
  faCopy,
  faLocationDot,
  faFlagCheckered,
  faBookmark,
  faRotate,
  faShieldHalved,
  faCartShopping,
  faDiscord,
  faGithub,
)

export function installFontAwesome(app: App) {
  app.component('FontAwesomeIcon', FontAwesomeIcon)
}
