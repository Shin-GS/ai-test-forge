type SpinnerSize = 'sm' | 'md' | 'lg';

interface SpinnerProps {
  size?: SpinnerSize;
  className?: string;
}

const sizeStyles: Record<SpinnerSize, string> = {
  sm: 'h-4 w-4 border-2',
  md: 'h-5 w-5 border-2',
  lg: 'h-8 w-8 border-3',
};

function Spinner({ size = 'md', className = '' }: SpinnerProps) {
  const base =
    'animate-spin rounded-full border-[var(--color-border)] border-t-[var(--color-accent)]';

  return (
    <div
      role="status"
      aria-label="로딩 중"
      className={`${base} ${sizeStyles[size]} ${className}`}
    />
  );
}

export default Spinner;
