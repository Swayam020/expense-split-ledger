// Mirrors src/main/java/com/swayam/ledger/api/Dtos.java field-for-field for the
// shapes this milestone uses. UUIDs arrive as strings and BigDecimal amounts as
// plain JSON numbers (Jackson's default), so nothing here needs converting.

export interface CreateGroupRequest {
  name: string
  currency: string
  members: string[]
}

export interface MemberResponse {
  id: string
  name: string
}

export interface GroupResponse {
  id: string
  name: string
  currency: string
  members: MemberResponse[]
}

export interface CreateExpenseRequest {
  payerId: string
  description: string
  amount: number
}

export interface ShareResponse {
  memberId: string
  memberName: string
  amount: number
}

export interface ExpenseResponse {
  id: string
  description: string
  amount: number
  payerId: string
  incurredAt: string
  shares: ShareResponse[]
}

export interface BalanceResponse {
  memberId: string
  memberName: string
  amount: number
}

export interface GroupBalancesResponse {
  groupId: string
  balanced: boolean
  balances: BalanceResponse[]
}

export interface SuggestedTransferResponse {
  fromMemberId: string
  fromMemberName: string
  toMemberId: string
  toMemberName: string
  amount: number
}

export interface CreateSettlementRequest {
  fromMemberId: string
  toMemberId: string
  amount: number
}

export interface SettlementResponse {
  id: string
  groupId: string
  fromMemberId: string
  toMemberId: string
  amount: number
  createdAt: string
}

export interface ApiError {
  error: string
  message: string
  timestamp: string
}
