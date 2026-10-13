/**
 * Direct-to-R2 upload using a backend-issued presigned PUT URL.
 * The backend allocates the object key; the browser never sees R2 secrets.
 */
import { api } from './client';

export interface PreparedUpload {
  fileId: number;
  objectKey: string;
  originalName: string;
  mimeType: string;
  sizeBytes: number;
  uploadUrl: string;
  uploadUrlExpiresInSeconds: number;
}

export async function uploadFile(file: File): Promise<PreparedUpload> {
  const mimeType = file.type || 'application/octet-stream';
  const prepared = await api.post<PreparedUpload>('/api/v1/files', {
    originalName: file.name,
    mimeType,
    sizeBytes: file.size,
    checksumSha256: null,
  });

  const res = await fetch(prepared.uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': mimeType },
    body: file,
  });
  if (!res.ok) {
    const details = await res.text().catch(() => '');
    const suffix = details ? `: ${details.slice(0, 240)}` : '';
    throw new Error(`Failed to upload file (${res.status})${suffix}`);
  }
  return prepared;
}
