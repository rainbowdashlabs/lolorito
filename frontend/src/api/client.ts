/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import axios from 'axios'

/** Talks to /api/v1/* endpoints. */
export const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: {
    'X-Requested-With': 'lolorito',
    'Content-Type': 'application/json',
  },
})

/** Talks to same-origin endpoints outside /api/v1 (auth callbacks etc.). */
export const rootClient = axios.create({
  baseURL: '/',
  headers: {
    'X-Requested-With': 'lolorito',
    'Content-Type': 'application/json',
  },
})
