import os
from dataclasses import dataclass, field
from typing import Dict, List, Optional, AsyncGenerator

# Backward-compatible import for LangChain message history across versions
try:
    from langchain_community.chat_message_histories import InMemoryChatMessageHistory  # v0.2+
except Exception:  # pragma: no cover
    try:
        from langchain_community.chat_message_histories import ChatMessageHistory as InMemoryChatMessageHistory  # some builds expose only ChatMessageHistory
    except Exception:
        # Final fallback for very old versions
        from langchain.memory import ChatMessageHistory as InMemoryChatMessageHistory  # type: ignore

from langchain_core.chat_history import BaseChatMessageHistory
from langchain_core.messages import HumanMessage, SystemMessage, AIMessage
from langchain_openai import ChatOpenAI

SYSTEM_PROMPT = (
    "Ты — опытный технический интервьюер по Java (Senior уровень). "
    "Веди интервью в доброжелательной и профессиональной манере. "
    "Цели: оценить архитектурное мышление, глубокое понимание JVM/JDK/Concurrency, Spring, SQL/NoSQL, распределённые системы, тестирование и DevOps-практики. "
    "Поддерживай диалог, отвечай на вопросы кандидата, а также задавай свои. "
    "Не перебивай кандидата: если кандидат говорит — дождись, пока он закончит (это обрабатывает голосовой шлюз). "
    "Коротко формулируй вопросы (1–2 предложения). Если ответ неполный — задавай уточняющие вопросы. "
    "После вступления и рассказа кандидата о себе переходи к техническому блоку. В конце предложи кандидату задать вопросы о компании и вакансии. "
)

JAVA_SENIOR_QUESTIONS: List[str] = [
    "Опишите, как работает JVM: загрузчик классов, область памяти (Heap/Stack/Metaspace), JIT/интерпретатор.",
    "Какие типы GC вы знаете? Когда выбирать G1/ZGC/Shenandoah? Какие метрики мониторите?",
    "Расскажите про правила happens-before в Java Memory Model и примеры гонок данных.",
    "Чем отличается synchronized от ReentrantLock? Где оправдано использовать StampedLock?",
    "Что такое false sharing и как его избежать?",
    "Как реализовать безопасные публикации объектов между потоками?",
    "Опишите механизмы неблокирующей синхронизации: CAS, Atomic-классы, LongAdder.",
    "Какие есть практики проектирования высоконагруженных систем в Java?",
    "Как устроен Spring Context, Bean lifecycle, scopes и прокси?",
    "Расскажите про транзакции в Spring: propagation, isolation, pitfalls.",
    "Spring WebFlux vs Spring MVC: когда что выбирать?",
    "Опишите паттерны в слоистой архитектуре: Application, Domain, Infra, Ports/Adapters.",
    "SQL vs NoSQL: критерии выбора. Нормализация, индексы, план выполнения запросов.",
    "Как проектировать схемы в PostgreSQL для высокой нагрузки?",
    "Кэширование: уровни, стратегии (write-through/back/around), invalidation.",
    "Messaging: Kafka vs RabbitMQ. Гарантии доставки, порядок, потребление, ретраи, DLQ.",
    "Идемпотентность и exactly-once semantics: как достигаете в практике?",
    "Распределённые транзакции: саги, outbox pattern, transactional outbox, CDC.",
    "Проектирование REST/GraphQL API: versioning, идемпотентность, пагинация, ошибки.",
    "Observability: логи, метрики, трейсы. Какие используете инструменты и метрики?",
    "Тестирование: pyramid, контрактные тесты, тестирование интеграций с БД и брокерами.",
    "Производительность: профилирование (JFR/Async-profiler), поиск утечек памяти.",
    "Безопасность: OAuth2/OIDC, хранение секретов, шифрование, защита от уязвимостей.",
    "DevOps: контейнеризация, CI/CD, Blue/Green и Canary deployments, feature flags.",
    "Архитектура: подход к разбиению монолита, микросервисы, взаимодействие сервисов.",
    "Проектный опыт: самый сложный инцидент, постмортем, изменения в процессе.",
    "Код-ревью и стандарты: чем руководствуетесь?",
    "Собеседование на культуру: как обучаете, менторите, делитесь знаниями?",
]


@dataclass
class InterviewSession:
    code: str
    token: str
    candidate_name: Optional[str] = None
    company_legend: str = (
        "Мы — Hunty Tech: условная продуктовая компания, платформа для автоматизации найма. "
        "Вакансия: Senior Java Backend Engineer в команду платформенных сервисов (сильный фокус на производительности и надёжности)."
    )
    phase: str = "intro"  # intro | tech | candidate_q | done
    questions: List[str] = field(default_factory=list)
    q_index: int = 0
    history: BaseChatMessageHistory = field(default_factory=InMemoryChatMessageHistory)


