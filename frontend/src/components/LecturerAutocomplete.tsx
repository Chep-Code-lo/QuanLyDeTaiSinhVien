import React, { useEffect, useId, useRef, useState } from 'react'
import { allPages } from '../api/student'
import { lecturerApi } from '../api/lecturers'
import type { LecturerDirectoryResponse } from '../types/domain'

type LecturerOption = Pick<LecturerDirectoryResponse, 'id' | 'fullName' | 'lecturerCode'> & Partial<LecturerDirectoryResponse>

interface LecturerAutocompleteProps {
  inputId?: string
  ariaLabel?: string
  selectedOption?: LecturerOption
  excludedIds?: number[]
  value?: number | null
  onChange: (lecturerId: number | null, lecturer?: LecturerDirectoryResponse | null) => void
  placeholder?: string
  disabled?: boolean
  required?: boolean
  label?: string
  error?: string
  facultyId?: number
  departmentId?: number
}

export const LecturerAutocomplete: React.FC<LecturerAutocompleteProps> = ({
  value,
  onChange,
  inputId,
  ariaLabel,
  selectedOption,
  excludedIds = [],
  placeholder = 'Tìm kiếm giảng viên theo tên, mã...',
  disabled = false,
  required = false,
  label,
  error,
  facultyId,
  departmentId,
}) => {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<LecturerDirectoryResponse[]>([])
  const [selectedLecturer, setSelectedLecturer] = useState<LecturerOption | null>(null)
  const [loading, setLoading] = useState(false)
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const generatedId = useId()
  const fieldId = inputId ?? generatedId

  useEffect(() => {
    const close = (event: PointerEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setIsOpen(false)
    }
    document.addEventListener('pointerdown', close)
    return () => document.removeEventListener('pointerdown', close)
  }, [])

  // Fetch initial selected lecturer if value is provided
  useEffect(() => {
    let active = true
    if (value && selectedOption?.id === value) {
      setSelectedLecturer((current) => current?.id === value && current.fullName === selectedOption.fullName ? current : selectedOption)
      return
    }
    if (value && (!selectedLecturer || selectedLecturer.id !== value)) {
      setSelectedLecturer(null)
      allPages(page => lecturerApi.list({ facultyId, departmentId, page, size: 100 }))
        .then((res) => {
          const found = res.find((l) => l.id === value)
          if (active && found) {
            setSelectedLecturer(found)
          }
        })
        .catch(() => {})
    } else if (!value) {
      setSelectedLecturer(null)
    }
    return () => { active = false }
  }, [value, selectedLecturer, selectedOption, facultyId, departmentId])

  // Search with debounce
  useEffect(() => {
    if (!isOpen) return
    let active = true
    setResults([])
    setLoading(true)
    const timer = setTimeout(() => {
      setLoading(true)
      lecturerApi
        .list({ search: query, facultyId, departmentId, size: 20 })
        .then((res) => {
          if (active) setResults(res.content)
        })
        .catch(() => {
          if (active) setResults([])
        })
        .finally(() => {
          if (active) setLoading(false)
        })
    }, 300)

    return () => { active = false; clearTimeout(timer) }
  }, [query, isOpen, facultyId, departmentId])

  const handleSelect = (lecturer: LecturerDirectoryResponse) => {
    setSelectedLecturer(lecturer)
    onChange(lecturer.id, lecturer)
    setIsOpen(false)
    setQuery('')
  }

  const handleClear = (e: React.MouseEvent) => {
    e.stopPropagation()
    setSelectedLecturer(null)
    onChange(null, null)
    setQuery('')
  }

  return (
    <div className="autocomplete-container" ref={containerRef} onKeyDown={event => { if (event.key === 'Escape') setIsOpen(false) }}>
      {label && (
        <label className="form-label" htmlFor={fieldId}>
          {label} {required && <span className="text-red">*</span>}
        </label>
      )}

      {selectedLecturer ? (
        <div className="autocomplete-selected">
          <div className="selected-info">
            <span className="selected-name font-semibold">{selectedLecturer.fullName}</span>
            <span className="selected-badge">
              {selectedLecturer.academicDegree ? `${selectedLecturer.academicDegree}. ` : ''}
              {selectedLecturer.lecturerCode} -{' '}
              {selectedLecturer.departmentName || 'Chưa có thông tin bộ môn'}
            </span>
          </div>
          {!disabled && (
            <button type="button" className="btn-clear" onClick={handleClear} title="Xóa lựa chọn">
              &times;
            </button>
          )}
        </div>
      ) : (
        <div className="autocomplete-input-wrapper">
          <input
            id={fieldId}
            aria-label={ariaLabel}
            type="text"
            className={`form-input ${error ? 'input-error' : ''}`}
            placeholder={placeholder}
            value={query}
            disabled={disabled}
            required={required}
            onChange={(e) => {
              setQuery(e.target.value)
              setIsOpen(true)
            }}
            onFocus={() => setIsOpen(true)}
          />
          {loading && <div className="autocomplete-spinner" />}

          {isOpen && (
            <div className="autocomplete-dropdown">
              {loading && (
                <div className="dropdown-item text-secondary py-2 px-3">Đang tìm kiếm...</div>
              )}
              {!loading && results.filter((lecturer) => !excludedIds.includes(lecturer.id)).length === 0 && (
                <div className="dropdown-item text-secondary py-2 px-3">
                  Không tìm thấy giảng viên
                </div>
              )}
              {!loading &&
                results.filter((lecturer) => !excludedIds.includes(lecturer.id)).map((lecturer) => (
                  <button
                    key={lecturer.id}
                    type="button"
                    className="dropdown-item cursor-pointer py-2 px-3 hover:bg-light"
                    onClick={() => handleSelect(lecturer)}
                  >
                    <div className="font-semibold text-primary">
                      {lecturer.fullName} ({lecturer.lecturerCode})
                    </div>
                    <div className="text-sm text-secondary">
                      {lecturer.academicDegree ? `${lecturer.academicDegree} - ` : ''}
                      {lecturer.departmentName}
                    </div>
                  </button>
                ))}
            </div>
          )}
        </div>
      )}
      {error && <span className="form-error text-red text-xs mt-1">{error}</span>}
    </div>
  )
}
