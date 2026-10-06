/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'

export interface ValuationEvent {
  type: 'model.refit'
  itemId: number
  worldId: number
  hq: boolean
}

const EVENTS_CAP = 40

type Handler = (e: ValuationEvent) => void
const handlers = new Set<Handler>()

/**
 * Register a plain (non-Vue-lifecycle) handler for valuation-stream
 * events. Returns an unsubscribe function. Consumers (offers cache,
 * planner cache) call this at module scope to react to refits without
 * needing to be inside a component.
 */
export function onValuationEvent(h: Handler): () => void {
  handlers.add(h)
  return () => handlers.delete(h)
}

/**
 * Subscribe to the backend's /api/v1/ws/valuations push stream. Callers
 * get a reactive tail of recent events; useful for tabs that need to
 * invalidate their own caches when the underlying model refits.
 *
 * <p>Reconnects with exponential backoff; caps out at a handful of
 * seconds so a temporary blip doesn't leave the SPA offline. Cookies
 * flow with the upgrade so the backend can gate the stream to
 * authenticated sessions if it ever wants to.
 */
export function useValuationStream() {
  const events = ref<ValuationEvent[]>([])
  const connected = ref(false)
  let socket: WebSocket | null = null
  let retryDelay = 500
  let stopped = false

  function open() {
    if (stopped) return
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${proto}://${window.location.host}/api/v1/ws/valuations`
    try {
      socket = new WebSocket(url)
    } catch {
      scheduleReconnect()
      return
    }
    socket.onopen = () => {
      connected.value = true
      retryDelay = 500
    }
    socket.onmessage = (msg) => {
      try {
        const parsed = JSON.parse(msg.data) as ValuationEvent
        events.value = [parsed, ...events.value].slice(0, EVENTS_CAP)
        for (const h of handlers) {
          try {
            h(parsed)
          } catch {
            // one bad handler shouldn't kill the rest
          }
        }
      } catch {
        // ignore malformed frames
      }
    }
    socket.onclose = () => {
      connected.value = false
      scheduleReconnect()
    }
    socket.onerror = () => {
      socket?.close()
    }
  }

  function scheduleReconnect() {
    if (stopped) return
    setTimeout(open, retryDelay)
    retryDelay = Math.min(retryDelay * 2, 15_000)
  }

  onMounted(open)
  onBeforeUnmount(() => {
    stopped = true
    socket?.close()
  })

  return { events, connected }
}
