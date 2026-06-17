import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import Button from './Button'

describe('Button', () => {
  it('렌더링 시 children이 표시된다', () => {
    render(<Button>클릭</Button>)
    expect(screen.getByRole('button')).toHaveTextContent('클릭')
  })

  it('기본 type은 button이다', () => {
    render(<Button>테스트</Button>)
    expect(screen.getByRole('button')).toHaveAttribute('type', 'button')
  })

  it('type="submit"을 전달하면 submit이 된다', () => {
    render(<Button type="submit">전송</Button>)
    expect(screen.getByRole('button')).toHaveAttribute('type', 'submit')
  })

  it('disabled 시 클릭이 동작하지 않는다', () => {
    const onClick = vi.fn()
    render(<Button disabled onClick={onClick}>비활성</Button>)
    fireEvent.click(screen.getByRole('button'))
    expect(onClick).not.toHaveBeenCalled()
  })

  it('onClick이 호출된다', () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick}>클릭</Button>)
    fireEvent.click(screen.getByRole('button'))
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('className을 추가할 수 있다', () => {
    render(<Button className="w-full">넓은 버튼</Button>)
    expect(screen.getByRole('button').className).toContain('w-full')
  })
})
