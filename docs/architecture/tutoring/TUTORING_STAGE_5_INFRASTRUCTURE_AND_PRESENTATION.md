# Tutoring — этап 5. Infrastructure, Persistence и Presentation

Статус: зафиксированы решения, последовательно утверждённые пользователем 2026-09-24. Это проектная спецификация, а не реализованные Java-классы, SQL-миграции, контроллеры или тесты. Более поздние уточнения этого документа имеют приоритет над прежними общими формулировками этапов 1–4; предметные правила Domain и Application остаются их собственностью.

## 1. Граница этапа

`tutoring.infrastructure` реализует выходные порты Application: хранение собственных данных Tutoring, интеграцию через публичные API Identity и Notifications, курсоры и транзакционные блокировки. `tutoring.presentation` переводит HTTP-запросы в команды Application и его результаты в HTTP-ответы. Контроллеры не вызывают Domain или jOOQ напрямую.

В реализации используется PostgreSQL, Flyway и jOOQ проекта. Tutoring не читает и не изменяет таблицы Identity, Scheduling или Notifications. Изменения этих модулей, необходимые для сквозных сценариев, перечислены как зависимости реализации, а не как таблицы Tutoring.

Предметно-ориентированная структура:

```text
tutoring.infrastructure
├── subject.persistence
├── profile.persistence
│   ├── teacher
│   └── student
├── invitation.persistence
├── relationship.persistence
├── idempotency.persistence
├── integration.identity
├── integration.notifications
└── email.persistence

tutoring.presentation
├── subject
├── profile
├── invitation
├── relationship
└── error
```

В каждой persistence-области адаптер реализует `port.out`, jOOQ-класс содержит SQL, mapper переводит записи БД в Domain или внутреннюю проекцию. Пустые уровни не создаются. Транзакцию открывает Application-сервис либо внешний workflow; repository-адаптер не открывает отдельную транзакцию. jOOQ records, HTTP DTO и типы чужой Domain не выходят через порты.

## 2. Физическая модель Tutoring

Ровно девять предметных таблиц v1. Межмодульных внешних ключей на `identity_users`, таблицы Scheduling и Notifications нет. Внутренние FK Tutoring допустимы и используются там, где указано ниже.

### 2.1 Справочник и выбор предметов

| Таблица | Поля и ограничения |
|---|---|
| `tutoring_subjects` | `code VARCHAR(32) PRIMARY KEY`, `name TEXT NOT NULL`. Код стабилен, uppercase ASCII по `^[A-Z][A-Z0-9_]*$`; название непустое. Первоначальные строки добавляет миграция; пользовательского CRUD в v1 нет. |
| `tutoring_teacher_profile_subjects` | `user_id UUID`, `subject_code VARCHAR(32)`, составной PK. Внутренние FK на TeacherProfile (`ON DELETE CASCADE`) и Subject (`ON DELETE RESTRICT`). |
| `tutoring_student_profile_subjects` | Аналогичный составной PK и FK на StudentProfile/Subject. Пустой набор строк допустим. |

Непустой набор предметов преподавателя защищает Domain/Application; ученик может выбрать ноль. Специализация преподавателя не ограничивает предмет урока.

### 2.2 Профили

`tutoring_teacher_profiles` и `tutoring_student_profiles` — независимые таблицы с `user_id UUID PRIMARY KEY` без FK на Identity. Общие поля: неизменяемый `created_at TIMESTAMPTZ NOT NULL`, `display_name TEXT NOT NULL`, `contact_email VARCHAR(254) NOT NULL`, `contact_details JSONB NOT NULL` как массив строк, `photo_url TEXT NULL`. TeacherProfile дополнительно содержит nullable `description TEXT`, `education TEXT`, `experience_years INTEGER CHECK (experience_years >= 0)`, `city TEXT`. Ноль лет опыта отличается от `NULL`; город необязателен.

БД проверяет непустое имя, нормализованный email и JSON-массив. Domain дополнительно проверяет email, состав contactDetails, URI и остальные инварианты. У профильного email нет уникальности: контактный адрес может совпадать у разных профилей. Дата рождения и возраст в Tutoring не сохраняются. Client-side `expectedVersion` в v1 не обещан; write-сценарии блокируют строку профиля.

Для публичного поиска обе профильные таблицы индексируются по `(created_at DESC, user_id DESC)`; таблицы выбранных предметов — по `(subject_code,user_id)`. Предметы конкретного человека читаются только из его профильной таблицы связей с предметами, а не из общего каталога. Возраст и статус ACTIVE получает Tutoring из Identity пакетами по ID кандидатов при выдаче страницы.

