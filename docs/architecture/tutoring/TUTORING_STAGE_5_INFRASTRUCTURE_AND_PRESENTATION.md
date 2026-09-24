# Tutoring — этап 5. Infrastructure, Persistence и Presentation

Статус: зафиксированы решения, последовательно утверждённые пользователем 2026-09-24. Это проектная спецификация, а не реализованные Java-классы, SQL-миграции, контроллеры или тесты. Более поздние уточнения этого документа имеют приоритет над прежними общими формулировками этапов 1–4; предметные правила Domain и Application остаются их собственностью.

## 1. Граница этапа

`tutoring.infrastructure` реализует выходные порты Application: хранение собственных данных Tutoring, интеграцию через публичные API Identity и Notifications, криптографию токенов, курсоры, транзакционные блокировки и запуск фонового VER-06. `tutoring.presentation` переводит HTTP-запросы в команды Application и его результаты в HTTP-ответы. Контроллеры не вызывают Domain или jOOQ напрямую.

В реализации используется PostgreSQL, Flyway и jOOQ проекта. Tutoring не читает и не изменяет таблицы Identity, Scheduling или Notifications. Изменения этих модулей, необходимые для сквозных сценариев, перечислены как зависимости реализации, а не как таблицы Tutoring.

Предметно-ориентированная структура:

```text
tutoring.infrastructure
├── subject.persistence
├── profile.persistence
│   ├── teacher
│   ├── student
│   └── verification
├── invitation.persistence
├── relationship.persistence
├── idempotency.persistence
├── integration.identity
├── integration.notifications
├── email.persistence
└── profile.verification.scheduler

tutoring.presentation
├── subject
├── profile
│   └── verification
├── invitation
├── relationship
└── error
```

В каждой persistence-области адаптер реализует `port.out`, jOOQ-класс содержит SQL, mapper переводит записи БД в Domain или внутреннюю проекцию. Пустые уровни не создаются. Транзакцию открывает Application-сервис либо внешний workflow; repository-адаптер не открывает отдельную транзакцию. jOOQ records, HTTP DTO и типы чужой Domain не выходят через порты.

## 2. Физическая модель Tutoring

Ровно десять предметных таблиц v1. Межмодульных внешних ключей на `identity_users`, таблицы Scheduling и Notifications нет. Внутренние FK Tutoring допустимы и используются там, где указано ниже.

### 2.1 Справочник и выбор предметов

| Таблица | Поля и ограничения |
|---|---|
| `tutoring_subjects` | `code VARCHAR(32) PRIMARY KEY`, `name TEXT NOT NULL`. Код стабилен, uppercase ASCII по `^[A-Z][A-Z0-9_]*$`; название непустое. Первоначальные строки добавляет миграция; пользовательского CRUD в v1 нет. |
| `tutoring_teacher_profile_subjects` | `user_id UUID`, `subject_code VARCHAR(32)`, составной PK. Внутренние FK на TeacherProfile (`ON DELETE CASCADE`) и Subject (`ON DELETE RESTRICT`). |
| `tutoring_student_profile_subjects` | Аналогичный составной PK и FK на StudentProfile/Subject. Пустой набор строк допустим. |

Непустой набор предметов преподавателя защищает Domain/Application; ученик может выбрать ноль. Специализация преподавателя не ограничивает предмет урока.

### 2.2 Профили

`tutoring_teacher_profiles` и `tutoring_student_profiles` — независимые таблицы с `user_id UUID PRIMARY KEY` без FK на Identity. Общие поля: `birth_date DATE NOT NULL`, `display_name TEXT NOT NULL`, `contact_email VARCHAR(254) NOT NULL`, `pending_contact_email VARCHAR(254) NULL`, `contact_email_verified_at TIMESTAMPTZ NULL`, `contact_details JSONB NOT NULL` как массив строк, `photo_url TEXT NULL`. TeacherProfile дополнительно содержит nullable `description TEXT`, `education TEXT`, `experience_years INTEGER CHECK (experience_years >= 0)`, `city TEXT`. Ноль лет опыта отличается от `NULL`; город необязателен.

