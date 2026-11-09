# SecondChance IL - Illinois Lottery Ticket Scanner & Batch Submitter

SecondChance IL is an Android app built to eliminate the tedious manual entry of losing Illinois instant scratch-off lottery tickets into Second Chance drawings. It pairs camera OCR scanning and instant code validation with structured game organization and an automated in-app browser submission engine.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Confirmed Choices from Phase 1:**
> - **Capture Mechanism:** Camera scanner with on-device text/OCR recognition and fallback manual entry dialog.
> - **Submission Engine:** Embedded secure Android WebView browser with credential auto-fill, step-by-step assisted injection, and automated entry queue processing.
> - **Ticket Organization:** Grouped hierarchically by specific scratch-off game titles and upcoming drawing deadlines.

- **Account Security & Credential Storage:** Illinois Lottery account credentials (email/username and password) will be stored securely on-device using Android encrypted preferences, strictly accessed locally by the in-app submission browser.
- **Physical Ticket Retention Reminder:** Illinois Lottery rules mandate retaining physical winning tickets; the app will include high-contrast ticket photo storage and clear retention warnings so entries can be validated if drawn.

---

## 1. Overview & Core Concept

- **What It Does:** Enables players to photograph losing Illinois instant lottery scratch-off tickets, extract entry/validation codes automatically via OCR, categorize codes under active Illinois Lottery promotional games and deadlines, and run an automated batch submission sequence through the official Illinois Lottery 2nd Chance Zone portal.
- **Target Audience:** Illinois Lottery scratch-off players who accumulate stacks of non-winning tickets and want to enter promotional second-chance drawings efficiently without manual typing or transcription errors.
- **Key Value:** Saves time by converting minutes of manual 14-digit typing into seconds of point-and-scan capture, prevents missed drawing deadlines with organized tracking, and submits batches directly into user accounts through an automated browser flow.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Dashboard & Deadlines:**
   - Summary cards displaying total scanned codes, pending submissions, and successfully entered entries.
   - Drawing countdown badges highlighting games approaching submission cutoffs.
   - Filterable view grouped by game title (e.g., "$100,000 Frenzy", "Holiday Bonus", "Jackpot 7s").

2. **Ticket Scanning & Code Capture:**
   - Camera viewfinder overlay with a guided alignment target box for the scratch-off entry area.
   - On-device text recognition immediately highlights parsed codes with visual confidence indicators.
   - Review bottom sheet allowing one-tap correction, game selection, entry code verification, and optional ticket photo saving.

3. **Batch Submitter & Assisted In-App Browser:**
   - "Submit Batch" action prompts the user to select pending tickets or submit all pending for a specific game.
   - Embedded full-featured browser opens to the Illinois Lottery Second Chance portal (`secondchancebonuszone.com`).
   - Secure Quick-Fill bar at the top with auto-login assistance, code injection buttons, progress counter (e.g., "Submitting Ticket 3 of 12"), and instant status mark (Success / Duplicate / Expired).

4. **Ticket Vault & Verification History:**
   - Searchable and filterable history of all scanned tickets with high-resolution photo reference, entry timestamp, and drawing confirmation receipts.

### Visual Identity & Theme

- **Aesthetic Direction:** Modern utilitarian fintech and lottery aesthetic—clean, sharp, high-contrast, and confidence-inspiring with vibrant emerald and golden lottery accents over dark slate surfaces.
- **Color Palette & Mood:**
  - *Primary:* Emerald Green (`#00A86B` / `#10B981`) representing luck, Illinois Lottery identity, and entry success.
  - *Accent & Warnings:* Warm Golden Amber (`#F59E0B`) for drawing countdowns and pending tickets.
  - *Surfaces & Backgrounds:* Deep Navy Slate (`#0F172A`) and Rich Charcoal Card (`#1E293B`) in dark mode, with crisp Off-White (`#F8FAFC`) in light mode.
- **Typography & Hierarchy:**
  - Bold tabular numbers for 10–14 digit lottery codes (`Monospace` font family) to ensure zero ambiguity between `0`/`O` and `1`/`I`.
  - Clean M3 typography hierarchy with prominent headlines and compact metadata labels.