### 2.3 Приглашения

`tutoring_student_invitations`: `id UUID PRIMARY KEY`, `teacher_user_id UUID`, нормализованный `student_email VARCHAR(254)`, nullable `student_user_id UUID`, nullable `attached_at TIMESTAMPTZ`, `status` (`PENDING`, `ACCEPTED`, `REJECTED`, `EXPIRED`), `created_at`, `expires_at`, nullable `responded_at`.

FK на собственный TeacherProfile без cascade; FK на Identity или StudentProfile нет, поскольку адресат может ещё не иметь аккаунта/учебного профиля. `student_user_id` и `attached_at` оба пусты либо оба заданы. Известный при создании адресат получает `attached_at = created_at`; поздняя привязка фиксирует время первой привязки. Проверяются запрет самоприглашения по ID, `expires_at > created_at`, допустимое сочетание статуса и `responded_at`. Частичный уникальный индекс `(teacher_user_id, student_email) WHERE status = 'PENDING'` требует материализовать старое истёкшее приглашение перед новым. Индексы поддерживают отправленный/входящий keyset-список и поиск действующих `PENDING` по email.

### 2.4 Связь

`tutoring_teacher_students`: `teacher_user_id UUID`, `student_user_id UUID`, `created_at TIMESTAMPTZ`, PK из двух ID, запрет равенства сторон, внутренние FK на соответствующие профили без cascade. Нет surrogate ID, статуса и `removedAt`. Индексы поддерживают страницы по преподавателю и ученику с сортировкой `created_at DESC, other_user_id DESC`. Физическое удаление выполняется только после guard и обработки уроков внешним `UnlinkStudentWorkflow`.

### 2.5 Идемпотентность и события

`tutoring_command_operations`: `operation_id UUID PRIMARY KEY`, `user_id UUID` без FK Identity, `operation_type`, `payload_fingerprint CHAR(64)`, `result_type`, `result_schema_version > 0`, nullable `result_payload JSONB`, `created_at`, nullable `completed_at`. Результат существует тогда и только тогда, когда операция завершена; `completed_at >= created_at`. Незавершённая строка допустима лишь внутри транзакции и никогда не коммитится. Даже для `void` сохраняется явное JSON-значение, не SQL `NULL`. Срок очистки не утверждён: автоматического удаления в v1 нет.

`tutoring_processed_identity_events`: `event_id UUID PRIMARY KEY`, стабильный `event_type` (`ACCOUNT_EMAIL_VERIFIED` в v1), `user_id UUID` без FK Identity, `payload_fingerprint CHAR(64)`, `fingerprint_version > 0`, nullable `processed_at` лишь до завершения текущей транзакции. Fingerprint строится из канонических eventType, userId, нормализованного verifiedEmail и verifiedAt; raw email в receipt не сохраняется. Очистки receipts в v1 нет. Дополнительные индексы сверх PK не нужны.

## 3. Контракты persistence-адаптеров

### 3.1 Subject и профили

`SubjectRepository`: `findAll`, `exists`, пакетный `findExistingCodes`. Пустой batch не делает SQL; пустой `findAll` сам repository не превращает в ошибку. Сортировку ответа выполняет Application.

Отдельные Teacher/Student adapters: `insert`, `update`, `findByUserId`, `findByUserIdForUpdate`, `existsByUserId`, пакетные summary, public и linked-проекции. Upsert не используется: повторное создание и обновление отсутствующего профиля различаются. Профиль и предметы сохраняются атомарно; при update выбор предметов синхронизируется по разнице под блокировкой родительской строки. Обычное чтение профиля с предметами использует один согласованный снимок запроса, а write-чтение сначала блокирует строку профиля, затем читает детей. Batch summary возвращает только `userId` и `displayName` без N+1. Public/linked-проекции содержат `contactDetails`, указанный контактный email и обычные поля; возраст добавляет Application из пакетного ответа Identity. Публичное чтение проверяет активность пользователя через Identity, linked-операция дополнительно проверяет связь. Если связь есть, а профиля нет, это ошибка целостности.

### 3.2 Invitation

