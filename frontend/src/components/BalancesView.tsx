import { useEffect, useState } from 'react'
import { getBalances } from '../api/client'
import type { GroupBalancesResponse } from '../api/types'

interface BalancesViewProps {
  groupId: string
  refreshKey: number
}

export function BalancesView({ groupId, refreshKey }: BalancesViewProps) {
  const [balances, setBalances] = useState<GroupBalancesResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    // refreshKey is a dependency rather than a manual refetch call, so a posted
    // expense needs to know nothing about how balances are loaded.
    let stale = false
    setError(null)
    getBalances(groupId)
      .then((result) => {
        if (!stale) setBalances(result)
      })
      .catch((e: unknown) => {
        if (!stale) setError(e instanceof Error ? e.message : 'request failed')
      })
    return () => {
      stale = true
    }
  }, [groupId, refreshKey])

  if (error !== null) {
    return (
      <p className="error" role="alert">
        {error}
      </p>
    )
  }
  if (balances === null) {
    return <p>Loading balances…</p>
  }
  return (
    <ul>
      {balances.balances.map((balance) => (
        <li key={balance.memberId}>
          {`${balance.memberName}: ${balance.amount.toFixed(2)}`}
        </li>
      ))}
    </ul>
  )
}
