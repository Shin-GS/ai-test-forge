import type { HTMLAttributes, ReactNode } from 'react';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  interactive?: boolean;
  children: ReactNode;
  className?: string;
}

function Card({ interactive = false, children, className = '', ...rest }: CardProps) {
  const base =
    'bg-[var(--color-bg-secondary)] border border-[var(--color-border)] rounded-[var(--radius-lg)] p-4 transition-all duration-[var(--transition-fast)]';
  const interactiveStyle = interactive
    ? 'cursor-pointer hover:border-[var(--color-border-light)] hover:bg-[var(--color-bg-tertiary)]'
    : '';

  return (
    <div className={`${base} ${interactiveStyle} ${className}`} {...rest}>
      {children}
    </div>
  );
}

export default Card;
