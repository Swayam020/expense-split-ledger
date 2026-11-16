import { useEffect, useState } from 'react'
import { getSuggestedSettlements, settleUp } from '../api/client'
import type { SuggestedTransferResponse } from '../api/types'

interface SettleUpViewProps {
  groupId: string
  refreshKey: number
  onSettled: () => void
}

// One member pair appears at most once in a minimized plan, so the pair identifies a row.
const rowKey = (transfer: SuggestedTransferResponse): string =>
  `${transfer.fromMemberId}-${transfer.toMemberId}`

export function SettleUpView({ groupId, refreshKey, onSettled }: SettleUpViewProps) {
  const [transfers, setTransfers] = useState<SuggestedTransferResponse[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [settlingKey, setSettlingKey] = useState<string | null>(null)
  const [settleError, setSettleError] = useState<string | null>(null)

  useEffect(() => {
    // Same shape as BalancesView: refreshKey drives the refetch, so settling one transfer
    // needs no manual call here — the parent bumping its key reloads this list too.
    let stale = false
    setLoadError(null)
    getSuggestedSettlements(groupId)
      .then((result) => {
        if (!stale) setTransfers(result)
      })
      .catch((e: unknown) => {
        if (!stale) setLoadError(e instanceof Error ? e.message : 'request failed')
      })
    return () => {
      stale = true
    }
  }, [groupId, refreshKey])

  async function settle(transfer: SuggestedTransferResponse) {
    setSettlingKey(rowKey(transfer))
    setSettleError(null)
    try {
      await settleUp(
        groupId,
        {
          fromMemberId: transfer.fromMemberId,
          toMemberId: transfer.toMemberId,
          amount: transfer.amount,
        },
        crypto.randomUUID(),
      )
      setSettlingKey(null)
      onSettled()
    } catch (e) {
      setSettlingKey(null)
      setSettleError(e instanceof Error ? e.message : 'request failed')
    }
  }

  return (
    <section>
      <h3>Settle up</h3>
      {loadError !== null ? (
        <p className="error" role="alert">
          {loadError}
        </p>
      ) : transfers === null ? (
        <p>Loading suggested settlements…</p>
      ) : transfers.length === 0 ? (
        <p>All settled up.</p>
      ) : (
        <>
          <ul>
            {transfers.map((transfer) => (
              <li key={rowKey(transfer)}>
                {`${transfer.fromMemberName} owes ${transfer.toMemberName}: ${transfer.amount.toFixed(2)}`}
                <button
                  type="button"
                  disabled={settlingKey !== null}
                  onClick={() => void settle(transfer)}
                >
                  {settlingKey === rowKey(transfer) ? 'Settling…' : 'Settle'}
                </button>
              </li>
            ))}
          </ul>
          {settleError !== null && (
            <p className="error" role="alert">
              {settleError}
            </p>
          )}
        </>
      )}
    </section>
  )
}
