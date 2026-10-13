import React, { useEffect, useState } from 'react';
import { Modal, Avatar } from '../../../../shared/components/ui';
import type { CallEvent } from '../../../../shared/types';
import './IncomingCallModal.css';

interface IncomingCallModalProps {
  call: CallEvent | null;
  onAccept: () => void;
  onReject: () => void;
  callerName: string;
  callerAvatar?: string;
  /** People in the call (the caller and the people being rung); more than two means a group call. */
  groupSize?: number;
}

export function IncomingCallModal({ call, onAccept, onReject, callerName, callerAvatar, groupSize = 0 }: IncomingCallModalProps) {
  const [ringing, setRinging] = useState(false);

  useEffect(() => {
    if (call?.type === 'INCOMING') {
      setRinging(true);
      const t = setTimeout(() => setRinging(false), 6000);
      return () => clearTimeout(t);
    }
    setRinging(false);
  }, [call?.callId, call?.type]);

  const kind = call?.callType === 'VIDEO' ? 'Video' : 'Voice';
  const isGroup = groupSize > 2;

  return (
    <Modal open={call?.type === 'INCOMING'} onClose={onReject} size="sm">
      <div className="call-modal" role="dialog" aria-label={`Incoming ${kind.toLowerCase()} call`}>
        <div className={`call-modal-avatar${ringing ? ' call-modal-avatar--ringing' : ''}`}>
          <Avatar name={callerName} src={callerAvatar} size="lg" />
        </div>
        <div className="call-modal-pulse-ring" aria-hidden="true" />
        <h2 className="call-modal-title">Incoming {isGroup ? `Group ${kind}` : kind} Call</h2>
        <p className="call-modal-subtitle">
          {callerName}
          {isGroup ? ` and ${groupSize - 1} ${groupSize - 1 === 1 ? 'other' : 'others'}` : ''}
        </p>

        <div className="call-modal-actions">
          <button
            className="call-action-btn call-action-btn--reject"
            onClick={onReject}
            aria-label="Decline call"
          >
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 12 19.79 19.79 0 0 1 1.61 3.38 2 2 0 0 1 3.6 1.22h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" transform="rotate(135 12 12)" />
            </svg>
          </button>
          <button
            className="call-action-btn call-action-btn--accept"
            onClick={onAccept}
            aria-label="Accept call"
          >
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 12 19.79 19.79 0 0 1 1.61 3.38 2 2 0 0 1 3.6 1.22h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
            </svg>
          </button>
        </div>
        <p className="call-modal-hint">Tap to answer or decline</p>
      </div>
    </Modal>
  );
}
