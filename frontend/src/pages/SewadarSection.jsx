import { useState } from 'react'
import ConstructionSewa from './sewadar/ConstructionSewa'
import Sewadars from './Sewadars'

/**
 * The Sewadar menu item, which now holds more than one screen.
 *
 * <p>A tab strip rather than a second menu entry: both screens are the sewadar
 * register seen two ways, and the office reads down the menu looking for "Sewadar",
 * not for "Construction Sewa".</p>
 */
export default function SewadarSection() {
  const [tab, setTab] = useState('register')

  const tabs = [
    { key: 'register', label: 'Sewadar Register' },
    { key: 'construction', label: 'Construction Sewa' },
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

      {tab === 'register' && <Sewadars />}
      {tab === 'construction' && <ConstructionSewa />}
    </div>
  )
}
