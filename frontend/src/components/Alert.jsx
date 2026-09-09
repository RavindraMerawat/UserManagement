/** Inline message bar. `kind` is one of error, success, info or warn. */
export default function Alert({ kind = 'info', children, onClose }) {
  if (!children) return null
  return (
    <div className={`alert ${kind}`}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
        <span>{children}</span>
        {onClose && (
          <button
            type="button"
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              fontSize: 16,
              lineHeight: 1,
              color: 'inherit',
            }}
            aria-label="Dismiss"
          >
            &times;
          </button>
        )}
      </div>
    </div>
  )
}