Явные `insert`, `update`, `updateAll`, без upsert. При создании под общей email-блокировкой старый `PENDING` для teacher+email читается `FOR UPDATE`; если срок вышел, переводится в `EXPIRED` перед вставкой нового. Ответ берёт приглашение `FOR UPDATE`; принятие сохраняется в одной транзакции со вставкой TeacherStudent. Привязка по событию пакетно блокирует действующие `PENDING` на нормализованный адрес, устанавливает recipient ID и `attachedAt` только один раз.

Отправленные и входящие страницы читаются без записей, сортируются по `created_at DESC, id DESC` и применяют фильтр статуса **до** `limit + 1`. Исторический статус на `asOf`: если `responded_at <= asOf`, сохранённый ответ; иначе при `expires_at <= asOf` — `EXPIRED`; иначе — `PENDING`. Входящая страница дополнительно требует `student_user_id = actorUserId` и `attached_at <= asOf`. Созданные или привязанные после первой страницы записи не появляются посреди просмотра. Отправленная проекция не раскрывает `studentUserId`.

### 3.3 Relationship

`find`, `findForUpdate`, `exists`, пакетный `findLinkedStudentUserIds`, keyset `findByTeacher`/`findByStudent`, `insert`, `delete`. Пустой batch не делает SQL. Конфликт PK при вставке не обновляет пару. `delete` ожидает ровно одну строку и вызывается лишь внутри внешнего UnlinkStudentWorkflow. Relationship-списки не имеют исторического `asOf`: cursor хранит позицию, но не обещает снимок на весь просмотр.

### 3.4 Command operation и Identity event receipt

Оба адаптера резервируют уникальный ID через `INSERT ... ON CONFLICT DO NOTHING`; конкурент ждёт commit/rollback первой транзакции. При конфликте читается запись и сравниваются все поля идентичности операции/события. Точное завершённое совпадение — replay/no-op; иные данные с тем же ID — конфликт. Сохранённая незавершённая запись означает техническое нарушение, а не право выполнить команду повторно. `complete`/отметка `processedAt` условно обновляет только незавершённую строку в той же транзакции. Result JSON декодируется лишь для известных result types и поддерживаемых schema versions; произвольных имён Java-классов в payload нет. Устаревшее Identity-событие после успешной актуальной проверки Identity фиксируется обработанным no-op; недоступность Identity откатывает receipt.

Ожидаемые уникальные нарушения переводятся в соответствующий конфликт сценария; неизвестные CHECK/FK, повреждённый JSON и enum — технические ошибки, не «не найдено».

## 4. Блокировки

`NormalizedEmailLock` применяет транзакционную PostgreSQL advisory lock по нормализованному account email. Одинаковый механизм используют создание приглашения INV-01 и обработчик Identity event. Он требует действующей write-транзакции; возможное совпадение hash разных адресов вызывает лишь лишнюю сериализацию.

Порядок для применимых операций: резервирование command/event ID → email lock → строки приглашений → заявка Notifications при создании приглашения. Ошибка enqueue откатывает записи, блокировки освобождаются с транзакцией. Смена контактного email профиля не использует эту блокировку и не отправляет письмо.

## 5. Интеграционные адаптеры

### 5.1 Identity

Порт `IdentityAccountGateway` находится в Tutoring Application; его инфраструктурный adapter вызывает **публичный Java API Identity**, не SQL. Нужны `findByVerifiedEmail(normalizedEmail)` (только подтверждённый владелец), проверка роли при создании профиля и пакетный `findAgesByIds(userIds, asOf)` для представлений и поиска. Публичное чтение отдельно проверяет ACTIVE-статус. Отсутствие пользователя отличается от технической недоступности API. Дата рождения остаётся только в Identity.

Listener `AccountEmailVerifiedEvent` переводит событие в Application-команду с `eventId`, `userId`, `verifiedEmail`, `verifiedAt`; сам не изменяет профили. Application повторно спрашивает Identity об актуальном подтверждённом адресе и привязывает приглашения. `ProcessedIdentityEventRepositoryAdapter` находится в `tutoring.infrastructure.idempotency.persistence`, поскольку хранит собственный receipt Tutoring, а не данные Identity. Надёжная доставка с retry требует durable outbox на стороне Identity; текущая публикация Spring-события сама по себе недостаточна.

### 5.2 Notifications

`InvitationNotificationSenderAdapter` вызывает публичный API Notifications, не его repository. Заявка на приглашение содержит studentEmail, invitationId, teacherDisplayName, expiresAt и dedup key `INVITATION_CREATED:{invitationId}`; не содержит recipient userId или признак существования аккаунта. Notifications формирует ссылку, хранит URL в своей durable delivery и выполняет SMTP после commit. Первая отправка после expiresAt запрещена. Enqueue участвует в общей транзакции; SMTP-сбой после commit не откатывает Tutoring.

