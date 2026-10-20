# CareSync - AI-First Telehealth Platform (Backend)

> 🌐 **Live Web Application**: [https://caresync-vikrant.vercel.app/](https://caresync-vikrant.vercel.app/)

![Live App](https://img.shields.io/badge/Live_App-caresync--vikrant.vercel.app-000000?style=for-the-badge&logo=vercel&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-green?style=for-the-badge&logo=spring-boot)
![Angular](https://img.shields.io/badge/Angular-19-DD0031?style=for-the-badge&logo=angular)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=for-the-badge&logo=postgresql)
![Redis](https://img.shields.io/badge/Redis-Cache_%26_Locks-DC382D?style=for-the-badge&logo=redis)
![Groq](https://img.shields.io/badge/Groq-gpt--oss-F55036?style=for-the-badge)
![Gemini](https://img.shields.io/badge/Gemini-2.5_Flash-4285F4?style=for-the-badge&logo=googlegemini&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Enabled-2496ED?style=for-the-badge&logo=docker)

---

## 🧠 AI-First: AI in Every Journey

```mermaid
flowchart LR
    subgraph PAT["👤 Patient"]
        P1["💬 Chat booking agent"]
        P2["🔎 Symptom search"]
        P3["📋 AI health brief"]
        P4["🧪 Lab report explainer"]
        P5["📝 Pre-visit form from free text"]
        P6["📄 After-visit summary"]
        P7["📷 Prescription scanner"]
        P8["🚨 Emergency triage"]
    end

    subgraph DOC["🩺 Doctor"]
        D1["🤖 Clinical co-pilot with citations"]
        D2["⏱️ Next-patient brief"]
        D3["🎙️ Voice dictation to SOAP"]
        D4["✍️ SOAP draft from transcript"]
        D5["💡 Diagnosis suggestions"]
        D6["📚 History summary"]
    end

    subgraph ADM["🛡️ Admin"]
        A1["🪪 Certificate check with vision AI"]
        A2["📊 AI usage audit"]
    end

    PAT --> CORE["CareSync AI Layer"]
    DOC --> CORE
    ADM --> CORE
    CORE --> G["Groq<br/>gpt-oss-120b / gpt-oss-20b"]
    CORE --> V["Gemini 2.5 Flash<br/>vision"]
```

---

## 💬 How One AI Chat Message Flows

```mermaid
sequenceDiagram
    autonumber
    actor U as Patient / Doctor
    participant FE as Angular Chat
    participant API as AiController (SSE)
    participant S as AiService
    participant R as Red-flag Rules
    participant A as Agent Loop
    participant L as Groq LLM
    participant T as Server-side Tools
    participant DB as PostgreSQL / Redis

    U->>FE: types message
    FE->>API: POST /api/ai/chat/stream
    API->>S: getResponse (bounded pool, same user)
    S->>S: length check (max 2000)
    S->>R: emergency rules (no AI)
    alt red flag found
        R-->>FE: 🚨 emergency card + 112 / 108 / 14416
    else normal message
        S->>A: run(message, encrypted memory)
        loop up to 5 steps
            A->>L: stream with tool definitions
            L-->>FE: status / text deltas
            L->>A: tool call
            A->>T: run as logged-in user (validated)
            T->>DB: read with access checks
            DB-->>T: data
            T-->>A: JSON result + card
        end
        A-->>S: final text + card
        S->>DB: save memory, chat history, audit (encrypted)
        S-->>FE: done event
    end
    FE-->>U: answer + action card
```

---

## 🔁 Agent Tool-Calling Loop

```mermaid
flowchart TD
    START(["User message"]) --> STEP{"Step ≤ 5?"}
    STEP -- yes --> LAST{"Last step?"}
    LAST -- no --> OFFER["Offer tools to LLM"]
    LAST -- yes --> NOTOOLS["Offer no tools<br/>(must answer)"]
    OFFER --> CALL["LLM response"]
    NOTOOLS --> CALL
    CALL --> WANTS{"Tool calls?"}
    WANTS -- no --> ANSWER(["Final answer + card"])
    WANTS -- yes --> LIMIT["Run max 3 tools"]
    LIMIT --> VALIDATE["Validate args<br/>ids · dates · times"]
    VALIDATE --> ACCESS["Access checks<br/>as logged-in user"]
    ACCESS --> RESULT["JSON result → LLM<br/>card → UI"]
    RESULT --> STOP{"User pressed Stop?"}
    STOP -- no --> STEP
    STOP -- yes --> ANSWER
    STEP -- no --> ANSWER
```

---

## 🧰 Tools the AI Can Use

```mermaid
flowchart LR
    subgraph PT["Patient Agent · 10 tools"]
        t1["flag_urgent_symptoms"]
        t2["list_specializations"]
        t3["search_doctors"]
        t4["get_available_slots"]
        t5["prepare_booking"]
        t6["list_my_appointments"]
        t7["prepare_cancel"]
        t8["prepare_reschedule"]
        t9["my_saved_doctors"]
        t10["search_lab_tests"]
    end

    subgraph DT["Doctor Co-pilot · 7 read-only tools"]
        d1["list_my_patients"]
        d2["get_patient_summary"]
        d3["get_recent_vitals"]
        d4["get_medical_history"]
        d5["list_lab_reports"]
        d6["get_schedule"]
        d7["brief_next_patient"]
    end

    PT --> BOX["AgentToolbox<br/>(one shared agent loop)"]
    DT --> BOX
    BOX --> LLM["Groq tool calling"]
```

---

## ✅ Human-in-the-Loop: AI Prepares, User Confirms

```mermaid
sequenceDiagram
    actor P as Patient
    participant AI as AI Agent
    participant T as prepare_booking
    participant UI as Confirm Card
    participant B as Booking Service

    P->>AI: "Book a skin doctor tomorrow after 5pm"
    AI->>T: doctor_id, date, time
    T-->>UI: confirmation card (no booking yet)
    UI-->>P: Doctor · Date · Time · Fee
    P->>UI: presses Confirm
    UI->>B: book + pay
    B-->>P: ✅ booked
    Note over AI,B: AI can never book, cancel, reschedule or pay by itself
```

---

## 🚨 Two-Layer Emergency Triage

```mermaid
flowchart LR
    M["Patient message"] --> L1{"Layer 1<br/>Red-flag rules<br/>(regex · negation · Hindi)"}
    L1 -- match --> E["🚨 Emergency card<br/>112 · 108 · Tele-MANAS 14416<br/>+ emergency booking"]
    L1 -- no match --> L2["Layer 2<br/>AI agent"]
    L2 -- "flag_urgent_symptoms" --> E
    L2 -- routine --> N["Normal answer"]
    E --> AU["Audit: TRIAGE"]
```

---

## 🩺 Doctor Co-pilot: Grounded, Cited, Access-Checked

```mermaid
flowchart TD
    Q["Doctor: @patient + question"] --> PICK{"Own patient?<br/>AccessGuard"}
    PICK -- no --> DENY["Refused<br/>(same reply for unknown ids)"]
    PICK -- yes --> TOOLS["Read-only tool"]
    TOOLS --> AG{"AccessGuard<br/>again per call"}
    AG -- yes --> AUD["Audit: AI_COPILOT_READ"]
    AUD --> MIN["Minimum data<br/>name · age · gender · blood group<br/>❌ phone ❌ email ❌ address ❌ DOB"]
    MIN --> LLM["LLM"]
    LLM --> ANS["Answer with citations<br/>BP 150/95 [vitals, 12 Oct 2026]"]
```

---

## ⚡ Proactive AI

```mermaid
flowchart LR
    subgraph HB["📋 Health Brief"]
        h1["DB facts<br/>next visit · to-dos · unread labs"] --> h2["AI tips<br/>(no names / ids)"] --> h3["Cache until facts change"]
    end
    subgraph LE["🧪 Lab Explainer"]
        l1["Explain button"] --> l2["Gemini reads PDF / image"] --> l3["Saved encrypted<br/>+ not-a-diagnosis note"]
    end
    subgraph SS["🔎 Symptom Search"]
        s1["Describe problem"] --> s2["Triage rules → agent"] --> s3["Filter doctor list"]
    end
    subgraph VS["📄 Visit Summary"]
        v1["Completed visit opened"] --> v2{"Record hash<br/>changed?"}
        v2 -- no --> v3["Saved summary<br/>0 LLM calls"]
        v2 -- yes --> v4["Rewrite + save"]
    end
```

---

## 🔀 Model Routing & Fallback

```mermaid
flowchart LR
    TASK["AI task"] --> TIER{"Tier"}
    TIER -- "SMART<br/>agent · co-pilot" --> M1["gpt-oss-120b"]
    TIER -- "FAST<br/>summaries · tips" --> M2["gpt-oss-20b"]
    TIER -- "VISION<br/>files" --> M3["Gemini 2.5 Flash"]
    M1 -- "404 / 429 / 5xx / bad tool JSON" --> M2
    M2 -- "clinical fallback" --> M3
    M3 -- "unavailable" --> F["Recorded facts / checklist<br/>booking by buttons"]
```

---

## 🛡️ AI Guardrails & Cost Control

```mermaid
flowchart TD
    IN["AI request"] --> Q1["Per-user quota<br/>20/min · 200/day"]
    Q1 --> Q2["Max 2000 chars"]
    Q2 --> Q3["Encrypted response cache<br/>SHA-256 key"]
    Q3 --> Q4["Prompt-injection guard<br/>tool results = data"]
    Q4 --> Q5["Grounding<br/>tools for facts · never invent"]
    Q5 --> Q6["Audit every call<br/>model · tokens · latency · tools"]
    Q6 --> Q7["AI text encrypted<br/>deleted after 30 days"]
```

---

## 💾 AI Memory & Saved Chats

```mermaid
flowchart LR
    MSG["Chat turn"] --> MEM["Redis memory<br/>last 10 msgs · 24h<br/>AES-256-GCM · per user"]
    MSG --> HIST["Saved chats (PostgreSQL)<br/>encrypted · owner-only"]
    HIST --> ACT["Rename · Delete · Delete all"]
    HIST --> RET["Auto-delete after 90 days unused"]
    HIST --> EXP["Included in patient data export"]
```

---

## 🏛️ System Architecture

```mermaid
flowchart LR
    U["Angular 19 SPA<br/>Vercel"] -- "HTTPS REST + SSE" --> API["Spring Boot 3.5 API<br/>Docker on Render"]
    U -- "WebSocket STOMP" --> API
    U -- "WebRTC" --> LK["LiveKit video"]
    API --> PG[("PostgreSQL<br/>Neon")]
    API --> RD[("Redis<br/>cache · slot locks · quotas · AI memory")]
    API --> ST[("Supabase Storage<br/>private bucket · signed links")]
    API --> GQ["Groq LLMs"]
    API --> GM["Gemini Vision"]
    API --> RZ["Razorpay"]
    API --> EM["Resend Email"]
```

---

## 🔐 Data Protection Layers

```mermaid
flowchart TD
    R["Request"] --> F1["Rate limit · IP block"]
    F1 --> F2["JWT + server-side session<br/>idle 1h · max 24h"]
    F2 --> F3["Role rules + @PreAuthorize"]
    F3 --> F4["AccessGuard<br/>object-level ownership"]
    F4 --> F5["Audit trail<br/>route · ids · status · user · IP"]
    F5 --> F6[("AES-256-GCM fields<br/>encrypted Redis values")]
```

---

## 🔒 No Double Booking

```mermaid
sequenceDiagram
    actor A as Patient A
    actor B as Patient B
    participant L as Redis Slot Lock
    participant DB as PostgreSQL

    A->>L: lock doctor + slot
    L-->>A: ✅ acquired (5 min)
    B->>L: lock same slot
    L-->>B: ❌ taken
    A->>DB: PESSIMISTIC_WRITE + conflict check
    DB-->>A: ✅ booked
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.5 · Java 21 |
| Frontend | Angular 19 · Tailwind CSS |
| Database | PostgreSQL · Flyway (13 migrations) |
| Cache / Locks | Redis |
| AI | Groq gpt-oss-120b / 20b · Gemini 2.5 Flash |
| Real-time | SSE · WebSocket STOMP · LiveKit |
| Security | JWT + sessions · AES-256-GCM · RBAC · Audit |
| Payments | Razorpay |
| Deploy | Docker · Render · Neon · Vercel |

---

## 📖 Documentation & Links

- 🌐 **[Live Web Application](https://caresync-vikrant.vercel.app/)**
- 📐 **[System Architecture Deep-Dive](docs/architecture_overview.md)**
- 🧪 **[Java Concurrency Learning Lab](docs/java-concurrency-learning-lab.md)**
- 🗄️ **[Database ERD Diagram](https://dbdiagram.io/d/6970e7c2bd82f5fce22c9b1d)**
- 🌐 **[Live Swagger API UI](http://caresync-backend-aq8e.onrender.com/swagger-ui)**

---

## ⚡ Quick Start

```bash
# Clone the repository
git clone https://github.com/vikrant48/careSync.git
cd careSync

# Run backend locally
./mvnw spring-boot:run
```