БД проверяет непустое имя, нормализованный email, отличие pending от current и JSON-массив. Domain дополнительно проверяет email, состав contactDetails, URI и остальные инварианты. У профильного email нет уникальности: контактный адрес может совпадать у разных профилей. `birthDate` копируется из Identity при создании, обычный профильный update её не меняет. Client-side `expectedVersion` в v1 не обещан; write-сценарии блокируют строку профиля.

### 2.3 Запросы подтверждения профильной почты

`tutoring_profile_email_verifications`: `id UUID PRIMARY KEY`, `user_id UUID` без межмодульного FK, `profile_type` (`TEACHER`/`STUDENT`), нормализованный `target_email VARCHAR(254)`, `purpose` (`INITIAL_CONFIRMATION`/`EMAIL_CHANGE`), `token_hash CHAR(64) UNIQUE`, `created_at`, `expires_at`, nullable `consumed_at`, `invalidated_at`, `expired_at` типа `TIMESTAMPTZ`.

Ограничения: `expires_at > created_at`, не более одного терминального времени, `expired_at >= expires_at`. Частичный уникальный индекс на `(user_id, profile_type, purpose)` действует, пока все три терминальных времени пусты: текущая и pending цели могут сосуществовать. Есть индекс для пакетного VER-06 по `expires_at` и для квоты по `(user_id, created_at)`. Перед созданием нового запроса старый истёкший материализуется или заменяемый аннулируется в той же транзакции. Raw token в Tutoring не хранится. Строки сохраняются как минимум до выхода из 24-часового окна квоты.

### 2.4 Приглашения

`tutoring_student_invitations`: `id UUID PRIMARY KEY`, `teacher_user_id UUID`, нормализованный `student_email VARCHAR(254)`, nullable `student_user_id UUID`, nullable `attached_at TIMESTAMPTZ`, `status` (`PENDING`, `ACCEPTED`, `REJECTED`, `EXPIRED`), `created_at`, `expires_at`, nullable `responded_at`.

FK на собственный TeacherProfile без cascade; FK на Identity или StudentProfile нет, поскольку адресат может ещё не иметь аккаунта/учебного профиля. `student_user_id` и `attached_at` оба пусты либо оба заданы. Известный при создании адресат получает `attached_at = created_at`; поздняя привязка фиксирует время первой привязки. Проверяются запрет самоприглашения по ID, `expires_at > created_at`, допустимое сочетание статуса и `responded_at`. Частичный уникальный индекс `(teacher_user_id, student_email) WHERE status = 'PENDING'` требует материализовать старое истёкшее приглашение перед новым. Индексы поддерживают отправленный/входящий keyset-список и поиск действующих `PENDING` по email.

### 2.5 Связь

`tutoring_teacher_students`: `teacher_user_id UUID`, `student_user_id UUID`, `created_at TIMESTAMPTZ`, PK из двух ID, запрет равенства сторон, внутренние FK на соответствующие профили без cascade. Нет surrogate ID, статуса и `removedAt`. Индексы поддерживают страницы по преподавателю и ученику с сортировкой `created_at DESC, other_user_id DESC`. Физическое удаление выполняется только после guard и обработки уроков внешним `UnlinkStudentWorkflow`.

### 2.6 Идемпотентность и события

`tutoring_command_operations`: `operation_id UUID PRIMARY KEY`, `user_id UUID` без FK Identity, `operation_type`, `payload_fingerprint CHAR(64)`, `result_type`, `result_schema_version > 0`, nullable `result_payload JSONB`, `created_at`, nullable `completed_at`. Результат существует тогда и только тогда, когда операция завершена; `completed_at >= created_at`. Незавершённая строка допустима лишь внутри транзакции и никогда не коммитится. Даже для `void` сохраняется явное JSON-значение, не SQL `NULL`. Срок очистки не утверждён: автоматического удаления в v1 нет.

