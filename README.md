# superkassa-core

Главный сервис и библиотека бизнес-логики кассы. Здесь собраны сценарии смены, чеки, X/Z отчеты, счетчики и интеграции с storage/queue/ofd/delivery через порты. В отдельном режиме может запускаться как HTTP сервис.

## Что есть внутри
- доменная модель кассы, смены, чека и отчета;
- порты для хранилища, очереди, доставки, ODF-менеджера;
- политики обновления счетчиков;
- HTTP API со Swagger для интеграции с внешними системами.

## Архитектура
- `domain` — модели и порты.
- `application` — сервисы и политики.
- `data` — адаптеры к storage/queue/ofd/delivery.
- `application/http` — HTTP API и запуск.

## HTTP API (Swagger)
Запуск сервера:
```bash
./gradlew run
```

Доступно:
- `GET /swagger` — Swagger UI
- `GET /openapi.yaml` — спецификация
- `GET /health` — health check
- `GET /config` / `PUT /config` — настройка ядра
- `POST /cashboxes` — регистрация кассы
- `GET /cashboxes` — список касс
- `GET /cashboxes/{cashboxId}` — касса по id
- `DELETE /cashboxes/{cashboxId}` — удаление кассы
- `POST /cashboxes/{cashboxId}/shift/open`
- `POST /cashboxes/{cashboxId}/shift/close`
- `POST /cashboxes/{cashboxId}/reports/x`
- `POST /cashboxes/{cashboxId}/receipts`

Пример конфигурации (`config/core-settings.json`):
```json
{
  "mode": "DESKTOP",
  "storage": {
    "engine": "SQLITE",
    "jdbcUrl": "jdbc:sqlite:build/core.db",
    "user": null,
    "password": null
  },
  "ofd": {
    "provider": "kazakhtelecom",
    "deviceId": 201873,
    "token": 208627316,
    "protocolVersion": "203"
  },
  "allowChanges": true
}
```

## Библиотечный режим
```kotlin
val service = CashboxService(
    storage = storagePort,
    queue = queuePort,
    ofd = ofdPort,
    delivery = deliveryPort,
    counters = DefaultCounterUpdater(storagePort),
    idGenerator = UuidGenerator,
    clock = SystemClock
)
```

## Первый запуск
Перед запуском создайте конфиг (файл не коммитится в git):
```bash
cp config/core-settings.example.json config/core-settings.json
# при необходимости отредактируйте config/core-settings.json (БД, ОФД и т.д.)
```

## Публикация на GitHub
1. Настройте имя и email (если ещё не настроено глобально):
   ```bash
   git config user.name "Ваше Имя"
   git config user.email "your@email.com"
   ```
2. Создайте новый репозиторий на GitHub (без README, без .gitignore).
3. Выполните в каталоге проекта:
   ```bash
   git add .
   git commit -m "Initial commit: Superkassa Core"
   git branch -M main
   git remote add origin https://github.com/ВАШ_ЛОГИН/superkassa-core.git
   git push -u origin main
   ```
   Подставьте свой URL репозитория вместо `ВАШ_ЛОГИН/superkassa-core`. Для SSH: `git@github.com:ВАШ_ЛОГИН/superkassa-core.git`.

Файл `config/core-settings.json` в git не попадает (в .gitignore) — используйте `config/core-settings.example.json` как шаблон.

## Документация
Подробные сценарии и интеграции описаны в `docs/core_service_guide.md`.
