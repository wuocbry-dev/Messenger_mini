import { api } from '../../../shared/api';
import type { Conversation } from '../../../shared/types';

export interface CreateDirectRequest {
  targetUserId: number;
}

export const conversationApi = {
  list(): Promise<Conversation[]> {
    return api.get<Conversation[]>('/api/v1/conversations');
  },

  get(id: number): Promise<Conversation> {
    return api.get<Conversation>(`/api/v1/conversations/${id}`);
  },

  create(otherUserId: number): Promise<Conversation> {
    return api.post<Conversation>('/api/v1/conversations/direct', { targetUserId: otherUserId });
  },

  createGroup(memberIds: number[], title?: string): Promise<Conversation> {
    return api.post<Conversation>('/api/v1/conversations/group', {
      title: title?.trim() || null,
      memberIds,
    });
  },

  addMembers(conversationId: number, userIds: number[]): Promise<Conversation> {
    return api.post<Conversation>(`/api/v1/conversations/${conversationId}/members`, { userIds });
  },

  createDirect(data: CreateDirectRequest): Promise<Conversation> {
    return api.post<Conversation>('/api/v1/conversations/direct', data);
  },
};
