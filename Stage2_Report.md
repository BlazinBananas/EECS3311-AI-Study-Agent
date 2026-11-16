# EECS 3311 — Project Stage 2: Implementation & Verification Report

**Project Title:** AI Study and Learning Agent (StudyAgent Workbench)  
**Author:** Ihsan Hussain  
**Course:** LE/EECS 3311 A — Software Design (Fall 2026)  
**Instructor:** Prof. Song Wang  
**Repository:** `https://github.com/blazinbananas/EECS3311-AI-Study-Agent`

---

## 1. System Architecture & High-Level Design Overview

### 1.1 Realization of Architecture in Code

In Stage 2, the high-level architecture designed in Stage 1 was realized as a modular Java 25 Maven application (`ai.studyagent`) following a **Hierarchical Heterogeneous Architecture** (Lecture 10) that combines three architectural styles:

1. **Layered Architecture Style:**
   - **Presentation Layer (`ai.studyagent.ui`):** Contains `MainGUI` (root frame, navigation, FlatLaf theme controller, and Gson workspace persistence), `CourseDashboardPanel` (blank-slate notebook grid), `StudyWorkspacePanel` (3-column RAG chat, source checkbox filter, and Studio library), `MaximizedStudyDialog` (full-screen Flashcard Focus Studio, Interactive Quiz/Exam Runner, and side-by-side Code Lab IDE), `ScheduleCalendarPanel` (customizable weekly timetable), `PomodoroPanel` (focus timer and always-on-top mini widget), and `CLI` (interactive console interface).
   - **Facade & Application Control Layer (`ai.studyagent.controller`):** Contains `StudyAgentFacade` (single unified entry point for both `MainGUI` and `CLI`) and `AgentController` (central RAG pipeline, threshold evaluator, and LLM prompt orchestrator).
   - **Business Logic & Service Subsystems (`ai.studyagent.rag`, `ai.studyagent.strategy`, `ai.studyagent.factory`, `ai.studyagent.session`, `ai.studyagent.observer`):** Contains `DocumentManager`, `WebSearchTool`, `LLMStrategy` implementations, `StudyMaterialFactory` implementations, `SessionManager`, `StudySession`, `SubmitAnswerCommand`, and `AnalyticsDashboard`.
   - **Domain & Configuration Layer (`ai.studyagent.domain`, `ai.studyagent.config`):** Contains domain entities (`Flashcard`, `Quiz`, `QuizQuestion`, `CodingProblem`, `GradeEvaluation`, `DocumentChunk`, `WebSnippet`, `ChatResponse`, `IngestionResult`, `SessionStats`, `InvalidSessionStateException`) and `EngineConfig`.
2. **Model-View-Controller (MVC) via Observer Pattern:** Domain models (`StudySession`, `SessionStats`, `DocumentChunk`) have zero dependency on `javax.swing` or `CLI`. User actions pass through `StudyAgentFacade` to mutate the model, and completion events propagate back to `AnalyticsDashboard` and `MasteryChartPanel` via the Observer pattern.
3. **Data-Oriented Repository & Workspace Persistence:** `DocumentManager` maintains an in-memory multi-subject repository (`Map<String, List<DocumentChunk>>`), while `MainGUI` serializes and deserializes pure data transfer objects (`PersistedWorkspace`) to `studyagent-workspace.json` using Gson so user courses, chat threads, studio libraries, ingested chunks, and schedules persist across restarts without serializing Swing View components.

---

## 2. Design Patterns & SOLID Principles Implementation

Each of the five Gang of Four (GoF) design patterns proposed in Stage 1 is implemented in the codebase and documented below using the four mandatory elements from Lecture 6 (**Pattern Name**, **Problem**, **Solution**, and **Consequences / Trade-offs**).

### 2.1 Facade Pattern (Structural)

