# Hướng dẫn deploy Messenger Mini lên VPS

Tài liệu này hướng dẫn deploy toàn bộ Messenger Mini lên một VPS Ubuntu, dùng Docker Compose cho ứng dụng và nginx + certbot trên VPS để có HTTPS.

> Bản tóm tắt kỹ thuật (tiếng Anh) nằm ở [DEPLOYMENT.md](DEPLOYMENT.md).

## 0. Thông tin cần chuẩn bị

Điền trước các thông tin dưới đây. Trong cả tài liệu, thay các giá trị mẫu bằng giá trị thật của bạn.

| Mục | Giá trị mẫu | Ghi chú |
| --- | --- | --- |
| IP VPS | `1.2.3.4` | Lấy từ nhà cung cấp VPS |
| Domain | `chat.tendomain.com` | Phải trỏ về IP VPS |
| Thư mục cài đặt | `/opt/messenger` | |
| Repo Git | `<repo-url>` | URL clone của repo |
| Cloudflare R2 | endpoint, access key, secret, bucket | Lấy trong Cloudflare dashboard |
| Agora | App ID, App Certificate | Lấy trong Agora Console |

Yêu cầu VPS:
- Ubuntu 22.04 hoặc 24.04.
- RAM ≥ 2 GB, ổ đĩa ≥ 20 GB.
- Có quyền `sudo`.

## 1. Kiến trúc khi chạy

```text
Trình duyệt ──HTTPS:443──> nginx trên VPS (certbot)
                               │
                               ▼  127.0.0.1:8081
                     ┌─ container frontend (nginx) ─┐
                     │  /       → giao diện React    │
                     │  /api/*  → backend:8080       │
                     │  /ws     → backend:8080       │
                     └───────────────────────────────┘
                     container backend (Spring Boot)
                               │
                     container mysql (chỉ dùng nội bộ, không mở port)

Cloudflare R2 (lưu file) và Agora (truyền âm thanh) là dịch vụ bên ngoài.
```

Phải có HTTPS thì trình duyệt mới cho dùng micro để gọi thoại.

## 2. Trỏ domain về VPS

Vào trang quản lý DNS của domain và tạo bản ghi:

| Loại | Tên | Giá trị |
| --- | --- | --- |
| A | `chat` | `1.2.3.4` |

Nếu domain dùng Cloudflare, đặt bản ghi này ở chế độ **DNS only (mây xám)**.

Kiểm tra (có thể phải chờ vài phút):

```bash
ping chat.tendomain.com    # phải ra đúng IP VPS
```

## 3. Cài đặt VPS lần đầu

SSH vào VPS:

```bash
ssh root@1.2.3.4
```

Cập nhật hệ thống, cài các công cụ cần thiết và Docker:

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y git curl nginx certbot python3-certbot-nginx
curl -fsSL https://get.docker.com | sh
docker --version
docker compose version
```

Nếu VPS chỉ có 2 GB RAM, nên tạo thêm swap để quá trình build không bị thiếu bộ nhớ:

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

Bật firewall. Luôn chạy `allow OpenSSH` **trước** `enable` để không mất kết nối SSH:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw enable
```

## 4. Lấy code và cấu hình

```bash
sudo git clone <repo-url> /opt/messenger
cd /opt/messenger
cp .env.production.example .env.production
nano .env.production
```

Nội dung `.env.production` cần sửa:

```env
HTTP_BIND=127.0.0.1:8081
CORS_ALLOWED_ORIGINS=https://chat.tendomain.com

DB_NAME=messenger_mini
DB_USERNAME=messenger_user
DB_PASSWORD=<mật khẩu mạnh>
MYSQL_ROOT_PASSWORD=<mật khẩu mạnh khác>

JWT_SECRET=<kết quả của: openssl rand -base64 64>

R2_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
R2_ACCESS_KEY_ID=...
R2_SECRET_ACCESS_KEY=...
R2_BUCKET_NAME=...

AGORA_APP_ID=...
AGORA_APP_CERTIFICATE=...
```

Tạo `JWT_SECRET` và mật khẩu ngẫu nhiên bằng:

```bash
openssl rand -base64 64     # JWT_SECRET
openssl rand -base64 24     # mật khẩu DB
```

Lưu ý:
- **Không commit** file `.env.production`. File này đã nằm trong `.gitignore`.
- Sau khi MySQL đã chạy lần đầu, nếu đổi `DB_PASSWORD` trong file env thì mật khẩu trong database **không tự đổi theo**.

Giới hạn quyền đọc file:

```bash
chmod 600 .env.production
```

## 5. Chạy ứng dụng

```bash
cd /opt/messenger
bash deploy.sh
```

Lần đầu build mất khoảng 3–10 phút. Kiểm tra kết quả:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.production ps
docker compose -f docker-compose.prod.yml --env-file .env.production logs -f backend
```

Trong log backend phải thấy hai dòng:
- `Successfully applied ... migrations` (Flyway đã tạo bảng)
- `Started MessengerApplication`

Bấm `Ctrl+C` để thoát xem log. Sau đó kiểm tra frontend:

```bash
curl -I http://127.0.0.1:8081    # phải ra HTTP/1.1 200 OK
```

## 6. Cấu hình nginx trên VPS

```bash
sudo nano /etc/nginx/sites-available/messenger
```

Dán nội dung sau:

```nginx
server {
    listen 80;
    server_name chat.tendomain.com;
    client_max_body_size 50m;

    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 1h;
        proxy_send_timeout 1h;
    }
}
```

Kích hoạt cấu hình:

```bash
sudo ln -s /etc/nginx/sites-available/messenger /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

