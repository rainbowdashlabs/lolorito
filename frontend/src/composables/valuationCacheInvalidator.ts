/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { onValuationEvent } from '@/composables/useValuationStream'

/**
 * Module-scoped hook. Imported for side-effect from {@code App.vue}
 * so it wires up exactly once per SPA session. When a
 * {@code model.refit} frame arrives we broadcast a small custom event
 * that any interested view (offers list, planner, item detail) can
 * listen for and use to drop its own cached result.
 *
 * <p>We route through {@link CustomEvent} rather than importing
 * every view's local store because there is no shared cache today —
 * each view just refetches on visibility change. This gives them a
 * hook without forcing a global store refactor.
 */
onValuationEvent((event) => {
  window.dispatchEvent(new CustomEvent('lolorito:model-refit', { detail: event }))
})
