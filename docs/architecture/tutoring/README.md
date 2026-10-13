# Архитектура Tutoring

Эта папка содержит согласованную архитектуру и спецификации Tutoring. На 2026-10-13 завершён утверждённый MVP: публичный Java API, Domain, Application, Infrastructure/HTTP этапа 5 и объединённые этапы 6–7 — регистрация с профилями, добавление второй роли, /me и сквозные проверки. Миграции V1–V8 применены; итоговый прогон backend: 450 тестов без ошибок и пропусков.

## С чего начать

1. [Итоговая архитектура](TUTORING_ARCHITECTURE.md) — каноническая сводка границ, слоёв, контрактов, хранения, интеграций и критериев готовности.
2. [Этап 1: границы и сценарии](tutoring-stage-1-boundaries-and-use-cases.md) — предметные решения.
3. [Этап 2: публичный Java API](TUTORING_STAGE_2_PUBLIC_API.md) — межмодульные контракты.
4. [Этап 3: Domain](TUTORING_STAGE_3_DOMAIN.md) — агрегаты, операции и инварианты.
5. [Этап 4: Application](TUTORING_STAGE_4_APPLICATION.md) — структура и координация сценариев.
6. [Подробные сценарии Application](TUTORING_APPLICATION_SCENARIOS.md) — единый документ по Subject, Profile, Idempotency, Integration, Invitation и Relationship.
7. [Этап 5: Infrastructure и Presentation](TUTORING_STAGE_5_INFRASTRUCTURE_AND_PRESENTATION.md) — PostgreSQL, адаптеры, HTTP и проверки.

## Правило чтения

Более поздние согласованные решения уточняют ранние этапы. Ученик может не выбирать предметы. Дата рождения хранится только в Identity и видна владельцу в данных аккаунта; активный учебный профиль публично показывает вычисленный возраст, указанный контактный email и обычные поля без связи. Контактный email профиля не требует подтверждения. При регистрации по приглашению email адресата зафиксирован в форме; сервер привязывает приглашение только после подтверждения этого account email. Принятие остаётся отдельным действием.

Пользователь 2026-10-13 отложил UnlinkStudentWorkflow, HTTP-отвязку и StudentCardQueryFacade со статистикой до проектирования/реализации Scheduling. Они не входят в завершённый MVP и не считаются реализованными. [Отчёт этапов 6–7](TUTORING_STAGE_6_7_IMPLEMENTATION_REPORT.md), [отчёт этапа 5](TUTORING_STAGE_5_IMPLEMENTATION_REPORT.md), [Workflows HTTP](../../api/workflows.openapi.json), [Tutoring HTTP](../../api/tutoring.openapi.json).
