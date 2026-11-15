import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { CreateGroupForm } from './CreateGroupForm'

const groupResponse = {
  id: 'g1',
  name: 'Goa trip',
  currency: 'INR',
  members: [
    { id: 'm1', name: 'Alice' },
    { id: 'm2', name: 'Bob' },
  ],
}

// The client only reads ok/status/json, so a plain stub is enough here.
const httpResponse = (ok: boolean, status: number, body: unknown) =>
  ({ ok, status, json: async () => body }) as unknown as Response

const nameField = () => screen.getByLabelText('Group name')
const currencyField = () => screen.getByLabelText('Currency')
const memberFields = () => screen.getAllByLabelText('Member name')
const submitButton = () => screen.getByRole('button', { name: 'Create group' })

describe('CreateGroupForm', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('creates a group and reports it to the parent', async () => {
    const onCreated = vi.fn()
    const fetchMock = vi.fn().mockResolvedValue(httpResponse(true, 201, groupResponse))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<CreateGroupForm onCreated={onCreated} />)

    expect(memberFields()).toHaveLength(2)
    await user.type(nameField(), 'Goa trip')
    expect(currencyField()).toHaveValue('INR')
    const members = memberFields()
    await user.type(members[0], 'Alice')
    await user.type(members[1], 'Bob')
    await user.click(submitButton())

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/groups')
    expect(init.method).toBe('POST')
    const body: unknown = JSON.parse(String(init.body))
    expect(body).toEqual({ name: 'Goa trip', currency: 'INR', members: ['Alice', 'Bob'] })

    await waitFor(() => expect(onCreated).toHaveBeenCalledTimes(1))
    expect(onCreated).toHaveBeenCalledWith(groupResponse)
  })

  it('shows a validation message and does not call the API when a member name is blank', async () => {
    const onCreated = vi.fn()
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<CreateGroupForm onCreated={onCreated} />)

    const members = memberFields()
    await user.type(nameField(), 'Goa trip')
    await user.type(members[0], 'Alice')
    await user.click(submitButton())

    expect(
      await screen.findByText('group name and at least two members are required'),
    ).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
    expect(onCreated).not.toHaveBeenCalled()
  })

  it("shows the server's error message when the request fails", async () => {
    const onCreated = vi.fn()
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        httpResponse(false, 422, {
          error: 'unprocessable',
          message: 'group name must not be blank',
          timestamp: '2026-01-01T00:00:00Z',
        }),
      )
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<CreateGroupForm onCreated={onCreated} />)

    await user.type(nameField(), 'Goa trip')
    const members = memberFields()
    await user.type(members[0], 'Alice')
    await user.type(members[1], 'Bob')
    await user.click(submitButton())

    expect(await screen.findByText('group name must not be blank')).toBeInTheDocument()
    expect(onCreated).not.toHaveBeenCalled()
  })
})
