import { api } from '../../../shared/api';
import type { ActiveCallsResponse, CallResponse, CallTokenResponse, CallHistoryPage } from '../../../shared/types';

export const callApi = {
  /** Omit `calleeId` (null) to ring every other member of a group conversation. */
  start(
    conversationId: number,
    calleeId: number | null,
    callType: 'VOICE' | 'VIDEO' = 'VOICE',
  ): Promise<CallResponse> {
    return api.post<CallResponse>('/api/v1/calls', { conversationId, calleeId, callType });
  },
  invite(callId: number, userIds: number[]): Promise<CallResponse> {
    return api.post<CallResponse>(`/api/v1/calls/${callId}/invite`, { userIds });
  },
  upgradeToVideo(callId: number): Promise<CallResponse> {
    return api.post<CallResponse>(`/api/v1/calls/${callId}/video`);
  },
  accept(callId: number): Promise<CallResponse> {
    return api.post<CallResponse>(`/api/v1/calls/${callId}/accept`);
  },
  /** Joins a call already in progress (e.g. a group call I missed or left). */
  join(callId: number): Promise<CallResponse> {
    return api.post<CallResponse>(`/api/v1/calls/${callId}/join`);
  },
  /** Calls still ringing for me and calls I can join, for catching up after being offline. */
  active(): Promise<ActiveCallsResponse> {
    return api.get<ActiveCallsResponse>('/api/v1/calls/active');
  },
  reject(callId: number): Promise<CallResponse> {
    return api.post<CallResponse>(`/api/v1/calls/${callId}/reject`);
  },
  cancel(callId: number): Promise<CallResponse> {
    return api.post<CallResponse>(`/api/v1/calls/${callId}/cancel`);
  },
  end(callId: number): Promise<CallResponse> {
    return api.post<CallResponse>(`/api/v1/calls/${callId}/end`);
  },
  getToken(callId: number): Promise<CallTokenResponse> {
    return api.post<CallTokenResponse>(`/api/v1/calls/${callId}/token`);
  },
  get(callId: number): Promise<CallResponse> {
    return api.get<CallResponse>(`/api/v1/calls/${callId}`);
  },
  getHistory(params?: { page?: number; size?: number }): Promise<CallHistoryPage> {
    const searchParams = new URLSearchParams();
    if (params?.page !== undefined) searchParams.set('page', String(params.page));
    if (params?.size !== undefined) searchParams.set('size', String(params.size));
    const query = searchParams.toString();
    return api.get<CallHistoryPage>(`/api/v1/calls${query ? `?${query}` : ''}`);
  },
};
