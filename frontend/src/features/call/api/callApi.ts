import { api } from '../../../shared/api';
import type { CallResponse, CallTokenResponse, CallEvent } from '../../../shared/types';

export const callApi = {
  start(conversationId: number, calleeId: number): Promise<CallResponse> {
    return api.post<CallResponse>('/api/v1/calls', { conversationId, calleeId });
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
};
