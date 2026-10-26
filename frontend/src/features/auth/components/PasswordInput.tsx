import { useState } from 'react';
import { Input } from '../../../shared/components/ui/Input';

type PasswordInputProps = Omit<React.ComponentProps<typeof Input>, 'type'>;

/** Password field with a show/hide toggle. Styles live in AuthPage.css. */
export function PasswordInput({ error, className = '', ...props }: PasswordInputProps) {
  const [visible, setVisible] = useState(false);

  // The error is rendered outside .auth-password so the toggle, anchored to the
  // bottom of that box, stays aligned with the input.
  return (
    <div className="auth-password-group">
      <div className="auth-password">
        <Input
          {...props}
          className={`${error ? 'input-group--error' : ''} ${className}`}
          type={visible ? 'text' : 'password'}
          aria-invalid={error ? true : undefined}
        />
        <button
          type="button"
          className="auth-password-toggle"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Hide password' : 'Show password'}
          aria-pressed={visible}
        >
          {visible ? (
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
              <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
              <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24" />
              <line x1="1" y1="1" x2="23" y2="23" />
            </svg>
          ) : (
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
          )}
        </button>
      </div>
      {error && <span className="input-error" role="alert">{error}</span>}
    </div>
  );
}
