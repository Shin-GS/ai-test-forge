import type { ReactNode } from 'react';

type AlertVariant = 'error' | 'warning' | 'info' | 'success';

interface AlertProps {
  variant?: AlertVariant;
  children: ReactNode;
  className?: string;
}

const variantStyles: Record<AlertVariant, string> = {
  error:
    'bg-[var(--color-error-subtle)] border-[rgba(239,68,68,0.3)]',
  warning:
    'bg-[var(--color-warning-subtle)] border-[rgba(245,158,11,0.3)]',
  info:
    'bg-[var(--color-info-subtle)] border-[rgba(59,130,246,0.3)]',
  success:
    'bg-[var(--color-success-subtle)] border-[rgba(34,197,94,0.3)]',
};

function Alert({ variant = 'info', children, className = '' }: AlertProps) {
  const base =
    'flex gap-3 px-4 py-3 rounded-[var(--radius-md)] border text-sm';

  return (
    <div role="alert" className={`${base} ${variantStyles[variant]} ${className}`}>
      {children}
    </div>
  );
}

export default Alert;
