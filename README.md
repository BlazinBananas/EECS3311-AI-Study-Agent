# AI Study and Learning Agent (StudyAgent Workbench)

![Status](https://img.shields.io/badge/Status-Stage_2_Complete-brightgreen?style=flat)
![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=flat&logo=apachemaven&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat&logo=docker&logoColor=white)
![OpenAI](https://img.shields.io/badge/OpenAI-412991?style=flat&logo=openai&logoColor=white)

**Course:** EECS 3311 - Software Design  
**Institution:** York University  
**Author:** Ihsan Hussain

---

## About This Project

This repository contains the design, implementation, and verification of **StudyAgent Workbench**, an AI-powered study and learning agent. The system is designed to ingest local course materials (`.pdf`, `.txt`, `.md`), synthesize interactive flashcards and adaptive quizzes, generate and grade Java coding problems, manage guided Pomodoro study sessions and cumulative timed exams, and provide a subject-grounded natural language chat with live web search augmentation. It features the option to toggle at runtime between a local self-hosted LLM (LM Studio / Ollama for offline privacy and utilization of local hardware) and a cloud-based API (OpenAI).

## Project Navigation

- [Stage 1: Software and Agent Design](Stage1_Report.md)
- [Stage 2: Implementation & Verification Report](Stage2_Report.md)
- **Stage 3:** Testing and Validation _(Upcoming)_
- **UML & Sequence Diagrams:** [`Diagrams/`](Diagrams/)
- **Sample Course Materials (F02):** [`sample-course-notes/`](sample-course-notes/)

---

## Tech Stack & Architecture

- **Language & Build:** Java 25, Apache Maven (`pom.xml`)
- **UI Frameworks:** Modernized Java Swing with **FlatLaf 3.7.2** (Dark/Light Mode, custom `Graphics2D` charts, and full-screen focus modals) + Interactive **CLI** (`Scanner` terminal menu)
- **AI Integration:** Local HTTP/1.1 client for **LM Studio** (`:1234/v1/chat/completions`, tested with `Qwen3-8B`) and **Ollama** (`:11434/api/generate`, `llama3.1:8b`), plus Cloud HTTPS client for **OpenAI API** (`gpt-4o-mini`)
- **Document & JSON Processing:** Apache PDFBox `2.0.30` (local PDF text extraction), Google Gson `2.10.1` (LLM JSON parsing and automatic `studyagent-workspace.json` persistence)
- **Verification & Contracts:** JUnit 5 (`junit-jupiter:5.14.2`), Design by Contract (DbC), and Java Modeling Language (JML) specifications
- **Implemented GoF Design Patterns:**
  1. **Facade Pattern (`StudyAgentFacade`):** Unified interface decoupling `MainGUI` and `CLI` from the RAG, LLM, Factory, Session, and Analytics subsystems.
  2. **Strategy Pattern (`LLMStrategy`, `LocalOllamaClient`, `CloudOpenAIClient`):** Interchangeable local and cloud AI inference algorithms.
  3. **Factory Method Pattern (`StudyMaterialFactory`, `FlashcardFactory`, `QuizFactory`):** Validated JSON parsing and instantiation of `Flashcard` and `Quiz` domain objects.
  4. **Command Pattern (`Command`, `SubmitAnswerCommand`, `SessionManager`, `StudySession`):** Reversible quiz answer submissions supporting multi-step `undo()` via a pre-execution state history stack.
  5. **Observer Pattern (`ProgressObserver`, `AnalyticsDashboard`, `SessionManager`):** Real-time publish-subscribe updates to the analytics dashboard and visual mastery bar chart upon session completion.

---

## Prerequisites & Local Setup

1. **JDK & Maven:** Install **Java 21+** (configured for Java 25 in `pom.xml`) and **Apache Maven 3.9+**.
2. **Clone the Repository:**
   ```bash
   git clone [https://github.com/blazinbananas/EECS3311-AI-Study-Agent.git](https://github.com/blazinbananas/EECS3311-AI-Study-Agent.git)
   cd EECS3311-AI-Study-Agent
   ```
