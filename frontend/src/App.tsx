import { useState } from 'react'
import { CreateGroupForm } from './components/CreateGroupForm'
import type { GroupResponse } from './api/types'

function App() {
  const [group, setGroup] = useState<GroupResponse | null>(null)

  return (
    <main>
      <h1>Expense Split Ledger</h1>
      {group === null ? (
        <CreateGroupForm onCreated={setGroup} />
      ) : (
        <>
          <h2>
            {group.name} ({group.currency})
          </h2>
          <ul>
            {group.members.map((member) => (
              <li key={member.id}>{member.name}</li>
            ))}
          </ul>
        </>
      )}
    </main>
  )
}

export default App
