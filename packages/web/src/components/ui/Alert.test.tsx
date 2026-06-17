import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import Alert from './Alert'

describe('Alert', () => {
  it('children을 렌더링한다', () => {
    render(<Alert>에러 발생</Alert>)
    expect(screen.getByRole('alert')).toHaveTextContent('에러 발생')
  })

  it('role=alert이 설정되어 있다', () => {
    render(<Alert>경고</Alert>)
    expect(screen.getByRole('alert')).toBeInTheDocument()
  })

  it('variant=error 시 error 배경 클래스가 적용된다', () => {
    render(<Alert variant="error">에러</Alert>)
    expect(screen.getByRole('alert').className).toContain('bg-[var(--color-error-subtle)]')
  })

  it('variant=warning 시 warning 배경 클래스가 적용된다', () => {
    render(<Alert variant="warning">주의</Alert>)
    expect(screen.getByRole('alert').className).toContain('bg-[var(--color-warning-subtle)]')
  })
})