- **Pattern Name:** Facade (`src/main/java/ai/studyagent/controller/StudyAgentFacade.java`)
- **Problem:** Both `MainGUI` and `CLI` must execute complex multi-step workflows across document ingestion (`DocumentManager`), web augmentation (`WebSearchTool`), AI orchestration (`AgentController`), session command management (`SessionManager`), and analytics (`AnalyticsDashboard`). Direct coupling between the UI classes and these five subsystems would violate low coupling and duplicate orchestration code across the GUI and CLI.
- **Solution:** `StudyAgentFacade` instantiates and wires the internal subsystems (including registering `analyticsDashboard` as an observer on `sessionManager` via `this.sessionManager.attachObserver(this.analyticsDashboard)`) and exposes high-level methods (`setEngineMode`, `ingestFolder`, `askQuestion`, `requestFlashcards`, `requestQuiz`, `generateCodingProblem`, `gradeSubmission`, `startStudySession`, `submitQuizAnswer`, `undoLastSessionAnswer`, `viewAnalytics`). Method overloading provides filtered variants (`Set<String> selectedSources` and `String customTopic`) for the GUI while preserving simpler signatures for the CLI and JUnit tests.
- **Consequences & Trade-offs:**
  - _Benefits:_ Decouples `ui` classes from subsystem internals; ensures identical business rules (such as the `F08` blank-submission guard) apply to both `MainGUI` and `CLI`.
  - _Trade-offs:_ Introduces wrapper methods on `StudyAgentFacade` whenever new subsystem features are exposed to the UI.

### 2.2 Strategy Pattern (Behavioral)

- **Pattern Name:** Strategy (`LLMStrategy.java`, `LocalOllamaClient.java`, `CloudOpenAIClient.java`, `AgentController.java`)
- **Problem:** Feature `F01` requires dynamic runtime switching between local hardware inference (LM Studio on `http://127.0.0.1:1234/v1/chat/completions` or Ollama on `http://127.0.0.1:11434/api/generate`) and cloud inference (`OpenAI gpt-4o-mini`). Hardcoding provider-specific HTTP headers, JSON payloads, and error handling inside `AgentController` would violate the Single Responsibility Principle (SRP) and Open/Closed Principle (OCP).
- **Solution:** `LLMStrategy` defines `String generateResponse(String prompt, List<String> contextChunks) throws Exception`. `LocalOllamaClient` implements HTTP/1.1 JSON requests for both LM Studio and Ollama endpoints, auto-detects installed local models (`detectInstalledLocalModels()`), and strips `<think>...</think>` reasoning blocks and markdown fences (`stripMarkdownCodeFences()`). `CloudOpenAIClient` implements Bearer-authenticated HTTPS requests to OpenAI. `AgentController.setEngine(boolean isLocal, EngineConfig newConfig)` swaps `activeStrategy` at runtime and reverts to `previousStrategy` if validation fails.
- **Consequences & Trade-offs:**
  - _Benefits:_ Satisfies OCP and the Liskov Substitution Principle (LSP), as `AgentController` invokes `activeStrategy.generateResponse(...)` identically regardless of whether `Qwen3-8B` locally or `gpt-4o-mini` in the cloud is active.
  - _Trade-offs:_ Increases the number of strategy classes and requires uniform parameter passing (`prompt` + `contextChunks`) across all providers.

### 2.3 Factory Method Pattern (Creational)

- **Pattern Name:** Factory Method (`StudyMaterialFactory.java`, `FlashcardFactory.java`, `QuizFactory.java`)
- **Problem:** Features `F05` and `F06` require parsing raw, potentially malformed JSON strings returned by an LLM into strongly-typed `Flashcard`, `Quiz`, and `QuizQuestion` domain objects. Placing Gson parsing and schema validation inside `AgentController` would violate SRP.
- **Solution:** `StudyMaterialFactory` defines the abstract creator contract `StudyMaterial createMaterial(String subjectId, String rawJson)`. `FlashcardFactory` parses JSON arrays of `{term, definition}` objects into `Flashcard` instances, while `QuizFactory` parses nested `questions` arrays, options lists, and `correctAnswerIndex` integers into `Quiz` aggregates. Both factories throw `IllegalArgumentException` on malformed JSON or empty arrays, prompting `AgentController` to execute a single deterministic retry/fallback.
- **Consequences & Trade-offs:**
  - _Benefits:_ Isolates JSON schema validation from AI prompt logic; makes `FlashcardFactory` and `QuizFactory` independently unit-testable (`FactoryAndRagTest`).
  - _Trade-offs:_ Adding new study material products requires creating a corresponding `StudyMaterialFactory` subclass.

### 2.4 Command Pattern (Behavioral)

