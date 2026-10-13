// ─── Shared Types ───────────────────────────────────────────────────────────────

export interface User {
  id: number;
  username: string;
  displayName: string;
  email?: string;
  avatarObjectKey?: string;
  avatarUrl?: string;
  status?: string;
  lastSeenAt?: string;
}

export interface UserSearchResult {
  id: number;
  username: string;
  displayName: string;
  avatarObjectKey?: string;
  avatarUrl?: string;
  /** Currently connected (directory only). */
  online?: boolean;
  /** UTC timestamp without zone, as sent by the backend. */
  lastSeenAt?: string;
}

export interface UserSummary {
  id: number;
  username: string;
  displayName: string;
  avatarUrl?: string;
}

export interface UserProfile extends UserSummary {
  email?: string;
}

export interface Conversation {
  id: number;
  type: 'DIRECT' | 'GROUP';
  title?: string;
  avatarObjectKey?: string;
  avatarUrl?: string;
  directKey?: string;
  directWithUser?: User;
  createdBy: number;
  createdAt: string;
  updatedAt: string;
  lastMessage?: Message;
  lastMessageAt?: string;
  unreadCount?: number;
  members?: ConversationMember[];
}

export interface ConversationMember {
  userId: number;
  username: string;
  displayName: string;
  avatarObjectKey?: string;
  avatarUrl?: string;
  role: string;
  joinedAt: string;
  leftAt?: string;
  isActive: boolean;
}

export interface Message {
  id: number;
  conversationId: number;
  senderId: number;
  senderUsername: string;
  senderDisplayName: string;
  senderAvatarUrl?: string;
  type: 'TEXT' | 'IMAGE' | 'FILE';
  content?: string;
  attachments?: MessageAttachment[];
  objectKey?: string;
  fileName?: string;
  fileSize?: number;
  mimeType?: string;
  downloadUrl?: string;
  createdAt: string;
  receipts: MessageReceipt[];
}

export interface MessageAttachment {
  id: number;
  fileId: number;
  attachmentType: 'IMAGE' | 'FILE';
  mimeType?: string;
  originalName?: string;
  sizeBytes?: number;
}

export interface MessageReceipt {
  messageId: number;
  conversationId: number;
  userId: number;
  state: 'DELIVERED' | 'READ';
  deliveredAt?: string;
  readAt?: string;
}

export interface TypingIndicator {
  conversationId: number;
  senderId: number;
  senderUsername: string;
  typing: boolean;
  emittedAt: string;
}

export interface PresenceUpdate {
  userId: number;
  online: boolean;
  lastSeenAt?: string;
}

export interface CallEvent {
  type: 'INCOMING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED' | 'MISSED' | 'ENDED';
  callId: number;
  conversationId: number;
  callerId: number;
  calleeId: number;
  callType: 'VOICE' | 'VIDEO';
  status: string;
  agoraChannelName: string;
  startedAt: string;
  answeredAt?: string;
  endedAt?: string;
  endReason?: string;
}

export interface CallTokenResponse {
  callId: number;
  agoraChannelName: string;
  token: string;
  appId: string;
  /** Numeric Agora uid the token is bound to (the user's id). */
  uid: number;
  expiresInSeconds: number;
}

// ─── API Response Types ────────────────────────────────────────────────────────

export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
}

export interface Paginated<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

/** Shape returned by GET /api/v1/calls (history). */
export interface CallHistoryPage {
  calls: CallResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface MessagePageResponse {
  messages: Message[];
  content?: Message[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

// ─── Call Types ─────────────────────────────────────────────────────────────────

export interface CallResponse {
  id: number;
  conversationId: number;
  callerId: number;
  calleeId: number;
  callerUsername: string;
  calleeUsername: string;
  callType: 'VOICE' | 'VIDEO';
  status: string;
  agoraChannelName: string;
  startedAt: string;
  answeredAt?: string;
  endedAt?: string;
  endedBy?: number;
  endReason?: string;
}