Lúc này mở `http://chat.tendomain.com` là phải thấy giao diện app.

## 7. Bật HTTPS

```bash
sudo certbot --nginx -d chat.tendomain.com
```

- Nhập email và đồng ý điều khoản.
- Khi được hỏi có chuyển HTTP sang HTTPS không, chọn **redirect**.

Certbot tự sửa cấu hình nginx và tự gia hạn chứng chỉ. Kiểm tra việc gia hạn bằng:

```bash
sudo certbot renew --dry-run
```

## 8. Cấu hình dịch vụ bên ngoài

### Cloudflare R2: CORS cho bucket

Vào Cloudflare → R2 → chọn bucket → **Settings** → **CORS Policy**, rồi dán:

```json
[
  {
    "AllowedOrigins": ["https://chat.tendomain.com"],
    "AllowedMethods": ["GET", "PUT", "HEAD"],
    "AllowedHeaders": ["*"],
    "MaxAgeSeconds": 3600
  }
]
```

### Agora

Trong Agora Console, project phải bật **App Certificate** (chế độ Secured). Giá trị certificate phải khớp với `AGORA_APP_CERTIFICATE` trong `.env.production`.

## 9. Kiểm tra sau khi deploy

- [ ] `https://chat.tendomain.com` mở được, có ổ khóa HTTPS.
- [ ] Đăng ký và đăng nhập được.
- [ ] Mở 2 trình duyệt với 2 tài khoản: nhắn tin realtime, có typing indicator, có đã xem.
- [ ] Gửi ảnh và file được.
- [ ] Gọi thoại: trình duyệt xin quyền micro và hai bên nghe được nhau.
- [ ] Lịch sử cuộc gọi hiển thị đúng.

## 10. Cập nhật phiên bản mới

Mỗi khi có code mới trên nhánh `main`:

```bash
cd /opt/messenger
bash deploy.sh
```

Script này sẽ:
1. Chạy `git pull`.
2. Build lại image và khởi động lại container.
3. Dọn các image cũ.

Dữ liệu MySQL được giữ nguyên. Migration Flyway mới tự chạy khi backend khởi động.

## 11. Sao lưu và khôi phục database

Sao lưu:

```bash
cd /opt/messenger
mkdir -p backups
docker compose -f docker-compose.prod.yml --env-file .env.production exec -T mysql \
  sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction messenger_mini' \
  > backups/messenger_$(date +%F_%H%M).sql
```

Tự động sao lưu lúc 3h sáng mỗi ngày (chạy `crontab -e` rồi thêm dòng sau):

```cron
0 3 * * * cd /opt/messenger && docker compose -f docker-compose.prod.yml --env-file .env.production exec -T mysql sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction messenger_mini' > backups/messenger_$(date +\%F).sql
```

Khôi phục:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.production exec -T mysql \
  sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" messenger_mini' \
  < backups/messenger_YYYY-MM-DD.sql
```

> ⚠️ **Không chạy `docker compose ... down -v`**: lệnh này xóa volume MySQL và mất toàn bộ dữ liệu. Dùng `down` (không có `-v`) khi chỉ muốn dừng.

## 12. Lệnh hay dùng

Đặt alias cho gọn (thêm vào `~/.bashrc`):

```bash
alias dc='docker compose -f /opt/messenger/docker-compose.prod.yml --env-file /opt/messenger/.env.production'
```

| Việc | Lệnh |
| --- | --- |
| Xem trạng thái | `dc ps` |
| Xem log backend | `dc logs -f backend` |
| Xem log frontend | `dc logs -f frontend` |
| Khởi động lại backend | `dc restart backend` |
| Dừng toàn bộ | `dc down` |
| Chạy lại toàn bộ | `dc up -d` |
| Vào MySQL | `dc exec mysql sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" messenger_mini'` |
| Log nginx VPS | `sudo tail -f /var/log/nginx/error.log` |

## 13. Xử lý sự cố

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| `502 Bad Gateway` | Container frontend hoặc backend chưa chạy | `dc ps`, `dc logs backend` |
| Backend restart liên tục | Sai biến môi trường hoặc sai mật khẩu DB | Xem `dc logs backend`, kiểm tra `.env.production` |
| `Access denied for user` | Đã đổi `DB_PASSWORD` sau khi MySQL khởi tạo | Đổi lại mật khẩu cũ, hoặc đổi trong MySQL bằng `ALTER USER` |
| Nhắn tin không realtime | WebSocket bị chặn | Kiểm tra nginx có 2 dòng `Upgrade` và `Connection "upgrade"` |
| Lỗi CORS khi gọi API | `CORS_ALLOWED_ORIGINS` sai | Phải đúng `https://chat.tendomain.com`, không có `/` ở cuối |
| Upload ảnh lỗi CORS | Thiếu CORS policy trên R2 | Xem mục 8 |
| Không gọi được, không xin quyền micro | Đang dùng HTTP | Truy cập bằng `https://` |
| Certbot thất bại | DNS chưa trỏ đúng, hoặc đang bật mây cam Cloudflare | Kiểm tra `ping`, chuyển sang mây xám |
| Build bị kill / hết RAM | VPS ít RAM | Tạo swap (mục 3) |
