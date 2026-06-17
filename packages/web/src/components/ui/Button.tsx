import type { ButtonHTMLAttributes, ReactNode } from 'react';

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
type ButtonSize = 'sm' | 'md' | 'lg';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  children: ReactNode;
  className?: string;
}

const variantStyles: Record<ButtonVariant, string> = {
  primary:
    'bg-[var(--color-accent)] text-white border-transparent hover:bg-[var(--color-accent-hover)]',
  secondary:
    'bg-[var(--color-bg-tertiary)] text-[var(--color-text-primary)] border-[var(--color-border)] hover:bg-[var(--color-bg-hover)]',
  ghost:
    'bg-transparent text-[var(--color-text-secondary)] border-transparent hover:bg-[var(--color-bg-hover)] hover:text-[var(--color-text-primary)]',
  danger:
    'bg-[var(--color-error)] text-white border-transparent hover:bg-[#dc2626]',
};

const sizeStyles: Record<ButtonSize, string> = {
  sm: 'px-3 py-1 text-xs',
  md: 'px-4 py-2 text-sm',
  lg: 'px-6 py-3 text-base',
};

function Button({
  variant = 'primary',
  size = 'md',
  children,
  className = '',
  disabled,
  type = 'button',
  ...rest
}: ButtonProps) {
  const base =
    'inline-flex items-center justify-center gap-2 rounded-[var(--radius-md)] border font-medium whitespace-nowrap transition-all duration-[var(--transition-fast)] cursor-pointer';
  const disabledStyle = disabled ? 'opacity-50 cursor-not-allowed' : '';

  return (
    <button
      type={type}
      disabled={disabled}
      className={`${base} ${variantStyles[variant]} ${sizeStyles[size]} ${disabledStyle} ${className}`}
      {...rest}
    >
      {children}
    </button>
  );
}

export default Button;
