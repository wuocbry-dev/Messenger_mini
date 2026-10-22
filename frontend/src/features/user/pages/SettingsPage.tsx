import React, { useState, useCallback, useEffect, useRef } from 'react';
import { Avatar } from '../../../shared/components/ui/Avatar';
import { Button } from '../../../shared/components/ui/Button';
import { Input } from '../../../shared/components/ui/Input';
import { Alert } from '../../../shared/components/ui/Alert';
import { showToast, type ToastData } from '../../../shared/components/ui/Toast';
import { uploadFile } from '../../../shared/api';
import { authApi } from '../../auth/api/authApi';
import { updateStoredUser } from '../../auth/store/authStore';
import type { User } from '../../../shared/types';
import './SettingsPage.css';

const MAX_AVATAR_BYTES = 5 * 1024 * 1024;
const AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
const MAX_NAME_LENGTH = 100;

type AlertPermission = NotificationPermission | 'unsupported';

function notificationPermission(): AlertPermission {
  return typeof Notification === 'undefined' ? 'unsupported' : Notification.permission;
}

/** Lets the browser show incoming calls while the app is in the background. */
function CallAlertsSetting() {
  const [permission, setPermission] = useState<AlertPermission>(notificationPermission);

  const enable = async () => {
    try {
      setPermission(await Notification.requestPermission());
    } catch {
      setPermission(notificationPermission());
    }
  };

  return (
    <div className="settings-section">
      <h2 className="settings-section-title">Notifications</h2>
      <div className="settings-alerts">
        <div className="settings-alerts-text">
          <span className="settings-alerts-title">Incoming call alerts</span>
          <span className="settings-alerts-hint">
            {permission === 'granted'
              ? 'On. A notification appears when someone calls while this app is in the background.'
              : permission === 'denied'
              ? 'Blocked. Allow notifications for this site in your browser settings.'
              : permission === 'unsupported'
              ? 'Not available in this browser. On iPhone, add the app to your Home Screen first.'
              : 'Get a notification when someone calls while this app is in the background.'}
          </span>
        </div>
        {permission === 'default' && (
          <Button type="button" variant="secondary" size="sm" onClick={() => void enable()}>
            Turn on
          </Button>
        )}
      </div>
    </div>
  );
}

export function SettingsPage() {
  const [profile, setProfile] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [displayName, setDisplayName] = useState('');
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const [removeAvatar, setRemoveAvatar] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const addToast = useCallback((type: ToastData['type'], message: string) => {
    showToast(type, message);
  }, []);

  useEffect(() => {
    authApi
      .me()
      .then((user) => {
        setProfile(user);
        setDisplayName(user.displayName);
      })
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load profile'))
      .finally(() => setLoading(false));
  }, []);

  // Release the local preview URL when it is replaced or the page unmounts.
  useEffect(() => {
    if (!avatarFile) {
      setAvatarPreview(null);
      return;
    }
    const url = URL.createObjectURL(avatarFile);
    setAvatarPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [avatarFile]);

  const handlePickFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = ''; // allow re-picking the same file
    if (!file) return;
    if (!AVATAR_TYPES.includes(file.type)) {
      setError('Please choose a JPEG, PNG, GIF or WebP image');
      return;
    }
    if (file.size > MAX_AVATAR_BYTES) {
      setError('Image is too large (max 5 MB)');
      return;
    }
    setError(null);
    setAvatarFile(file);
    setRemoveAvatar(false);
  };

  const handleRemoveAvatar = () => {
    setAvatarFile(null);
    setRemoveAvatar(true);
  };

  const trimmedName = displayName.trim();
  const nameChanged = profile !== null && trimmedName !== profile.displayName;
  const avatarChanged = avatarFile !== null || (removeAvatar && !!profile?.avatarObjectKey);
  const dirty = nameChanged || avatarChanged;
  const nameInvalid = trimmedName.length === 0 || trimmedName.length > MAX_NAME_LENGTH;

  const shownAvatar = avatarPreview ?? (removeAvatar ? undefined : profile?.avatarUrl);
  const hasAvatar = avatarFile !== null || (!removeAvatar && !!profile?.avatarObjectKey);

  const handleSave = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      if (!profile || !dirty || nameInvalid) return;

      setError(null);
      setSaving(true);
      try {
        let avatarObjectKey = profile.avatarObjectKey ?? '';
        if (avatarFile) {
          avatarObjectKey = (await uploadFile(avatarFile)).objectKey;
        } else if (removeAvatar) {
          avatarObjectKey = '';
        }

        const updated = await authApi.updateProfile({ displayName: trimmedName, avatarObjectKey });
        setProfile(updated);
        setDisplayName(updated.displayName);
        setAvatarFile(null);
        setRemoveAvatar(false);
        updateStoredUser(updated);
        addToast('success', 'Profile updated successfully');
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Failed to update profile';
        setError(message);
        addToast('error', message);
      } finally {
        setSaving(false);
      }
    },
    [profile, dirty, nameInvalid, avatarFile, removeAvatar, trimmedName, addToast]
  );

  const handleReset = () => {
    if (!profile) return;
    setDisplayName(profile.displayName);
    setAvatarFile(null);
    setRemoveAvatar(false);
    setError(null);
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

        {error && <Alert type="error" message={error} onClose={() => setError(null)} />}

        <form className="settings-form" onSubmit={handleSave}>
          <div className="settings-section">
            <h2 className="settings-section-title">Profile</h2>

            <div className="settings-profile-header">
              <Avatar name={trimmedName || profile?.displayName || 'User'} src={shownAvatar} size="xl" />
              <div className="settings-profile-info">
                <span className="settings-profile-name">{profile?.displayName}</span>
                <span className="settings-profile-username">@{profile?.username}</span>
                <div className="settings-avatar-actions">
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept={AVATAR_TYPES.join(',')}
                    className="settings-avatar-input"
                    onChange={handlePickFile}
                    aria-label="Upload profile photo"
                  />
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    onClick={() => fileInputRef.current?.click()}
                    disabled={saving}
                  >
                    {hasAvatar ? 'Change photo' : 'Upload photo'}
                  </Button>
                  {hasAvatar && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={handleRemoveAvatar}
                      disabled={saving}
                    >
                      Remove
                    </Button>
                  )}
                </div>
                <span className="settings-avatar-hint">JPEG, PNG, GIF or WebP, up to 5 MB</span>
              </div>
            </div>

            <div className="settings-fields">
              <Input
                label="Display Name"
                type="text"
                placeholder="Your display name"
                value={displayName}
                maxLength={MAX_NAME_LENGTH}
                onChange={(e) => setDisplayName(e.target.value)}
                error={trimmedName.length === 0 ? 'Display name is required' : undefined}
              />
              <Input
                label="Email"
                type="email"
                value={profile?.email ?? ''}
                disabled
                hint="Email cannot be changed"
              />
            </div>
          </div>

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

          <div className="settings-actions">
            <Button type="button" variant="ghost" onClick={handleReset} disabled={!dirty || saving}>
              Reset
            </Button>
            <Button type="submit" variant="primary" loading={saving} disabled={!dirty || nameInvalid}>
              Save Changes
            </Button>
          </div>
        </form>

        <CallAlertsSetting />
      </div>
    </div>
  );
}
