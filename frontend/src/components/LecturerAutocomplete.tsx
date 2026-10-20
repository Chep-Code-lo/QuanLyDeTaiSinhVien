import React, { useEffect, useState } from 'react'
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
}) => {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<LecturerDirectoryResponse[]>([])
  const [selectedLecturer, setSelectedLecturer] = useState<LecturerOption | null>(null)
  const [loading, setLoading] = useState(false)
  const [isOpen, setIsOpen] = useState(false)

  // Fetch initial selected lecturer if value is provided
  useEffect(() => {
    if (value && selectedOption?.id === value) {
      setSelectedLecturer((current) => current?.id === value && current.fullName === selectedOption.fullName ? current : selectedOption)
      return
    }
    if (value && (!selectedLecturer || selectedLecturer.id !== value)) {
      lecturerApi
        .list({ size: 50 })
        .then((res) => {
          const found = res.content.find((l) => l.id === value || l.userId === value)
          if (found) {
            setSelectedLecturer(found)
          }
        })
        .catch(() => {})
    } else if (!value) {
      setSelectedLecturer(null)
    }
  }, [value, selectedLecturer, selectedOption])

  // Search with debounce
  useEffect(() => {
    if (!isOpen) return

    const timer = setTimeout(() => {
      setLoading(true)
      lecturerApi
        .list({ search: query, size: 20 })
        .then((res) => {
          setResults(res.content)
        })
        .catch(() => {
          setResults([])
        })
        .finally(() => {
          setLoading(false)
        })
    }, 300)

    return () => clearTimeout(timer)
  }, [query, isOpen])

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
    <div className="autocomplete-container">
      {label && (
        <label className="form-label" htmlFor={inputId}>
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
            id={inputId}
            aria-label={ariaLabel}
            type="text"
            className={`form-input ${error ? 'input-error' : ''}`}
            placeholder={placeholder}
            value={query}
            disabled={disabled}
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
                  <div
                    key={lecturer.id}
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
                  </div>
                ))}
            </div>
          )}
        </div>
      )}
      {error && <span className="form-error text-red text-xs mt-1">{error}</span>}
    </div>
  )
}
