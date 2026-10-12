import { api } from '../../../shared/api';
import type { Conversation, UserSearchResult } from '../../../shared/types';

export const userApi = {
  /**
   * Search active users by username or display name.
   * Used by the New Conversation modal.
   */
  search(query: string): Promise<UserSearchResult[]> {
    return api.get<UserSearchResult[]>(`/api/v1/users/search?q=${encodeURIComponent(query)}`);
  },

  /**
   * Create a direct conversation with a user.
   * Returns the full conversation object (or at least the minimal shape needed).
   */
  createConversation(otherUserId: number): Promise<Conversation> {
    return api.post<Conversation>('/api/v1/conversations/direct', {
      targetUserId: otherUserId,
    });
  },
};
