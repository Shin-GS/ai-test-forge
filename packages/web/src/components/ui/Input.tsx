import type { InputHTMLAttributes } from 'react';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  error?: boolean;
  className?: string;
}

function Input({ error = false, className = '', ...rest }: InputProps) {
  const base =
    'w-full px-3 py-2 text-sm text-[var(--color-text-primary)] bg-[var(--color-bg-tertiary)] border rounded-[var(--radius-md)] outline-none transition-[border-color] duration-[var(--transition-fast)] placeholder:text-[var(--color-text-tertiary)] focus:border-[var(--color-accent)]';
  const borderStyle = error
    ? 'border-[var(--color-error)]'
    : 'border-[var(--color-border)]';

  return (
    <input className={`${base} ${borderStyle} ${className}`} {...rest} />
  );
}

export default Input;
