# Tutoring — отчёт объединённых этапов 6–7

Дата: 2026-10-13. Ветка: feature/tutoring. Выполнен согласованный MVP без Scheduling. Код оставлен для ревью без commit/push.

## 1. Результат

| Сценарий | Что реализовано |
|---|---|
| Регистрация | POST /api/v1/auth/register принимает аккаунт, роли и профиль на каждую роль; всё сохраняется атомарно. Ответ 202 с email/verificationExpiresAt, без токенов. |
| Вторая роль | POST /api/v1/me/roles/teacher или /student создаёт роль и новый профиль в одной транзакции; ответ 201 MeResponse. |
| Свои данные | GET /api/v1/me возвращает собственный аккаунт и оба соответствующих профиля. |
| Повтор запроса | Один ключ и эквивалентные данные возвращают сохранённый результат; другой payload/контекст/сценарий дают 409. |
| Приглашение | Проверен полный путь: неизвестный адрес → регистрация с профилем → подтверждение → доставка события → входящее приглашение → отдельный accept → связь. |

UnlinkStudentWorkflow, HTTP-отвязка и составная карточка со статистикой отложены пользователем до проектирования Scheduling. Его Java-код не добавлялся; работающих lesson cancellation/guard/statistics в этом MVP нет. Внутренняя команда удаления связи Tutoring из прежнего этапа сохранена.

## 2. Регистрация

Основной сервис: workflows/application/registration/service/RegistrationWorkflow.java.

Порядок:

1. Проверить точное соответствие ролей и данных профилей.
2. Получить защищённый fingerprint запроса.
3. В общей write-транзакции заблокировать operationId и проверить journal.
4. При replay вернуть прежний результат.
5. Через IdentityRegistrationGateway вызвать существующий RegisterUserUseCase.
6. Получить userId и через TutoringProfileCreationCommands создать один или два профиля.
7. Сохранить результат операции и commit.

Любая ошибка откатывает journal, User/roles, verification, заявку письма и профили. Тест проверяет и сбой после фактической вставки обоих профилей.

RegisterUserUseCase остался в identity/application/port/in/account. RegisterUserService продолжает создавать аккаунт и verification; его бизнес-путь не дублировался. Из AuthController убран только HTTP register; login/refresh/logout остались. Удалены старые account-only HTTP RegisterRequest и VerificationPendingResponse, связанные mapper-методы; новые HTTP DTO принадлежат Workflows.

## 3. Добавление роли

RoleOnboardingWorkflow — один сервис с двумя понятными типизированными методами и входными портами.

Сначала он читает актуальный статус аккаунта через IdentityQuery и требует ACTIVE. Затем journal/replay, проверка новой роли, IdentityRoleGateway.addRole и создание соответствующего профиля Tutoring. MeQueryFacade собирает результат, который сохраняется для повторов в той же транзакции.

Ошибка профиля откатывает роль. Первый профиль не меняется. Разные конкурентные ключи для одной роли дают один успех и один конфликт; одинаковый ключ даёт один результат. После 201 клиент обновляет JWT существующим refresh; сквозной тест проверяет появление обеих ролей в новом JWT.

## 4. GET /me и приватные account-данные

MeQueryFacade/GetMeUseCase находятся в Workflows. Контроллер получает actorUserId только из principal. Target userId в body/query не выбирает другого владельца.

В Identity добавлены OwnAccountQuery, OwnAccountView и OwnAccountQueryService. Это отдельное чтение account-данных владельца: birthDate, current/pending email, имена, роли, статус, даты. Tutoring, Scheduling и Notifications этот контракт не используют; ArchUnit проверяет ограничение.

Account-данные находятся в MeResponse.user. Профили содержат возраст и пользовательский contactEmail, без birthDate/pendingEmail аккаунта. При отсутствии роли профиль null. Роль без профиля даёт PROFILE_INCONSISTENCY, а не успешный частичный /me.

PATCH /me сохраняет существующий Identity-контракт и ответ UserResponse.

RegistrationGatewayAdapter/RoleGatewayAdapter переводят ожидаемые внутренние ошибки в публичный IdentityCommandException. Workflows не импортирует Identity Domain/Application и не разбирает текст доменных исключений.

## 5. OPEN-025: журнал повторов

V8__create_workflow_operations.sql создаёт workflows_operations. Миграция применена и валидирована Flyway; отдельная jOOQ-генерация Workflows добавлена в Gradle.

Поля: operation UUID (глобальный PK), тип REGISTER/ADD_TEACHER_ROLE/ADD_STUDENT_ROLE, контекст email/actor, HMAC и версия, JSON schema version, JSON результата, createdAt/completedAt. Нет FK на таблицы других модулей.

JooqWorkflowOperationRepository реализует конкретные методы регистрации/onboarding. Перед чтением получает PostgreSQL transaction advisory lock по operationId, включая первый запрос при отсутствующей строке. Запись и бизнес-изменения входят в общую транзакцию. Незавершённый результат не коммитится; ошибка удаляет резервирование откатом.

