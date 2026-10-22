import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react'
import { formatDate, parseUtcDateTime } from '../utils/dateTime'
import { milestoneCalendarDays, milestoneDayKey } from '../utils/milestoneSchedule'
import type { MilestoneResponse } from '../types/domain'
import './MilestoneBoard.css'

type Props = {
  milestones: MilestoneResponse[]
  month: Date
  selectedDay: string | null
  now: Date
  onMonthChange: (month: Date) => void
  onSelectDay: (day: string, milestones: MilestoneResponse[]) => void
}

export function MilestoneCalendar({
  milestones,
  month,
  selectedDay,
  now,
  onMonthChange,
  onSelectDay,
}: Props) {
  const today = milestoneDayKey(now)
  return (
    <section className="milestone-board__calendar" aria-label="Lịch hạn báo cáo">
      <div className="milestone-board__calendar-heading">
        <h3>
          <CalendarDays size={17} aria-hidden="true" />
          Tháng {month.getMonth() + 1}, {month.getFullYear()}
        </h3>
        <div>
          <button
            type="button"
            aria-label="Tháng trước"
            onClick={() => onMonthChange(new Date(month.getFullYear(), month.getMonth() - 1, 1))}
          >
            <ChevronLeft size={16} aria-hidden="true" />
          </button>
          <button
            type="button"
            aria-label="Tháng sau"
            onClick={() => onMonthChange(new Date(month.getFullYear(), month.getMonth() + 1, 1))}
          >
            <ChevronRight size={16} aria-hidden="true" />
          </button>
        </div>
      </div>
      <div className="milestone-board__calendar-grid">
        {['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'].map((day) => (
          <span className="milestone-board__weekday" key={day}>
            {day}
          </span>
        ))}
        {milestoneCalendarDays(month).map((date, index) => {
          if (!date) return <span key={`blank-${index}`} />
          const key = milestoneDayKey(date)
          const due = milestones.filter(
            (milestone) => milestoneDayKey(parseUtcDateTime(milestone.dueAt)) === key,
          )
          return (
            <button
              key={key}
              type="button"
              className={`${due.length ? 'milestone-board__day--deadline' : ''}${selectedDay === key ? ' milestone-board__day--selected' : ''}${today === key ? ' milestone-board__day--today' : ''}`}
              aria-label={`${formatDate(date)}${due.length ? `, ${due.length} mốc báo cáo` : ''}`}
              aria-pressed={selectedDay === key}
              aria-current={today === key ? 'date' : undefined}
              onClick={() => onSelectDay(key, due)}
            >
              {date.getDate()}
            </button>
          )
        })}
      </div>
      <div className="milestone-board__calendar-legend">
        <span>
          <i />
          Hạn báo cáo
        </span>
        <span>
          <i />
          Ngày đang chọn
        </span>
      </div>
    </section>
  )
}
