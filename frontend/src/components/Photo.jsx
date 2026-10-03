import { useEffect, useRef, useState } from 'react'
import { loadPhoto } from '../api/endpoints'

/**
 * Photo of a sewadar or an account, falling back to initials when there is none.
 *
 * `kind` is the API collection: "sewadars" or "users". `stamp` is the record's
 * photoUpdatedAt, which keys the cache so a fresh upload replaces the old image.
 */
export function Avatar({ kind, id, stamp, name, size = 40, square = false }) {
  const [url, setUrl] = useState(null)

  useEffect(() => {
    let cancelled = false
    if (!stamp || !id) {
      setUrl(null)
      return () => {
        cancelled = true
      }
    }
    loadPhoto(kind, id, stamp).then((resolved) => {
      if (!cancelled) setUrl(resolved)
    })
    return () => {
      cancelled = true
    }
  }, [kind, id, stamp])

  const initials = (name || '?')
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

  const style = {
    width: size,
    height: size,
    flex: `0 0 ${size}px`,
    borderRadius: square ? Math.round(size / 6) : '50%',
    fontSize: Math.max(11, Math.round(size / 2.8)),
  }

  if (url) {
    return <img className="photo" src={url} alt={name || 'Photo'} style={style} />
  }
  return (
    <span className="photo photo-initials" style={style} aria-label={name}>
      {initials}
    </span>
  )
}

/**
 * Which photo belongs to the signed-in account, as props for {@link Avatar}.
 *
 * The office does not upload a picture to a login - the photo of a person was taken
 * for their badge and lives on their sewadar record. So an account with one of its
 * own wins, and otherwise the sewadar's is shown, read by id. Neither is copied, and
 * an account with no photo anywhere falls through to initials.
 *
 * The server decides who the sewadar is (by the account link, or by GR. No for the
 * accounts made before that link existed) and sends it on the profile.
 */
export function ownPhoto(user) {
  if (user?.photoUpdatedAt) {
    return { kind: 'users', id: user.userId, stamp: user.photoUpdatedAt }
  }
  if (user?.photoSewadarId && user?.sewadarPhotoUpdatedAt) {
    return { kind: 'sewadars', id: user.photoSewadarId, stamp: user.sewadarPhotoUpdatedAt }
  }
  return { kind: 'users', id: null, stamp: null }
}

/**
 * Photo field for the add/edit forms.
 *
 * A new record has no id yet, so the file is held in state and handed back through
 * `onPick`; the caller uploads it after the record is saved. An existing record
 * uploads straight away through `onUpload`.
 */
/** The same limits the server enforces, so the two cannot disagree. */
const MAX_BYTES = 3 * 1024 * 1024
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']

/** Why this file cannot be used, in the words the person will read, or null. */
function describeProblem(file) {
  if (!ALLOWED_TYPES.includes((file.type || '').toLowerCase())) {
    return 'Choose a JPEG, PNG or WebP image.'
  }
  if (file.size > MAX_BYTES) {
    const mb = (file.size / 1024 / 1024).toFixed(1)
    return `That image is ${mb} MB. The limit is 3 MB - choose a smaller one.`
  }
  return null
}

export function PhotoPicker({
  kind,
  id,
  stamp,
  /*
   * Where to show a photo from when this record has none of its own - an account
   * borrowing the face from its sewadar record, say. Display only: `kind`, `id`
   * and `stamp` above stay the upload target, so choosing a file always writes to
   * the record being edited rather than to the one lending the picture.
   */
  fallback,
  name,
  file,
  onPick,
  onUpload,
  onRemove,
  onReject,
  busy,
  error,
}) {
  const inputRef = useRef(null)
  const [preview, setPreview] = useState(null)

  // Local preview for a file that has not been uploaded yet.
  useEffect(() => {
    if (!file) {
      setPreview(null)
      return undefined
    }
    const objectUrl = URL.createObjectURL(file)
    setPreview(objectUrl)
    return () => URL.revokeObjectURL(objectUrl)
  }, [file])

  const onChange = (event) => {
    const chosen = event.target.files?.[0]
    // Allow re-picking the same file, whatever happens next.
    event.target.value = ''
    if (!chosen) return

    // Check here as well as on the server. The server is what enforces it, but a
    // 12 MB photo should not be pushed up a phone connection only to be refused -
    // and the reason arrives instantly instead of after the upload.
    const problem = describeProblem(chosen)
    if (problem) {
      onReject?.(problem)
      return
    }

    if (id && onUpload) {
      onUpload(chosen)
    } else {
      onPick(chosen)
    }
  }

  return (
    <div className="photo-picker">
      {preview ? (
        <img className="photo" src={preview} alt="Selected" style={{ width: 76, height: 76, borderRadius: 12 }} />
      ) : (
        <Avatar
          kind={stamp ? kind : fallback?.kind || kind}
          id={stamp ? id : fallback?.id ?? null}
          stamp={stamp || fallback?.stamp || null}
          name={name}
          size={76}
          square
        />
      )}

      <div className="photo-picker-actions">
        <input
          ref={inputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          onChange={onChange}
          hidden
        />
        <button
          type="button"
          className="btn ghost small"
          onClick={() => inputRef.current?.click()}
          disabled={busy}
        >
          {busy ? 'Uploading...' : stamp || file ? 'Change photo' : 'Choose photo'}
        </button>
        {(stamp || file) && (
          <button
            type="button"
            className="btn ghost small"
            onClick={() => (file ? onPick(null) : onRemove?.())}
            disabled={busy}
          >
            Remove
          </button>
        )}
        <span className="photo-hint">JPEG, PNG or WebP. 3 MB max.</span>
        {error && <span className="photo-error">{error}</span>}
      </div>
    </div>
  )
}
