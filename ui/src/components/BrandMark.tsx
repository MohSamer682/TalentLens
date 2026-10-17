import { cn } from '@/lib/utils';

export interface BrandMarkProps {
  size?: number;
  className?: string;
}

export function BrandMark({ size = 28, className }: BrandMarkProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 64 64"
      width={size}
      height={size}
      aria-hidden="true"
      className={cn('shrink-0 select-none', className)}
    >
      <rect width="64" height="64" rx="16" fill="#0E1614" />
      <circle cx="28" cy="28" r="15" fill="none" stroke="#E8EEEB" strokeWidth="5" />
      <path d="M39.5 39.5 50 50" stroke="#E8EEEB" strokeWidth="6.5" strokeLinecap="round" />
      <rect data-bar x="19.5" y="29" width="4.5" height="6" rx="1.5" fill="#8B9B96" />
      <rect data-bar x="25.75" y="25" width="4.5" height="10" rx="1.5" fill="#8B9B96" />
      <rect data-bar x="32" y="19.5" width="4.5" height="15.5" rx="1.5" fill="#FFB81C" />
    </svg>
  );
}
