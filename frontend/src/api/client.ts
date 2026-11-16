import type {
  ApiError,
  CreateExpenseRequest,
  CreateGroupRequest,
  CreateSettlementRequest,
  ExpenseResponse,
  GroupBalancesResponse,
  GroupResponse,
  SettlementResponse,
  SuggestedTransferResponse,
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

/** Suggestions are derived from balances server-side, so the UI never minimizes anything. */
export async function getSuggestedSettlements(
  groupId: string,
): Promise<SuggestedTransferResponse[]> {
  const response = await fetch(`${API_BASE_URL}/api/groups/${groupId}/settlements/suggested`)
  if (!response.ok) {
    throw await toFailure(response)
  }
  return (await response.json()) as SuggestedTransferResponse[]
}

/**
 * The key makes a retry (or a double click) land on the settlement already written,
 * so the caller generates one per click rather than per transfer.
 */
export async function settleUp(
  groupId: string,
  req: CreateSettlementRequest,
  idempotencyKey: string,
): Promise<SettlementResponse> {
  const response = await fetch(`${API_BASE_URL}/api/groups/${groupId}/settlements`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(req),
  })
  if (!response.ok) {
    throw await toFailure(response)
  }
  return (await response.json()) as SettlementResponse
}
