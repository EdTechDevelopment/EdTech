# Tutoring — технический отчёт этапа 5

Дата: 2026-10-13. Ветка: feature/tutoring. Этап 5 завершён. Изменения оставлены для ревью без commit/push.

## 1. Что теперь работает

| Область | Реализация |
|---|---|
| Справочник | jOOQ-адаптер читает десять предметов, проверяет отдельные коды и набор одним запросом. |
| Профили | Teacher/Student adapters сохраняют обычные поля и выбранные предметы атомарно, читают одиночные и пакетные результаты. |
| Поиск | Публичные активные профили, предмет/возраст, до 20 коротких карточек, продолжение по подписанному cursor. Полный профиль читается отдельным GET. |
| Приглашения | Создание по account email, известный или ещё не зарегистрированный получатель, входящие/отправленные списки, принятие/отказ. |
| Связи | Создание пары при принятии, списки с профилями, доверенная команда удаления для будущего workflow. |
| Повторы | Сохранение результата операции в JSON, replay и конфликт payload; receipt Identity event. |
| Доставка | Durable outbox Identity и отдельная очередь приглашений Notifications с retry, expiry и claim. |
| HTTP | 14 операций под /api/v1/tutoring, свои DTO, ошибки, Bearer auth, CORS, ограничение тела. |

## 2. Хранение: 5.2–5.4

Основные файлы находятся в backend/src/main/java/io/github/edtechdevelopment/tutoring/infrastructure:

- subject/persistence — JooqSubjectRepositoryAdapter.
- profile/persistence/teacher и student — отдельные RepositoryAdapter, JooqRepository, PersistenceMapper и PersistenceData.
- invitation/persistence — StudentInvitationJooqRepository, mapper и adapter.
- relationship/persistence — JooqTeacherStudentRepositoryAdapter.
- idempotency/persistence — adapters command operations/Identity receipts и CommandResultJson.

Порты Application получают доменные модели и прикладные данные, а не jOOQ records. SQL остаётся в Infrastructure.

Чтение профиля и предметов использует один SQL-снимок с jOOQ multiset. Это избегает отдельного SQL на каждый профиль и несогласованного чтения родительской строки/предметов. При изменении сначала блокируется профиль, затем обновляются поля и выбор предметов. createdAt не обновляется. Пустые batch не выполняют SQL.

Поиск кандидатов использует индекс createdAt/userId и EXISTS по выбранному предмету. Возраст/ACTIVE приходят пакетно из Identity через публичный API; чужие таблицы Tutoring не читает. Полные профили загружаются только для карточек возвращаемой страницы. Проверка 21-го подходящего кандидата и повторное чтение lookahead на следующей странице — согласованное MVP-допущение.

Списки приглашений вычисляют статус на asOf в SQL до limit. Новые/поздно привязанные приглашения после snapshot не появляются на следующих страницах. respondedAt не раскрывается раньше момента ответа на snapshot. Список связей остаётся живым и не обещает исторический snapshot.

## 3. Повторы, блокировки и курсоры: 5.5–5.6

TransactionKeyLock получает PostgreSQL advisory lock по пространству имён и SHA-256 идентификатора. Для operationId/eventId lock получается до чтения журнала, поэтому работает и при ещё отсутствующей строке. Блокировки освобождаются с commit/rollback. Email lock применяется одинаково при создании и привязке приглашения.

CommandResultJson сохраняет конкретный результат: InvitationCreatedResult, InvitationAcceptedResult, InvitationRejectedResult или TeacherStudentRemovalResult. Тип определяется известным OperationType, проверяется version 1 и имя ожидаемого класса. Произвольный Class.forName не используется. JSON восстанавливает тот же результат после повторного запроса.

HmacPublicProfileCursorCodec, HmacInvitationCursorCodec и HmacRelationshipCursorCodec реализуют существующие порты. Общий SignedCursor подписывает данные HMAC-SHA256. Курсор привязан к своему виду, фильтрам/владельцу/направлению; подпись проверяется до repository. Курсор подписан, не зашифрован; дата рождения/email в нём не передаются.

Локально создан backend/.local/keys/tutoring-cursor.key. Git его игнорирует. Скрипт backend/scripts/New-LocalCursorKey.ps1 создаёт ключ на другом компьютере и сохраняет существующий. Конфигурация другого окружения задаётся TUTORING_CURSOR_SIGNING_KEY.

Общий Clock нормализован до микросекунд PostgreSQL, чтобы ответы/JSON replay и сохранённые timestamp совпадали.

## 4. Интеграции и миграция V7

IdentityAccountGatewayAdapter использует только identity.api. Listener AccountEmailVerifiedEvent переводит событие в команду Application. Уже привязанный recipient не меняется, даже если тот же адрес теперь подтверждён другим аккаунтом.

V7__add_tutoring_integration_delivery.sql добавляет две таблицы владельцев:

- identity_account_email_event_outbox — сохранённые подтверждения account email.
- notification_invitation_email_deliveries — заявки на письмо приглашения.

Девять таблиц Tutoring из V5 и справочник V6 не переделывались. jOOQ Identity/Notifications сгенерирован заново для новых собственных таблиц. Генерации и compileJava задан правильный порядок Gradle; обычная компиляция использует сохранённые generated sources без обязательной генерации/БД.

