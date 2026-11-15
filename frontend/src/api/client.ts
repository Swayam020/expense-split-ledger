import type {
  ApiError,
  CreateExpenseRequest,
  CreateGroupRequest,
  ExpenseResponse,
  GroupBalancesResponse,
  GroupResponse,
} from './types'

const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/**
 * Every non-ok response is an ApiError from the shared exception handler, so the
 * server's own words become the message the UI shows. Anything that is not that
 * JSON — a proxy's error page, a truncated body — has no message to borrow.
 */
async function toFailure(response: Response): Promise<Error> {
  let message: string
  try {
    message = (await response.json() as ApiError).message
  } catch {
    return new Error('request failed')
  }
  return new Error(message)
}

export async function createGroup(req: CreateGroupRequest): Promise<GroupResponse> {
  const response = await fetch(`${API_BASE_URL}/api/groups`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
  if (!response.ok) {
    throw await toFailure(response)
  }
  return (await response.json()) as GroupResponse
}

export async function postExpense(
  groupId: string,
  req: CreateExpenseRequest,
): Promise<ExpenseResponse> {
  const response = await fetch(`${API_BASE_URL}/api/groups/${groupId}/expenses`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  })
  if (!response.ok) {
    throw await toFailure(response)
  }
  return (await response.json()) as ExpenseResponse
}

/** A GET fails the same way as a write: an ApiError body, e.g. for an unknown group id. */
export async function getBalances(groupId: string): Promise<GroupBalancesResponse> {
  const response = await fetch(`${API_BASE_URL}/api/groups/${groupId}/balances`)
  if (!response.ok) {
    throw await toFailure(response)
  }
  return (await response.json()) as GroupBalancesResponse
}
