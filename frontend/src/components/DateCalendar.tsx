import { useLayoutEffect, useRef, useState, type RefObject } from 'react'
import { formatDate } from '../utils/dateTime'

const dayValue = (year: number, month: number, day: number) =>
  `${String(year).padStart(4, '0')}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`

export function DateCalendar({
  anchor,
  value,
  min,
  max,
  required,
  onSelect,
  onClose,
}: {
  anchor: RefObject<HTMLDivElement | null>
  value: string
  min?: string
  max?: string
  required?: boolean
  onSelect: (day: string) => void
  onClose: () => void
}) {
  const today = new Date()
  const todayValue = dayValue(today.getFullYear(), today.getMonth(), today.getDate())
  const initial = /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : todayValue
  const [month, setMonth] = useState(() => Number(initial.slice(5, 7)) - 1)
  const [year, setYear] = useState(() => Number(initial.slice(0, 4)))
  const calendar = useRef<HTMLDivElement>(null)
  const close = useRef(onClose)
  useLayoutEffect(() => {
    close.current = onClose
  }, [onClose])
  const allowed = (day: string) => (!min || day >= min) && (!max || day <= max)
  useLayoutEffect(() => {
    const element = calendar.current
    if (!element) return
    // The popover top layer escapes scrolling containers and native modal dialogs.
    if (element.showPopover) element.showPopover()
    else element.style.display = 'block'
    const position = () => {
      const field = anchor.current?.getBoundingClientRect()
      if (!field) return
      const box = element.getBoundingClientRect()
      const gap = 12
      const viewport = window.visualViewport
      const viewportLeft = viewport?.offsetLeft ?? 0
      const viewportTop = viewport?.offsetTop ?? 0
      const right = viewportLeft + (viewport?.width ?? window.innerWidth)
      const bottom = viewportTop + (viewport?.height ?? window.innerHeight)
      let left = field.right + gap
      let top = field.top
      if (left + box.width > right - gap) {
        if (field.left - box.width - gap >= viewportLeft + gap) left = field.left - box.width - gap
        else {
          left = field.left
          top = field.bottom + gap
          if (top + box.height > bottom - gap) top = field.top - box.height - gap
        }
      }
      element.style.left = `${Math.max(viewportLeft + gap, Math.min(left, right - box.width - gap))}px`
      element.style.top = `${Math.max(viewportTop + gap, Math.min(top, bottom - box.height - gap))}px`
      element.style.visibility = 'visible'
    }
    position()
    const selected = element.querySelector<HTMLButtonElement>(
      'button[aria-pressed="true"]:not(:disabled)',
    )
    const first = element.querySelector<HTMLButtonElement>('button[data-day]:not(:disabled)')
    ;(selected ?? first)?.focus()
    const resize = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(position)
    resize?.observe(element)
    const dismiss = (event: PointerEvent) => {
      if (
        !element.contains(event.target as Node) &&
        !anchor.current?.contains(event.target as Node)
      )
        close.current()
    }
    window.addEventListener('resize', position)
    window.addEventListener('scroll', position, true)
    window.visualViewport?.addEventListener('resize', position)
    window.visualViewport?.addEventListener('scroll', position)
    document.addEventListener('pointerdown', dismiss)
    return () => {
      resize?.disconnect()
      window.removeEventListener('resize', position)
      window.removeEventListener('scroll', position, true)
      window.visualViewport?.removeEventListener('resize', position)
      window.visualViewport?.removeEventListener('scroll', position)
      document.removeEventListener('pointerdown', dismiss)
    }
  }, [anchor])

  const moveMonth = (step: number) => {
    const next = month + step
    const nextYear = year + (next < 0 ? -1 : next > 11 ? 1 : 0)
    if (nextYear < 1 || nextYear > 9999) return
    setYear(nextYear)
    setMonth((next + 12) % 12)
  }
  const firstDay = new Date(0)
  firstDay.setFullYear(year, month, 1)
  const offset = (firstDay.getDay() + 6) % 7
  const lastDay = new Date(0)
  lastDay.setFullYear(year, month + 1, 0)
  return (
    <div
      ref={calendar}
      popover="auto"
      role="dialog"
      aria-label="Lịch chọn ngày"
      className="date-calendar"
      onToggle={(event) => {
        if (event.newState === 'closed') close.current()
      }}
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          event.preventDefault()
          event.stopPropagation()
          onClose()
        }
      }}
    >
      <div className="date-calendar__heading">
        <button type="button" aria-label="Tháng trước" onClick={() => moveMonth(-1)}>
          ‹
        </button>
        <select
          aria-label="Tháng"
          value={month}
          onChange={(event) => setMonth(Number(event.target.value))}
        >
          {Array.from({ length: 12 }, (_, index) => (
            <option key={index} value={index}>
              Tháng {index + 1}
            </option>
          ))}
        </select>
        <input
          type="number"
          aria-label="Năm"
          value={year}
          min={1}
          max={9999}
          onChange={(event) => {
            const next = Number(event.target.value)
            if (Number.isInteger(next) && next >= 1 && next <= 9999) setYear(next)
          }}
        />
        <button type="button" aria-label="Tháng sau" onClick={() => moveMonth(1)}>
          ›
        </button>
      </div>
      <div className="date-calendar__days">
        {['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'].map((day) => (
          <span key={day} className="date-calendar__weekday">
            {day}
          </span>
        ))}
        {Array.from({ length: offset }, (_, index) => (
          <span key={`empty-${index}`} />
        ))}
        {Array.from({ length: lastDay.getDate() }, (_, index) => {
          const day = dayValue(year, month, index + 1)
          return (
            <button
              key={day}
              type="button"
              data-day={day}
              aria-label={formatDate(day)}
              aria-pressed={day === value}
              aria-current={day === todayValue ? 'date' : undefined}
              disabled={!allowed(day)}
              onClick={() => onSelect(day)}
            >
              {index + 1}
            </button>
          )
        })}
      </div>
      <div className="date-calendar__actions">
        {!required && (
          <button type="button" onClick={() => onSelect('')}>
            Xóa
          </button>
        )}
        <button type="button" disabled={!allowed(todayValue)} onClick={() => onSelect(todayValue)}>
          Hôm nay
        </button>
        <button type="button" onClick={onClose}>
          Đóng
        </button>
      </div>
    </div>
  )
}
