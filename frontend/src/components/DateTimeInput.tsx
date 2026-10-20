import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type InputHTMLAttributes,
} from 'react'
import { CalendarDays } from 'lucide-react'
import { formatDateInput, parseDateInput } from '../utils/dateTime'
import './DateTimeInput.css'

type DateTimeInputProps = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'type' | 'value' | 'onChange' | 'min' | 'max'
> & {
  type: 'date' | 'datetime-local'
  value?: string | null
  min?: string
  max?: string
  onValueChange: (value: string) => void
}

const dateSegments = [
  { start: 0, end: 2, placeholder: 'dd', max: 31, min: 1 },
  { start: 3, end: 5, placeholder: 'mm', max: 12, min: 1 },
  { start: 6, end: 10, placeholder: 'yyyy', max: 9999, min: 1 },
  { start: 11, end: 13, placeholder: '--', max: 12, min: 1 },
  { start: 14, end: 16, placeholder: '--', max: 59, min: 0 },
  { start: 17, end: 19, placeholder: '--', max: 0, min: 0 },
] as const

function readSegments(text: string, withTime: boolean) {
  return dateSegments.slice(0, withTime ? 6 : 3).map((segment, index) => {
    const part = text.slice(segment.start, segment.end)
    return (index === 5 ? /^(AM|PM)$/i : /^\d+$/).test(part) ? part.toUpperCase() : ''
  })
}

function segmentText(parts: string[], withTime: boolean) {
  if (parts.every((part) => !part)) return ''
  const filled = parts.map((part, index) =>
    part
      ? part.padStart(dateSegments[index].end - dateSegments[index].start, '0')
      : dateSegments[index].placeholder,
  )
  return `${filled[0]}/${filled[1]}/${filled[2]}${withTime ? ` ${filled[3]}:${filled[4]} ${filled[5]}` : ''}`
}

