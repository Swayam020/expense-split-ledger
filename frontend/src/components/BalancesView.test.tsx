import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { BalancesView } from './BalancesView'

// The client only reads ok/status/json, so a plain stub is enough here.
const httpResponse = (ok: boolean, status: number, body: unknown) =>
  ({ ok, status, json: async () => body }) as unknown as Response

const firstPayload = {
  groupId: 'g1',
  balanced: true,
  balances: [
    { memberId: 'm1', memberName: 'Alice', amount: 50 },
    { memberId: 'm2', memberName: 'Bob', amount: -50 },
  ],
}

const secondPayload = {
  groupId: 'g1',
  balanced: true,
  balances: [
    { memberId: 'm1', memberName: 'Carol', amount: 25.5 },
    { memberId: 'm2', memberName: 'Dave', amount: -25.5 },
  ],
}

describe('BalancesView', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('loads and displays balances on mount', async () => {
    const fetchMock = vi.fn().mockResolvedValue(httpResponse(true, 200, firstPayload))
    vi.stubGlobal('fetch', fetchMock)
    render(<BalancesView groupId="g1" refreshKey={0} />)

    expect(screen.getByText('Loading balances…')).toBeInTheDocument()

    expect(await screen.findByText('Alice: 50.00')).toBeInTheDocument()
    expect(await screen.findByText('Bob: -50.00')).toBeInTheDocument()
    expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8080/api/groups/g1/balances')
  })

  it('re-fetches when refreshKey changes', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(httpResponse(true, 200, firstPayload))
      .mockResolvedValueOnce(httpResponse(true, 200, secondPayload))
    vi.stubGlobal('fetch', fetchMock)
    const { rerender } = render(<BalancesView groupId="g1" refreshKey={0} />)

    expect(await screen.findByText('Alice: 50.00')).toBeInTheDocument()

    rerender(<BalancesView groupId="g1" refreshKey={1} />)

    expect(await screen.findByText('Carol: 25.50')).toBeInTheDocument()
    expect(await screen.findByText('Dave: -25.50')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})
