import { useState } from 'react'
import type { FormEvent } from 'react'
import { createGroup } from '../api/client'
import type { GroupResponse } from '../api/types'

const VALIDATION_MESSAGE = 'group name and at least two members are required'

interface CreateGroupFormProps {
  onCreated: (group: GroupResponse) => void
}

export function CreateGroupForm({ onCreated }: CreateGroupFormProps) {
  const [name, setName] = useState('')
  const [currency, setCurrency] = useState('INR')
  const [members, setMembers] = useState<string[]>(['', ''])
  const [message, setMessage] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function changeMember(index: number, value: string) {
    // Index-keyed and positional, so the list only ever grows by one at the end.
    setMembers((current) => current.map((member, i) => (i === index ? value : member)))
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const memberNames = members.map((member) => member.trim()).filter((member) => member !== '')
    if (name.trim() === '' || memberNames.length < 2) {
      setMessage(VALIDATION_MESSAGE)
      return
    }

    setMessage(null)
    setSubmitting(true)
    try {
      onCreated(await createGroup({ name: name.trim(), currency: currency.trim(), members: memberNames }))
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'request failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={submit}>
      <label>
        Group name
        <input type="text" value={name} onChange={(e) => setName(e.target.value)} />
      </label>

      <label>
        Currency
        <input type="text" value={currency} onChange={(e) => setCurrency(e.target.value)} />
      </label>

      {members.map((member, index) => (
        <label key={index}>
          Member name
          <input type="text" value={member} onChange={(e) => changeMember(index, e.target.value)} />
        </label>
      ))}

      <button
        type="button"
        className="secondary"
        onClick={() => setMembers((current) => [...current, ''])}
      >
        Add member
      </button>

      <button type="submit" disabled={submitting}>
        {submitting ? 'Creating…' : 'Create group'}
      </button>

      {message !== null && (
        <p className="error" role="alert">
          {message}
        </p>
      )}
    </form>
  )
}
