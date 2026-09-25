# Workflows

Workflows координирует процессы, затрагивающие несколько модулей; обычная логика одного модуля остаётся у его владельца. Общие правила приведены в [архитектуре backend](../BACKEND_ARCHITECTURE.md).

Согласованные для Tutoring внешние процессы: `RegistrationWorkflow`, `RoleOnboardingWorkflow`, `UnlinkStudentWorkflow`, `MeQueryFacade` и `StudentCardQueryFacade`. Их порядок вызовов и транзакционные требования описаны в [итоговой архитектуре Tutoring](../tutoring/TUTORING_ARCHITECTURE.md). Доверенные команды Tutoring вызываются из workflow в общей транзакции; прямыми пользовательскими endpoint они не являются. Отдельная полная спецификация внутреннего устройства Workflows в этом каталоге пока не утверждена.
