import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import Badge from './Badge'

describe('Badge', () => {
  it('children이 표시된다', () => {
    render(<Badge>ACTIVE</Badge>)
    expect(screen.getByText('ACTIVE')).toBeInTheDocument()
  })

  it('variant에 따라 다른 스타일이 적용된다', () => {
    const { container } = render(<Badge variant="success">OK</Badge>)
    const span = container.querySelector('span')!
    expect(span.className).toContain('color-success')
  })

  it('기본 variant는 neutral이다', () => {
    const { container } = render(<Badge>기본</Badge>)
    const span = container.querySelector('span')!
    expect(span.className).toContain('color-bg-tertiary')
  })
})
