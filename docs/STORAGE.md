# Storage

Messenger Mini uses Cloudflare R2 for uploaded binary objects.

## R2 Stores

- Avatars.
- Chat images.
- Attachments and files.
    10|
## MySQL Stores

- File metadata.
- Object keys.
- Uploader information.
- Message attachment relationships.

## Rules

- Backend owns R2 credentials.
- Never expose `R2_SECRET_ACCESS_KEY` to the frontend.
- Persist object keys, not temporary presigned URLs.
- Do not store uploaded binaries in MySQL.

## Expected Backend Environment Variables

```text
R2_ENDPOINT
R2_ACCESS_KEY_ID
R2_SECRET_ACCESS_KEY
R2_BUCKET_NAME
R2_PRESIGNED_TTL_SECONDS (default 900)
R2_KEY_PREFIX (default "messenger/")
```

---

## Object storage architecture (task 008)

The file/image storage slice uses Cloudflare R2 via the AWS SDK v2 S3 client. All R2 calls go through a single `ObjectStorageService` interface so the rest of the application can be unit-tested without R2 credentials.

```
HttpRequest
   ↓
FileController / MessageController
   ↓
FileService / MessageService
   ↓
ObjectStorageService  ← interface, package-private
   ↓
R2StorageService   (real, AWS SDK v2 S3)
   ↓
Cloudflare R2
```

`NoopObjectStorageService` is wired in when R2 is not configured so the application can still start (upload and download then fail with `503 STORAGE_UNAVAILABLE`).

### REST endpoints (task 008)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/files` | Pre-persist file metadata and return a presigned **PUT** URL the client uses to upload directly to R2. |
| `GET`  | `/api/v1/files/{fileId}/download` | Return file metadata plus a presigned **GET** URL (time-limited). |
| `POST` | `/api/v1/conversations/{conversationId}/attachments` | Send an IMAGE/FILE message that references an existing `stored_files` row. |

#### Upload request

```http
POST /api/v1/files
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "originalName": "photo.png",
  "mimeType": "image/png",
  "sizeBytes": 102400,
  "checksumSha256": "..."
}
```

#### Upload response (presigned PUT)

```json
{
  "fileId": 17,
  "objectKey": "messenger/u1/<uuid>.png",
  "originalName": "photo.png",
  "mimeType": "image/png",
  "sizeBytes": 102400,
  "checksumSha256": "...",
  "etag": null,
  "createdAt": "2026-10-12T12:34:56.789",
  "uploadUrl": "https://<account>.r2.cloudflarestorage.com/...",
  "uploadUrlExpiresInSeconds": 900
}
```

The client then issues:

```http
PUT <uploadUrl>
Content-Type: image/png
Content-Length: 102400

<binary>
```

#### Download access

Only the uploader, or an active member of a conversation where the file is attached to a message, may call the download endpoint. Everyone else receives `404 FILE_NOT_FOUND`, so file ids cannot be probed.

#### Download response (presigned GET)

```json
{
  "fileId": 17,
  "objectKey": "messenger/u1/<uuid>.png",
  "originalName": "photo.png",
  "mimeType": "image/png",
  "sizeBytes": 102400,
  "checksumSha256": "...",
  "etag": "etag-1",
  "uploaderId": 1,
  "createdAt": "2026-10-12T12:34:56.789",
  "downloadUrl": "https://<account>.r2.cloudflarestorage.com/...",
  "downloadUrlExpiresInSeconds": 900
}
```

### Object key policy

- Server-generated; the client never supplies the storage path.
- Format: `<keyPrefix>u<userId>/<uuid><ext>`.
- The extension is taken from the original filename and validated against
  `\\.[a-z0-9]{1,8}`; anything else falls back to no extension.
- The original filename is sanitized (path separators stripped, length
  capped) and persisted for display only — it is not used as the storage
  key.

### Upload validation

| Check | Reject code |
|---|---|
| MIME type not in allow-list (`image/jpeg`, `image/png`, `image/gif`, `image/webp`, `application/pdf`, `text/plain`, `application/zip`) | `UNSUPPORTED_MIME_TYPE` (415) |
| `sizeBytes > 50 MB` | `FILE_TOO_LARGE` (413) |
| Caller not authenticated | 401 |

The 50 MB cap matches the presigned URL's `Content-Length` constraint and
keeps the API surface safe for the university project. The cap lives on
`FileService.MAX_FILE_SIZE_BYTES`.

### Persisted metadata (`stored_files`)

| Column | Source |
|---|---|
| `uploader_id` | authenticated principal |
| `object_key` | server-generated |
| `original_name` | sanitized client value |
| `mime_type` | client value, lowercased |
| `size_bytes` | client-declared, can be reconciled later |
| `checksum_sha256` | optional, client-supplied |
| `etag` | null at prepare time; can be filled by a later reconciliation job |
| `created_at` | server-generated |

### Message attachment integration

`message_attachments` references `stored_files` by `file_id`. Sending an
IMAGE/FILE message via `POST /api/v1/conversations/{id}/attachments` creates
a `messages` row, a `message_attachments` row, and the standard
`message_receipts` rows in the same transaction. The backend rejects
attempts to attach a file that the caller did not upload.

### Security guarantees

- `R2_SECRET_ACCESS_KEY` is read from environment variables only; it never
  appears in any HTTP response, log line, or DTO.
- Presigned URLs are time-limited (`R2_PRESIGNED_TTL_SECONDS`, default 900s)
  and never persisted in MySQL.
- The presigned `PUT` URL binds the content type and the `Content-Length`
  the client must send; mismatches will be rejected by R2.
- Object keys are server-generated, include a per-user prefix, and embed a
  random UUID so they cannot be guessed from the outside.

### Profile avatars

- The client uploads the image with the normal flow (`POST /api/v1/files`, then
  `PUT` to the presigned URL) and saves the returned `objectKey` with
  `PATCH /api/v1/users/me/profile` (`displayName`, `avatarObjectKey`).
- Only the object key is stored (`users.avatar_object_key`). The backend
  accepts a new key only if it is an **image uploaded by the same user**
  (`INVALID_AVATAR` otherwise). A blank key removes the avatar.
- `avatarUrl` in `UserResponse`, `UserSearchResult` and
  `ConversationMemberResponse` is a presigned GET URL created per response
  (`AvatarUrlResolver`); it is never persisted and expires with
  `R2_PRESIGNED_TTL_SECONDS`. The frontend falls back to initials if it fails.
- The R2 bucket CORS policy must allow `PUT` from the frontend origin
  (including a dev-tunnel origin when testing through a tunnel).
