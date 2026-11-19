# 📁 Family WhatsApp Document & Picture Vault

A private, secure, and fast Java 21 & Spring Boot 3 backend application that connects directly to **WhatsApp** to serve as a **centralized family document & picture vault**.

---

## 🌟 Key Highlights
- **100% Free of Cost:** Connects to Meta's official WhatsApp Cloud API (free tier gives **1,000 free conversations per month**). Zero middleman fees.
- **Master Admin Account:**
  - **Phone:** `+91 8790208445`
  - **Email:** `hrudaypreetham10@gmail.com`
  - Only Master Admin can delete documents, approve/reject uploads, manage users, and view user analytics graphs.
- **Strict Indian Phone Number Policy:**
  - Only `+91` followed by 10 digits is allowed in the entire system. Foreign and malformed numbers are rejected.
  - Zero duplicate numbers: database and service-level uniqueness guarantee.
- **Dynamic User & Folder Generation:**
  - Admin dynamically adds members (`/adduser <Name> <+91Phone>`).
  - Personal Google Drive vaults are dynamically created: `drive/family_chatbot/{UserName}/`.
- **User Document Protection:**
  - **Regular users CANNOT delete documents.** Members can only select, view, and download documents.
  - Deletion is strictly reserved for Master Admin (`/delete <DOC-ID>`).
- **"Hii" Workflow & Document Services:**
  - Starts with `"Hii"` -> Bot opens `drive/family_chatbot/{UserName}/` and presents document services:
    1: Aadhaar, 2: PAN, 3: Driving License, 4: Passport, 5: Health, 6: Other, 7: List Files, 8: Stats.
- **Admin Approval Workflow:**
  - Member uploads start as `PENDING_APPROVAL`.
  - Admin receives **Email notification** (`hrudaypreetham10@gmail.com`) and **WhatsApp alert** (`/approve <ID>` or `/reject <ID> <reason>`).
- **Re-Download Documents:**
  - Approved documents can be downloaded anytime via `/download <DOC-ID>`.
- **User Analytics & Activity Graphs:**
  - Admin command `/analytics` or `/graph` renders visual ASCII/Unicode bar graphs showing login counts per user, most used services, and top active bot users.
  - Also available via REST API: `GET /api/analytics`.
- **Google Drive Sync (100% Free):**
  - Point `drive-sync-dir` in `application.yml` to your Google Drive Desktop folder (signed in with `hrudaypreetham10@gmail.com`). Files are synchronized automatically.

---

## 🚀 Quick Start Guide

### 1. Requirements
- **Java 21** or higher
- **Maven 3.8+** (or use included Maven build)
- A free **Meta for Developers** account

### 2. Configure Whitelist & Admin Settings
Open `src/main/resources/application.yml`:
```yaml
whatsapp:
  provider: meta
  verify-token: FAMILY_DOC_BOT_VERIFY_TOKEN
  access-token: "YOUR_META_PERMANENT_OR_TEMP_ACCESS_TOKEN"
  phone-number-id: "YOUR_PHONE_NUMBER_ID"

  # Master Admin (Must be +91 followed by 10 digits)
  admin-phone-number: "918790208445"
  admin-email: "hrudaypreetham10@gmail.com"
  enable-email-notification: true

  # Whitelisted Family Members (Only +91 followed by 10 digits allowed)
  whitelist:
    - "919876543211"
    - "919876543212"

storage:
  base-dir: "./data/storage"
  drive-sync-dir: "./data/drive-sync" # Or ~/Google Drive/My Drive/FamilyVault
  enable-drive-sync: true
```

### 3. Run Tests
```bash
mvn test
```
All **46 automated tests** run with 100% pass rate.

### 4. Start the Application
```bash
mvn spring-boot:run
```

Once the application displays `Started WhatsAppChatbotApplication in X seconds`, the server is running on `http://localhost:8080`.

---

## 🧪 How to Check & Test the Application (Step-by-Step)

Here are the **5 easy ways** to check, test, and verify everything:

### Method 1: Interactive Web Simulator (Fastest — Instant in Browser)
Open **`http://localhost:8080/`** in your browser.
This is a built-in WhatsApp testing simulator with real-time audit logging and quick persona buttons:

1. **Test Master Admin (`+91 8790208445`):**
   - Click the **👑 Admin** button.
   - Click the **👋 "hii"** chip. The bot responds with Admin tools: `/pending`, `/analytics`, `/adduser`, `/delete`.
   - Click the **📊 "/analytics"** chip. The bot renders visual ASCII/Unicode bar charts showing user logins and service usage.
   - Type `/adduser Brother +919876543219` and send. The bot dynamically registers the new user and initializes their Drive folder `drive/family_chatbot/Brother/`.
2. **Test Regular Member Dad (`+91 9876543211`):**
   - Click the **👨 Dad** button.
   - Click the **👋 "hii"** chip. Notice the greeting: *"Hii Dad! Your Personal Drive Vault: drive/family_chatbot/Dad/"*.
   - Type `1` -> Views Aadhaar Card service.
   - Type `9` -> Opens *"Any Issues? / Report a Problem"* showing the Admin email, WhatsApp alert, and clickable call link `https://wa.me/918790208445`.
   - Type `/delete DOC-1001` -> The bot strictly blocks deletion: *"Only the Master Admin can delete documents"*.
