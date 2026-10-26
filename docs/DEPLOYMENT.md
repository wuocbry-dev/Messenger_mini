# Deployment (VPS + Docker Compose)

Production runs as three containers defined in `docker-compose.prod.yml`:

```text
Browser ──HTTPS──> [host nginx + certbot]  (optional, see "HTTPS")
                          │
                   frontend (nginx)  :80
                     ├─ /        → React build (static)
                     ├─ /api/*   → backend:8080
                     └─ /ws      → backend:8080 (WebSocket/STOMP)
                   backend (Spring Boot, profile prod)
                     └─ mysql:3306 (internal network only, volume mysql-data)
```

The frontend bundle is built without `VITE_API_URL` / `VITE_WS_URL`, so it calls
the same origin and the frontend nginx forwards `/api` and `/ws`. Cloudflare R2
and Agora remain external services.

## Files

| File | Purpose |
| --- | --- |
| `backend/Dockerfile` | Maven build → JRE 17 runtime image |
| `frontend/Dockerfile` | `npm run build` → nginx image |
| `frontend/nginx.conf` | Static files, SPA fallback, `/api` + `/ws` proxy |
| `docker-compose.prod.yml` | MySQL + backend + frontend |
| `.env.production.example` | Template for server secrets (`.env.production`, never committed) |
| `deploy.sh` | `git pull` + rebuild + restart |

`docker-compose.yml` stays the local-development MySQL only.

## First Deploy

Requirements: Ubuntu VPS (≥ 2 GB RAM recommended), a domain whose DNS A record
points at the VPS.

```bash
# Docker
curl -fsSL https://get.docker.com | sh

# Code
git clone <repo-url> /opt/messenger && cd /opt/messenger
cp .env.production.example .env.production
nano .env.production          # real DB passwords, JWT_SECRET, R2, Agora, CORS origin

bash deploy.sh                # or: docker compose -f docker-compose.prod.yml --env-file .env.production up -d --build
```

Flyway creates/migrates the schema on backend startup.

Useful commands:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.production logs -f backend
docker compose -f docker-compose.prod.yml --env-file .env.production ps
```

## HTTPS

HTTPS is required in practice: browsers only allow microphone access (Agora voice
calls) in a secure context.

**Option A: host nginx + certbot (recommended)**

1. In `.env.production` set `HTTP_BIND=127.0.0.1:8081` and run `bash deploy.sh`.
2. On the host:

   ```bash
   sudo apt install -y nginx certbot python3-certbot-nginx
   ```

   `/etc/nginx/sites-available/messenger`:

   ```nginx
   server {
       server_name chat.example.com;
       client_max_body_size 50m;

       location / {
           proxy_pass http://127.0.0.1:8081;
           proxy_http_version 1.1;
           proxy_set_header Upgrade $http_upgrade;
           proxy_set_header Connection "upgrade";
           proxy_set_header Host $host;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
           proxy_read_timeout 1h;
       }
   }
   ```

   ```bash
   sudo ln -s /etc/nginx/sites-available/messenger /etc/nginx/sites-enabled/
   sudo nginx -t && sudo systemctl reload nginx
   sudo certbot --nginx -d chat.example.com
   ```

**Option B: Cloudflare proxy.** Keep `HTTP_BIND=80`, enable the orange-cloud
proxy on the DNS record, and enable WebSockets in the Cloudflare dashboard.

Set `CORS_ALLOWED_ORIGINS` to the final `https://` origin in both cases.

## External Service Checklist

- R2 bucket CORS policy allows the production origin (presigned uploads/downloads).
- Agora project has App Certificate enabled and matches `AGORA_APP_CERTIFICATE`.
- Firewall: only 22, 80, 443 open (`ufw allow OpenSSH && ufw allow 80,443/tcp && ufw enable`).

## Updating

```bash
cd /opt/messenger && bash deploy.sh
```

## Database Backup

```bash
docker compose -f docker-compose.prod.yml --env-file .env.production exec mysql \
  sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction messenger_mini' \
  > backup_$(date +%F).sql
```

The `mysql-data` volume survives `down`/`up`; `down -v` deletes it.
