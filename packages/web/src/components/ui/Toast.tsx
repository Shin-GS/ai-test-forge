import { useEffect, useState } from 'react'

type ToastType = 'success' | 'error' | 'info'

interface ToastItem {
  id: number
  type: ToastType
  message: string
}

let toastId = 0
let addToastFn: ((type: ToastType, message: string) => void) | null = null

/** 전역 toast 호출 함수 */
// eslint-disable-next-line react-refresh/only-export-components
export function toast(type: ToastType, message: string) {
  addToastFn?.(type, message)
}

const typeStyles: Record<ToastType, string> = {
  success: 'bg-[var(--color-success)] text-white',
  error: 'bg-[var(--color-error)] text-white',
  info: 'bg-[var(--color-info)] text-white',
}

/** App 루트에 한 번 배치하는 Toast 컨테이너 */
export function ToastContainer() {
  const [items, setItems] = useState<ToastItem[]>([])

  useEffect(() => {
    addToastFn = (type, message) => {
      const id = ++toastId
      setItems((prev) => [...prev, { id, type, message }])
      setTimeout(() => {
        setItems((prev) => prev.filter((t) => t.id !== id))
      }, 3000)
    }
    return () => { addToastFn = null }
  }, [])

  if (items.length === 0) return null

  return (
    <div className="fixed right-4 top-4 z-[9999] flex flex-col gap-2">
      {items.map((item) => (
        <div
          key={item.id}
          className={`rounded-[var(--radius-md)] px-4 py-3 text-sm font-medium shadow-lg ${typeStyles[item.type]}`}
          role="alert"
        >
          {item.message}
        </div>
      ))}
    </div>
  )
}
