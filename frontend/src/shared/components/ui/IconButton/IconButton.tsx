import React from 'react';
import './IconButton.css';

interface IconButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'default' | 'ghost' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  label: string; // accessible label
  children: React.ReactNode;
}

export function IconButton({
  variant = 'default',
  size = 'md',
  label,
  children,
  className = '',
  ...props
}: IconButtonProps) {
  return (
    <button
      className={`icon-btn icon-btn-${variant} icon-btn-${size} ${className}`}
      aria-label={label}
      title={label}
      {...props}
    >
      {children}
    </button>
  );
}