`tutoring_processed_identity_events`: `event_id UUID PRIMARY KEY`, стабильный `event_type` (`ACCOUNT_EMAIL_VERIFIED` в v1), `user_id UUID` без FK Identity, `payload_fingerprint CHAR(64)`, `fingerprint_version > 0`, nullable `processed_at` лишь до завершения текущей транзакции. Fingerprint строится из канонических eventType, userId, нормализованного verifiedEmail и verifiedAt; raw email в receipt не сохраняется. Очистки receipts в v1 нет. Дополнительные индексы сверх PK не нужны.

## 3. Контракты persistence-адаптеров

### 3.1 Subject и профили

`SubjectRepository`: `findAll`, `exists`, пакетный `findExistingCodes`. Пустой batch не делает SQL; пустой `findAll` сам repository не превращает в ошибку. Сортировку ответа выполняет Application.

Отдельные Teacher/Student adapters: `insert`, `update`, `findByUserId`, `findByUserIdForUpdate`, `existsByUserId`, пакетные summary и linked-проекции. Upsert не используется: повторное создание и обновление отсутствующего профиля различаются. Профиль и предметы сохраняются атомарно; при update выбор предметов синхронизируется по разнице под блокировкой родительской строки. Обычное чтение профиля с предметами использует один согласованный снимок запроса, а write-чтение сначала блокирует строку профиля, затем читает детей. Batch summary возвращает только `userId` и `displayName` без N+1. Linked-проекция включает `birthDate`, а current email — только если есть `contact_email_verified_at`; pending не выдаётся. Право доступа проверяет Application через связь. Если связь есть, а профиля нет, это ошибка целостности, не `Optional.empty()`.

### 3.2 Verification

Явные `insert` и `update` терминального перехода; поиск по hash, повторное чтение `FOR UPDATE`, поиск ещё не терминального запроса по `(userId, profileType, purpose)`, ограниченная выборка истёкших для VER-06. Нахождение строки в частичном индексе само по себе не означает активности: `now < expiresAt` проверяется синхронно. Подтверждение сначала неблокирующе узнаёт целевой email по hash, затем берёт email lock, блокирует профиль и перечитывает verification `FOR UPDATE`; все условия проверяются повторно. VER-06 использует `FOR UPDATE SKIP LOCKED`, не меняет профиль и не рассылает письма.

### 3.3 Invitation

Явные `insert`, `update`, `updateAll`, без upsert. При создании под общей email-блокировкой старый `PENDING` для teacher+email читается `FOR UPDATE`; если срок вышел, переводится в `EXPIRED` перед вставкой нового. Ответ берёт приглашение `FOR UPDATE`; принятие сохраняется в одной транзакции со вставкой TeacherStudent. Привязка по событию пакетно блокирует действующие `PENDING` на нормализованный адрес, устанавливает recipient ID и `attachedAt` только один раз.

Отправленные и входящие страницы читаются без записей, сортируются по `created_at DESC, id DESC` и применяют фильтр статуса **до** `limit + 1`. Исторический статус на `asOf`: если `responded_at <= asOf`, сохранённый ответ; иначе при `expires_at <= asOf` — `EXPIRED`; иначе — `PENDING`. Входящая страница дополнительно требует `student_user_id = actorUserId` и `attached_at <= asOf`. Созданные или привязанные после первой страницы записи не появляются посреди просмотра. Отправленная проекция не раскрывает `studentUserId`.

### 3.4 Relationship

`find`, `findForUpdate`, `exists`, пакетный `findLinkedStudentUserIds`, keyset `findByTeacher`/`findByStudent`, `insert`, `delete`. Пустой batch не делает SQL. Конфликт PK при вставке не обновляет пару. `delete` ожидает ровно одну строку и вызывается лишь внутри внешнего UnlinkStudentWorkflow. Relationship-списки не имеют исторического `asOf`: cursor хранит позицию, но не обещает снимок на весь просмотр.

### 3.5 Command operation и Identity event receipt

