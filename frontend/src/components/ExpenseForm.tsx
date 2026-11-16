import { useState } from 'react'
import type { FormEvent } from 'react'
import { postExpense } from '../api/client'
import type { MemberResponse } from '../api/types'

const VALIDATION_MESSAGE = 'description and a positive amount are required'

interface ExpenseFormProps {
  groupId: string
  members: MemberResponse[]
  onPosted: () => void
}

export function ExpenseForm({ groupId, members, onPosted }: ExpenseFormProps) {
  // Seeded with the first member instead of a blank option: a <select> is always
  // on some option, so the state starts where the DOM already is.
  const [payerId, setPayerId] = useState(members[0]?.id ?? '')
  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const parsedAmount = Number(amount)
    if (description.trim() === '' || !Number.isFinite(parsedAmount) || parsedAmount <= 0) {
      setMessage(VALIDATION_MESSAGE)
      return
    }

    setMessage(null)
    setSubmitting(true)
    try {
      await postExpense(groupId, {
        payerId,
        description: description.trim(),
        amount: parsedAmount,
      })
      setDescription('')
      setAmount('')
      onPosted()
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'request failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    // noValidate: min/step on the amount input would otherwise make the browser
    // block submission (and so our onSubmit) before our own check can report the
    // inline message, so the JS validation below is the single source of the error.
    <form noValidate onSubmit={submit}>
      <label>
        Payer
        <select value={payerId} onChange={(e) => setPayerId(e.target.value)}>
          {members.map((member) => (
            <option key={member.id} value={member.id}>
              {member.name}
            </option>
          ))}
        </select>
      </label>

      <label>
        Description
        <input type="text" value={description} onChange={(e) => setDescription(e.target.value)} />
      </label>

      <label>
        Amount
        <input
          type="number"
          step="0.01"
          min="0.01"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
        />
      </label>

      <button type="submit" disabled={submitting}>
        {submitting ? 'Posting…' : 'Post expense'}
      </button>

      {message !== null && (
        <p className="error" role="alert">
          {message}
        </p>
      )}
    </form>
  )
}