- **Pattern Name:** Command (`Command.java`, `SubmitAnswerCommand.java`, `SessionManager.java`, `StudySession.java`)
- **Problem:** Feature `F09` requires interactive quiz and timed exam execution where students can submit answers sequentially and **undo** their previous answers (`Command.undo()`), restoring the exact prior question index, score, and completion status.
- **Solution:** `Command` declares `boolean execute()` and `void undo()`. `SubmitAnswerCommand` encapsulates the Receiver (`StudySession`), the current `QuizQuestion`, and `submittedChoiceIndex`. Inside `execute()`, it records `previousScore = session.getCurrentScore()` and `previousQuestionIndex = session.getCurrentQuestionIndex()` before calling `session.applyAnswer(...)`. `SessionManager` pushes executed commands onto `Stack<Command> historyStack`. Calling `SessionManager.undoLastAction()` pops the top command and invokes `undo()`, which calls `session.restoreState(previousQuestionIndex, previousScore)`.
- **Consequences & Trade-offs:**
  - _Benefits:_ Provides deterministic, multi-step state reversal even after a quiz reaches completion; decouples UI buttons from `StudySession` state mutation.
  - _Trade-offs:_ Consumes O(N) memory on `historyStack` proportional to the number of answered questions in the current session.

### 2.5 Observer Pattern (Behavioral)

- **Pattern Name:** Observer (`ProgressObserver.java`, `AnalyticsDashboard.java`, `SessionManager.java`)
- **Problem:** Feature `F10` requires the Progress Analytics Dashboard and visual `MasteryChartPanel` to update automatically as soon as a quiz or cumulative exam finishes in `F09`, without coupling `SessionManager` to Swing UI classes.
- **Solution:** `ProgressObserver` defines `void onSessionUpdated(SessionStats stats)`. `SessionManager` maintains `List<ProgressObserver> observers`. When `activeSession.isCompleted()` evaluates to `true` inside `SessionManager.executeCommand()`, `SessionManager` constructs a `SessionStats` instance and calls `notifyObservers(stats)`, which updates `AnalyticsDashboard`'s subject history map.
- **Consequences & Trade-offs:**
  - _Benefits:_ Supports broadcast communication and loose coupling between session management and analytics/UI views; new observers can be attached without modifying `SessionManager`.
  - _Trade-offs:_ Observers are notified of completion events as a coarse-grained `SessionStats` snapshot rather than per-question changes.

---

## 3. Feature Implementation (`F01`–`F10`), Contracts & Traceability

### 3.1 Traceability Matrix Across All 10 Features

All 10 features from Stage 1 (along with their normal flows and error/alternative flows) are fully implemented:

