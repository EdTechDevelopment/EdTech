# Notifications

Notifications владеет очередями доставки, SMTP, шаблонами писем и техническими статусами. Бизнес-решение о необходимости письма принимает вызывающий модуль; его Infrastructure вызывает только публичный Notifications API.

Существующий NotificationGateway/SendVerificationEmailCommand обслуживает подтверждение account email Identity. Для Tutoring реализованы отдельный узкий InvitationNotificationGateway/SendStudentInvitationCommand и notification_invitation_email_deliveries (V7). В заявку входят invitationId, recipientEmail, teacherDisplayName, expiresAt и deduplicationKey. Enqueue требует действующей общей транзакции; повторный ключ не создаёт вторую заявку.

ProcessInvitationEmailDeliveriesService захватывает заявки в короткой транзакции, выполняет SMTP после commit и отдельно записывает результат. Claim ID защищает ack старого обработчика; просроченный claim возвращается в очередь. Временная ошибка повторяется, постоянная даёт FAILED, истёкшее приглашение не отправляется. В терминальном статусе URL удаляется.

Ссылка ведёт на будущую страницу frontend /invitations/{id} и передаёт email для фиксированного регистрационного поля. Право принятия проверяет Tutoring по привязанному аккаунту, а не по URL. Контактный email профиля не подтверждается и писем не вызывает.

При падении после SMTP и до SENT возможно повторное письмо: exactly-once для SMTP не обещается. [ADR delivery](../../decisions/tutoring-durable-delivery.md) описывает транзакции, retry и ограничения; [отчёт Tutoring](../tutoring/TUTORING_STAGE_5_IMPLEMENTATION_REPORT.md) — тесты и статус.
