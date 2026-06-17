import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import Badge from './Badge'

describe('Badge', () => {
  it('children 텍스트를 렌더링한다', () => {
    render(<Badge>ACTIVE</Badge>)
    expect(screen.getByText('ACTIVE')).toBeInTheDocument()
  })

  it('variant=success 시 success 색상 클래스가 적용된다', () => {
    render(<Badge variant="success">성공</Badge>)
    expect(screen.getByText('성공').className).toContain('text-[var(--color-success)]')
  })

  it('variant=error 시 error 색상 클래스가 적용된다', () => {
    render(<Badge variant="error">실패</Badge>)
    expect(screen.getByText('실패').className).toContain('text-[var(--color-error)]')
  })

  it('기본 variant는 neutral이다', () => {
    render(<Badge>기본</Badge>)
    expect(screen.getByText('기본').className).toContain('text-[var(--color-text-secondary)]')
  })
})