Оба адаптера резервируют уникальный ID через `INSERT ... ON CONFLICT DO NOTHING`; конкурент ждёт commit/rollback первой транзакции. При конфликте читается запись и сравниваются все поля идентичности операции/события. Точное завершённое совпадение — replay/no-op; иные данные с тем же ID — конфликт. Сохранённая незавершённая запись означает техническое нарушение, а не право выполнить команду повторно. `complete`/отметка `processedAt` условно обновляет только незавершённую строку в той же транзакции. Result JSON декодируется лишь для известных result types и поддерживаемых schema versions; произвольных имён Java-классов в payload нет. Устаревшее Identity-событие после успешной актуальной проверки Identity фиксируется обработанным no-op; недоступность Identity откатывает receipt.

Ожидаемые уникальные нарушения переводятся в соответствующий конфликт сценария; неизвестные CHECK/FK, повреждённый JSON и enum — технические ошибки, не «не найдено».

## 4. Блокировки и квота

`NormalizedEmailLock` применяет транзакционную PostgreSQL advisory lock по нормализованному email. Одинаковый механизм используют профильная почта, INV-01 и обработчик Identity event. Он требует действующей write-транзакции; возможное совпадение hash разных адресов вызывает лишь лишнюю сериализацию. Область ключей квоты отделена от email locks.

Порядок для применимых операций: резервирование command/event ID → email lock → строка профиля/приглашения → verification → user quota lock → заявка Notifications. Предварительный поиск токена перед email lock не блокирует запись; после блокировки данные перечитываются. `ProfileVerificationEmailQuota` под отдельной advisory lock по userId считает все новые строки verification за скользящие 24 часа по обоим профилям, включая использованные/аннулированные; максимум пять новых писем. Нет отдельной таблицы квоты. Совпавший account email и ожидание подтверждения Identity слот не используют. Ошибка enqueue откатывает все записи и блокировки освобождаются с транзакцией.

## 5. Интеграционные адаптеры

### 5.1 Identity

Порт `IdentityAccountGateway` находится в Tutoring Application; его инфраструктурный adapter вызывает **публичный Java API Identity**, не SQL. Нужны `findAccountEmailState(userId)` (current email и verifiedAt), `findByVerifiedEmail(normalizedEmail)` (только подтверждённый владелец) и разрешённая публичная проверка роли при создании профиля. Отсутствие пользователя отличается от технической недоступности API. BirthDate в первоначальные/вторичные профили передаёт доверенный Registration/RoleOnboarding workflow.

Listener `AccountEmailVerifiedEvent` переводит событие в Application-команду с `eventId`, `userId`, `verifiedEmail`, `verifiedAt`; сам не изменяет профили. Application повторно спрашивает Identity об актуальном подтверждённом адресе, затем подтверждает совпадающий current или pending email профилей и привязывает приглашения. `ProcessedIdentityEventRepositoryAdapter` находится в `tutoring.infrastructure.idempotency.persistence`, поскольку хранит собственный receipt Tutoring, а не данные Identity. Надёжная доставка с retry требует durable outbox на стороне Identity; текущая публикация Spring-события сама по себе недостаточна.

### 5.2 Notifications

`ProfileVerificationEmailSenderAdapter` и `InvitationNotificationSenderAdapter` вызывают публичный API Notifications, не его repository. Заявка на профильное письмо содержит recipient, raw token, назначение, expiresAt и dedup key `PROFILE_EMAIL_VERIFICATION:{verificationId}`. Заявка на приглашение содержит studentEmail, invitationId, teacherDisplayName, expiresAt и dedup key `INVITATION_CREATED:{invitationId}`; не содержит recipient userId или признак существования аккаунта. Notifications формирует ссылку, хранит raw token/URL в своей durable delivery и выполняет SMTP после commit. Первая отправка после expiresAt запрещена. Enqueue участвует в общей транзакции; SMTP-сбой после commit не откатывает Tutoring.

Существующий Notifications API verification-only и не содержит dedup key или шаблон приглашения: его публичный контракт и очередь требуется расширить. Tutoring не подменяет профильное подтверждение существующим назначением account `EMAIL_CHANGE`.

## 6. Технические адаптеры