HmacWorkflowFingerprint использует HMAC-SHA256 с отдельным ключом. Канонизация нормализует email/имена и сортирует роли/предметы. Порядок contactDetails, пароль и различие null/пустого описания значимы. Raw password/request/token в журнал не записываются.

REGISTER сохраняет только email/verificationExpiresAt. Onboarding сохраняет MeResult для воспроизведения первоначального 201. Этот технический снимок включает account/profile данные уже выданного владельцу ответа, включая account birthDate. Он не используется как источник актуальных данных, не попадает в таблицы профилей и не передаётся другим пользователям. GET /me читает Identity/Tutoring заново; replay 201 не обещает обновлённые поля после последующих изменений профиля.

Версии равны 1. Декодирование выполняется в известные RegistrationOutcome/MeResult без Class.forName и generic результата в публичном порте. Автоочистка не включена по согласованному MVP-решению. OPEN-025 закрыт для этого объёма.

Локальный backend/.local/keys/workflows-fingerprint.key создан и игнорируется Git. Скрипт backend/scripts/New-LocalWorkflowKey.ps1 создаёт его на новом компьютере и сохраняет существующий. В другом окружении используется WORKFLOWS_FINGERPRINT_KEY как путь Spring Resource. Ключ должен сохраняться с журналом; ротация старых версий ключа не реализована.

## 6. HTTP и архитектура

Входные порты и команды/result находятся в Application; DTO, mapper, controllers, errors и body filter — в Presentation. SQL/HMAC/Spring wiring — в Infrastructure. Workflows координирует только публичные API модулей.

Добавлены свои HTTP DTO /me и регистрации/onboarding. Principal не принимается в JSON. Bean Validation проверяет транспортные поля; бизнес-формат email/предметов/профилей остаётся у модулей. Body limit POST — workflows.http.max-request-bytes, default 1 MiB, превышение даёт 413. Ошибки и логи не выводят raw password/payload/fingerprint.

OpenAPI Workflows: docs/api/workflows.openapi.json, 4 реализованных операции; ссылки на профильные schemas Tutoring. Общий scheduling.openapi.json маркирован целевым, со ссылкой на актуальные спецификации. Frontend contracts.ts исправлен по фактическим invitation accept/reject, userId summary, student/teacher полям relationship и code в fieldErrors. Добавлены aliases для onboarding.

Обновлены статус Tutoring, Workflows, Identity, Scheduling, backend/API/frontend документы и реестр. Старые утверждения публичной даты рождения/подтверждения contactEmail в README исправлены. Исторические UML и будущая Scheduling-спецификация сохраняются как исторические/целевые материалы.

## 7. Проверки и исправленные ошибки

Итоговый gradlew --offline --console=plain test: **450 тестов, 98 suites, 0 ошибок, 0 пропусков**.

Новые проверки:

- WorkflowsFlowIntegrationTest (16): роли/профили, 202 без токенов, missing/extra profile, rollback, replay/изменённый password/email, own /me, onboarding/refresh, сохранение первого профиля, пустые student subjects, отказ suspended, нарушение инварианта, ключ/body limit/подмена actor, полный приглашательный путь и другая подтверждённая почта.
- WorkflowsConcurrencyIntegrationTest (4): одновременная регистрация/replay, одинаковый onboarding key, разные ключи одной роли, rollback после вставки двух профилей.
- HmacWorkflowFingerprintTest (4): нормализация и порядок, значимость password/контактов/null, влияние секретного ключа, отказ короткого ключа.
- WorkflowsArchitectureTest (5): публичные межмодульные контракты, SQL/слои, изоляция приватного account API, отсутствие Scheduling-зависимости.

Существующие Identity HTTP-тесты обновлены обязательными ключами и профилями. Сохранены проверки login/refresh/logout/verification/PATCH, Tutoring и Notifications. PostgreSQL настоящий; HTTP MockMvc с настоящими JWT.

Исправлены две ошибки тестового окружения: SecurityTestController случайно занимал GET /me в общем контексте — помечен TestComponent и явно импортирован лишь в Security MVC test. В новом refresh-тесте Cookie header заменён объектом Cookie, который читает MockMvc @CookieValue.

После проверки тестовых аккаунтов, профилей и workflows_operations в локальной БД нет. V1–V8 успешны. Git diff --check прошёл. Существовавший staged diff этапа 5 сохранён; новый код не staged/committed/pushed автоматически.

## 8. Что осталось вне завершённого MVP

- Scheduling и согласованная с ним отвязка/статистика — следующий отдельный модуль.
- Frontend страницы/формы и блокировка email в UI приглашения — frontend; серверная проверка владения адресом уже работает.
- Утверждённые ранее открытые эксплуатационные вопросы остаются в реестре; runtime retention/key rotation в этой работе не реализованы.

Backend Tutoring завершён в явно согласованном пользователем объёме без Scheduling.

