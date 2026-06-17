import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import Spinner from './Spinner'

describe('Spinner', () => {
  it('role=status로 렌더링된다', () => {
    render(<Spinner />)
    expect(screen.getByRole('status')).toBeInTheDocument()
  })

  it('aria-label이 설정되어 있다', () => {
    render(<Spinner />)
    expect(screen.getByRole('status')).toHaveAttribute('aria-label', '로딩 중')
  })

  it('size=lg 시 h-8 w-8 클래스가 적용된다', () => {
    render(<Spinner size="lg" />)
    const el = screen.getByRole('status')
    expect(el.className).toContain('h-8')
    expect(el.className).toContain('w-8')
  })
})