- Генератор профильного токена использует не менее 32 случайных байт и URL-safe Base64 без padding; hasher — SHA-256 как 64 hex-символа. Это собственные классы Tutoring, не импорт `identity.infrastructure`. Raw token не логируется.
- `InvitationCursorCodec` и `RelationshipCursorCodec` кодируют версию, владельца, направление, фильтр и keyset-позицию в подписанную HMAC URL-safe строку. Invitation cursor также хранит `asOf`; Relationship cursor — нет. Подпись и структура проверяются до SQL. Курсор подписан, но не зашифрован, поэтому секретов внутри нет. Ключ хранится в секретах конфигурации; TTL курсора в v1 не вводится.
- Application использует общий внедрённый `Clock` проекта, время передаётся Domain явно и сохраняется с точностью до микросекунд. Отдельный TutoringTimeProvider без необходимости не создаётся; тесты используют фиксированный Clock.
- `ExpiredProfileVerificationJob` в Infrastructure ежедневно вызывает VER-06. По умолчанию конфигурируются UTC и пачка 100; каждая пачка имеет собственную транзакцию, `SKIP LOCKED` допускает несколько экземпляров. Задержка задачи не делает токены действительными после expiresAt. Фоновой INV-06 в v1 нет.

## 7. Публичный Java API и HTTP Presentation

Утверждённый `tutoring.api` этапа 2 сохраняется: query-сервисы и доверенные Registration/Relationship commands реализует Application. Команды внешних workflows требуют уже открытой общей транзакции (`MANDATORY`) и не публикуются как прямые HTTP endpoint Tutoring. Составной `/me` принадлежит MeQueryFacade; linked-карточка — утверждённой query facade. Ни одна из них не выдаёт произвольный полный профиль другого пользователя.

Предложенный и утверждённый префикс Tutoring HTTP — `/api/v1/tutoring`:

| Метод и путь | Вход и успешный ответ |
|---|---|
| `GET /subjects` | Без входных данных/авторизации; `200` массив `{subjectCode,name}`. Пустой справочник — внутренняя ошибка. |
| `PUT /profiles/teacher` | Полная замена обычных teacher-полей; `204`. `subjectCodes` непустой. |
| `PUT /profiles/student` | Полная замена обычных student-полей; `204`. `subjectCodes` может быть пустым. |
| `PUT /profiles/{type}/contact-email` | `{newEmail}`; `200` с состоянием: подтверждено, ожидание Identity или письмо поставлено в очередь. |
| `POST /profiles/{type}/contact-email/confirmation-requests` | `{target: CURRENT\|PENDING}`; `200` с тем же типом результата. |
| `POST /profile-email-confirmations` | Raw token только в теле, авторизация не обязательна; `204`, без email/userId. Никакого изменяющего `GET`. |
| `POST /invitations` | `{studentEmail}`, обязательный `Idempotency-Key: UUID`; `201` `{invitationId,expiresAt}`. |
| `GET /invitations/sent`, `GET /invitations/incoming` | Необязательные status/cursor/limit; `200` `{items,nextCursor}`. Limit по умолчанию 50, допустимо 1…100. |
| `POST /invitations/{id}/accept`, `POST /invitations/{id}/reject` | Обязательный `Idempotency-Key`; `200` с минимальным результатом; клиентский email не принимается. |
| `GET /relationships/students`, `GET /relationships/teachers` | Необязательные cursor/limit; `200` `{items,nextCursor}`, без общего total. |

HTTP DTO живут в соответствующем предметном пакете Presentation, не являются `tutoring.api` model. Teacher update: `displayName`, `contactDetails`, `subjectCodes`, nullable `description`, `education`, `experienceYears`, `city`, `photoUrl`. Student update: `displayName`, `contactDetails`, `subjectCodes`, nullable `photoUrl`. PUT заменяет перечисленные поля целиком; отсутствие необязательного поля и `null` означают очистку. Коллекции не могут быть `null`. `birthDate`, current/pending email и verifiedAt обычный PUT не меняет. Произвольные длины остальных строк не вводятся без отдельного решения; размер HTTP-тела ограничивается на транспортной границе.

