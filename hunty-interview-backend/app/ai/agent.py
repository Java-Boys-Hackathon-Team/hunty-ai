import os
import asyncio
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
from .planner import InterviewPlan, PlanItem, generate_plan

SYSTEM_PROMPT = (
    "Ты — опытный технический интервьюер по Java (Senior уровень). "
    "Всегда говори от лица интервьюера, а не кандидата. "
    "Веди интервью в доброжелательной и профессиональной манере. "
    "Цели: оценить архитектурное мышление, глубокое понимание JVM/JDK/Concurrency, Spring, SQL/NoSQL, распределённые системы, тестирование и DevOps-практики. "
    "Поддерживай диалог, отвечай на вопросы кандидата, а также задавай свои. "
    "Не перебивай кандидата: если кандидат говорит — дождись, пока он закончит (это обрабатывает голосовой шлюз). "
    "Коротко формулируй вопросы (1–2 предложения). В одном ответе — ровно один вопрос. Не повторяй формулировку вопроса дважды. "
    "После вступления и рассказа кандидата о себе переходи к техническому блоку. В конце предложи кандидату задать вопросы о компании и вакансии. "
)


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
    plan: List[PlanItem] = field(default_factory=list)
    q_index: int = 0
    followup_index: int = 0  # 0..n within current question
    asked_main: bool = False  # whether main question for q_index has been asked
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
            # Seed with system prompt
            sess.history.add_message(SystemMessage(content=SYSTEM_PROMPT))
            self.sessions[sid] = sess
            # Schedule background plan generation
            try:
                loop = asyncio.get_event_loop()
                loop.create_task(self._generate_plan_async(sid))
            except Exception:
                pass
        else:
            sess = self.sessions[sid]
            if candidate_name:
                sess.candidate_name = candidate_name
        return sess

    def _build_prompt(self, sess: InterviewSession, user_text: Optional[str]) -> List:
        msgs: List = []
        # We add only the new turn instruction; full history is passed separately
        if user_text is None:
            # Ask model to produce an interviewer greeting (not candidate speech)
            instruction = (
                f"Сгенерируй короткое приветствие от лица интервьюера (1–2 предложения) для кандидата {sess.candidate_name or ''}. "
                f"Коротко упомяни контекст компании: {sess.company_legend}. "
                "Попроси кандидата кратко рассказать о себе и релевантном опыте. "
                "Говори от лица интервьюера."
            )
            msgs.append(HumanMessage(content=instruction))
        else:
            if sess.phase == "intro":
                # Move to first technical question
                if sess.plan and len(sess.plan) > 0:
                    q = sess.plan[0].question
                    instruction = (
                        "Кандидат кратко рассказал о себе. Коротко отзеркаль ключевые моменты (1–2 предложения) "
                        "и задай первый технический вопрос. В одном ответе — ровно один вопрос, без повторов.\n"
                        f"Вопрос: {q}"
                    )
                else:
                    instruction = (
                        "Плана вопросов ещё нет. Вежливо сообщи об этом и предложи кандидату задать вопросы о компании/команде."
                    )
                msgs.append(HumanMessage(content=f"{user_text}\n\nИнструкция интервьюера: {instruction}"))
            elif sess.phase == "tech":
                # Ask main or follow-up or advance to next main
                item = sess.plan[sess.q_index] if (sess.plan and sess.q_index < len(sess.plan)) else None
                if item is None:
                    instruction = (
                        "План вопросов пуст. Вежливо перейди к блоку вопросов кандидата о компании."
                    )
                else:
                    if not sess.asked_main:
                        instruction = (
                            "Поблагодари за ответ и задай основной технический вопрос по текущей теме. "
                            "Ровно один вопрос, без дублирования формулировки.\n"
                            f"Вопрос: {item.question}"
                        )
                    else:
                        if sess.followup_index < len(item.followups):
                            fu = item.followups[sess.followup_index]
                            instruction = (
                                "Задай один уточняющий follow-up по теме текущего вопроса. Коротко.\n"
                                f"Follow-up: {fu}"
                            )
                        else:
                            # advance to next main question if exists
                            if sess.q_index + 1 < len(sess.plan):
                                next_q = sess.plan[sess.q_index + 1].question
                                instruction = (
                                    "Коротко поблагодари за ответ и задай следующий технический вопрос. "
                                    "Ровно один вопрос, без повторов.\n"
                                    f"Вопрос: {next_q}"
                                )
                            else:
                                instruction = (
                                    "Технические вопросы исчерпаны. Вежливо предложи кандидату задать вопросы о компании/команде/процессах."
                                )
                msgs.append(HumanMessage(content=f"{user_text}\n\nИнструкция интервьюера: {instruction}"))
            elif sess.phase == "candidate_q":
                instruction = (
                    "Поддерживай диалог и отвечай на вопросы о компании/вакансии. "
                    f"Легенда: {sess.company_legend}. Заверши интервью, когда вопросов больше нет."
                )
                msgs.append(HumanMessage(content=f"{user_text}\n\nИнструкция интервьюера: {instruction}"))
            else:
                msgs.append(HumanMessage(content=user_text))
        return msgs

    def _advance_phase(self, sess: InterviewSession):
        # Minimal phase advance used in candidate questions
        if sess.phase == "candidate_q":
            # stay until the conversation naturally ends
            return

    async def stream_reply(
            self, code: str, token: str, user_text: Optional[str]
    ) -> AsyncGenerator[str, None]:
        """Stream assistant reply text chunks using OpenAI streaming via LangChain.
        user_text=None indicates initial greeting turn initiated by the system.
        """
        sess = self.ensure_session(code, token)
        sid = self._sid(code, token)

        # Ensure plan is ready when we need to ask tech questions
        if user_text is not None and sess.phase in ("intro", "tech") and not sess.plan:
            await self._generate_plan_async(sid)

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

        # Update deterministic state machine to avoid duplicates
        if user_text is None:
            # We have just greeted and asked to introduce themselves
            sess.phase = "intro"
            # reset indices; plan may still be generating in background
            sess.q_index = 0
            sess.followup_index = 0
            sess.asked_main = False
            return

        if sess.phase == "intro":
            # We just asked the first main question (index 0)
            if sess.plan:
                sess.phase = "tech"
                sess.q_index = 0
                sess.followup_index = 0
                sess.asked_main = True
            else:
                # no plan — go to candidate questions block
                sess.phase = "candidate_q"
            return

        if sess.phase == "tech":
            if not sess.plan or sess.q_index >= len(sess.plan):
                sess.phase = "candidate_q"
                return
            item = sess.plan[sess.q_index]
            if not sess.asked_main:
                # We just asked main question for current item
                sess.asked_main = True
                sess.followup_index = 0
                return
            # After main asked: either asked a follow-up or moved to next main
            if sess.followup_index < len(item.followups):
                # We just asked a follow-up
                sess.followup_index += 1
                return
            # No followups remain => we just asked next main or switched to candidate questions
            if sess.q_index + 1 < len(sess.plan):
                sess.q_index += 1
                sess.asked_main = True  # we have just asked the next main
                sess.followup_index = 0
            else:
                sess.phase = "candidate_q"
            return

        # candidate_q/done stay as-is

    def get_next_question(self, code: str, token: str) -> Optional[str]:
        sess = self.ensure_session(code, token)
        if not sess.plan:
            return None
        if sess.phase == "tech":
            item = sess.plan[sess.q_index] if sess.q_index < len(sess.plan) else None
            if not item:
                return None
            if not sess.asked_main:
                return item.question
            if sess.followup_index < len(item.followups):
                return item.followups[sess.followup_index]
            if sess.q_index + 1 < len(sess.plan):
                return sess.plan[sess.q_index + 1].question
            return None
        if sess.phase == "intro":
            return sess.plan[0].question if sess.plan else None
        return None

    async def _generate_plan_async(self, sid: str) -> None:
        """Generate and store interview plan for a session if missing."""
        try:
            sess = self.sessions.get(sid)
            if not sess or (sess.plan and len(sess.plan) > 0):
                return
            plan = await generate_plan(self.llm)
            # Store as list of PlanItem
            sess.plan = list(plan.items)
            # ensure indices are sane
            sess.q_index = 0
            sess.followup_index = 0
            sess.asked_main = False
        except Exception:
            # leave plan empty if failed; agent will gracefully handle
            pass
