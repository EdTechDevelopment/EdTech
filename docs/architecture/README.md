# Архитектура backend EdTech

Этот каталог содержит текстовые спецификации backend и экспорт UML-модели. Текстовые документы можно читать без Visual Paradigm; файл `architecture.vpp` нужен для редактирования исходной модели.

## Актуальные спецификации

- [Общая архитектура backend](BACKEND_ARCHITECTURE.md) — модули, границы, технологии, межмодульные workflow и правила разработки.
- [Полная архитектура Identity](IDENTITY_ARCHITECTURE.md) — публичный API, domain, application, presentation, infrastructure, хранение и безопасность.
- [Tutoring: границы и сценарии этапа 1](TUTORING_STAGE_1_BOUNDARIES_AND_USE_CASES.md) — владение данными, профили, приглашения, связи и интеграция с Identity.
- [Изменения после исходной спецификации Tutoring](CHANGES_AFTER_TUTORING_STAGE_1.md) — принятые решения и список документов/контрактов, которые ими затронуты.

## Экспорт Visual Paradigm

- [Интерактивный просмотр](index.html) — раскрывающееся дерево пакетов, поиск, список классов и диаграмм.
- [Исходный отчёт Visual Paradigm](visual-paradigm-report.html) — стандартный интерфейс Project Publisher.
- [Структура пакетов](catalog/packages.md) — дерево пакетов и их ответственность.
- [Каталог классов](catalog/classes.md) — классы, модели, события и исключения.
- [Каталог интерфейсов](catalog/interfaces.md) — публичный API, входные и выходные порты.
- [Зависимости](catalog/dependencies.md) — связи между слоями, пакетами и элементами модели.
- [Identity — общий вид](diagrams/identity-overview.md).
- [Identity Domain](diagrams/identity-domain.md).
- [Identity Application](diagrams/identity-application.md).
- [Identity Infrastructure](diagrams/identity-infrastructure.md).
- [Identity Persistence — jOOQ](diagrams/identity-persistence.md).
- [Identity Security — JWT and Tokens](diagrams/identity-security.md).
- [Identity Messaging and Time](diagrams/identity-messaging-time.md).
- [Identity — Layers and Dependencies](diagrams/identity-layers.md).
- [Передача проекта](handoff-checklist.md).

> `architecture.vpp`, `index.html`, `visual-paradigm-report.html`, `catalog/*` и `diagrams/*` отражают UML-снимок до решений от 13 сентября 2026 года. Пока модель не обновлена и повторно не экспортирована, источниками истины являются четыре актуальные спецификации выше.

## Технологический контекст

- Java 21;
- Spring Boot и Spring Modulith;
- Spring MVC и Spring Security Resource Server;
- PostgreSQL;
- jOOQ и Flyway;
- Gradle Kotlin DSL;
- SMTP и шаблоны писем принадлежат Notifications;
- JPA в Identity не используется.

## Границы модулей

Внутри бизнес-модуля используется направление:

```text
presentation → application → domain
                   ↑
            infrastructure
```

Другие модули используют только публичный пакет `<module>.api`. Они не импортируют чужие application-порты, domain-модели, infrastructure-классы и не читают чужие таблицы.

Для Identity публичными являются:

```text
identity.api.query.*
identity.api.command.registration.*
identity.api.command.role.*
identity.api.model.*
identity.api.event.*
```

## Главные решения

1. Identity владеет аккаунтом: `email`, `pendingEmail`, `firstName`, `lastName`, `birthDate`, паролем, статусом и ролями.
2. Tutoring владеет `TeacherProfile` и `StudentProfile`. Каждый профиль хранит собственные `displayName` и `contactEmail`; они могут совпадать с данными аккаунта, но имеют отдельный жизненный цикл.
3. При регистрации создаётся минимум одна образовательная роль и ровно один профиль для каждой выбранной роли.
4. `RegistrationWorkflow` координирует создание пользователя и профилей в общей транзакции PostgreSQL. Identity знает только роли и не знает о профилях.
5. `RoleOnboardingWorkflow` атомарно добавляет вторую роль в Identity и соответствующий профиль в Tutoring.
6. Межмодульные write-сценарии передают один `operationId` публичным command API. Повторная обработка должна быть идемпотентной.
7. Межмодульные составные чтения выполняют query facades. Точная дата рождения доступна через ограниченный `IdentityPersonalDataQuery` после проверки связи и видимости.
8. Identity публикует отдельное событие `AccountEmailVerifiedEvent`; Tutoring идемпотентно использует его для контактов и ожидающих приглашений.
9. Текущий и ожидающий подтверждения account email принадлежат агрегату `User`; конкурентное резервирование защищено `UNIQUE(email)` в PostgreSQL.
10. Access token — короткоживущий JWT RS256. Входящий JWT проверяется локально без запроса в базу на каждый вызов.
11. Refresh и verification tokens выдаются как случайные URL-safe значения; в базе хранится только SHA-256 hash.
12. Notifications сохраняет delivery request в общей транзакции, а SMTP выполняет после успешного commit.

## Что ещё нужно синхронизировать

Перед реализацией HTTP-слоя требуется отдельно утвердить и обновить OpenAPI:

- registration request: `birthDate`, минимум один профиль и соответствие `roles ↔ profiles`;
- удаление `birthDate` из `StudentProfile`;
- `displayName`, `contactEmail` и состояние подтверждения в обоих профилях;
- endpoint и DTO добавления второй роли;
- endpoint и DTO изменения/подтверждения профильного email;
- составной ответ `/me` через `MeQueryFacade`.

До этой синхронизации старые OpenAPI-схемы не следует использовать для генерации кода этих сценариев.

## Рекомендуемый порядок чтения

1. `BACKEND_ARCHITECTURE.md`.
2. `IDENTITY_ARCHITECTURE.md`.
3. `TUTORING_STAGE_1_BOUNDARIES_AND_USE_CASES.md`.
4. `CHANGES_AFTER_TUTORING_STAGE_1.md`.
5. UML-экспорт для деталей предыдущей модели.

После переноса новых решений в Visual Paradigm следует сохранить `architecture.vpp` и заново опубликовать HTML, каталог и диаграммы.

## Критерии архитектурной проверки

- Spring Modulith и ArchUnit подтверждают разрешённые зависимости;
- роль и профиль создаются или откатываются вместе;
- повтор межмодульной команды с тем же `operationId` не создаёт дубликаты;
- Identity не импортирует Tutoring, а Tutoring не читает данные Identity напрямую;
- jOOQ records не выходят из persistence;
- открытые пароли и токены не сохраняются;
- JWT проверяется локально по публичному ключу;
- SMTP и шаблоны принадлежат Notifications;
- ошибки Spring Security и REST-контроллеров имеют общий `ApiError`.
