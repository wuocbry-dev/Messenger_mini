import React, { useState, useEffect, useCallback } from 'react';
import { Avatar } from '../../../shared/components/ui/Avatar';
import { EmptyState } from '../../../shared/components/ui/EmptyState';
import { Skeleton } from '../../../shared/components/ui/Skeleton';
import { callApi } from '../api/callApi';
import { loadAuthFromStorage } from '../../auth/store/authStore';
import type { CallResponse } from '../../../shared/types';
import './CallsPage.css';

function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const diffDays = Math.floor((now.getTime() - date.getTime()) / (1000 * 60 * 60 * 24));
  
  if (diffDays === 0) {
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  } else if (diffDays === 1) {
    return 'Yesterday';
  } else if (diffDays < 7) {
    return date.toLocaleDateString([], { weekday: 'short' });
  } else {
    return date.toLocaleDateString([], { month: 'short', day: 'numeric' });
  }
}

function formatDuration(startStr: string, endStr?: string): string {
  if (!endStr) return '--:--';
  const start = new Date(startStr).getTime();
  const end = new Date(endStr).getTime();
  const diffSec = Math.floor((end - start) / 1000);
  const min = Math.floor(diffSec / 60);
  const sec = diffSec % 60;
  return `${min}:${sec.toString().padStart(2, '0')}`;
}

function getStatusLabel(status: string): string {
  switch (status) {
    case 'COMPLETED': return 'Completed';
    case 'ENDED': return 'Ended';
    case 'REJECTED': return 'Declined';
    case 'CANCELLED': return 'Cancelled';
    case 'MISSED': return 'Missed';
    default: return status;
  }
}

export function CallsPage() {
  const [calls, setCalls] = useState<CallResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedCall, setSelectedCall] = useState<CallResponse | null>(null);
  const currentUser = loadAuthFromStorage().user;
  const currentUserId = currentUser?.id;

  useEffect(() => {
    setLoading(true);
    callApi
      .getHistory({ page: 0, size: 50 })
      .then((res) => {
        setCalls(res.content ?? []);
      })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const getOtherUser = useCallback((call: CallResponse): { id: number; name: string; username: string } => {
    if (call.callerId === currentUserId) {
      return { id: call.calleeId, name: call.calleeUsername, username: call.calleeUsername };
    }
    return { id: call.callerId, name: call.callerUsername, username: call.callerUsername };
  }, [currentUserId]);

  const isIncoming = useCallback((call: CallResponse): boolean => {
    return call.calleeId === currentUserId;
  }, [currentUserId]);

  const isMissedOrRejected = useCallback((call: CallResponse): boolean => {
    return call.status === 'REJECTED' || call.status === 'CANCELLED' || call.status === 'MISSED';
  }, []);

  return (
    <div className="calls-page">
      <div className="calls-page-content">
        <div className="calls-page-header">
          <h1 className="calls-page-title">Calls</h1>
          <p className="calls-page-subtitle">Your call history</p>
        </div>

        <div className="calls-list">
          {loading ? (
            Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="call-item-skeleton">
                <Skeleton width={48} height={48} borderRadius="var(--radius-full)" />
                <div className="call-item-skeleton-text">
                  <Skeleton width="60%" height={16} />
                  <Skeleton width="40%" height={12} />
                </div>
              </div>
            ))
          ) : calls.length === 0 ? (
            <EmptyState
              title="No call history"
              description="Your voice call history will appear here"
              icon={
                <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 12 19.79 19.79 0 0 1 1.61 3.38 2 2 0 0 1 3.6 1.22h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
                </svg>
              }
            />
          ) : (
            calls.map((call) => {
              const other = getOtherUser(call);
              const incoming = isIncoming(call);
              const missed = isMissedOrRejected(call);

              return (
                <div
                  key={call.id}
                  className={`call-item${selectedCall?.id === call.id ? ' call-item--selected' : ''}`}
                  onClick={() => setSelectedCall(call)}
                >
                  <div className="call-item-avatar">
                    <Avatar name={other.name} size="md" />
                    <span className={`call-item-type-badge${incoming ? ' call-item-type-badge--incoming' : ''}`}>
                      {incoming ? (
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                          <polyline points="15 3 21 3 21 9" />
                          <path d="M21 3l-9 9" />
                          <line x1="10" y1="14" x2="3" y2="21" />
                        </svg>
                      ) : (
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                          <polyline points="9 3 3 3 3 9" />
                          <path d="M3 3l9 9" />
                          <line x1="14" y1="14" x2="21" y2="21" />
                        </svg>
                      )}
                    </span>
                  </div>
                  <div className="call-item-info">
                    <span className="call-item-name">{other.name}</span>
                    <div className="call-item-meta">
                      {missed ? (
                        <span className="call-item-status call-item-status--missed">Missed</span>
                      ) : (
                        <span className="call-item-status">{getStatusLabel(call.status)}</span>
                      )}
                      {call.answeredAt && (
                        <span className="call-item-duration">{formatDuration(call.startedAt, call.endedAt)}</span>
                      )}
                    </div>
                  </div>
                  <div className="call-item-time">
                    {formatDate(call.startedAt)}
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* Call Detail Panel */}
      {selectedCall && (
        <div className="calls-detail-panel">
          <div className="calls-detail-header">
            <Avatar name={getOtherUser(selectedCall).name} size="lg" />
            <h2 className="calls-detail-name">{getOtherUser(selectedCall).name}</h2>
            <span className="calls-detail-username">@{getOtherUser(selectedCall).username}</span>
          </div>

          <div className="calls-detail-info">
            <div className="calls-detail-row">
              <span className="calls-detail-label">Type</span>
              <span className="calls-detail-value">{selectedCall.callType}</span>
            </div>
            <div className="calls-detail-row">
              <span className="calls-detail-label">Direction</span>
              <span className="calls-detail-value">{isIncoming(selectedCall) ? 'Incoming' : 'Outgoing'}</span>
            </div>
            <div className="calls-detail-row">
              <span className="calls-detail-label">Status</span>
              <span className="calls-detail-value">{getStatusLabel(selectedCall.status)}</span>
            </div>
            <div className="calls-detail-row">
              <span className="calls-detail-label">Started</span>
              <span className="calls-detail-value">
                {new Date(selectedCall.startedAt).toLocaleString()}
              </span>
            </div>
            {selectedCall.answeredAt && (
              <div className="calls-detail-row">
                <span className="calls-detail-label">Duration</span>
                <span className="calls-detail-value">
                  {formatDuration(selectedCall.startedAt, selectedCall.endedAt)}
                </span>
              </div>
            )}
            {selectedCall.endReason && (
              <div className="calls-detail-row">
                <span className="calls-detail-label">Ended because</span>
                <span className="calls-detail-value">{selectedCall.endReason}</span>
              </div>
            )}
          </div>

          <button
            className="calls-detail-close"
            onClick={() => setSelectedCall(null)}
          >
            Close
          </button>
        </div>
      )}
    </div>
  );
}
