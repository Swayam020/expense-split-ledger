import { useState } from 'react'
import { CreateGroupForm } from './components/CreateGroupForm'
import { ExpenseForm } from './components/ExpenseForm'
import { BalancesView } from './components/BalancesView'
import { SettleUpView } from './components/SettleUpView'
import type { GroupResponse } from './api/types'

function App() {
  const [group, setGroup] = useState<GroupResponse | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

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
          <ExpenseForm
            groupId={group.id}
            members={group.members}
            onPosted={() => setRefreshKey((key) => key + 1)}
          />
          <BalancesView groupId={group.id} refreshKey={refreshKey} />
          <SettleUpView
            groupId={group.id}
            refreshKey={refreshKey}
            onSettled={() => setRefreshKey((key) => key + 1)}
          />
        </>
      )}
    </main>
  )
}

export default App
