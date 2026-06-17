import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import Spinner from './Spinner'

describe('Spinner', () => {
  it('role="status"로 렌더링된다', () => {
    render(<Spinner />)
    expect(screen.getByRole('status')).toBeInTheDocument()
  })

  it('aria-label이 설정되어 있다', () => {
    render(<Spinner />)
    expect(screen.getByRole('status')).toHaveAttribute('aria-label', '로딩 중')
  })

  it('size에 따라 크기 클래스가 적용된다', () => {
    const { container } = render(<Spinner size="lg" />)
    const div = container.querySelector('[role="status"]')!
    expect(div.className).toContain('h-8')
  })
})
