import { api } from '../../../shared/api';
import type { CallResponse, CallTokenResponse, CallHistoryPage } from '../../../shared/types';

export const callApi = {
  start(
    conversationId: number,
    calleeId: number,
    callType: 'VOICE' | 'VIDEO' = 'VOICE',
  ): Promise<CallResponse> {
    return api.post<CallResponse>('/api/v1/calls', { conversationId, calleeId, callType });
  },
  accept(callId: number): Promise<CallResponse> {
    return api.post<CallResponse>(`/api/v1/calls/${callId}/accept`);
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
