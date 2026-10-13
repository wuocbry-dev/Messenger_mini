import { api } from '../../../shared/api';
import type { Message, MessagePageResponse } from '../../../shared/types';

export interface UploadFileResponse {
  fileId: number;
  objectKey: string;
  originalName: string;
  mimeType: string;
  sizeBytes: number;
  uploadUrl: string;
  uploadUrlExpiresInSeconds: number;
}

export interface FileResponse {
  fileId: number;
  originalName: string;
  mimeType: string;
  sizeBytes: number;
  downloadUrl: string;
  downloadUrlExpiresInSeconds: number;
}

export const messageApi = {
  list(conversationId: number, params?: { page?: number; size?: number }): Promise<MessagePageResponse> {
    const page = params?.page ?? 0;
    const size = params?.size ?? 50;
    return api.get<MessagePageResponse>(
      `/api/v1/conversations/${conversationId}/messages?page=${page}&size=${size}`
    );
  },

  async uploadAttachment(file: File): Promise<UploadFileResponse> {
    const prepared = await api.post<UploadFileResponse>('/api/v1/files', {
      originalName: file.name,
      mimeType: file.type || 'application/octet-stream',
      sizeBytes: file.size,
      checksumSha256: null,
    });

    const uploadRes = await fetch(prepared.uploadUrl, {
      method: 'PUT',
      headers: {
        'Content-Type': file.type || 'application/octet-stream',
      },
      body: file,
    });
    if (!uploadRes.ok) {
      const details = await uploadRes.text().catch(() => '');
      const suffix = details ? `: ${details.slice(0, 240)}` : '';
      throw new Error(`Failed to upload file (${uploadRes.status})${suffix}`);
    }

    return prepared;
  },

  sendAttachment(
    conversationId: number,
    fileId: number,
    type: 'IMAGE' | 'FILE',
    content?: string,
  ): Promise<Message> {
    return api.post<Message>(`/api/v1/conversations/${conversationId}/attachments`, {
      conversationId,
      fileId,
      type,
      content,
    });
  },

  getFile(fileId: number): Promise<FileResponse> {
    return api.get<FileResponse>(`/api/v1/files/${fileId}/download`);
  },

  getDownloadUrl(fileId: number): Promise<FileResponse> {
    return api.get<FileResponse>(`/api/v1/files/${fileId}/download`);
  },
};