Справочник и подтверждение токена открыты без обязательного login. Остальные маршруты аутентифицированы, actorUserId всегда берётся из доверенного principal, не из HTTP-тела. Создание приглашения требует активного аккаунта с TEACHER-ролью на Identity/auth-границе и TeacherProfile в Tutoring. Входящие приглашения и отказ доступны привязанному пользователю до роли/профиля ученика; принятие требует StudentProfile. Списки связей возвращают только linked-view после проверки подтверждённой пары. Отправленный список не раскрывает recipient userId или факт его регистрации. Полный self-view доступен только через MeQueryFacade.

Форма ошибки совместима по структуре с `ApiError`: `{code,message,fieldErrors,requestId}`, но Tutoring не импортирует `identity.presentation`. HTTP mapping: `400` — валидация, повреждённый cursor, отсутствующий/некорректный Idempotency-Key, неизвестный предмет, любой недействительный verification token; `401` — нет обязательной аутентификации; `403` — запрещённое действие, включая создание приглашения без TEACHER-роли; `404` — собственный отсутствующий профиль либо единый ответ для отсутствующего/чужого приглашения; `409` — конфликт приглашения/состояния, отсутствие StudentProfile при принятии или несовпавший replay; `429` — квота письма с безопасным Retry-After; `500/503` — целостность либо техническая недоступность зависимого модуля/БД. Полные email, raw token и fingerprint не попадают в ответ или логи.

## 8. Сквозная проверка

| Область | Критический тест |
|---|---|
| Регистрация и onboarding | Одна/две роли, заполненные профили и совпадающая birthDate; ошибка любого модуля вызывает общий rollback; добавление второй роли не меняет первый профиль. |
| Обычный профиль | PUT меняет только разрешённые поля; пустые предметы ученика допустимы, преподавателя запрещены; предмет урока не ограничен специализацией. |
| Профильная почта | current сохраняется до подтверждения pending; старые/использованные/истёкшие токены отвергаются; событие Identity подтверждает актуальный current или pending. |
| Доставка и квота | Enqueue и бизнес-изменение атомарны; не более пяти новых писем за 24 часа; SMTP после commit не меняет бизнес-результат. |
| Приглашение | Неизвестный адрес допустим, последующее подтверждение Identity привязывает его без принятия; самоприглашение и второй действующий PENDING запрещены. |
| Ответ и связь | Отказ до student-onboarding разрешён; принятие требует StudentProfile и атомарно создаёт одну пару; now == expiresAt запрещает ответ. |
| Privacy | Без связи чужие birthDate и контакты не раскрываются; linked email только подтверждённый current, pending никогда. |
| Курсоры | Нет дублей при одинаковом createdAt; status-фильтр применяется до limit; attachedAt > asOf не появляется на следующих входящих страницах. |
| Отвязка | Scheduling guard и обработка уроков предшествуют физическому удалению пары; ошибка откатывает всё; создание нового урока пользуется совместимым guard. |
| Повторы и гонки | Exact replay operationId/eventId, конфликт изменённого payload, конкурентный accept/create, VER-06 на нескольких экземплярах. |
| Архитектура | Tutoring API не импортирует HTTP/SQL/Identity Domain; Tutoring Infrastructure не читает чужие таблицы; workflow вызывает только публичный Tutoring API. |

Для ограничений, блокировок и конкурентных сценариев требуются интеграционные тесты с реальным PostgreSQL; для времени — фиксируемый Clock. Перечисленные тесты являются критериями будущей реализации, а не уже выполненными проверками.

## 9. Зависимости перед реализацией

Текущий код проекта ещё не реализует Tutoring. Для сквозного запуска потребуются: birthDate в Identity регистрации/хранении, публичные Identity query/role API и событие подтверждения email с durable outbox; расширение Notifications API и delivery для профильной почты и приглашений с dedup key; внешний Registration/RoleOnboarding workflow и Scheduling guard для удаления связи; HTTP-auth граница с доверенным actorUserId. Эти изменения принадлежат соответствующим модулям/workflows, а не Tutoring Persistence. Политика хранения завершённых command operations и receipts не утверждена; v1 их автоматически не удаляет.
