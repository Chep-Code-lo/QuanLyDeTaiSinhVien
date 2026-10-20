import type { SelectHTMLAttributes } from 'react'
import { ChevronDown } from 'lucide-react'
import './AnimatedSelect.css'

type AnimatedSelectProps = SelectHTMLAttributes<HTMLSelectElement> & {
  wrapperClassName?: string
}

export function AnimatedSelect({
  className = '',
  wrapperClassName = '',
  children,
  ...props
}: AnimatedSelectProps) {
  return (
    <div className={`animated-select ${wrapperClassName}`}>
      <select {...props} className={`form-select animated-select__input ${className}`}>
        {children}
      </select>
      <ChevronDown className="animated-select__arrow" size={18} aria-hidden="true" />
    </div>
  )
}