| Feature ID | Feature Name                                  | Classes & Methods                                                                                                | Normal Flow & Error / Alternative Flow Implementation                                                                                                                                                                                                                                                                                                                                                                      |
| :--------- | :-------------------------------------------- | :--------------------------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **F01**    | **Local & Cloud AI Engine Toggle**            | `AgentController.setEngine()`, `EngineConfig`, `LocalOllamaClient`, `CloudOpenAIClient`                          | **Normal:** Toggles between Local (`LM Studio :1234` / `Ollama :11434`) and Cloud (`OpenAI`), with auto-detection of installed local models.<br>**Error/Alt:** Invalid endpoint URL (`!startsWith("http")`) or invalid key throws `IllegalArgumentException`; `AgentController` catches the error, restores `previousStrategy`, and alerts the user.                                                                       |
| **F02**    | **Local Directory Document Ingestion**        | `DocumentManager.ingestFolder()`, `IngestionResult`                                                              | **Normal:** Parses `.txt`, `.md`, and `.pdf` (via Apache PDFBox) into 450-character `DocumentChunk` objects indexed by `subjectId`, with per-file checkboxes in the GUI.<br>**Error/Alt:** Encrypted PDFs or unsupported files (e.g., `corrupted_notes.unsupported`) are caught, skipped, and listed in `IngestionResult.getSkippedFiles()`.                                                                               |
| **F03**    | **Live Web Search Augmentation**              | `WebSearchTool.searchWeb()`, `AgentController.processChatRequest()`                                              | **Normal:** When local chunk similarity `< 0.65` and `permitWebSearch == true`, queries Wikipedia MediaWiki Search API & DuckDuckGo Instant Answer API, strips HTML tags, and returns `WebSnippet` citations.<br>**Error/Alt:** If unchecked or offline, returns a clear fallback message stating local notes do not cover the query and web verification is unavailable/disabled.                                         |
| **F04**    | **Subject-Grounded Natural Language Chat**    | `AgentController.processChatRequest()`, `StudyWorkspacePanel`                                                    | **Normal:** Isolates multi-thread chats per course (`Map<String, NotebookState>`), filters chunks by checked source files, and provides `Citations (F04)` and `📌 Save to Note`.<br>**Error/Alt:** If the course has no ingested files or no checked sources, throws `IllegalStateException` prompting the user to import/select materials first.                                                                          |
| **F05**    | **Interactive Flashcard Generation**          | `AgentController.generateFlashcards()`, `FlashcardFactory`, `MaximizedStudyDialog.showFlashcardsModal()`         | **Normal:** Synthesizes topic-focused flashcard decks saved to the Studio Library; full-screen Focus View supports `Space` flip, `←/→` navigation, bounded `✕`/`✓` tracking (`Map<Integer, Boolean>`), shuffle, add/edit/delete, and stopwatch.<br>**Error/Alt:** If LLM returns malformed JSON, `AgentController` catches `IllegalArgumentException` from `FlashcardFactory` and executes a single deterministic retry.   |
| **F06**    | **Adaptive Quiz Generation**                  | `AgentController.generateQuiz()`, `QuizFactory`                                                                  | **Normal:** Generates `Beginner`, `Intermediate`, or `Advanced` multiple-choice quizzes filtered by checked sources and custom topic prompt.<br>**Error/Alt:** If requested question count exceeds `chunks.size() + 2` (or folder is empty), throws `IllegalStateException("Insufficient source material to generate a full quiz...")`.                                                                                    |
| **F07**    | **Coding Problem Synthesis**                  | `AgentController.synthesizeCodingProblem()`, `MaximizedStudyDialog.showCodeLabModal()`                           | **Normal:** Synthesizes a Java programming problem with description, starter code, and SOLID/DbC constraints in both the sidebar and full-screen Code Lab IDE.<br>**Error/Alt:** Non-CS or unsafe topics (e.g., `"Bake a cake"`, `"hack"`) are deterministically rejected with a polite academic refusal alert.                                                                                                            |
| **F08**    | **Intelligent Answer Grading**                | `StudyAgentFacade.gradeSubmission()`, `AgentController.evaluateSolution()`                                       | **Normal:** Evaluates submitted Java code and returns a `GradeEvaluation` (`/100` score, semantic feedback, and actionable hints).<br>**Error/Alt:** Blank or gibberish submissions (`< 10` characters or lacking code tokens) are blocked before calling the LLM with `"Please enter a valid answer before submitting"`.                                                                                                  |
| **F09**    | **Guided Study Session & Timed Exam Builder** | `SessionManager`, `StudySession`, `SubmitAnswerCommand`, `MaximizedStudyDialog.showQuizModal()`, `PomodoroPanel` | **Normal:** Runs interactive quizzes or merges saved quizzes into a Cumulative Timed Exam with a dynamic countdown timer (`30s`–`120s`/question); supports `↶ Undo (F09)` during and after completion; includes top-bar `⏱ Pomodoro` timer with a pop-out always-on-top mini widget.<br>**Error/Alt:** Closing the window mid-session pauses the timer and prompts `"Session in progress. Save progress before exiting?"`. |
| **F10**    | **Progress Analytics Dashboard**              | `AnalyticsDashboard`, `ProgressObserver`, `MasteryChartPanel`                                                    | **Normal:** Automatically receives `SessionStats` upon quiz/exam completion and renders per-subject session counts, accuracy percentages, and a visual `Graphics2D` mastery bar chart.<br>**Error/Alt:** Before any session is completed, renders the empty-state prompt `"No data available yet. Start a study session!"`.                                                                                                |

### 3.2 Design by Contract (DbC) & JML Specifications (Lectures 14 & 15)

Formal Client-Supplier contracts are implemented in `StudySession.java` and `SubmitAnswerCommand.java`:

- **Class Invariants (`//@ public invariant`):**
  - `currentScore >= 0 && currentScore <= currentQuestionIndex`
  - `currentQuestionIndex >= 0 && currentQuestionIndex <= quiz.getQuestions().size()`
- **Preconditions (`//@ requires`):** Validated at method entry via negated `if` checks throwing `IllegalArgumentException` or `IllegalStateException` (e.g., rejecting null/empty quizzes or out-of-bounds option indices `< 0` or `>= options.size()`).
- **Postconditions (`//@ ensures`):** Verified at method exit using pre-execution state variables (`oldIndex`, `oldScore`) and Java `assert` statements confirming `currentQuestionIndex == \old(currentQuestionIndex) + 1` and accurate score updates/rollbacks.

---

## 4. Evolution of Design & Resolution of Stage 1 Feedback