Identity сохраняет событие в транзакции подтверждения email. После commit диспетчер доставляет его синхронному listener в новой транзакции. publishedAt, receipt Tutoring и привязка фиксируются вместе. При сбое событие повторяется через десять секунд.

InvitationNotificationSenderAdapter вызывает новый публичный InvitationNotificationGateway. Enqueue входит в транзакцию создания приглашения; SMTP выполняется отдельно. Verification gateway Identity сохранил прежний контракт. Очередь поддерживает retry, постоянную ошибку, expiry, восстановление зависшего claim и запрет ack от старого worker. В терминальном статусе URL очищается.

Ссылка приглашения направлена на будущий frontend /invitations/{id}?email=... для зафиксированного регистрационного поля. Параметр не даёт права принятия; подтверждённый account email и отдельная команда accept обязательны.

## 5. HTTP и Spring wiring: 5.7–5.8

TutoringConfiguration связывает уже существующие сервисы Application, mapper и выходные порты. Доменные модели и сценарии сохранили свои пакеты.

Presentation состоит из subject/profile/invitation/relationship/error/request. Контроллеры вызывают входные порты Application. Собственные api.model, полученные как результат Application, переводятся mapper в отдельные HTTP DTO. Прямых вызовов доверенных api.command/api.query, Domain и repositories из HTTP нет.

Основные изменения security:

- subjects и публичные GET профилей/поиска доступны без login.
- Защищённые Tutoring-запросы требуют актуальный ACTIVE аккаунт.
- Create invitation/teacher PUT требуют TEACHER; student PUT — STUDENT.
- Incoming/reject доступны привязанному аккаунту до StudentProfile.
- Accept требует StudentProfile в Application.
- Actor берётся только из principal; HTTP DTO не принимают userId владельца.
- CORS разрешает PUT и Idempotency-Key.
- Тело POST/PUT ограничено tutoring.http.max-request-bytes: 1 MiB по умолчанию, превышение — 413.

photoUrl реализован по явному ответу пользователя: абсолютная ссылка http/https с хостом, null очищает, пустая строка ошибочна. OPEN-028 закрыт.

HTTP не зависит от InvitationStatus Domain: статусы результатов Application передаются строками. При неверных данных ответ не содержит email/payload/fingerprint; unexpected logs содержат requestId и класс ошибки.

Точный реализованный контракт: [tutoring.openapi.json](../../api/tutoring.openapi.json). Обновлены документы архитектуры, frontend/API, Identity/Notifications и реестр решений.

## 6. Проверки

Выполнен gradlew --offline --console=plain test: **421 тест, 94 test suites, 0 ошибок, 0 пропусков**. Проверены существующие Identity/Notifications и новые компоненты Tutoring. Сборка distributable/bootJar не запускалась.

Новые тесты:

- TutoringInfrastructureIntegrationTest: PostgreSQL roundtrip профилей/предметов, сохранение createdAt, HTTP карточки/полный профиль/возраст, страницы без дублей при одинаковом времени, cursor/filter/type, фото, пустые student subjects, unknown subjects, роли/статус/anonymous, подмена actor, CORS/413, replay, recipient binding, incoming до onboarding, reject/accept и связь, смена владельца email, outbox, claim fencing, snapshot/status before limit.
- TutoringConcurrencyIntegrationTest: два одинаковых operationId без готовой строки; два одновременных accept; rollback приглашения/queue/operation при enqueue failure; rollback receipt/binding/outbox publication и последующий retry.
- TutoringCursorCodecTest: подпись, другой ключ, подмена, владелец, направление, фильтры и вид профиля.
- ProcessInvitationEmailDeliveriesServiceTest: содержание письма, успех, временная/постоянная SMTP-ошибка, now == expiresAt.
- TutoringLayerArchitectureTest: Domain/API/Application/Presentation boundaries. Существующий ModularityTests проверяет межмодульные границы.

После оформления Java-файлов выполнена повторная компиляция и проверка архитектуры. Spring-контекст приложения успешно поднимался в интеграционных тестах. HTTP проверялся MockMvc с настоящими подписанными access JWT; живой браузер/frontend на этом этапе не реализован. SMTP проверялся через mock JavaMailSender; фактическая отправка внешним адресатам не выполнялась.

Flyway/БД: V1–V7 успешны. После тестов отмеченных Tutoring test users, профилей, приглашений и command operations в локальной БД нет. Проверка git diff --check прошла; локальный HMAC-ключ исключён из Git.

## 7. Ограничения и следующие этапы

SMTP может повторить письмо при падении между отправкой и сохранением SENT. Dedup key гарантирует одну заявку, но не exactly-once SMTP. Очистка завершённых operations/receipts/outbox пока не включена: срок хранения не утверждён. Подробности — [ADR delivery](../../decisions/tutoring-durable-delivery.md).

Следующий этап 6: RegistrationWorkflow, RoleOnboardingWorkflow, MeQueryFacade и UnlinkStudentWorkflow с Scheduling guard. В частности, текущий account-only register ещё не создаёт профили через общий workflow, GET /me ещё не составлен, публичный unlink endpoint не добавлен. После него этап 7 проверяет эти составные пути и готовность Tutoring целиком.

