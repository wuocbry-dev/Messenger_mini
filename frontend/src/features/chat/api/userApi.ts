import { api } from '../../../shared/api';
import type { Conversation, UserSearchResult } from '../../../shared/types';

export const userApi = {
  /**
   * Get all active users except the current user.
   * Used by the user directory page.
   */
  list(): Promise<UserSearchResult[]> {
    return api.get<UserSearchResult[]>('/api/v1/users');
  },

  /**
   * Search active users by username or display name.
   * Used by the New Conversation modal.
   */
  search(query: string): Promise<UserSearchResult[]> {
    if (!query || query.trim().length === 0) {
      return Promise.resolve([]);
    }
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
