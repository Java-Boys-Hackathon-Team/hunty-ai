import asyncio
from typing import List, Optional
from pydantic import BaseModel, Field
from langchain_openai import ChatOpenAI
from langchain_core.messages import SystemMessage, HumanMessage


class PlanItem(BaseModel):
    topic: str = Field(..., description="Short topic name, e.g., 'JVM & GC'")
    question: str = Field(..., description="Main concise technical question (1 sentence)")
    followups: List[str] = Field(default_factory=list, description="2–4 concise probing follow-up questions")


class InterviewPlan(BaseModel):
    items: List[PlanItem] = Field(default_factory=list)


async def generate_plan(llm: ChatOpenAI, *, role: str = "Senior Java Backend Engineer", language: str = "ru") -> InterviewPlan:
    """Generate a dynamic interview plan (22–28 items) with follow-ups using structured output.
    Keeps everything in-memory. Uses LangChain structured output for robustness.
    """
    planner = llm.with_structured_output(InterviewPlan)

    sys = (
        "Ты помогаешь техническому интервьюеру сформировать план вопросов для собеседования. "
        "Нужны 22–28 основных технических вопросов для позиции: "
        f"{role}. "
        "Темы распределить по блокам: язык и JVM, память/GC, многопоточность и JMM, коллекции, I/O/сериализация, Spring (ядро, транзакции, web), базы данных (SQL/PostgreSQL, NoSQL), архитектура и распределённые системы, тестирование и CI/CD, безопасность и наблюдаемость. "
        "Каждый вопрос должен быть коротким (1 предложение) и проверять глубокое понимание. "
        "Для каждого вопроса добавь 2–4 уточняющих follow-up вопроса (короткие, точечные). "
        "Не включай нумерацию и лишние комментарии. Строго следуй схеме. "
    )

    human = (
        "Сформируй план из 22–28 пунктов. "
        "Важно: пиши на русском языке. В каждом пункте: topic, question, followups[]. "
        "Follow-ups — по 2–4 штуки, очень короткие."
    )

    # ainvoke returns pydantic object thanks to with_structured_output
    plan: InterviewPlan = await planner.ainvoke([SystemMessage(content=sys), HumanMessage(content=human)])

    # Safety: trim overly long plans, ensure followups size bounds
    max_items = 28
    min_items = 18
    items = plan.items[:max_items]
    # If model returned less, it's still acceptable; we just keep as-is.
    cleaned: List[PlanItem] = []
    for it in items:
        fus = [f.strip() for f in (it.followups or []) if f and f.strip()]
        # Keep 0–4 followups; ideally 2–4, but don't fail if missing
        if len(fus) > 4:
            fus = fus[:4]
        cleaned.append(PlanItem(topic=it.topic.strip(), question=it.question.strip(), followups=fus))

    return InterviewPlan(items=cleaned)