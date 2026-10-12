import { api } from '../../../shared/api';
import type { Conversation } from '../../../shared/types';

export interface CreateDirectRequest {
  otherUserId: number;
}

export const conversationApi = {
  list(): Promise<Conversation[]> {
    return api.get<Conversation[]>('/api/v1/conversations');
  },

  get(id: number): Promise<Conversation> {
    return api.get<Conversation>(`/api/v1/conversations/${id}`);
  },

  createDirect(data: CreateDirectRequest): Promise<Conversation> {
    return api.post<Conversation>('/api/v1/conversations/direct', data);
  },
};
