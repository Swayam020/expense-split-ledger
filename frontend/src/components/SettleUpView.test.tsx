import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SettleUpView } from './SettleUpView'

// The client only reads ok/status/json, so a plain stub is enough here.
const httpResponse = (ok: boolean, status: number, body: unknown) =>
  ({ ok, status, json: async () => body }) as unknown as Response

const transfersPayload = [
  {
    fromMemberId: 'm2',
    fromMemberName: 'Bob',
    toMemberId: 'm1',
    toMemberName: 'Alice',
    amount: 33.33,
  },
]

const settlementResponse = {
  id: 's1',
  groupId: 'g1',
  fromMemberId: 'm2',
  toMemberId: 'm1',
  amount: 33.33,
  createdAt: '2026-01-01T00:00:00Z',
}

const idempotencyKey = '11111111-1111-4111-8111-111111111111'

const settleButton = () => screen.getByRole('button', { name: 'Settle' })

describe('SettleUpView', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    // unstubAllGlobals only reverses vi.stubGlobal targets, so the crypto.randomUUID
    // spy has to be restored separately.
    vi.restoreAllMocks()
  })

  it('loads and displays suggested transfers on mount', async () => {
    const fetchMock = vi.fn().mockResolvedValue(httpResponse(true, 200, transfersPayload))
    vi.stubGlobal('fetch', fetchMock)
    render(<SettleUpView groupId="g1" refreshKey={0} onSettled={vi.fn()} />)

    expect(screen.getByText('Loading suggested settlements…')).toBeInTheDocument()

    expect(await screen.findByText('Bob owes Alice: 33.33')).toBeInTheDocument()
    expect(fetchMock.mock.calls[0][0]).toBe(
      'http://localhost:8080/api/groups/g1/settlements/suggested',
    )
  })

  it('shows an empty state when there is nothing to settle', async () => {
    const fetchMock = vi.fn().mockResolvedValue(httpResponse(true, 200, []))
    vi.stubGlobal('fetch', fetchMock)
    render(<SettleUpView groupId="g1" refreshKey={0} onSettled={vi.fn()} />)

    expect(await screen.findByText('All settled up.')).toBeInTheDocument()
  })

  it('settles a transfer and reports it to the parent', async () => {
    const onSettled = vi.fn()
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(httpResponse(true, 200, transfersPayload))
      .mockResolvedValueOnce(httpResponse(true, 201, settlementResponse))
    vi.stubGlobal('fetch', fetchMock)
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue(idempotencyKey)
    const user = userEvent.setup()
    render(<SettleUpView groupId="g1" refreshKey={0} onSettled={onSettled} />)

    expect(await screen.findByText('Bob owes Alice: 33.33')).toBeInTheDocument()
    await user.click(settleButton())

    await waitFor(() => expect(onSettled).toHaveBeenCalledTimes(1))
    const [url, init] = fetchMock.mock.calls[1] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/groups/g1/settlements')
    expect(init.method).toBe('POST')
    expect((init.headers as Record<string, string>)['Idempotency-Key']).toBe(idempotencyKey)
    const body: unknown = JSON.parse(String(init.body))
    expect(body).toEqual({ fromMemberId: 'm2', toMemberId: 'm1', amount: 33.33 })
  })

  it("shows the server's error message when settling fails", async () => {
    const onSettled = vi.fn()
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(httpResponse(true, 200, transfersPayload))
      .mockResolvedValueOnce(
        httpResponse(false, 409, {
          error: 'conflict',
          message: 'idempotency key already used with a different request',
          timestamp: '2026-01-01T00:00:00Z',
        }),
      )
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<SettleUpView groupId="g1" refreshKey={0} onSettled={onSettled} />)

    expect(await screen.findByText('Bob owes Alice: 33.33')).toBeInTheDocument()
    await user.click(settleButton())

    expect(
      await screen.findByText('idempotency key already used with a different request'),
    ).toBeInTheDocument()
    expect(onSettled).not.toHaveBeenCalled()
  })
})
