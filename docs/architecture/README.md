# Архитектура модуля Identity

Этот каталог — текстовая спецификация модуля `identity`, которую разработчик может читать без Visual Paradigm. Она дополняет UML-модель и фиксирует пакеты, классы, интерфейсы, зависимости, схему хранения и основные архитектурные решения.

## Состав комплекта

- [Реестр решений и открытых вопросов](../decisions/README.md) — статусы принятых решений, временных настроек и вопросов, которые нельзя закрывать молча.
- [Интерактивный просмотр](index.html) — раскрывающееся дерево пакетов, поиск, список классов и диаграмм.
- [Исходный отчёт Visual Paradigm](visual-paradigm-report.html) — стандартный интерфейс Project Publisher.
- [Структура пакетов](catalog/packages.md) — полное дерево пакетов и ответственность каждого пакета.
- [Каталог классов](catalog/classes.md) — расположение, назначение, поля и основные операции классов, моделей, событий и исключений.
- [Каталог интерфейсов](catalog/interfaces.md) — публичный API, входные и выходные порты, сигнатуры операций и реализации.
- [Зависимости](catalog/dependencies.md) — разрешённые связи между слоями, пакетами и элементами модели.
- [Схема данных](catalog/database.md) — таблицы PostgreSQL, ограничения, индексы и правила преобразования.
- [Identity — общий вид](diagrams/identity-overview.md) — контекст модуля и направления зависимостей.
- [Identity Domain](diagrams/identity-domain.md) — агрегаты, value objects, события и исключения.
- [Identity Application](diagrams/identity-application.md) — use cases, application services и output ports.
- [Identity Infrastructure](diagrams/identity-infrastructure.md) — persistence, security, messaging и time.
- [Identity Persistence — jOOQ](diagrams/identity-persistence.md) — adapters, mappers, repositories и граница jOOQ types.
- [Identity Security — JWT and Tokens](diagrams/identity-security.md) — выпуск, проверка и ротация токенов.
- [Identity Messaging and Time](diagrams/identity-messaging-time.md) — Notifications, события и Clock.
- [Identity — Layers and Dependencies](diagrams/identity-layers.md) — разрешённые направления между слоями.
- [Передача проекта](handoff-checklist.md) — что сохранить и экспортировать из Visual Paradigm.

## Технологический контекст

- Java 21;
- Spring Boot и Spring Modulith;
- Spring MVC и Spring Security Resource Server;
- PostgreSQL;
- jOOQ и Flyway;
- Gradle Kotlin DSL;
- SMTP и шаблоны писем принадлежат модулю Notifications;
- JPA в модуле Identity не используется.

## Границы модуля

Модуль построен по принципам портов и адаптеров:

```text
presentation → application → domain
                   ↑
            infrastructure
```

`identity.api` — единственный публичный пакет модуля. Остальные пакеты являются внутренними деталями реализации. Другие бизнес-модули обращаются к Identity через `identity.api.IdentityQuery` и получают интеграционные события из `identity.api.event`.

Infrastructure реализует выходные порты Application. Application и Domain не импортируют Infrastructure. Presentation вызывает только входные порты Application.

## Главные решения

1. `User` и `EmailVerification` являются отдельными aggregate roots.
2. Текущий и ожидающий подтверждения email принадлежат агрегату `User`, но сохраняются в отдельной таблице `identity_user_emails`.
3. Конкурентное резервирование email защищено ограничением `UNIQUE(email)` в PostgreSQL.
4. Access token — короткоживущий JWT RS256. Входящий JWT проверяет Spring Security без обращения к базе на каждый запрос.
5. Refresh token и verification token выдаются как случайные URL-safe значения; в базе хранится только SHA-256 hash в lowercase hex-формате.
6. Refresh token передаётся в cookie с `HttpOnly`, `SameSite=Lax`, ограниченным путём `/api/v1/auth` и `Secure` в production.
7. После подтверждения email `ConfirmEmailUseCase` возвращает `AuthenticationResult`, а контроллер устанавливает refresh cookie.
8. Отправка email выполняется через публичный API Notifications. Identity не импортирует внутренние пакеты Notifications.
9. Внутренние интеграционные события публикуются после успешной фиксации транзакции.
10. Ошибки контроллеров и Spring Security преобразуются в единый формат `ApiError`.
11. Refresh token загружается через `findByTokenHashForUpdate`: запись возвращается независимо от состояния и блокируется до завершения транзакции.
12. Token family отзывается по паре `userId + familyId`, соответствующей составному индексу `(user_id, family_id)`.

## Как использовать комплект

Разработчику достаточно передать весь каталог `docs/architecture`. Рекомендуемый порядок чтения:

1. прочитать реестр решений и открытых вопросов;
2. прочитать этот файл;
3. открыть общий вид;
4. изучить структуру пакетов;
5. открыть каталог интерфейсов;
6. открыть каталог классов;
7. проверить зависимости и схему данных;
8. изучить подробные диаграммы слоёв;
9. использовать `index.html` для навигации по UML, учитывая отмеченный в реестре долг синхронизации.

Файл Visual Paradigm следует сохранить рядом под именем `architecture.vpp`, когда в нём будут записаны все несохранённые изменения. Текстовые документы остаются доступными без Visual Paradigm, а `.vpp` позволяет продолжить редактирование исходной UML-модели.

## Критерии готовности реализации

- каждый output port имеет одну infrastructure-реализацию;
- jOOQ records не покидают `infrastructure.persistence`;
- открытые пароли и открытые refresh/verification tokens не сохраняются;
- JWT проверяется локально по публичному ключу;
- SMTP и шаблоны отсутствуют в Identity;
- ошибки Spring Security имеют тот же контракт `ApiError`, что и ошибки REST-контроллеров;
- нет горизонтальных зависимостей между `service.account`, `service.verification` и `service.authentication`;
- ArchUnit и Spring Modulith подтверждают описанные границы пакетов и модулей.
