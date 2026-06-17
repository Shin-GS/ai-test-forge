import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import Input from './Input'

describe('Input', () => {
  it('렌더링 시 input 요소가 표시된다', () => {
    render(<Input placeholder="입력" />)
    expect(screen.getByPlaceholderText('입력')).toBeInTheDocument()
  })

  it('value와 onChange가 동작한다', () => {
    const { container } = render(<Input defaultValue="hello" />)
    const input = container.querySelector('input')!
    expect(input.value).toBe('hello')
  })

  it('error=true 시 에러 border 클래스가 적용된다', () => {
    const { container } = render(<Input error />)
    const input = container.querySelector('input')!
    expect(input.className).toContain('border-[var(--color-error)]')
  })

  it('error=false 시 기본 border 클래스가 적용된다', () => {
    const { container } = render(<Input />)
    const input = container.querySelector('input')!
    expect(input.className).toContain('border-[var(--color-border)]')
  })

  it('className을 추가할 수 있다', () => {
    const { container } = render(<Input className="flex-1" />)
    const input = container.querySelector('input')!
    expect(input.className).toContain('flex-1')
  })
})
