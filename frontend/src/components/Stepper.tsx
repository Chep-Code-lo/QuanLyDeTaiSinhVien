import React from 'react'

export interface StepItem {
  number: number
  title: string
  description?: string
}

interface StepperProps {
  steps: StepItem[]
  currentStep: number
}

export const Stepper: React.FC<StepperProps> = ({ steps, currentStep }) => {
  return (
    <div className="stepper-container my-6">
      <div className="stepper-wrapper">
        {steps.map((step, idx) => {
          const isCompleted = step.number < currentStep
          const isCurrent = step.number === currentStep
          return (
            <React.Fragment key={step.number}>
              <div
                className={`stepper-item ${isCompleted ? 'completed' : ''} ${isCurrent ? 'active' : ''}`}
              >
                <div className="stepper-circle">{isCompleted ? '✓' : step.number}</div>
                <div className="stepper-content">
                  <div className="stepper-title">{step.title}</div>
                  {step.description && <div className="stepper-desc">{step.description}</div>}
                </div>
              </div>
              {idx < steps.length - 1 && (
                <div className={`stepper-line ${step.number < currentStep ? 'completed' : ''}`} />
              )}
            </React.Fragment>
          )
        })}
      </div>
    </div>
  )
}
