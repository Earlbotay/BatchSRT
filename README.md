# 🤖 My AI Server

AI chatbot tanpa sekatan, hosted percuma di GitHub Actions.

## Features
- ✅ Uncensored AI (Dolphin Mistral 7B)
- ✅ Web chat UI + OpenAI-compatible API
- ✅ Auto-restart setiap 4.5 jam
- ✅ Percuma selamanya (public repo)
- ✅ Fixed URL via Cloudflare Tunnel

## Quick Start

1. Fork/clone repo ini
2. Setup Cloudflare Tunnel (lihat bawah)
3. Push — AI server auto-start!

## Setup Cloudflare Tunnel (URL Tetap)

### Langkah 1: Cloudflare Zero Trust Dashboard

1. Pergi ke [one.dash.cloudflare.com](https://one.dash.cloudflare.com)
2. Klik **Networks** → **Tunnels**
3. Klik **Create a tunnel**
4. Pilih **Cloudflared**
5. Nama tunnel: `ai-server`
6. **Copy token** yang ditunjukkan — simpan dulu

### Langkah 2: Setup Public Hostname

Masih di halaman tunnel setup:

1. Klik **Public Hostnames** → **Add a public hostname**
2. Isi:
   - **Subdomain**: `ai` (atau apa-apa)
   - **Domain**: pilih domain kamu
   - **Service Type**: `HTTP`
   - **URL**: `localhost:8080`
3. Klik **Save**

URL tetap kamu: `https://ai.domainku.com` 🎉

### Langkah 3: Tambah Token ke GitHub

1. Pergi ke repo GitHub → **Settings** → **Secrets and variables** → **Actions**
2. Klik **New repository secret**
3. Name: `CLOUDFLARE_TUNNEL_TOKEN`
4. Value: paste token dari Langkah 1
5. Klik **Add secret**

### Langkah 4: Push!

```bash
git add .
git commit -m "🚀 Launch AI server"
git push
```

Tunggu ~5-10 minit (pertama kali). Selepas itu, buka URL kamu! 🎉

## Tukar Model

Edit `.github/workflows/ai-server.yml` — bahagian "Download model".
Uncomment model yang kamu nak:

| Model | Saiz | Speed | Uncensored |
|-------|------|-------|------------|
| Dolphin Mistral 7B | 4.4GB | ~5 tok/s | ✅ Ya |
| Dolphin Phi-2 2.7B | 1.6GB | ~18 tok/s | ✅ Ya |
| DeepSeek R1 7B | 4.5GB | ~5 tok/s | ⚠️ Sikit |

## API Usage

```bash
curl https://ai.domainku.com/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "Hello!"}],
    "temperature": 0.7
  }'
```

Compatible dengan mana-mana app yang support OpenAI API.

## Stop Server

- **Actions** → cancel running workflow, atau
- **Actions** → **🤖 AI Server** → **⋯** → **Disable workflow**
