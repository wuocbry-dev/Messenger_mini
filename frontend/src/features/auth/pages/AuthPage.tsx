import { useState, useCallback, useRef } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { authApi } from '../api/authApi';
import type { LoginRequest, RegisterRequest } from '../api/authApi';
import { Button } from '../../../shared/components/ui/Button';
import { Input } from '../../../shared/components/ui/Input';
import { Alert } from '../../../shared/components/ui/Alert';
import { PasswordInput } from '../components/PasswordInput';
import { ForgotPasswordForm } from '../components/ForgotPasswordForm';
import { ResetPasswordForm } from '../components/ResetPasswordForm';
import { FeatureShowcase, SHOWCASE_SCENES, type ShowcaseScene } from '../../../shared/components/FeatureShowcase';
import './AuthPage.css';

type Mode = 'login' | 'register' | 'forgot' | 'reset';

const HEADINGS: Record<Mode, { title: string; subtitle: string }> = {
  login: { title: 'Welcome back', subtitle: 'Sign in to continue to Messenger Mini' },
  register: { title: 'Create account', subtitle: 'Get started with your free account' },
  forgot: { title: 'Forgot password?', subtitle: "Enter your account email and we'll send you a reset link" },
  reset: { title: 'Set a new password', subtitle: 'Choose a new password for your account' },
};

/** Router state used when leaving /reset-password, which remounts this page on /auth. */
interface AuthNavState {
  mode?: Mode;
  notice?: string;
}

interface AuthPageProps {
  /** '/reset-password' opens straight into the reset form. */
  initialMode?: Mode;
}

export function AuthPage({ initialMode = 'login' }: AuthPageProps) {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const navState = useLocation().state as AuthNavState | null;
  const [mode, setMode] = useState<Mode>(navState?.mode ?? initialMode);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(navState?.notice ?? null);
  const [loading, setLoading] = useState(false);
  const [caption, setCaption] = useState<string>(SHOWCASE_SCENES[0].caption);
  const onSceneChange = useCallback((scene: ShowcaseScene) => setCaption(scene.caption), []);
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

  const switchMode = (next: Mode) => {
    setMode(next);
    setError(null);
    setNotice(null);
    setForm(next === 'register'
      ? { username: '', email: '', password: '', displayName: '' }
      : { usernameOrEmail: '', password: '' }
    );
  };

  const toggleMode = () => switchMode(mode === 'login' ? 'register' : 'login');

  // Leave /reset-password so the token is no longer in the address bar.
  const leaveResetPage = (next: Mode, message?: string) => {
    if (initialMode === 'reset') {
      navigate('/auth', { replace: true, state: { mode: next, notice: message } satisfies AuthNavState });
      return;
    }
    switchMode(next);
    if (message) setNotice(message);
  };

  const handleResetDone = () => {
    leaveResetPage('login', 'Your password has been reset. Sign in with your new password.');
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

          <FeatureShowcase fluid onDark onSceneChange={onSceneChange} className="auth-showcase" />
          <p className="auth-showcase-caption">{caption}</p>
        </div>
      </div>

      {/* Right form panel */}
      <div className="auth-panel-right">
        <div className="auth-card animate-fade-in">
          {/* Phones hide the promotional panel, so keep the product mark here. */}
          <div className="auth-mobile-logo" aria-hidden="true">
            <svg width="40" height="40" viewBox="0 0 32 32" fill="none">
              <rect width="32" height="32" rx="9" fill="url(#auth-mobile-logo-bg)" />
              <path d="M8 12c0-1.1.9-2 2-2h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H18l-4 4v-4H10a2 2 0 0 1-2-2v-6Z" fill="white" />
              <defs>
                <linearGradient id="auth-mobile-logo-bg" x1="0" y1="0" x2="32" y2="32">
                  <stop stopColor="#5B5BD6" />
                  <stop offset="1" stopColor="#7C5CFC" />
                </linearGradient>
              </defs>
            </svg>
            <span>Messenger Mini</span>
          </div>
          {/* Narrow screens hide the hero panel, so show a compact showcase here. */}
          <FeatureShowcase fluid className="auth-compact-showcase" />
          <div className="auth-brand">
            <h1 className="auth-title">{HEADINGS[mode].title}</h1>
            <p className="auth-subtitle">{HEADINGS[mode].subtitle}</p>
          </div>

          {mode === 'forgot' && (
            <ForgotPasswordForm onBackToLogin={() => switchMode('login')} />
          )}

          {mode === 'reset' && (
            <ResetPasswordForm
              token={searchParams.get('token')}
              onDone={handleResetDone}
              onRequestNewLink={() => leaveResetPage('forgot')}
            />
          )}

          {(mode === 'login' || mode === 'register') && (
          <>
          {notice && <Alert type="success" message={notice} onClose={() => setNotice(null)} />}
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
                    autoCapitalize="none"
                    autoCorrect="off"
                    spellCheck={false}
                  />
                  <Input
                    label="Email"
                    type="email"
                    placeholder="you@example.com"
                    value={(form as RegisterRequest).email}
                    onChange={update('email')}
                    required
                    autoComplete="email"
                    autoCapitalize="none"
                    spellCheck={false}
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
                autoCapitalize="none"
                autoCorrect="off"
                spellCheck={false}
              />
            )}

            <PasswordInput
              label="Password"
              placeholder={mode === 'register' ? 'Create a strong password' : 'Enter your password'}
              value={form.password}
              onChange={update('password')}
              required
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            />

            {mode === 'login' && (
              <div className="auth-forgot-row">
                <button type="button" className="auth-toggle-btn" onClick={() => switchMode('forgot')}>
                  Forgot password?
                </button>
              </div>
            )}

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
          </>
          )}
        </div>
      </div>
    </div>
  );
}
