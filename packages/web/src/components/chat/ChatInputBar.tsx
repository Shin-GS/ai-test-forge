import { useState, useRef, useCallback } from 'react'

interface ChatInputBarProps {
  onSend: (message: string) => void
  isLoading: boolean
}

const MAX_ROWS = 5
const LINE_HEIGHT = 24 // px per line (1.5rem at 16px)
const PADDING = 24 // top + bottom padding (12px * 2)

function ChatInputBar({ onSend, isLoading }: ChatInputBarProps) {
  const [value, setValue] = useState('')
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const adjustHeight = useCallback(() => {
    const textarea = textareaRef.current
    if (!textarea) return
    textarea.style.height = 'auto'
    const maxHeight = LINE_HEIGHT * MAX_ROWS + PADDING
    textarea.style.height = `${Math.min(textarea.scrollHeight, maxHeight)}px`
  }, [])

  const handleInput = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setValue(e.target.value)
    adjustHeight()
  }

  const handleSubmit = () => {
    const trimmed = value.trim()
    if (!trimmed || isLoading) return
    onSend(trimmed)
    setValue('')
    // 높이 리셋
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSubmit()
    }
  }

  const canSend = value.trim().length > 0 && !isLoading

  return (
    <div className="border-t border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-4 py-3">
      <div className="mx-auto flex max-w-[800px] items-end gap-2">
        <textarea
          ref={textareaRef}
          value={value}
          onChange={handleInput}
          onKeyDown={handleKeyDown}
          disabled={isLoading}
          placeholder={
            isLoading ? 'AI 응답 대기 중...' : '메시지를 입력하세요...'
          }
          rows={1}
          className="flex-1 resize-none rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-tertiary)] px-3 py-3 text-sm text-[var(--color-text-primary)] placeholder-[var(--color-text-tertiary)] outline-none transition-[border-color] focus:border-[var(--color-accent)] disabled:cursor-not-allowed disabled:opacity-50"
          style={{ minHeight: '44px', maxHeight: `${LINE_HEIGHT * MAX_ROWS + PADDING}px` }}
        />
        <button
          onClick={handleSubmit}
          disabled={!canSend}
          className="flex h-11 shrink-0 items-center justify-center rounded-lg bg-[var(--color-accent)] px-4 text-sm font-medium text-white transition-colors hover:bg-[var(--color-accent-hover)] disabled:cursor-not-allowed disabled:opacity-50"
        >
          전송
        </button>
      </div>
    </div>
  )
}

export default ChatInputBar