| Stage 1 TA Feedback / Design Area                             | How It Was Resolved and Improved in Stage 2                                                                                                                                                                                                                                                                                                                                                                    |
| :------------------------------------------------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **1. Missing explicit pattern consequences / trade-offs**     | Documented concrete space, time, coupling, and extensibility trade-offs for all 5 GoF patterns in Section 2 (matching Lecture 6 Slide 9).                                                                                                                                                                                                                                                                      |
| **2. Implicit agent decision & tool contracts (`F03`/`F04`)** | Formalized the `0.65` keyword-overlap similarity threshold in `AgentController.processChatRequest()` and added user-controlled source file checkboxes so context selection and `WebSearchTool` invocation are deterministic and transparent.                                                                                                                                                                   |
| **3. Incomplete Command undo state specification (`F09`)**    | Implemented `SubmitAnswerCommand` with explicit pre-execution fields (`previousScore`, `previousQuestionIndex`) and `StudySession.restoreState()`, enabling full rollback even after a quiz reaches the completed state.                                                                                                                                                                                       |
| **4. UI Modularity & Non-Blocking Concurrency**               | Decomposed `MainGUI` into 6 specialized Swing classes (`MainGUI`, `CourseDashboardPanel`, `StudyWorkspacePanel`, `MaximizedStudyDialog`, `ScheduleCalendarPanel`, `PomodoroPanel`), wrapped LLM/network calls in background `SwingWorker` threads with an indeterminate progress bar so the Event Dispatch Thread never freezes, and added automatic JSON workspace persistence (`studyagent-workspace.json`). |

---

## 5. Weekly Git Commit Progress & Repository Organization

Development followed an iterative Agile sprint schedule across all three required Stage 2 commit periods:

- **Period 1 (October 20 – October 26, 2026):** 5+ commits establishing the domain models (`Flashcard`, `Quiz`, `QuizQuestion`, `CodingProblem`, `GradeEvaluation`, `DocumentChunk`, `WebSnippet`), `LLMStrategy` hierarchy, `StudyMaterialFactory` hierarchy, `DocumentManager`, and `WebSearchTool`.
- **Period 2 (October 27 – November 2, 2026):** 5+ commits implementing `SessionManager`, `StudySession`, `SubmitAnswerCommand`, `AnalyticsDashboard`, `AgentController`, `StudyAgentFacade`, `MainGUI`, `CLI`, and sequence/class diagram alignment.
- **Period 3 (November 3 – November 9, 2026):** 5+ commits adding JML & Design by Contract assertions (`StudySession`, `SubmitAnswerCommand`), live HTTP/1.1 LM Studio/Ollama & OpenAI clients, live Wikipedia/DuckDuckGo `WebSearchTool`, JUnit 5 verification suites (`SessionAndContractTest`, `FactoryAndRagTest`), FlatLaf multi-panel UI decomposition, `MaximizedStudyDialog` focus views, Cumulative Timed Exam Builder, `PomodoroPanel` mini widget, and `studyagent-workspace.json` persistence.
- **Secret Management:** `.gitignore` excludes `config.local.json`, `.env`, `target/`, and `studyagent-workspace.json`, while `config.local.json.example` and `sample-course-notes/` are tracked for safe grading reproduction.

---

## 6. Verification & Unit Testing Summary

In accordance with Lectures 2, 11, and 12, automated JUnit 5 tests (`src/test/java/ai/studyagent/`) and manual GUI/CLI checks were executed to verify the implementation:

1. **`SessionAndContractTest.java` (6 Unit Tests — PASS):**
   - `testStudySessionPreconditionRejectsNullOrEmptyQuiz`: Verifies negated DbC constructor preconditions throw `IllegalArgumentException`.
   - `testApplyAnswerPreconditionRejectsOutOfBoundsOption`: Verifies option indices `-1` and `4` throw `IllegalArgumentException`.
   - `testCommandExecuteAndUndoRestoresPreExecutionState`: Verifies `SubmitAnswerCommand.execute()` increments index/score and `undo()` restores both to `0`.
   - `testUndoOnEmptyCommandStackReturnsFalse`: Verifies boundary behavior on an empty `SessionManager` command stack.
   - `testObserverNotifiedOnlyWhenSessionCompletes`: Verifies `AnalyticsDashboard` remains empty mid-session and updates to `50.0%` mastery upon final question submission.
   - `testBoundaryValueAnalysisOnSimilarityThreshold`: Applies **Boundary-Value Analysis (BVA)** around threshold `0.65` using `0.64` (below), `0.65` (on), and `0.66` (above).
