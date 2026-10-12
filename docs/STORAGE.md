# Storage

Messenger Mini uses Cloudflare R2 for uploaded binary objects.

## R2 Stores

- Avatars.
- Chat images.
- Attachments and files.

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
```
