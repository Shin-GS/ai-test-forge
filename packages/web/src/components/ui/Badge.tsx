import type { ReactNode } from 'react';

type BadgeVariant = 'success' | 'warning' | 'error' | 'info' | 'neutral';

interface BadgeProps {
  variant?: BadgeVariant;
  children: ReactNode;
  className?: string;
}

const variantStyles: Record<BadgeVariant, string> = {
  success: 'bg-[var(--color-success-subtle)] text-[var(--color-success)]',
  warning: 'bg-[var(--color-warning-subtle)] text-[var(--color-warning)]',
  error: 'bg-[var(--color-error-subtle)] text-[var(--color-error)]',
  info: 'bg-[var(--color-info-subtle)] text-[var(--color-info)]',
  neutral: 'bg-[var(--color-bg-tertiary)] text-[var(--color-text-secondary)]',
};

function Badge({ variant = 'neutral', children, className = '' }: BadgeProps) {
  const base =
    'inline-flex items-center px-2 py-1 text-xs font-medium rounded-full';

  return (
    <span className={`${base} ${variantStyles[variant]} ${className}`}>
      {children}
    </span>
  );
}

export default Badge;
