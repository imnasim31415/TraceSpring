# Deployment Guide

Deploying TraceSpring to an Oracle Cloud Free VM behind Cloudflare, using GitHub Actions and Docker Hub as the CI/CD pipeline.

---

## Architecture

```
Developer pushes to main
        │
        ▼
GitHub Actions
  ├─ Builds multi-arch Docker image (amd64 + arm64)
  └─ Pushes to Docker Hub
        │
        ▼
Oracle Free VM (Ubuntu)
  ├─ Pulls image from Docker Hub
  ├─ Runs container on port 8082
  └─ Nginx reverse proxy → port 80 / 443
        │
        ▼
Cloudflare DNS
  └─ tracespring.yourdomain.com → VM public IP
        │
        ▼
User's browser
```

---

## Prerequisites

| What | Where |
|---|---|
| Oracle Cloud account | cloud.oracle.com (free tier) |
| Docker Hub account | hub.docker.com (free) |
| Cloudflare account | cloudflare.com (free) |
| Domain with nameservers pointing to Cloudflare | Any registrar |
| GitHub repository | Already set up |

---

## Step 1 — Create the Oracle VM

### 1.1 Create a Compute Instance

1. Log in to [cloud.oracle.com](https://cloud.oracle.com)
2. **Compute → Instances → Create instance**
3. Choose a name (e.g. `tracespring-vm`)
4. **Image:** Ubuntu 22.04
5. **Shape:**
   - Free ARM: `VM.Standard.A1.Flex` — 1 OCPU, 6 GB RAM (best free option)
   - Free AMD: `VM.Standard.E2.1.Micro` — 1/8 OCPU, 1 GB RAM
6. **SSH keys:** Upload your public key (or generate one — Oracle will give you the private key to download)
7. Click **Create**

Note the **Public IP address** after the instance starts.

### 1.2 Open Ports in the OCI Security List

By default only port 22 is open. You must open 80 and 443.

1. **Networking → Virtual Cloud Networks → your VCN**
2. Click your **Subnet → Security List**
3. **Add Ingress Rules:**

| Stateless | Source CIDR | Protocol | Destination Port |
|---|---|---|---|
| No | 0.0.0.0/0 | TCP | 80 |
| No | 0.0.0.0/0 | TCP | 443 |

### 1.3 Open Ports in the VM OS Firewall

Oracle's Ubuntu images use `iptables` by default. SSH into the VM:

```bash
ssh ubuntu@<VM_PUBLIC_IP>
```

Then open the ports at the OS level:

```bash
# Allow HTTP and HTTPS through iptables
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80  -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT

# Persist across reboots
sudo netfilter-persistent save
```

---

## Step 2 — Install Docker on the VM

```bash
# Install Docker
curl -fsSL https://get.docker.com | sudo sh

# Allow your user to run Docker without sudo
sudo usermod -aG docker $USER

# Re-login so the group change takes effect
exit
# SSH back in
ssh ubuntu@<VM_PUBLIC_IP>

# Verify
docker --version
```

---

## Step 3 — Install and Configure Nginx

Nginx sits in front of the container and handles port 80/443, forwarding traffic to the app on port 8082.

```bash
sudo apt update && sudo apt install -y nginx
```

Create the site config:

```bash
sudo nano /etc/nginx/sites-available/tracespring
```

Paste this (replace `tracespring.yourdomain.com` with your actual subdomain):

```nginx
server {
    listen 80;
    server_name tracespring.yourdomain.com;

    location / {
        proxy_pass         http://localhost:8082;
        proxy_http_version 1.1;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
}
```

Enable it:

```bash
sudo ln -s /etc/nginx/sites-available/tracespring /etc/nginx/sites-enabled/
sudo nginx -t          # verify config syntax
sudo systemctl reload nginx
sudo systemctl enable  nginx
```

---

## Step 4 — Docker Hub Setup

1. Go to [hub.docker.com](https://hub.docker.com) and create a free account
2. Create a repository named **`tracespring`** (public)
3. Generate an access token:
   - **Account Settings → Security → New Access Token**
   - Permissions: **Read, Write, Delete**
   - Copy the token — you only see it once

---

## Step 5 — Generate SSH Key for GitHub Actions

GitHub Actions needs to SSH into your VM. Generate a dedicated key pair (do not reuse your personal key):

```bash
ssh-keygen -t ed25519 -C "github-actions-tracespring" -f ~/.ssh/github_actions_tracespring
```

This creates two files:
- `~/.ssh/github_actions_tracespring` — **private key** (goes into GitHub Secret)
- `~/.ssh/github_actions_tracespring.pub` — **public key** (goes onto the VM)

Add the public key to the VM's authorized keys:

```bash
# On your VM
echo "<paste contents of github_actions_tracespring.pub>" >> ~/.ssh/authorized_keys
```

---

## Step 6 — Configure GitHub Secrets

Go to your GitHub repository → **Settings → Secrets and variables → Actions → New repository secret**.

Add these five secrets:

| Secret name | Value |
|---|---|
| `DOCKERHUB_USERNAME` | Your Docker Hub username |
| `DOCKERHUB_TOKEN` | The access token from Step 4 |
| `VM_HOST` | Your Oracle VM's public IP address |
| `VM_USER` | `ubuntu` (default for Oracle Ubuntu instances) |
| `VM_SSH_KEY` | Full contents of `~/.ssh/github_actions_tracespring` (the private key, including `-----BEGIN...` and `-----END...` lines) |

---

## Step 7 — Configure Cloudflare DNS

1. Log in to [cloudflare.com](https://cloudflare.com)
2. Select your domain
3. Go to **DNS → Records → Add record**

| Type | Name | Content | Proxy status | TTL |
|---|---|---|---|---|
| A | `tracespring` | `<VM_PUBLIC_IP>` | Proxied (orange cloud) | Auto |

This makes `tracespring.yourdomain.com` resolve to your VM.

**Why enable the Cloudflare proxy (orange cloud)?**
- Hides your VM's real IP
- Free DDoS protection
- Free HTTPS (Cloudflare terminates SSL at the edge — no cert needed on VM for basic setup)

**SSL Mode:** In Cloudflare dashboard → **SSL/TLS → Overview**, set mode to **Flexible** (simplest) or **Full** (better). Do not use **Full (Strict)** unless you install a certificate on the VM.

> To install a real cert on the VM with Let's Encrypt:
> ```bash
> sudo apt install -y certbot python3-certbot-nginx
> sudo certbot --nginx -d tracespring.yourdomain.com
> ```
> Then set Cloudflare SSL mode to **Full (Strict)**.

---

## Step 8 — First Deploy

Push any change to `main` (or trigger manually):

```bash
# Trigger manually from CLI
gh workflow run deploy.yml

# Or just push
git commit --allow-empty -m "trigger deploy" && git push
```

Watch the workflow in **GitHub → Actions tab**.

When it completes, visit:

```
http://tracespring.yourdomain.com/debug/dashboard
```

---

## Step 9 — Verify

```bash
# Check container is running on VM
ssh ubuntu@<VM_PUBLIC_IP> "docker ps"

# Check container logs
ssh ubuntu@<VM_PUBLIC_IP> "docker logs tracespring --tail 50"

# Check health status
ssh ubuntu@<VM_PUBLIC_IP> "docker inspect --format='{{.State.Health.Status}}' tracespring"

# Hit the API from anywhere
curl https://tracespring.yourdomain.com/api/users
curl https://tracespring.yourdomain.com/debug/traces | jq .
```

---

## VM Architecture Note

Oracle Free Tier has two free shape options:

| Shape | Architecture | Free allowance |
|---|---|---|
| `VM.Standard.A1.Flex` | ARM64 (Ampere) | 4 OCPUs + 24 GB RAM total |
| `VM.Standard.E2.1.Micro` | AMD64 | 2 instances, 1 GB RAM each |

The GitHub Actions workflow builds a **multi-arch image** (`linux/amd64,linux/arm64`) using QEMU, so the same image works on both. If you only have one type, you can edit [deploy.yml](.github/workflows/deploy.yml) and simplify:

```yaml
# AMD/Intel VM:
platforms: linux/amd64

# Oracle Ampere A1:
platforms: linux/arm64
```

Removing the unused architecture cuts build time roughly in half.

---

## Updating the App

Every push to `main` automatically:
1. Rebuilds the Docker image
2. Pushes `:latest` + `:sha-<shortsha>` tags to Docker Hub
3. SSH into VM, pulls `:latest`, restarts the container with zero manual steps

Rollback to a previous image:

```bash
ssh ubuntu@<VM_PUBLIC_IP>
docker stop tracespring && docker rm tracespring
docker run -d --name tracespring --restart unless-stopped -p 8082:8082 \
  yourdockerhubuser/tracespring:sha-<previous-sha>
```

Find previous tags in Docker Hub → your repository → Tags.

---

## Troubleshooting

**Container not starting**
```bash
docker logs tracespring
```

**Nginx 502 Bad Gateway**
The container is not running or not healthy. Check `docker ps` and `docker logs tracespring`.

**Port 80/443 not reachable from outside**
Two places to check:
1. OCI Security List — ingress rules for TCP 80 and 443 must exist
2. VM OS firewall — `sudo iptables -L -n | grep -E '80|443'`

**GitHub Actions SSH timeout**
- Verify `VM_HOST` secret is the public IP (not private)
- Verify the public key from Step 5 is in `~/.ssh/authorized_keys` on the VM
- Test manually: `ssh -i ~/.ssh/github_actions_tracespring ubuntu@<VM_IP>`

**Cloudflare 524 (origin timeout)**
App is too slow to respond. Check container health: `docker inspect tracespring`.

**Multi-arch build is slow**
QEMU-based cross-compilation is slow (~5–10 min for arm64). Use GitHub Actions cache (already configured with `cache-from/cache-to: type=gha`) to skip unchanged layers.