Существующий Notifications API verification-only и не содержит dedup key или шаблон приглашения: его публичный контракт и очередь требуется расширить для приглашений.

## 6. Технические адаптеры

- `InvitationCursorCodec` и `RelationshipCursorCodec` кодируют версию, владельца, направление, фильтр и keyset-позицию в подписанную HMAC URL-safe строку. Invitation cursor также хранит `asOf`; Relationship cursor — нет. Подпись и структура проверяются до SQL. Курсор подписан, но не зашифрован, поэтому секретов внутри нет. Ключ хранится в секретах конфигурации; TTL курсора в v1 не вводится.
- `PublicProfileCursorCodec` кодирует вид профиля, нормализованные фильтры, дату расчёта возраста UTC и позицию `createdAt/userId`; публичный курсор не содержит сведений об отношениях или дате рождения. Поиск сначала применяет фильтры профиля, затем пакетно получает возраст и активность из Identity и добирает страницу до `limit + 1` либо конца набора.
- Application использует общий внедрённый `Clock` проекта, время передаётся Domain явно и сохраняется с точностью до микросекунд. Отдельный TutoringTimeProvider без необходимости не создаётся; тесты используют фиксированный Clock.
- Фоновой задачи для истечения приглашений в v1 нет: истёкший `PENDING` обрабатывается при создании нового приглашения.

## 7. Публичный Java API и HTTP Presentation

Утверждённый `tutoring.api` этапа 2 сохраняется: query-сервисы и доверенные Registration/Relationship commands реализует Application. Команды внешних workflows требуют уже открытой общей транзакции (`MANDATORY`) и не публикуются как прямые HTTP endpoint Tutoring. Составной `/me` принадлежит MeQueryFacade; linked-карточка — утверждённой query facade. Ни одна из них не выдаёт произвольный полный профиль другого пользователя.

Предложенный и утверждённый префикс Tutoring HTTP — `/api/v1/tutoring`:

| Метод и путь | Вход и успешный ответ |
|---|---|
| `GET /subjects` | Без входных данных/авторизации; `200` массив `{subjectCode,name}`. Пустой справочник — внутренняя ошибка. |
| `GET /profiles/teachers`, `GET /profiles/students` | Публичный поиск активных профилей; фильтры `subjectCode`, `minAge`, `maxAge`, `cursor`, `limit`; `200` `{items,nextCursor}`. Выбранные предметы читаются из профилей. |
| `GET /profiles/teachers/{userId}`, `GET /profiles/students/{userId}` | Публичный активный профиль с возрастом, описанием и указанными пользователем контактами; `200` или `404`. |
| `PUT /profiles/teacher` | Полная замена обычных teacher-полей; `204`. `subjectCodes` непустой. |
| `PUT /profiles/student` | Полная замена обычных student-полей; `204`. `subjectCodes` может быть пустым. |
| `PUT /profiles/{type}/contact-email` | `{newEmail}`; `204` после проверки формата и немедленной замены адреса. |
| `POST /invitations` | `{studentEmail}`, обязательный `Idempotency-Key: UUID`; `201` `{invitationId,expiresAt}`. |
| `GET /invitations/sent`, `GET /invitations/incoming` | Необязательные status/cursor/limit; `200` `{items,nextCursor}`. Limit по умолчанию 50, допустимо 1…100. |
| `POST /invitations/{id}/accept`, `POST /invitations/{id}/reject` | Обязательный `Idempotency-Key`; `200` с минимальным результатом; клиентский email не принимается. |
| `GET /relationships/students`, `GET /relationships/teachers` | Необязательные cursor/limit; `200` `{items,nextCursor}`, без общего total. |

HTTP DTO живут в соответствующем предметном пакете Presentation, не являются `tutoring.api` model. Teacher update: `displayName`, `contactDetails`, `subjectCodes`, nullable `description`, `education`, `experienceYears`, `city`, `photoUrl`. Student update: `displayName`, `contactDetails`, `subjectCodes`, nullable `photoUrl`. PUT заменяет перечисленные поля целиком; отсутствие необязательного поля и `null` означают очистку. Коллекции не могут быть `null`. Контактный email меняет отдельный PUT; дату рождения профили не содержат. Произвольные длины остальных строк не вводятся без отдельного решения; размер HTTP-тела ограничивается на транспортной границе.

