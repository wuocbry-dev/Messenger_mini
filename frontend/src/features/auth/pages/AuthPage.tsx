import { useState, useCallback, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '../api/authApi';
import type { LoginRequest, RegisterRequest } from '../api/authApi';
import { Button } from '../../../shared/components/ui/Button';
import { Input } from '../../../shared/components/ui/Input';
import { Alert } from '../../../shared/components/ui/Alert';
import './AuthPage.css';

type Mode = 'login' | 'register';

export function AuthPage() {
  const navigate = useNavigate();
  const [mode, setMode] = useState<Mode>('login');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<LoginRequest | RegisterRequest>({
    usernameOrEmail: '',
    password: '',
  });
  const abortRef = useRef<AbortController | null>(null);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    abortRef.current?.abort();
    const ctrl = new AbortController();
    abortRef.current = ctrl;

    try {
      if (mode === 'login') {
        const req = form as LoginRequest;
        const res = await authApi.login(req);
        authApi.persistLogin(res);
        navigate('/');
      } else {
        const req = form as RegisterRequest;
        const res = await authApi.register(req);
        authApi.persistLogin(res);
        navigate('/');
      }
    } catch (err) {
      if (err instanceof Error && err.name !== 'AbortError') {
        setError(err.message);
      }
    } finally {
      setLoading(false);
    }
  }, [mode, form, navigate]);

  const update = (field: string) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm(prev => ({ ...prev, [field]: e.target.value }));
  };

  const toggleMode = () => {
    setMode(m => m === 'login' ? 'register' : 'login');
    setError(null);
    setForm(mode === 'login'
      ? { username: '', email: '', password: '', displayName: '' }
      : { usernameOrEmail: '', password: '' }
    );
  };

  return (
    <div className="auth-page">
      <div className="auth-card animate-fade-in">
        <div className="auth-brand">
          <div className="auth-logo">
            <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
              <rect width="32" height="32" rx="8" fill="var(--color-primary)" />
              <path d="M8 12c0-1.1.9-2 2-2h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H18l-4 4v-4H10a2 2 0 0 1-2-2v-6Z" fill="white" />
            </svg>
          </div>
          <h1 className="auth-title">Messenger Mini</h1>
          <p className="auth-subtitle">
            {mode === 'login' ? 'Sign in to your account' : 'Create a new account'}
          </p>
        </div>

        {error && <Alert type="error" message={error} onClose={() => setError(null)} />}

        <form className="auth-form" onSubmit={handleSubmit}>
          {mode === 'register' && (
            <>
              <Input
                label="Username"
                type="text"
                placeholder="your_username"
                value={(form as RegisterRequest).username}
                onChange={update('username')}
                required
                autoComplete="username"
              />
              <Input
                label="Display Name"
                type="text"
                placeholder="Your Name"
                value={(form as RegisterRequest).displayName}
                onChange={update('displayName')}
                required
              />
              <Input
                label="Email"
                type="email"
                placeholder="you@example.com"
                value={(form as RegisterRequest).email}
                onChange={update('email')}
                required
                autoComplete="email"
              />
            </>
          )}

          {mode === 'login' && (
            <Input
              label="Username or Email"
              type="text"
              placeholder="username or you@example.com"
              value={(form as LoginRequest).usernameOrEmail}
              onChange={update('usernameOrEmail')}
              required
              autoComplete="username"
            />
          )}

          <Input
            label="Password"
            type="password"
            placeholder={mode === 'register' ? 'At least 8 characters' : 'Your password'}
            value={form.password}
            onChange={update('password')}
            required
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
          />

          <Button type="submit" variant="primary" loading={loading} className="auth-submit">
            {mode === 'login' ? 'Sign In' : 'Create Account'}
          </Button>
        </form>

        <p className="auth-toggle">
          {mode === 'login' ? "Don't have an account? " : 'Already have an account? '}
          <button type="button" className="auth-toggle-btn" onClick={toggleMode}>
            {mode === 'login' ? 'Sign up' : 'Sign in'}
          </button>
        </p>
      </div>
    </div>
  );
}
