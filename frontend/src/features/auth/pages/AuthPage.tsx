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
      {/* Left promotional panel */}
      <div className="auth-panel-left">
        <div className="auth-left-content">
          <div className="auth-left-logo">
            <svg width="48" height="48" viewBox="0 0 32 32" fill="none">
              <rect width="32" height="32" rx="8" fill="white" fillOpacity="0.2" />
              <path d="M8 12c0-1.1.9-2 2-2h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H18l-4 4v-4H10a2 2 0 0 1-2-2v-6Z" fill="white" />
            </svg>
            <span className="auth-left-logo-text">Messenger Mini</span>
          </div>
          
          <p className="auth-left-tagline">
            Connect with friends and colleagues through seamless real-time messaging
          </p>

          <div className="auth-left-features">
            <div className="auth-left-feature">
              <div className="auth-left-feature-icon">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                </svg>
              </div>
              <span>Instant real-time messaging</span>
            </div>
            <div className="auth-left-feature">
              <div className="auth-left-feature-icon">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 12 19.79 19.79 0 0 1 1.61 3.38 2 2 0 0 1 3.6 1.22h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
                </svg>
              </div>
              <span>Voice calls with anyone</span>
            </div>
            <div className="auth-left-feature">
              <div className="auth-left-feature-icon">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                  <circle cx="8.5" cy="8.5" r="1.5" />
                  <polyline points="21 15 16 10 5 21" />
                </svg>
              </div>
              <span>Share photos and files securely</span>
            </div>
            <div className="auth-left-feature">
              <div className="auth-left-feature-icon">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10" />
                  <polyline points="12 6 12 12 16 14" />
                </svg>
              </div>
              <span>See when friends are online</span>
            </div>
          </div>
        </div>
      </div>

      {/* Right form panel */}
      <div className="auth-panel-right">
        <div className="auth-card animate-fade-in">
          <div className="auth-brand">
            <h1 className="auth-title">
              {mode === 'login' ? 'Welcome back' : 'Create account'}
            </h1>
            <p className="auth-subtitle">
              {mode === 'login' 
                ? 'Sign in to continue to Messenger Mini' 
                : 'Get started with your free account'}
            </p>
          </div>

          {error && <Alert type="error" message={error} onClose={() => setError(null)} />}

          <form className="auth-form" onSubmit={handleSubmit}>
            {mode === 'register' && (
              <>
                <Input
                  label="Display Name"
                  type="text"
                  placeholder="Your full name"
                  value={(form as RegisterRequest).displayName}
                  onChange={update('displayName')}
                  required
                />
                <div className="auth-form-row">
                  <Input
                    label="Username"
                    type="text"
                    placeholder="username"
                    value={(form as RegisterRequest).username}
                    onChange={update('username')}
                    required
                    autoComplete="username"
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
                </div>
              </>
            )}

            {mode === 'login' && (
              <Input
                label="Username or Email"
                type="text"
                placeholder="Enter your username or email"
                value={(form as LoginRequest).usernameOrEmail}
                onChange={update('usernameOrEmail')}
                required
                autoComplete="username"
              />
            )}

            <Input
              label="Password"
              type="password"
              placeholder={mode === 'register' ? 'Create a strong password' : 'Enter your password'}
              value={form.password}
              onChange={update('password')}
              required
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            />

            <Button 
              type="submit" 
              variant="primary" 
              loading={loading} 
              className="auth-submit"
            >
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
    </div>
  );
}
