import { useState } from 'react'
import BadgeDetails from './BadgeDetails'
import WeeklySeatingSewa from './badge/WeeklySeatingSewa'

/**
 * The Badge Detail menu item, which now holds two kinds of badge work.
 *
 * <p>Weekly seating is a token handed out on a Sunday or Thursday and taken back;
 * the annual satsang badge is issued once and kept. They are the same desk and the
 * same people, so they are two tabs rather than two menu entries.</p>
 */
export default function BadgeSection() {
  const [tab, setTab] = useState('weekly')

  const tabs = [
    { key: 'weekly', label: 'Weekly Seating Sewa' },
    { key: 'annual', label: 'Annual Satsang Sewa' },
  ]

  return (
    <div>
      <div className="tabs">
        {tabs.map((t) => (
          <button
            key={t.key}
            type="button"
            className={tab === t.key ? 'active' : ''}
            onClick={() => setTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'weekly' && <WeeklySeatingSewa />}
      {tab === 'annual' && <BadgeDetails />}
    </div>
  )
}
