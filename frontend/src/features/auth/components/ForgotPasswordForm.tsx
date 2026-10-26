import { useState } from 'react';
import { authApi } from '../api/authApi';
import { Button } from '../../../shared/components/ui/Button';
import { Input } from '../../../shared/components/ui/Input';
import { Alert } from '../../../shared/components/ui/Alert';

interface ForgotPasswordFormProps {
  onBackToLogin: () => void;
}

export function ForgotPasswordForm({ onBackToLogin }: ForgotPasswordFormProps) {
  const [email, setEmail] = useState('');
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await authApi.forgotPassword(email.trim());
      setSentTo(email.trim());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  if (sentTo) {
    return (
      <div className="auth-form">
        <div className="auth-notice" role="status">
          <span className="auth-notice-icon" aria-hidden="true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <rect x="2" y="4" width="20" height="16" rx="2" />
              <path d="m22 7-10 6L2 7" />
            </svg>
          </span>
          <p>
            If an account exists for <strong>{sentTo}</strong>, we&apos;ve sent a link to reset your password.
            It expires in 30 minutes. Check your spam folder if you don&apos;t see it.
          </p>
        </div>
        <Button type="button" variant="primary" className="auth-submit" onClick={onBackToLogin}>
          Back to Sign In
        </Button>
        <p className="auth-toggle auth-toggle--tight">
          Didn&apos;t get it?{' '}
          <button type="button" className="auth-toggle-btn" onClick={() => setSentTo(null)}>
            Try again
          </button>
        </p>
      </div>
    );
  }

  return (
    <>
      {error && <Alert type="error" message={error} onClose={() => setError(null)} />}
      <form className="auth-form" onSubmit={handleSubmit}>
        <Input
          label="Email"
          type="email"
          placeholder="you@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
          autoFocus
          autoComplete="email"
          autoCapitalize="none"
          spellCheck={false}
        />
        <Button type="submit" variant="primary" loading={loading} className="auth-submit">
          Send Reset Link
        </Button>
      </form>
      <p className="auth-toggle">
        Remembered it?{' '}
        <button type="button" className="auth-toggle-btn" onClick={onBackToLogin}>
          Back to Sign In
        </button>
      </p>
    </>
  );
}
