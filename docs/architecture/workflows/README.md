# Workflows

На 2026-10-13 реализован утверждённый MVP этапов 6–7: регистрация с профилями, добавление второй роли и собственный /me. Общий прогон backend содержит 450 успешных тестов. Workflows координирует публичные API модулей; каждый модуль владеет своими данными и SQL.

| Процесс | Реализация | HTTP |
|---|---|---|
| RegistrationWorkflow | Одна транзакция: journal → IdentityRegistrationGateway → TutoringProfileCreationCommands → результат. | POST /api/v1/auth/register, ключ обязателен, 202 VerificationPendingResponse. |
| RoleOnboardingWorkflow | Проверяет ACTIVE, добавляет роль через IdentityRoleGateway, создаёт профиль, собирает MeResponse; всё в одной транзакции. | POST /api/v1/me/roles/teacher или /student, ключ обязателен, 201 MeResponse. |
| MeQueryFacade | Читает собственный аккаунт и профили через публичные queries. Роль без профиля — ошибка целостности. | GET /api/v1/me, Bearer JWT. |
| UnlinkStudentWorkflow | Отложен пользователем до Scheduling; внутренняя команда Tutoring готова. | DELETE не реализован. |
| StudentCardQueryFacade | Статистика уроков требует Scheduling; отложена. Публичный полный профиль уже доступен через Tutoring. | Составной endpoint со статистикой не реализован. |

## Структура

- workflows.application.registration — RegisterAccountUseCase, RegistrationCommand/Outcome, RegistrationWorkflow.
- workflows.application.onboarding — типизированные команды/входные порты для двух ролей, один RoleOnboardingWorkflow.
- workflows.application.me — GetMeUseCase, MeResult, MeQueryFacade.
- workflows.application.idempotency — конкретные output ports fingerprint и journal.
- workflows.infrastructure — jOOQ, HMAC/configuration.
- workflows.presentation — свои DTO, mapper, controllers, errors и body limit.

Application вызывает публичные Java-контракты модулей. Presentation вызывает собственные входные порты Application; не обращается к gateways/repositories/Domain. Workflows не копирует бизнес-валидацию email/предметов/профилей и не читает чужие таблицы. RegisterUserUseCase остаётся внутренним входным портом Identity.

## Регистрация и токены

Роли точно соответствуют переданным профилям. Teacher требует хотя бы один предмет; Student допускает ноль. Ошибка любого шага откатывает аккаунт, роли, verification, заявку письма, профили и journal. Успех — 202 с email и verificationExpiresAt без токенов. Confirm Identity выдаёт access JWT и refresh cookie. Повтор регистрационной операции не создаёт новое письмо; resend выполняется отдельным запросом.

После успешного onboarding клиент вызывает существующий refresh, чтобы получить JWT с обновлёнными ролями. Старый профиль не изменяется.

## Собственный аккаунт

OwnAccountQuery возвращает отдельный OwnAccountView Identity только для доверенной сборки account-данных владельца. MeQueryFacade получает actorUserId из HTTP principal. Дата рождения и pendingEmail присутствуют в user; в профилях — вычисленный возраст и контактный email без подтверждения. Tutoring/Scheduling/Notifications не используют закрытый account-контракт; ограничение проверяется ArchUnit.

GET /me не принимает целевой userId. Null profile означает отсутствие соответствующей роли; роль без профиля даёт PROFILE_INCONSISTENCY. PATCH /me остаётся операцией Identity и возвращает UserResponse.

## OPEN-025: журнал и fingerprint

V8 создаёт workflows_operations: UUID operation_id (глобальный PK), тип REGISTER/ADD_TEACHER_ROLE/ADD_STUDENT_ROLE, context_key (нормализованный registration email либо actor UUID), fingerprint/version, result schema version, JSON result, createdAt/completedAt.

Перед чтением журнал получает PostgreSQL transaction advisory lock для UUID. Это сериализует и два первых запроса при отсутствии строки. Exact replay сравнивает тип, контекст и HMAC; несовпадение даёт 409 IDEMPOTENCY_CONFLICT. Незавершённая строка существует лишь внутри транзакции; ошибка откатывает её вместе с бизнес-изменениями.

HMAC-SHA256 подписывает канонический запрос. Email/имена нормализуются как в модулях, роли/предметы сортируются, порядок contactDetails и различие null/пустого описания сохраняются. Пароль включён в HMAC без изменения, но raw password/payload и токены в таблицу не попадают. В REGISTER JSON хранятся email и verificationExpiresAt. В onboarding JSON хранится снимок MeResult для того же 201 MeResponse, включая account/profile данные, уже выданные владельцу. Снимок не используется как источник актуальных данных; GET /me читает владельцев модулей. Очистка в MVP не включена.

Fingerprint и schema version равны 1. JSON читается только в известные RegistrationOutcome или MeResult; result class не выбирается из запроса или произвольного имени в БД.

## Локальная конфигурация

Ключ: backend/.local/keys/workflows-fingerprint.key, минимум 32 случайных байта, игнорируется Git. На новом компьютере выполнить ./backend/scripts/New-LocalWorkflowKey.ps1 из корня; существующий корректный ключ сохраняется. WORKFLOWS_FINGERPRINT_KEY задаёт путь Spring Resource в другом окружении. Ключ должен сохраняться вместе с журналом: его замена изменяет сравнение старых fingerprints. Ротация со старым ключом/версиями не реализована.

POST Workflows ограничены workflows.http.max-request-bytes, default 1048576 (1 MiB), превышение — 413. Ключ идемпотентности обязателен для регистрации/onboarding; actor не принимается в body.

[Реализованный OpenAPI](../../api/workflows.openapi.json), [итоговый отчёт](../tutoring/TUTORING_STAGE_6_7_IMPLEMENTATION_REPORT.md).