2. **`FactoryAndRagTest.java` (4 Unit Tests — PASS):**
   - `testFlashcardFactoryParsesValidJsonAndRejectsMalformedJson`: Exercises both valid and exception branches of `FlashcardFactory`.
   - `testQuizFactoryCreatesQuizAggregateAndRejectsEmptyQuestions`: Verifies `Quiz` creation and empty-question rejection.
   - `testLocalDirectoryIngestionAndGroundedGeneration`: Verifies `F02` ingestion on `sample-course-notes` and grounded `F05`/`F06` synthesis through `StudyAgentFacade`.
   - `testIntelligentGradingRejectsBlankSubmissionAndGradesValidCode`: Exercises both Control Flow Graph (CFG) branches of the `F08` guard (blank string throwing `IllegalArgumentException` vs. valid Java code returning a `GradeEvaluation` score $\ge 80$).

---

## 7. Feature Demonstration Videos (`F01`–`F10`)

Video Link: https://drive.google.com/file/d/1thLoV4y_6hTfX4XzFegzBckOYChShfKj/view?usp=sharing)

The table below provides the links and timestamps for the Stage 2 feature demonstration recordings covering both the **GUI** and **CLI**, including normal executions and error/alternative flows:

- **Master Video Folder / Playlist Link:**

| Feature ID | Feature Demonstrated                             | Interface Shown        | Normal Flow & Error / Alternative Flow Demonstrated                                                                                                                                                                                                               | Video Link / Timestamp |
| :--------- | :----------------------------------------------- | :--------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :--------------------- |
| **F01**    | **Local & Cloud AI Engine Toggle**               | GUI & CLI              | Switching between Local AI (`Qwen3-8B` on LM Studio) and Cloud OpenAI (`gpt-4o-mini`); auto-detecting local models; entering an invalid endpoint to show error handling and automatic strategy revert.                                                            | `00:25`                |
| **F02**    | **Local Directory Document Ingestion**           | GUI & CLI              | Importing `sample-course-notes/`; showing `EECS3311_Notes.txt` chunked and listed with a source checkbox while `corrupted_notes.unsupported` is skipped with a warning report.                                                                                    | `00:45`                |
| **F03**    | **Live Web Search Augmentation**                 | GUI                    | Asking an out-of-notes question (`similarity < 0.65`) with `Web Search (F03)` enabled to show live Wikipedia/DuckDuckGo citations; unchecking `F03` to show the disabled-fallback alert.                                                                          | `01:02`                |
| **F04**    | **Subject-Grounded RAG Chat**                    | GUI & CLI              | Asking `"What is the Liskov Substitution Principle?"` on `EECS3311`, inspecting retrieved chunks via `Citations (F04)`, pinning the answer via `Save to Note`, and testing an empty notebook to show the empty-folder guard.                                      | `00:55`                |
| **F05**    | **Interactive Flashcard Generation**             | GUI                    | Generating topic-focused flashcards, opening `⤢ Maximize` Focus Studio, flipping with `Space`, grading with bounded `✕`/`✓` buttons, shuffling/editing via `⋮ Options`, and toggling the stopwatch.                                                               | `01:45`                |
| **F06**    | **Adaptive Quiz Generation**                     | GUI                    | Generating an `Intermediate` quiz from checked sources; then requesting `15 questions` to demonstrate the `"Insufficient source material"` exception alert.                                                                                                       | `02:55`                |
| **F07**    | **Coding Problem Synthesis**                     | GUI (`⤢ Maximize` IDE) | Synthesizing a Java coding problem for `"Java Strategy Pattern"`; then entering `"Bake a cake"` to demonstrate the academic safety & scope refusal alert.                                                                                                         | `04:25`                |
| **F08**    | **Intelligent Answer Grading**                   | GUI (`⤢ Maximize` IDE) | Submitting a Java implementation to receive a `/100` score and hints; then clicking `Clear Editor` and submitting to trigger the empty/gibberish input validation guard.                                                                                          | `05:00`                |
| **F09**    | **Guided Study Session, Timed Exam**             | GUI & CLI              | Combining saved quizzes into a Cumulative Timed Exam with countdown timer, answering questions, clicking `↶ Undo (F09)` during and after completion, opening the top-bar `⏱ Pomodoro` pop-out mini widget, and showing the mid-session close confirmation dialog. | `04:07`                |
| **F10**    | **Progress Analytics Dashboard and study tools** | GUI & CLI              | Completing a study session and showing `AnalyticsDashboard` and the visual `MasteryChartPanel` automatically update cumulative mastery via the Observer pattern.                                                                                                  | `05:25`                |
