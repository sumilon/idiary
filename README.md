# iDiary — Personal Diary App

A full-stack personal diary web application built with **Kotlin + Ktor**, backed by **Google Cloud Firestore**, and enhanced with **Groq AI** for intelligent diary entry rewriting. Designed for easy deployment on **Google Cloud Run**.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Features](#features)
- [Data Models](#data-models)
- [API Reference](#api-reference)
- [Environment Variables](#environment-variables)
- [Database Setup (Firebase Firestore)](#database-setup-firebase-firestore)
- [Local Development](#local-development)
- [Building the Application](#building-the-application)
- [Deploying to Google Cloud Run](#deploying-to-google-cloud-run)
- [Security Notes](#security-notes)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Framework | Ktor (Netty engine) |
| Database | Google Cloud Firestore |
| Authentication | JWT (HMAC-256 via Auth0 JWT library) |
| Password Hashing | BCrypt (jBCrypt) |
| AI Integration | Groq API (OpenAI-compatible) |
| Templating | Plain HTML (server-side string injection) |
| Frontend | Vanilla JS + CSS (served as static resources) |
| Logging | Logback (SLF4J) |
| Serialization | kotlinx.serialization |

---

## Project Structure

```
idiary/
└── src/
    └── main/
        ├── kotlin/com/diary/
        │   ├── Application.kt              # Entry point, wires all modules
        │   ├── models/
        │   │   └── Models.kt               # All data classes (User, DiaryEntry, DTOs)
        │   ├── plugins/
        │   │   ├── Plugins.kt              # Serialization, compression, status pages
        │   │   ├── Routing.kt              # Route wiring, service instantiation
        │   │   └── Security.kt             # JWT authentication plugin
        │   ├── routes/
        │   │   ├── AuthRoutes.kt           # /api/auth endpoints
        │   │   ├── DiaryRoutes.kt          # /api/diary endpoints (CRUD + AI)
        │   │   └── PageRoutes.kt           # HTML page serving
        │   └── services/
        │       ├── AuthService.kt          # Registration, login, password change
        │       ├── FirebaseService.kt      # Firestore CRUD operations
        │       └── GrokAIService.kt        # Groq AI diary rewriting
        └── resources/
            ├── application.conf            # HOCON config (reads from env vars)
            ├── logback.xml                 # Logging configuration
            ├── static/
            │   ├── css/app.css             # Application styles
            │   └── js/app.js               # Frontend JavaScript
            └── templates/
                ├── login.html
                ├── register.html
                ├── dashboard.html
                ├── entry.html              # New / Edit entry (ID injected server-side)
                └── profile.html
```

---

## Features

- **User Authentication** — Register, login, and change password with BCrypt-hashed passwords and JWT sessions.
- **Diary CRUD** — Create, read, update, and delete personal diary entries stored in Firestore. Each entry belongs strictly to its owner (enforced server-side).
- **Mood Tracking** — Attach an optional mood tag to each diary entry.
- **AI Rewriting** — Send any entry to Groq's LLM for intelligent improvement using modes: `improve`, `grammar`, `expand`, `shorten`, or `formal`.
- **Responsive UI** — Multi-page HTML app served directly from the Ktor backend; no separate frontend server needed.
- **Gzip Compression** — Automatic response compression for payloads over 1KB.
- **Structured Error Handling** — All exceptions are mapped to consistent `ApiResponse<T>` JSON envelopes.

---

## Data Models

### User
```
id           String   UUID
email        String   Unique, lowercased
name         String
passwordHash String   BCrypt hash (never returned to client)
createdAt    Long     Unix timestamp (ms)
```

### DiaryEntry
```
id        String   UUID
userId    String   Owner's user ID (foreign key)
title     String
content   String
mood      String?  Optional
createdAt Long     Unix timestamp (ms)
updatedAt Long     Unix timestamp (ms)
```

Firestore Collections:
- `users` — keyed by `user.id`
- `diaries` — keyed by `entry.id`, database ID: `diaryapp`

---

## API Reference

All API routes are prefixed with `/api`. Protected routes require a `Bearer <token>` header.

### Authentication

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | No | Create a new account |
| POST | `/api/auth/login` | No | Login and receive JWT |
| POST | `/api/auth/change-password` | Yes | Change own password |

**Register / Login request body:**
```json
{ "email": "user@example.com", "password": "secret123", "name": "Alice" }
```

**Auth response:**
```json
{
  "success": true,
  "data": {
    "token": "<jwt>",
    "user": { "id": "...", "email": "...", "name": "..." }
  }
}
```

### Diary Entries

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/diary` | Yes | List up to 50 entries (newest first) |
| GET | `/api/diary/{id}` | Yes | Get a single entry |
| POST | `/api/diary` | Yes | Create a new entry |
| PUT | `/api/diary/{id}` | Yes | Update an entry |
| DELETE | `/api/diary/{id}` | Yes | Delete an entry |
| POST | `/api/diary/ai-rewrite` | Yes | AI-enhance entry content |

**Create/Update entry body:**
```json
{ "title": "Day 1", "content": "...", "mood": "happy" }
```

**AI rewrite body:**
```json
{ "content": "my raw text", "instruction": "improve" }
```
Supported instructions: `improve`, `grammar`, `expand`, `shorten`, `formal`

### Page Routes (HTML)

| Path | Description |
|---|---|
| `/` | Redirects to `/login` |
| `/login` | Login page |
| `/register` | Registration page |
| `/dashboard` | Entry list dashboard |
| `/entry/new` | New entry editor |
| `/entry/{id}` | Edit existing entry |
| `/profile` | User profile page |

---

## Environment Variables

All configuration is read via HOCON (`application.conf`) from environment variables. Set these before running locally or in Cloud Run.

| Variable | Required | Description |
|---|---|---|
| `PORT` | No | Server port (default: `8080`) |
| `JWT_SECRET` | Yes | Secret key for signing JWT tokens (use a long random string) |
| `FIREBASE_PROJECT_ID` | Yes | Your GCP project ID |
| `FIREBASE_DATABASE_ID` | No | Firestore database name (default used is `diaryapp`) |
| `FIREBASE_CREDENTIALS_JSON` | Yes* | Full service account JSON as a single-line string |
| `FIREBASE_CREDENTIALS_PATH` | Yes* | Path to service account JSON file on disk |
| `GROK_API_KEY` | Yes | Groq API key from [console.groq.com](https://console.groq.com) |
| `GROK_MODEL` | Yes | Groq model name, e.g. `llama3-8b-8192` |

> *Provide either `FIREBASE_CREDENTIALS_JSON` (preferred for Cloud Run) **or** `FIREBASE_CREDENTIALS_PATH`. If neither is set, Application Default Credentials (ADC) are used.

---

## Database Setup (Firebase Firestore)

### 1. Create a Firebase / GCP Project

1. Go to [console.firebase.google.com](https://console.firebase.google.com) and create a new project (or use an existing GCP project).
2. Enable **Firestore** — choose **Native mode** when prompted.

### 2. Create the Firestore Database

1. In the Firebase Console, navigate to **Firestore Database → Create database**.
2. When asked for a database ID, enter: **`diaryapp`**
   (this matches the hardcoded database ID in `FirebaseService.kt`).
3. Choose a region close to your Cloud Run deployment region.
4. Start in **production mode** (rules below will lock it down).

### 3. Create Firestore Indexes

The app queries entries by `userId` ordered by `createdAt DESC`. You must create a composite index:

**Via Firebase Console:**
1. Go to **Firestore → Indexes → Composite → Add Index**.
2. Collection: `diaries`
3. Fields:
   - `userId` — Ascending
   - `createdAt` — Descending
4. Click **Create**.

**Via Firebase CLI:**
```bash
firebase init firestore
# Add to firestore.indexes.json:
```
```json
{
  "indexes": [
    {
      "collectionGroup": "diaries",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "userId", "order": "ASCENDING" },
        { "fieldPath": "createdAt", "order": "DESCENDING" }
      ]
    }
  ]
}
```
```bash
firebase deploy --only firestore:indexes
```

### 4. Firestore Security Rules

Apply these rules to restrict access (the backend uses a service account that bypasses client rules):

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Block all direct client access — backend uses Admin SDK
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

### 5. Create a Service Account

1. In GCP Console → **IAM & Admin → Service Accounts → Create Service Account**.
2. Name it `idiary-backend` (or similar).
3. Assign role: **Cloud Datastore User** (covers Firestore in Native mode).
4. After creation, go to **Keys → Add Key → JSON**.
5. Download the JSON file — this is your `FIREBASE_CREDENTIALS_JSON`.

---

## Local Development

### Prerequisites

- JDK 17+
- Gradle (or use the wrapper `./gradlew`)
- A Firebase project with Firestore configured (see above)
- A Groq API key from [console.groq.com](https://console.groq.com)

### Set Environment Variables

Create a `.env` file or export these in your shell:

```bash
export JWT_SECRET="your-super-secret-jwt-key-min-32-chars"
export FIREBASE_PROJECT_ID="your-gcp-project-id"
export FIREBASE_DATABASE_ID="diaryapp"
export FIREBASE_CREDENTIALS_JSON='{ "type": "service_account", ... }'
export GROK_API_KEY="gsk_your_groq_api_key"
export GROK_MODEL="llama3-8b-8192"
```

### Run Locally

```bash
./gradlew run
```

The app will start at `http://localhost:8080`.

---

## Building the Application

### Build a Fat JAR

```bash
./gradlew buildFatJar
```

Output: `build/libs/idiary-all.jar`

### Run the Fat JAR

```bash
java -jar build/libs/idiary-all.jar
```

---

## Deploying to Google Cloud Run

### Prerequisites

- [Google Cloud SDK](https://cloud.google.com/sdk/docs/install) installed and authenticated
- Docker installed locally (for local builds), or use Cloud Build
- A GCP project with **Cloud Run**, **Artifact Registry**, and **Firestore** APIs enabled

### 1. Enable Required GCP APIs

```bash
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  firestore.googleapis.com \
  cloudbuild.googleapis.com
```

### 2. Create a Dockerfile

Create `Dockerfile` in the project root:

```dockerfile
FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle buildFatJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/idiary-all.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 3. Create Artifact Registry Repository

```bash
gcloud artifacts repositories create idiary \
  --repository-format=docker \
  --location=asia-south1 \
  --description="iDiary Docker images"
```

### 4. Build and Push the Docker Image

```bash
# Configure Docker to use gcloud credentials
gcloud auth configure-docker asia-south1-docker.pkg.dev

# Build and push
gcloud builds submit \
  --tag asia-south1-docker.pkg.dev/YOUR_PROJECT_ID/idiary/idiary-app:latest
```

Replace `YOUR_PROJECT_ID` with your actual GCP project ID and adjust the region as needed.

### 5. Store Secrets in Secret Manager

It is strongly recommended to store sensitive values in GCP Secret Manager rather than passing them as plain env vars:

```bash
# Store JWT secret
echo -n "your-super-secret-jwt-key" | \
  gcloud secrets create JWT_SECRET --data-file=-

# Store Firebase credentials JSON
gcloud secrets create FIREBASE_CREDENTIALS_JSON \
  --data-file=path/to/your/service-account.json

# Store Groq API key
echo -n "gsk_your_groq_key" | \
  gcloud secrets create GROK_API_KEY --data-file=-
```

### 6. Deploy to Cloud Run

```bash
gcloud run deploy idiary \
  --image asia-south1-docker.pkg.dev/YOUR_PROJECT_ID/idiary/idiary-app:latest \
  --region asia-south1 \
  --platform managed \
  --allow-unauthenticated \
  --memory 512Mi \
  --cpu 1 \
  --min-instances 0 \
  --max-instances 5 \
  --port 8080 \
  --set-env-vars "FIREBASE_PROJECT_ID=YOUR_PROJECT_ID,FIREBASE_DATABASE_ID=diaryapp,GROK_MODEL=llama3-8b-8192" \
  --set-secrets "JWT_SECRET=JWT_SECRET:latest,FIREBASE_CREDENTIALS_JSON=FIREBASE_CREDENTIALS_JSON:latest,GROK_API_KEY=GROK_API_KEY:latest"
```

### 7. Grant Secret Access to Cloud Run Service Account

Cloud Run uses a dedicated service account. Grant it access to the secrets:

```bash
# Get the Cloud Run service account email
SA_EMAIL=$(gcloud iam service-accounts list \
  --filter="displayName:Compute Engine default service account" \
  --format="value(email)")

# Grant Secret Manager access
gcloud secrets add-iam-policy-binding JWT_SECRET \
  --member="serviceAccount:$SA_EMAIL" --role="roles/secretmanager.secretAccessor"

gcloud secrets add-iam-policy-binding FIREBASE_CREDENTIALS_JSON \
  --member="serviceAccount:$SA_EMAIL" --role="roles/secretmanager.secretAccessor"

gcloud secrets add-iam-policy-binding GROK_API_KEY \
  --member="serviceAccount:$SA_EMAIL" --role="roles/secretmanager.secretAccessor"
```

### 8. Verify Deployment

```bash
gcloud run services describe idiary --region asia-south1
```

The command will print the public URL. Open it in a browser to access the app.

### Optional: Set Up a Custom Domain

```bash
gcloud run domain-mappings create \
  --service idiary \
  --domain yourdomain.com \
  --region asia-south1
```

Follow the DNS instructions printed by the command to point your domain to Cloud Run.

---

## Security Notes

> **⚠️ Important:** The `FirebaseService.kt` file in the original source contains a hardcoded service account private key in the `buildCredentialsJson()` method. This key **must be rotated immediately** and **must never be committed to version control**.

Follow these steps to remediate:

1. Go to GCP IAM → Service Accounts → find the compromised key → **Delete the key**.
2. Create a new service account key (see [Database Setup step 5](#5-create-a-service-account)).
3. Store the new key in **GCP Secret Manager** (see [Deploying step 5](#5-store-secrets-in-secret-manager)).
4. Remove the `buildCredentialsJson()` method from `FirebaseService.kt` entirely and rely only on `FIREBASE_CREDENTIALS_JSON` env var or Application Default Credentials.
5. Add `*.json` and `*credentials*` patterns to `.gitignore`.

**Other security recommendations:**

- Use a JWT secret of at least 32 random characters.
- Set JWT token expiration (`expirationHours`) to a short value (e.g., `24`) and implement refresh token logic for production.
- Enable Firestore security rules to block all direct client access (see [Database Setup step 4](#4-firestore-security-rules)).
- Use HTTPS only — Cloud Run enforces HTTPS by default.
- Enable Cloud Run binary authorization for supply chain security.
- Set Cloud Run `--min-instances 0` for cost efficiency; increase if cold starts are a concern.
