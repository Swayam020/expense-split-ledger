import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ExpenseForm } from './ExpenseForm'

const members = [
  { id: 'm1', name: 'Alice' },
  { id: 'm2', name: 'Bob' },
]

const expenseResponse = {
  id: 'e1',
  description: 'Dinner',
  amount: 100,
  payerId: 'm1',
  incurredAt: '2026-01-01T00:00:00Z',
  shares: [],
}

// The client only reads ok/status/json, so a plain stub is enough here.
const httpResponse = (ok: boolean, status: number, body: unknown) =>
  ({ ok, status, json: async () => body }) as unknown as Response

const payerField = () => screen.getByLabelText('Payer')
const descriptionField = () => screen.getByLabelText('Description')
const amountField = () => screen.getByLabelText('Amount')
const submitButton = () => screen.getByRole('button', { name: 'Post expense' })

describe('ExpenseForm', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('posts an expense for the selected payer and notifies the parent', async () => {
    const onPosted = vi.fn()
    const fetchMock = vi.fn().mockResolvedValue(httpResponse(true, 201, expenseResponse))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<ExpenseForm groupId="g1" members={members} onPosted={onPosted} />)

    // A <select> with no explicit selection starts on its first option (Alice),
    // so picking Bob is what proves the payer actually comes from the dropdown.
    await user.selectOptions(payerField(), screen.getByRole('option', { name: 'Bob' }))
    await user.type(descriptionField(), 'Dinner')
    await user.type(amountField(), '100')
    await user.click(submitButton())

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/groups/g1/expenses')
    expect(init.method).toBe('POST')
    const body: unknown = JSON.parse(String(init.body))
    expect(body).toEqual({ payerId: 'm2', description: 'Dinner', amount: 100 })

    await waitFor(() => expect(onPosted).toHaveBeenCalledTimes(1))
    expect(descriptionField()).toHaveValue('')
  })

  it('rejects a zero or blank amount without calling the API', async () => {
    const onPosted = vi.fn()
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<ExpenseForm groupId="g1" members={members} onPosted={onPosted} />)

    await user.type(descriptionField(), 'Dinner')
    await user.type(amountField(), '0')
    await user.click(submitButton())

    expect(
      await screen.findByText('description and a positive amount are required'),
    ).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
    expect(onPosted).not.toHaveBeenCalled()
  })
})
