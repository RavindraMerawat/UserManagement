/**
 * Calendar dates, in the timezone the person is standing in.
 *
 * <p>`new Date().toISOString().slice(0, 10)` is the obvious way to get today as
 * yyyy-mm-dd and it is wrong: it returns the date in <b>UTC</b>. India runs at
 * +05:30, so between midnight and half past five every morning the UTC date is
 * still yesterday. A screen defaulting to it asks the server for the wrong day -
 * attendance marked minutes earlier disappears from the zone sheet, and a bulk
 * check in posted from that screen lands on the previous date.</p>
 *
 * <p>`toLocaleDateString('en-CA')` formats as yyyy-mm-dd in local time, which is
 * what every date input and every API date here means. Everything that needs a
 * calendar date should come through this file.</p>
 */

/** A Date as local yyyy-mm-dd. */
export function isoDate(date = new Date()) {
  return date.toLocaleDateString('en-CA')
}

/** Today, where the person is. */
export function todayIso() {
  return isoDate()
}

/** Yesterday, where the person is. */
export function yesterdayIso() {
  const d = new Date()
  d.setDate(d.getDate() - 1)
  return isoDate(d)
}

/** The first of the month the person is in. */
export function monthStartIso() {
  const d = new Date()
  return isoDate(new Date(d.getFullYear(), d.getMonth(), 1))
}

/** yyyy-mm-dd parsed as a local date, never as UTC midnight. */
export function fromIso(iso) {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

/** yyyy-mm-dd -> 05-09-2026, the way the screens print a date. */
export function prettyDate(iso) {
  if (!iso) return '-'
  const [y, m, d] = iso.split('-')
  return `${d}-${m}-${y}`
}
