import type { ApiError, CreateGroupRequest, GroupResponse } from './types'

const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/**
 * Every non-ok response is an ApiError from the shared exception handler, so the
 * server's own words become the message the UI shows. Anything that is not that
 * JSON — a proxy's error page, a truncated body — has no message to borrow.
 */
export async function createGroup(req: CreateGroupRequest): Promise<GroupResponse> {
  const response = await fetch(`${API_BASE_URL}/api/groups`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
  if (!response.ok) {
    let message: string
    try {
      message = (await response.json() as ApiError).message
    } catch {
      throw new Error('request failed')
    }
    throw new Error(message)
  }
  return (await response.json()) as GroupResponse
}