class InterviewAgent:
    def __init__(self):
        api_key = os.getenv("OPENAI_API_KEY")
        if not api_key:
            raise RuntimeError("OPENAI_API_KEY is not set in environment")
        # Streaming-enabled LLM
        self.llm = ChatOpenAI(model="gpt-4o", temperature=0.3, streaming=True)
        self.sessions: Dict[str, InterviewSession] = {}

    def _sid(self, code: str, token: str) -> str:
        return f"{code}:{token}"

    def ensure_session(self, code: str, token: str, candidate_name: Optional[str] = None) -> InterviewSession:
        sid = self._sid(code, token)
        if sid not in self.sessions:
            sess = InterviewSession(code=code, token=token, candidate_name=candidate_name)
            sess.questions = list(JAVA_SENIOR_QUESTIONS)
            # Seed with system prompt
            sess.history.add_message(SystemMessage(content=SYSTEM_PROMPT))
            self.sessions[sid] = sess
        else:
            sess = self.sessions[sid]
            if candidate_name:
                sess.candidate_name = candidate_name
        return sess

    def _build_prompt(self, sess: InterviewSession, user_text: Optional[str]) -> List:
        msgs: List = []
        # History is managed separately; we only supply the new turn
        if user_text is None:
            # Initial greeting
            intro = (
                f"Привет, {sess.candidate_name or 'коллега'}! Я — технический интервьюер. "
                f"Компания: {sess.company_legend}. "
                "Для начала расскажите, пожалуйста, кратко о себе и вашем самом значимом опыте, "
                "релевантном вакансии. На что вы делаете упор сейчас?"
            )
            msgs.append(HumanMessage(content=intro))
        else:
            if sess.phase == "intro":
                # After candidate introduction, smoothly go to tech questions
                next_q = sess.questions[sess.q_index] if sess.q_index < len(sess.questions) else None
                if next_q:
                    prompt = (
                            "Кандидат рассказал о себе. Ответь в 2–3 предложениях, отзеркаливая ключевые моменты, "
                            "и задай следующий технический вопрос: " + next_q
                    )
                else:
                    prompt = (
                        "Технические вопросы исчерпаны. Предложи кандидату задать вопросы о компании/команде/процессе."
                    )
                msgs.append(HumanMessage(content=f"{user_text}\n\nИнструкция интервьюера: {prompt}"))
            elif sess.phase == "tech":
                # Ask next technical question or move to candidate questions
                if sess.q_index < len(sess.questions) - 1:
                    next_q = sess.questions[sess.q_index + 1]
                    prompt = (
                            "Кратко поблагодари за ответ и задай следующий технический вопрос: " + next_q
                    )
                    msgs.append(HumanMessage(content=f"{user_text}\n\nИнструкция интервьюера: {prompt}"))
                else:
                    prompt = (
                        "Технические вопросы исчерпаны. Переведи разговор к вопросам кандидата о компании."
                    )
                    msgs.append(HumanMessage(content=f"{user_text}\n\nИнструкция интервьюера: {prompt}"))
            elif sess.phase == "candidate_q":
                prompt = (
                        "Поддерживай диалог, отвечай на вопросы о компании/вакансии, «легенда»: "
                        + sess.company_legend + ". Заверши интервью, когда вопросов больше нет."
                )
                msgs.append(HumanMessage(content=f"{user_text}\n\nИнструкция интервьюера: {prompt}"))
            else:
                msgs.append(HumanMessage(content=user_text))
        return msgs

    def _advance_phase(self, sess: InterviewSession):
        # Advance phase based on q_index
        if sess.phase == "intro":
            sess.phase = "tech"
        elif sess.phase == "tech" and sess.q_index >= len(sess.questions) - 1:
            sess.phase = "candidate_q"
        elif sess.phase == "candidate_q":
            sess.phase = "done"

    async def stream_reply(
            self, code: str, token: str, user_text: Optional[str]
    ) -> AsyncGenerator[str, None]:
        """Stream assistant reply text chunks using OpenAI streaming via LangChain.
        user_text=None indicates initial greeting turn initiated by the system.
        """
        sess = self.ensure_session(code, token)

        # Build new turn (human side)
        messages = self._build_prompt(sess, user_text)
        # Append human message(s) to history before calling LLM
        for m in messages:
            sess.history.add_message(m)

        # Stream assistant reply using full conversation history
        content_accum = ""
        async for chunk in self.llm.astream(list(sess.history.messages)):
            text = getattr(chunk, "content", None)
            if not text:
                continue
            content_accum += text
            yield text

        # Save assistant message to history
        if content_accum:
            sess.history.add_message(AIMessage(content=content_accum))

        # Update state (advance question index/phase heuristically)
        if user_text is None:
            sess.phase = "intro"
        else:
            if sess.phase in ("intro", "tech") and sess.q_index < len(sess.questions):
                sess.q_index += 1
            self._advance_phase(sess)

    def get_next_question(self, code: str, token: str) -> Optional[str]:
        sess = self.ensure_session(code, token)
        if sess.q_index < len(sess.questions):
            return sess.questions[sess.q_index]
        return None