Справочник, публичный просмотр/поиск активных профилей открыты без обязательного login. Остальные маршруты аутентифицированы, actorUserId всегда берётся из доверенного principal, не из HTTP-тела. Создание приглашения требует активного аккаунта с TEACHER-ролью на Identity/auth-границе и TeacherProfile в Tutoring. Входящие приглашения и отказ доступны привязанному пользователю до роли/профиля ученика; принятие требует StudentProfile. Списки связей возвращают public-view профиля после проверки подтверждённой пары; те же публичные данные активного профиля можно прочитать без связи. Отправленный список не раскрывает recipient userId или факт его регистрации. Полный self-view доступен только через MeQueryFacade.

При реализации общий Spring Security/CORS config должен открыть публичные `GET` профилей/предметов, разрешить метод `PUT` и заголовок `Idempotency-Key` для browser-клиента. Текущая конфигурация Identity MVP этих правил ещё не содержит; это изменение кода выполняется вместе с HTTP-ручками.

Форма ошибки совместима по структуре с `ApiError`: `{code,message,fieldErrors,requestId}`, но Tutoring не импортирует `identity.presentation`. HTTP mapping: `400` — валидация, повреждённый cursor, отсутствующий/некорректный Idempotency-Key, неизвестный предмет или неверный формат контактного email; `401` — нет обязательной аутентификации; `403` — запрещённое действие, включая создание приглашения без TEACHER-роли; `404` — собственный отсутствующий профиль либо единый ответ для отсутствующего/чужого приглашения; `409` — конфликт приглашения/состояния, отсутствие StudentProfile при принятии или несовпавший replay; `500/503` — целостность либо техническая недоступность зависимого модуля/БД. Полные email и fingerprint не попадают в ответ или логи.

## 8. Сквозная проверка

| Область | Критический тест |
|---|---|
| Регистрация и onboarding | Одна/две роли и заполненные профили без копии даты рождения; ошибка любого модуля вызывает общий rollback; добавление второй роли не меняет первый профиль. |
| Обычный профиль | PUT меняет только разрешённые поля; пустые предметы ученика допустимы, преподавателя запрещены; предмет урока не ограничен специализацией. |
| Контактный email профиля | Формат проверяется; новый адрес сохраняется сразу, может отличаться от account email и не считается подтверждённым. |
| Доставка приглашения | Enqueue и бизнес-изменение атомарны; SMTP после commit не меняет бизнес-результат. |
| Приглашение | Неизвестный адрес допустим, последующее подтверждение Identity привязывает его без принятия; самоприглашение и второй действующий PENDING запрещены. |
| Ответ и связь | Отказ до student-onboarding разрешён; принятие требует StudentProfile и атомарно создаёт одну пару; now == expiresAt запрещает ответ. |
| Privacy | Активный публичный профиль виден без связи, включая возраст, contactDetails и указанный пользователем контактный email; точная дата рождения остаётся в Identity. |
| Курсоры | Нет дублей при одинаковом createdAt; status-фильтр применяется до limit; attachedAt > asOf не появляется на следующих входящих страницах. |
| Отвязка | Scheduling guard и обработка уроков предшествуют физическому удалению пары; ошибка откатывает всё; создание нового урока пользуется совместимым guard. |
| Повторы и гонки | Exact replay operationId/eventId, конфликт изменённого payload и конкурентный accept/create. |
| Архитектура | Tutoring API не импортирует HTTP/SQL/Identity Domain; Tutoring Infrastructure не читает чужие таблицы; workflow вызывает только публичный Tutoring API. |

Для ограничений, блокировок и конкурентных сценариев требуются интеграционные тесты с реальным PostgreSQL; для времени — фиксируемый Clock. Перечисленные тесты являются критериями будущей реализации, а не уже выполненными проверками.

## 9. Зависимости перед реализацией

Публичные Java-контракты Tutoring добавлены, но его Domain, Application, Infrastructure и Presentation ещё не реализованы. Для сквозного запуска потребуются: дата рождения в Identity регистрации/хранении, публичные Identity query/role API и событие подтверждения email с durable outbox; расширение Notifications API и delivery для приглашений с dedup key; внешний Registration/RoleOnboarding workflow и Scheduling guard для удаления связи; HTTP-auth граница с доверенным actorUserId. Эти изменения принадлежат соответствующим модулям/workflows, а не Tutoring Persistence. Политика хранения завершённых command operations и receipts не утверждена; v1 их автоматически не удаляет.
