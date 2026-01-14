# AWS Deployment Guide - Face Recognition Backend

## Overview
This guide deploys the FastAPI backend to **AWS EC2** with HTTPS support for production use.

---

## Step 1: Create AWS EC2 Instance

### 1.1 Launch EC2 Instance

1. Go to **AWS Console** → **EC2** → **Instances** → **Launch Instances**

2. **Choose AMI**: Select **Ubuntu Server 22.04 LTS** (Free tier eligible)

3. **Instance Type**: Select **t2.micro** or **t3.micro** (Free tier)

4. **Configure Security Group**:
   - Allow SSH (22): Your IP
   - Allow HTTP (80): Anywhere (0.0.0.0/0)
   - Allow HTTPS (443): Anywhere (0.0.0.0/0)

5. **Create Key Pair**: 
   - Download `.pem` file (save securely)
   - This is your SSH access key

6. **Launch Instance** and wait for it to start

### 1.2 Get Public IP
1. Go to **EC2 Instances**
2. Copy your instance's **Public IPv4 address**
3. Example: `ec2-user@54.123.45.67`

---

## Step 2: Connect to EC2 via SSH

```powershell
# SSH into your instance
ssh -i "path/to/your-key.pem" ubuntu@YOUR_PUBLIC_IP

# Example:
# ssh -i "C:/keys/my-key.pem" ubuntu@54.123.45.67
```

---

## Step 3: Install Python & Dependencies

On the EC2 instance, run:

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Python 3.10 and dependencies
sudo apt install -y python3.10 python3.10-venv python3-pip git curl

# Install system dependencies for OpenCV
sudo apt install -y libsm6 libxext6 libxrender-dev

# Verify Python installation
python3.10 --version
```

---

## Step 4: Clone & Setup Backend

```bash
# Create app directory
mkdir -p /opt/face-recognition
cd /opt/face-recognition

# Clone your repository (or upload files)
# Option A: Clone from GitHub
git clone https://github.com/YOUR_USERNAME/Employee-Attendance-Marking-Application.git
cd Employee-Attendance-Marking-Application/backend

# Option B: Or copy files using SCP
# From your local machine:
# scp -i "key.pem" -r backend/* ubuntu@YOUR_IP:/opt/face-recognition/

# Create virtual environment
python3.10 -m venv venv
source venv/bin/activate

# Install dependencies
pip install --upgrade pip
pip install -r requirements.txt
```

---

## Step 5: Configure Environment Variables

```bash
# Edit .env file
nano .env
```

Update with your Supabase credentials:

```env
# Supabase Configuration
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_KEY=your-service-role-key

# Face Recognition Settings
CONFIDENCE_THRESHOLD=0.80
MAX_ATTENDANCE_PER_DAY=2

# Server Configuration (for EC2)
HOST=0.0.0.0
PORT=8000
WORKERS=1

# Security
API_KEY=your-secure-api-key-here

# Debug Mode
DEBUG=false
```

**Important**: Use a **service role key** from Supabase (not publishable key)

---

## Step 6: Set Up HTTPS with Let's Encrypt

### 6.1 Install Certbot

```bash
sudo apt install -y certbot python3-certbot-nginx

# Create domain name (use Elastic IP for fixed address)
# Or get a domain and point it to your EC2 IP
```

### 6.2 Get SSL Certificate

```bash
# Get certificate for your domain
sudo certbot certonly --standalone -d your-domain.com

# Certificate location: /etc/letsencrypt/live/your-domain.com/
```

---

## Step 7: Use Nginx as Reverse Proxy

### 7.1 Install Nginx

```bash
sudo apt install -y nginx
```

### 7.2 Configure Nginx

```bash
sudo nano /etc/nginx/sites-available/default
```

Replace content with:

```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    # Redirect HTTP to HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;
    
    # SSL certificates (from Let's Encrypt)
    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;
    
    # SSL settings
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    
    # Proxy to FastAPI backend
    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Enable and restart Nginx:

```bash
sudo systemctl restart nginx
sudo systemctl enable nginx
```

---

## Step 8: Run Backend with Gunicorn (Production)

### 8.1 Install Gunicorn

```bash
cd /opt/face-recognition/backend
source venv/bin/activate
pip install gunicorn
```

### 8.2 Create Systemd Service

```bash
sudo nano /etc/systemd/system/face-recognition.service
```

Add:

