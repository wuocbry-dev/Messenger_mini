import React, { useState, useCallback, useEffect } from 'react';
import { Avatar } from '../../../shared/components/ui/Avatar';
import { Button } from '../../../shared/components/ui/Button';
import { Input } from '../../../shared/components/ui/Input';
import { Alert } from '../../../shared/components/ui/Alert';
import { authApi } from '../../auth/api/authApi';
import { ToastContainer, type ToastData } from '../../../shared/components/ui/Toast';
import type { User } from '../../../shared/types';
import './SettingsPage.css';

function generateId() {
  return Math.random().toString(36).slice(2);
}

export function SettingsPage() {
  const [profile, setProfile] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [toasts, setToasts] = useState<ToastData[]>([]);
  const [form, setForm] = useState({
    displayName: '',
    email: '',
  });

  useEffect(() => {
    authApi
      .me()
      .then((user) => {
        setProfile(user);
        setForm({
          displayName: user.displayName,
          email: user.email ?? '',
        });
      })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const addToast = useCallback((type: ToastData['type'], message: string) => {
    const id = generateId();
    setToasts((prev) => [...prev, { id, type, message }]);
  }, []);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const handleSave = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    if (!profile) return;

    setError(null);
    setSuccess(null);
    setSaving(true);

    try {
      const updated = await authApi.updateProfile({
        displayName: form.displayName,
      });
      setProfile(updated);
      setSuccess('Profile updated successfully');
      addToast('success', 'Profile updated successfully');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to update profile';
      setError(message);
      addToast('error', message);
    } finally {
      setSaving(false);
    }
  }, [profile, form, addToast]);

  const update = (field: string) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
  };

  if (loading) {
    return (
      <div className="settings-page">
        <div className="settings-loading">Loading...</div>
      </div>
    );
  }

  return (
    <div className="settings-page">
      <div className="settings-content">
        <div className="settings-header">
          <h1 className="settings-title">Settings</h1>
          <p className="settings-subtitle">Manage your account and preferences</p>
        </div>

        {error && (
          <Alert type="error" message={error} onClose={() => setError(null)} />
        )}
        {success && (
          <Alert type="success" message={success} onClose={() => setSuccess(null)} />
        )}

        <form className="settings-form" onSubmit={handleSave}>
          {/* Profile Section */}
          <div className="settings-section">
            <h2 className="settings-section-title">Profile</h2>
            
            <div className="settings-profile-header">
              <Avatar
                name={profile?.displayName ?? 'User'}
                src={profile?.avatarUrl}
                size="lg"
              />
              <div className="settings-profile-info">
                <span className="settings-profile-name">{profile?.displayName}</span>
                <span className="settings-profile-username">@{profile?.username}</span>
              </div>
            </div>

            <div className="settings-fields">
              <Input
                label="Display Name"
                type="text"
                placeholder="Your display name"
                value={form.displayName}
                onChange={update('displayName')}
              />
              <Input
                label="Email"
                type="email"
                placeholder="your@email.com"
                value={form.email}
                onChange={update('email')}
                disabled
                hint="Email cannot be changed"
              />
            </div>
          </div>

          {/* Account Section */}
          <div className="settings-section">
            <h2 className="settings-section-title">Account</h2>
            <div className="settings-fields">
              <Input
                label="Username"
                type="text"
                value={profile?.username ?? ''}
                disabled
                hint="Username cannot be changed"
              />
            </div>
          </div>

          {/* Save Button */}
          <div className="settings-actions">
            <Button type="submit" variant="primary" loading={saving}>
              Save Changes
            </Button>
          </div>
        </form>
      </div>
      <ToastContainer toasts={toasts} onRemove={removeToast} />
    </div>
  );
}