3. **Test Stranger / Unauthorized Caller (`+91 9999999999`):**
   - Click the **👤 Stranger** button.
   - Type `"hii"` -> The bot rejects with: `⛔ Access Denied: This WhatsApp bot is private and restricted to authorized family members only.`
4. **Live Security Audit Trail (Right Panel):**
   - Watch the right-hand panel update in real-time with timestamped audit entries (`LOGIN`, `COMMAND`, `NUMBER_BIAS_VIOLATION`, `DRIVE_SYNC`).

---

### Method 2: Swagger UI (Interactive REST API Testing)
Open **`http://localhost:8080/swagger-ui.html`** in your browser:

1. Click the green **Authorize 🔓** button at the top right:
   - In `X-API-KEY`: Enter `chat-vault-secret-admin-key-2026`
   - In `X-Caller-Phone`: Enter `918790208445` (Admin) or `919876543211` (Member)
   - Click **Authorize** > **Close**.
2. Expand any endpoint and click **Try it out** > **Execute**:
   - `GET /api/health` -> Returns `{"status": "UP"}`.
   - `GET /api/documents` -> Lists documents with Number-Bias isolation.
   - `GET /api/stats` -> Returns storage breakdown.
   - `GET /api/analytics` -> Returns login frequencies and service usage JSON (Blocked for non-admin).
   - `GET /api/logs` -> Returns system audit logs (Admin only).
   - `POST /api/test/chat` -> Test sending any WhatsApp message and view the JSON response.

---

### Method 3: Postman Collection (1-Click Automated Requests)
1. Open **Postman**.
2. Click **Import** and select [postman/WhatsApp_Vault_Postman_Collection.json](file:///home/hruday/github/chat-bot/postman/WhatsApp_Vault_Postman_Collection.json).
3. The collection has pre-configured environments with 17 ready-to-run requests:
   - **Folder 1:** Health check & OpenAPI spec.
   - **Folder 2:** Simulator chat (Admin greeting, Member Dad greeting, Option 9 problem report, dynamic user creation).
   - **Folder 3:** Document retrieval & Number-Bias security tests (including cross-member download block).
   - **Folder 4:** Admin monitoring & analytics JSON.
   - **Folder 5:** Meta Cloud API & Twilio Webhook simulations.

---

### Method 4: Terminal / cURL Commands
You can test endpoints directly from your terminal:

```bash
# 1. Health check
curl -s http://localhost:8080/api/health

# 2. Test Admin Greeting
curl -s -X POST http://localhost:8080/api/test/chat \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: chat-vault-secret-admin-key-2026" \
  -d '{"senderPhone":"918790208445","message":"hii"}'

# 3. Test Member Dad Greeting & Drive Folder
curl -s -X POST http://localhost:8080/api/test/chat \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: chat-vault-secret-admin-key-2026" \
  -d '{"senderPhone":"919876543211","message":"hii"}'

# 4. Test Option 9 (Any Issues? Prompt with Admin Call link)
curl -s -X POST http://localhost:8080/api/test/chat \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: chat-vault-secret-admin-key-2026" \
  -d '{"senderPhone":"919876543211","message":"9"}'

# 5. Test REST API Security (Missing API Key returns 401 Unauthorized)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/documents
# Output: 401
```

---

### Method 5: Google Drive Cloud Sync Verification
Whenever an approved document is saved by the bot:
1. Open your Google Drive at [drive.google.com](https://drive.google.com) signed in as **`hrudaypreetham10@gmail.com`**.
2. You will see the root folder: **`family_chatbot/`**.
3. Inside, individual subfolders are automatically created per user (e.g. `family_chatbot/Dad/`, `family_chatbot/Sister/`).
4. On your local machine, an offline mirror is also organized at `./data/drive-sync/family_chatbot/{UserName}/`.
5. The original file is stored with military-grade **AES-256-GCM encryption at rest** in `./data/storage/`.

---

### Method 6: Connecting Live WhatsApp on Mobile (Meta Webhook)
To connect your running local application to real WhatsApp:
1. Run a free tunnel:
   ```bash
   cloudflared tunnel --url http://localhost:8080
   # OR using ngrok:
   ngrok http 8080
   ```
2. Copy the generated HTTPS URL (e.g. `https://your-domain.trycloudflare.com`).
3. Go to **Meta for Developers > WhatsApp > Configuration**:
   - **Callback URL:** `https://your-domain.trycloudflare.com/webhook`
   - **Verify Token:** `FAMILY_DOC_BOT_VERIFY_TOKEN`
4. Click **Verify and Save**. Meta sends a handshake that your bot immediately verifies with 200 OK.
5. Text your test phone number from WhatsApp on your phone!

---

## 📮 Postman Collection
Import the ready-to-use Postman collection from:
- [postman/WhatsApp_Vault_Postman_Collection.json](file:///home/hruday/github/chat-bot/postman/WhatsApp_Vault_Postman_Collection.json)

Includes pre-configured requests for all:
- Simulator Chat workflows (Admin, Members, Option 9 problem report, /analytics)
- Number-Bias isolated document listing and AES-256 download
- Admin system audit logs and analytics JSON
- Meta WhatsApp Cloud API & Twilio Webhooks

---

## 📖 Complete Documentation
Read the full PRD and architecture specification in [PROJECT_DOCUMENTATION.md](file:///home/hruday/github/chat-bot/PROJECT_DOCUMENTATION.md).