```ini
[Unit]
Description=Face Recognition Backend
After=network.target

[Service]
User=ubuntu
WorkingDirectory=/opt/face-recognition/backend
ExecStart=/opt/face-recognition/backend/venv/bin/gunicorn \
    -w 2 \
    -b 127.0.0.1:8000 \
    -k uvicorn.workers.UvicornWorker \
    --timeout 60 \
    main:app

Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Enable and start service:

```bash
sudo systemctl daemon-reload
sudo systemctl enable face-recognition
sudo systemctl start face-recognition

# Check status
sudo systemctl status face-recognition

# View logs
sudo journalctl -u face-recognition -f
```

---

## Step 9: Configure Android App

### 9.1 Update Backend URL

Update `PythonFaceService.kt`:

```kotlin
// Change this line:
private var BASE_URL = "http://10.0.2.2:8000"

// To your AWS domain with HTTPS:
private var BASE_URL = "https://your-domain.com"
```

### 9.2 Rebuild Android App

```powershell
./gradlew clean build
./gradlew installDebug
```

---

## Step 10: Test Everything

### 10.1 Check Backend Health

```bash
# From your local machine:
curl https://your-domain.com/health

# Should return:
# {"status":"healthy","model_loaded":true,"database_connected":true}
```

### 10.2 Test in Android App

1. **Rebuild app** with new URL
2. **Install on device/emulator**
3. **Try enrollment** - should work now!
4. **Try identification** - should work with HTTPS

---

## Step 11: Set Up Database (Supabase)

### 11.1 Run Schema in Supabase

1. Go to **Supabase Dashboard**
2. **SQL Editor** → New Query
3. Paste contents from `backend/supabase_schema.sql`
4. **Run**

This creates:
- `face_embeddings` table
- `attendance` table
- Proper indexes and security

---

## Cost Estimation (AWS)

| Component | Cost/Month |
|-----------|-----------|
| EC2 t3.micro | Free (first 12 months) |
| Elastic IP | $3.65 (if not associated) |
| Data transfer | Free (up to 1GB) |
| **Total** | **~$0-5** |

*After 12 months: ~$10-15/month*

---

## Monitoring & Maintenance

### Check Backend Status
```bash
sudo systemctl status face-recognition
```

### View Logs
```bash
sudo journalctl -u face-recognition -f
```

### Restart Service
```bash
sudo systemctl restart face-recognition
```

### Update Backend Code
```bash
cd /opt/face-recognition/backend
git pull origin main
source venv/bin/activate
pip install -r requirements.txt
sudo systemctl restart face-recognition
```

---

## Troubleshooting

### 1. SSL Certificate Issues
```bash
# Check certificate validity
sudo certbot renew --dry-run

# Manual renewal (auto-renewal runs daily)
sudo certbot renew
```

### 2. Backend Not Responding
```bash
# Check service status
sudo systemctl status face-recognition

# Restart
sudo systemctl restart face-recognition

# Check logs
sudo journalctl -u face-recognition -n 50
```

### 3. Database Connection Issues
- Verify Supabase credentials in `.env`
- Check if tables exist in Supabase
- Ensure service role key is correct

### 4. Android App Can't Connect
- Ensure domain is accessible: `ping your-domain.com`
- Check firewall allows HTTPS (443)
- Verify certificate is valid: `openssl s_client -connect your-domain.com:443`

---

## Production Checklist

- ✅ EC2 instance created and running
- ✅ Security group allows HTTP/HTTPS
- ✅ SSH access configured
- ✅ Python & dependencies installed
- ✅ Backend code cloned/uploaded
- ✅ .env configured with credentials
- ✅ Supabase tables created
- ✅ SSL certificate installed
- ✅ Nginx reverse proxy configured
- ✅ Gunicorn service running
- ✅ Android app points to HTTPS URL
- ✅ Health check passes
- ✅ Face enrollment works
- ✅ Face identification works

---

## Quick Commands Reference

```bash
# Connect to EC2
ssh -i "key.pem" ubuntu@YOUR_IP

# Activate venv
source venv/bin/activate

# Start backend (development)
python -m uvicorn main:app --host 0.0.0.0 --port 8000

# Check service status
sudo systemctl status face-recognition

# View logs
sudo journalctl -u face-recognition -f

# Restart service
sudo systemctl restart face-recognition
```

---

## Next Steps

1. **Get AWS account**: https://aws.amazon.com/
2. **Create EC2 instance** (Free tier eligible)
3. **Follow steps 1-11 above**
4. **Update Android app** with HTTPS URL
5. **Deploy and test**

Questions? Check AWS documentation: https://docs.aws.amazon.com/ec2/
