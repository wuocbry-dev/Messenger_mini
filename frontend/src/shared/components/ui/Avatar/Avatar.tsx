import React, { useEffect, useState } from 'react';
import './Avatar.css';

interface AvatarProps {
  src?: string;
  name: string;
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl';
  online?: boolean;
  className?: string;
}

function getInitials(name: string): string {
  return name
    .split(/[\s_-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((n) => n[0])
    .join('')
    .toUpperCase();
}

const AVATAR_COLORS = [
  '#5B5BD6', // primary
  '#8B5CF6', // accent
  '#3B82F6', // blue
  '#10B981', // emerald
  '#F59E0B', // amber
  '#EF4444', // red
  '#EC4899', // pink
  '#6366F1', // indigo
];

function getColorFromName(name: string): string {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}

export function Avatar({ src, name, size = 'md', online, className = '' }: AvatarProps) {
  const initials = getInitials(name);
  const bgColor = getColorFromName(name);
  // Presigned avatar URLs expire; fall back to initials if the image fails.
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [src]);

  return (
    <div className={`avatar avatar-${size} ${className}`}>
      {src && !failed ? (
        <img src={src} alt={name} className="avatar-img" onError={() => setFailed(true)} />
      ) : (
        <span className="avatar-initials" style={{ background: bgColor }}>
          {initials}
        </span>
      )}
      {online !== undefined && (
        <span className={`avatar-status ${online ? 'avatar-status--online' : 'avatar-status--offline'}`} />
      )}
    </div>
  );
}
