import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import Button from './Button'

describe('Button', () => {
  it('children 텍스트를 렌더링한다', () => {
    render(<Button>클릭</Button>)
    expect(screen.getByRole('button', { name: '클릭' })).toBeInTheDocument()
  })

  it('클릭 시 onClick을 호출한다', async () => {
    const user = userEvent.setup()
    const onClick = vi.fn()
    render(<Button onClick={onClick}>실행</Button>)

    await user.click(screen.getByRole('button'))
    expect(onClick).toHaveBeenCalledOnce()
  })

  it('disabled 시 클릭해도 onClick이 호출되지 않는다', async () => {
    const user = userEvent.setup()
    const onClick = vi.fn()
    render(<Button disabled onClick={onClick}>비활성</Button>)

    await user.click(screen.getByRole('button'))
    expect(onClick).not.toHaveBeenCalled()
  })

  it('type 기본값이 button이다', () => {
    render(<Button>버튼</Button>)
    expect(screen.getByRole('button')).toHaveAttribute('type', 'button')
  })

  it('variant=primary 시 accent 배경 클래스가 적용된다', () => {
    render(<Button variant="primary">Primary</Button>)
    const btn = screen.getByRole('button')
    expect(btn.className).toContain('bg-[var(--color-accent)]')
  })

  it('variant=danger 시 error 배경 클래스가 적용된다', () => {
    render(<Button variant="danger">삭제</Button>)
    const btn = screen.getByRole('button')
    expect(btn.className).toContain('bg-[var(--color-error)]')
  })

  it('className을 추가할 수 있다', () => {
    render(<Button className="w-full">전체</Button>)
    const btn = screen.getByRole('button')
    expect(btn.className).toContain('w-full')
  })
})
