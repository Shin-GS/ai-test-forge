import type { MessageResponse } from '@/types/chat'

interface MessageBubbleProps {
  message: MessageResponse
}

function MessageBubble({ message }: MessageBubbleProps) {
  const isUser = message.role === 'USER'

  return (
    <div
      className={`flex gap-3 px-4 py-3 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}
    >
      {/* 아바타 */}
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[var(--color-bg-tertiary)] text-sm">
        {isUser ? '👤' : '🤖'}
      </div>

      {/* 메시지 내용 */}
      <div
        className={`max-w-[70%] rounded-xl px-4 py-3 text-sm leading-relaxed ${
          isUser
            ? 'rounded-br-sm bg-[var(--color-accent)] text-white'
            : 'rounded-bl-sm border border-[var(--color-border)] bg-[var(--color-bg-secondary)] text-[var(--color-text-primary)]'
        }`}
      >
        <p className="whitespace-pre-wrap">{message.content}</p>
      </div>
    </div>
  )
}

export default MessageBubble