- **Interactive Feedback & Motion:**
  - Haptic feedback and green particle ripple on successful code scan.
  - Smooth animated progress bar during batch submission sequence.
  - Bottom sheet animations for quick ticket adjustments.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Embedded WebView with Assisted DOM Injection vs. Headless HTTP Bot**
  - *Chosen Approach:* Embedded Android WebView with JavaScript injection helper and user-guided credentials.
  - *Why:* Illinois Lottery Second Chance websites employ anti-bot measures, CSRF tokens, session cookies, and occasional reCAPTCHA. An in-app browser lets the user authenticate safely in a real browser session while automating the repetitive copy-paste and submit cycle.
  - *Alternatives Considered:* Direct REST/HTTP client automation. Rejected because lottery sites block headless requests and automated session scraping frequently breaks.

- **Decision 2: Local Persistence (Room Database)**
  - *Chosen Approach:* Local Room SQLite database with reactive Kotlin Flow.
  - *Why:* Instant tickets and personal lottery account credentials belong securely on the user's local device without requiring external cloud accounts or servers.
  - *Alternatives Considered:* Cloud database (Firebase). Avoided for sensitive user credentials and to allow fully functional offline ticket scanning.

- **Decision 3: On-Device Vision/OCR with Rule-Based Parser**
  - *Chosen Approach:* On-device camera capture with intelligent lottery pattern matching (matching standard Illinois 3-digit game + 5-digit pack + 3-digit ticket + 10-14 digit validation code).
  - *Why:* Instant feedback with zero network latency, zero per-scan API costs, and full privacy.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
├──────────────────┬──────────────────┬──────────────────┬───────────────┤
│  DashboardScreen │   ScannerScreen  │ BatchSubmitScreen│  VaultScreen  │
│ (Games & Deadlines) (Camera Viewfinder) (WebView & Auto)│(Ticket Ledger)│
└────────┬─────────┴────────┬─────────┴────────┬─────────┴───────┬───────┘
         │                  │                  │                 │
         ▼                  ▼                  ▼                 ▼
┌────────────────────────────────────────────────────────────────────────┐
│                               ViewModel                                │
│       • UI State Flow     • Scan State       • Submission Orchestrator │
└──────────────────┬───────────────────────────┬─────────────────────────┘
                   │                           │
                   ▼                           ▼
┌──────────────────────────────────────┐ ┌───────────────────────────────┐
│           Repository Layer           │ │    Automated Web Bridge       │
│  • TicketRepository                  │ │ • In-App WebView Controller   │
│  • GamePromotionRepository           │ │ • JavaScript Script Injector  │
│  • UserCredentialRepository          │ │ • Submission Queue Engine     │
└──────────────────┬───────────────────┘ └───────────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────┐
│           Local Persistence          │
│   • Room Database (SQLite)           │
│     - Tickets Table                  │
│     - Games & Deadlines Table        │
│   • Encrypted Shared Preferences     │
│   • Internal App Storage (Photos)    │
└──────────────────────────────────────┘
```

### Data Model Entities

- **LotteryTicket:**
  - `id`: Long (Primary Key)
  - `gameId`: String (e.g., "852")
  - `gameName`: String (e.g., "Holiday Cash Spectacular")
  - `packNumber`: String (e.g., "12345")
  - `ticketNumber`: String (e.g., "042")
  - `entryCode`: String (e.g., "948271059382")
  - `imageUri`: String? (local file path to captured photo)
  - `status`: TicketStatus (`PENDING`, `SUBMITTED`, `ERROR`, `DUPLICATE`)
  - `submissionTimestamp`: Long?
  - `confirmationCode`: String?
  - `notes`: String?

- **GamePromotion:**
  - `id`: String (e.g., "852")
  - `title`: String
  - `ticketPrice`: Double ($1, $2, $5, $10, $20, $30)
  - `drawingDeadline`: Long
  - `drawingDate`: Long
  - `portalUrl`: String
  - `isActive`: Boolean

- **UserCredentials:**
  - `accountEmail`: String
  - `password`: String (securely stored)
  - `rememberMe`: Boolean

### Interactive State Transitions

- **Scanning Flow:** Camera active -> Frame analyzed -> Candidate code matched -> Haptic notification -> BottomSheet review -> Insert into Room -> Return to viewfinder.
- **Batch Submission Flow:** Select game or pending tickets -> Open Submission WebView -> Auto-fill Illinois Lottery login -> User confirms session -> Queue step advances -> Auto-paste entry code & trigger submit -> Detect portal confirmation -> Mark ticket `SUBMITTED` -> Advance to next code -> Complete summary dialog.