export function DateTimeInput({
  type,
  value = '',
  min,
  max,
  onValueChange,
  className = '',
  disabled,
  readOnly,
  onBlur,
  ...props
}: DateTimeInputProps) {
  const withTime = type === 'datetime-local'
  const canonical = value || ''
  const [draft, setDraft] = useState(() => formatDateInput(canonical, withTime))
  const lastEmitted = useRef(canonical)
  const textInput = useRef<HTMLInputElement>(null)
  const picker = useRef<HTMLInputElement>(null)
  const pendingSelection = useRef<number | null>(null)
  const digitBuffer = useRef({ index: -1, text: '' })
  const justAdvanced = useRef(false)
  const formatHint = withTime ? 'dd/mm/yyyy --:-- --' : 'dd/mm/yyyy'
  const segmentCount = withTime ? 6 : 3

  const selectSegment = (index: number) => {
    const target = Math.max(0, Math.min(segmentCount - 1, index))
    pendingSelection.current = target
    textInput.current?.setSelectionRange(dateSegments[target].start, dateSegments[target].end)
  }

  useLayoutEffect(() => {
    if (pendingSelection.current !== null) {
      const segment = dateSegments[pendingSelection.current]
      textInput.current?.setSelectionRange(segment.start, segment.end)
      pendingSelection.current = null
    }
  }, [draft])

  const currentSegment = (position = textInput.current?.selectionStart || 0) => {
    const index = dateSegments
      .slice(0, segmentCount)
      .findIndex((segment) => position <= segment.end)
    return index < 0 ? segmentCount - 1 : index
  }

  const validationMessage = useCallback(
    (text: string, parsed: string | null) => {
      if (!text) return ''
      if (!parsed)
        return `Vui lòng nhập ngày hợp lệ theo định dạng ${withTime ? 'dd/mm/yyyy hh:mm AM/PM' : formatHint}.`
      if (min && parsed < min)
        return `Vui lòng chọn thời điểm từ ${formatDateInput(min, withTime)} trở đi.`
      if (max && parsed > max)
        return `Vui lòng chọn thời điểm không quá ${formatDateInput(max, withTime)}.`
      return ''
    },
    [formatHint, min, max, withTime],
  )

  useEffect(() => {
    if (canonical !== lastEmitted.current) {
      setDraft(formatDateInput(canonical, withTime))
      lastEmitted.current = canonical
    }
  }, [canonical, withTime])

  useEffect(() => {
    textInput.current?.setCustomValidity(validationMessage(draft, parseDateInput(draft, withTime)))
  }, [draft, validationMessage, withTime])

  const emit = (next: string) => {
    lastEmitted.current = next
    onValueChange(next)
  }

  const updateDraft = (text: string) => {
    const parsed = parseDateInput(text, withTime)
    setDraft(text)
    textInput.current?.setCustomValidity(validationMessage(text, parsed))
    if (parsed || !text.trim()) emit(parsed || '')
  }

  const typeCharacter = (character: string, index = currentSegment(), replaceAll = false) => {
    const parts = replaceAll
      ? (Array(segmentCount).fill('') as string[])
      : readSegments(draft, withTime)
    if (index === 5) {
      if (!/^[ap]$/i.test(character)) return
      parts[index] = character.toUpperCase() === 'A' ? 'AM' : 'PM'
      digitBuffer.current = { index: -1, text: '' }
      justAdvanced.current = false
      updateDraft(segmentText(parts, withTime))
      selectSegment(index)
      return
    }
    if (!/^\d$/.test(character)) return
    const definition = dateSegments[index]
    const width = definition.end - definition.start
    const previous =
      !replaceAll && digitBuffer.current.index === index ? digitBuffer.current.text : ''
    const next = previous + character
    if (Number(next) > definition.max || (next.length === width && Number(next) < definition.min))
      return
    parts[index] = next.padStart(width, '0')
    const complete =
      next.length >= width || (width === 2 && Number(next) > Math.floor(definition.max / 10))
    digitBuffer.current = complete ? { index: -1, text: '' } : { index, text: next }
    justAdvanced.current = complete && index < segmentCount - 1
    updateDraft(segmentText(parts, withTime))
    selectSegment(complete ? index + 1 : index)
  }

  const deleteSegment = (backward: boolean, all: boolean) => {
    const parts = readSegments(draft, withTime)
    let index = currentSegment()
    if (all) parts.fill('')
    else {
      if (backward && !parts[index] && index > 0) index--
      parts[index] = ''
    }
    digitBuffer.current = { index: -1, text: '' }
    justAdvanced.current = false
    updateDraft(segmentText(parts, withTime))
    selectSegment(all ? 0 : index)
  }

  return (
    <div className="date-time-input">
      <input
        {...props}
        ref={textInput}
        type="text"
        className={`form-input date-time-input__text ${className}`}
        placeholder={formatHint}
        value={draft}
        disabled={disabled}
        readOnly={readOnly}
        autoComplete="off"
        onFocus={(event) => {
          digitBuffer.current = { index: -1, text: '' }
          justAdvanced.current = false
          selectSegment(0)
          props.onFocus?.(event)
        }}
        onClick={(event) => {
          digitBuffer.current = { index: -1, text: '' }
          justAdvanced.current = false
          selectSegment(currentSegment())
          props.onClick?.(event)
        }}
        onKeyDown={(event) => {
          props.onKeyDown?.(event)
          if (
            event.defaultPrevented ||
            readOnly ||
            disabled ||
            event.ctrlKey ||
            event.metaKey ||
            event.altKey
          )
            return
          const index = currentSegment()
          const all =
            event.currentTarget.selectionStart === 0 &&
            event.currentTarget.selectionEnd === event.currentTarget.value.length
          if (/^\d$/.test(event.key) || (index === 5 && /^[ap]$/i.test(event.key))) {
            event.preventDefault()
            typeCharacter(event.key, all ? 0 : index, all)
          } else if (event.key === 'Backspace' || event.key === 'Delete') {
            event.preventDefault()
            deleteSegment(event.key === 'Backspace', all)
          } else if (['ArrowLeft', 'ArrowRight', '/', ':', ' '].includes(event.key)) {
            event.preventDefault()
            digitBuffer.current = { index: -1, text: '' }
            const skipSeparator = justAdvanced.current && ['/', ':', ' '].includes(event.key)
            justAdvanced.current = false
            if (!skipSeparator) selectSegment(index + (event.key === 'ArrowLeft' ? -1 : 1))
          } else if (
            event.key === 'Tab' &&
            (event.shiftKey ? index > 0 : index < segmentCount - 1)
          ) {
            event.preventDefault()
            digitBuffer.current = { index: -1, text: '' }
            justAdvanced.current = false
            selectSegment(index + (event.shiftKey ? -1 : 1))
          } else if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
            event.preventDefault()
            const parts = readSegments(draft, withTime)
            const definition = dateSegments[index]
            if (index === 5) parts[index] = parts[index] === 'AM' ? 'PM' : 'AM'
            else {
              const step = event.key === 'ArrowUp' ? 1 : -1
              const number =
                Number(parts[index] || (index === 2 ? new Date().getFullYear() : definition.min)) +
                step
              const adjusted =
                number > definition.max
                  ? definition.min
                  : number < definition.min
                    ? definition.max
                    : number
              parts[index] = String(adjusted).padStart(definition.end - definition.start, '0')
            }
            digitBuffer.current = { index: -1, text: '' }
            justAdvanced.current = false
            updateDraft(segmentText(parts, withTime))
            selectSegment(index)
          } else if (event.key.length === 1) event.preventDefault()
        }}
        onPaste={(event) => {
          props.onPaste?.(event)
          if (event.defaultPrevented || readOnly || disabled) return
          const text = event.clipboardData.getData('text').trim()
          const parsed = parseDateInput(text, withTime)
          if (parsed || text.includes('/')) {
            event.preventDefault()
            digitBuffer.current = { index: -1, text: '' }
            justAdvanced.current = false
            updateDraft(parsed ? formatDateInput(parsed, withTime) : text)
            selectSegment(parsed ? segmentCount - 1 : 0)
          }
        }}
        onChange={(event) => {
          const text = event.target.value
          const parsed = parseDateInput(text, withTime)
          const native = event.nativeEvent as InputEvent
          if (native.inputType?.startsWith('delete')) {
            deleteSegment(native.inputType === 'deleteContentBackward', !text)
          } else if (native.inputType === 'insertText' && native.data?.length === 1) {
            const position = Math.max(0, (event.target.selectionStart || 0) - 1)
            typeCharacter(native.data, currentSegment(position))
          } else {
            digitBuffer.current = { index: -1, text: '' }
            updateDraft(parsed ? formatDateInput(parsed, withTime) : text)
          }
        }}
        onBlur={(event) => {
          const parsed = parseDateInput(draft, withTime)
          if (parsed) setDraft(formatDateInput(parsed, withTime))
          pendingSelection.current = null
          digitBuffer.current = { index: -1, text: '' }
          justAdvanced.current = false
          onBlur?.(event)
        }}
      />
      <button
        type="button"
        className="date-time-input__calendar"
        aria-label="Mở lịch chọn ngày"
        disabled={disabled || readOnly}
        onClick={() => {
          if (picker.current?.showPicker) picker.current.showPicker()
          else picker.current?.click()
        }}
      >
        <CalendarDays size={16} aria-hidden="true" />
      </button>
      <input
        ref={picker}
        type="date"
        className="date-time-input__native"
        lang="vi"
        aria-hidden="true"
        tabIndex={-1}
        disabled={disabled || readOnly}
        min={min?.slice(0, 10)}
        max={max?.slice(0, 10)}
        value={canonical.slice(0, 10)}
        onChange={(event) => {
          const day = event.target.value
          const next = day
            ? `${day}${withTime ? `T${canonical.split('T')[1]?.slice(0, 5) || '00:00'}` : ''}`
            : ''
          setDraft(formatDateInput(next, withTime))
          emit(next)
          textInput.current?.focus()
        }}
      />
    </div>
  )
}
