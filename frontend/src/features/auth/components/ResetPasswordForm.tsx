import { useState } from 'react';
import { authApi } from '../api/authApi';
import { Button } from '../../../shared/components/ui/Button';
import { Alert } from '../../../shared/components/ui/Alert';
import { PasswordInput } from './PasswordInput';

const MIN_LENGTH = 8;

interface ResetPasswordFormProps {
  token: string | null;
  onDone: () => void;
  onRequestNewLink: () => void;
}

export function ResetPasswordForm({ token, onDone, onRequestNewLink }: ResetPasswordFormProps) {
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const tooShort = password.length > 0 && password.length < MIN_LENGTH;
  const mismatch = confirm.length > 0 && confirm !== password;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || password.length < MIN_LENGTH || password !== confirm) return;
    setError(null);
    setLoading(true);
    try {
      await authApi.resetPassword(token, password);
      onDone();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  if (!token) {
    return (
      <div className="auth-form">
        <Alert type="error" message="This reset link is missing its token. Please request a new one." />
        <Button type="button" variant="primary" className="auth-submit" onClick={onRequestNewLink}>
          Request New Link
        </Button>
      </div>
    );
  }

  return (
    <>
      {error && <Alert type="error" message={error} onClose={() => setError(null)} />}
      <form className="auth-form" onSubmit={handleSubmit}>
        <PasswordInput
          label="New Password"
          placeholder={`At least ${MIN_LENGTH} characters`}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          minLength={MIN_LENGTH}
          maxLength={72}
          autoFocus
          autoComplete="new-password"
          error={tooShort ? `Password must be at least ${MIN_LENGTH} characters` : undefined}
        />
        <PasswordInput
          label="Confirm Password"
          placeholder="Re-enter your new password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          required
          maxLength={72}
          autoComplete="new-password"
          error={mismatch ? 'Passwords do not match' : undefined}
        />
        <Button
          type="submit"
          variant="primary"
          loading={loading}
          disabled={password.length < MIN_LENGTH || password !== confirm}
          className="auth-submit"
        >
          Reset Password
        </Button>
      </form>
      <p className="auth-toggle">
        Link expired?{' '}
        <button type="button" className="auth-toggle-btn" onClick={onRequestNewLink}>
          Request a new one
        </button>
      </p>
    </>
  );
}
