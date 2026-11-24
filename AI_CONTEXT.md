═══════════════════════════════════════════════════════════
AI_CONTEXT — ПЛАН ЗАМЕРОВ (Android / Kotlin / Compose)
Дата актуализации: 25.11.2026
Git HEAD: dd4ad64 (origin/main, Actions #120 GREEN)
═══════════════════════════════════════════════════════════

ЭТОТ ФАЙЛ — ЕДИНАЯ ТОЧКА ВХОДА ДЛЯ ЛЮБОГО AI-АГЕНТА ИЛИ НОВОЙ СЕССИИ.
ЧИТАЙ ЭТОТ ФАЙЛ ПОЛНОСТЬЮ ПЕРЕД ЛЮБЫМИ ПРАВКАМИ КОДА.
Он фиксирует не только что сделано, но и какие ловушки уже наступали.

───────────────────────────────────────────────────────────
ПРОДУКТ
───────────────────────────────────────────────────────────
«План замеров» — Android-приложение для строительных бригад:
план-график замеров по дням, карточки клиентов (имя, телефон,
адрес, площадь, цена, источник «от кого», голосовая заметка),
статусы PLANNED/DONE/CANCELLED, напоминания через AlarmManager,
виджет на главном экране с листанием карточек, резервные копии
базы и голосовых.

ВАЖНО: это НЕ тот же проект, что m3pass / «ПесокШеринг»
(маркетплейс стройматериалов, Next.js/Fastify/PostgreSQL).
Разные репозитории, разные задачи, разные стеки. Не смешивай.

───────────────────────────────────────────────────────────
СТЕК И СБОРКА
───────────────────────────────────────────────────────────
Kotlin + Jetpack Compose (Material3), minSdk 24+, compileSdk 34+.
Хранение данных: SharedPreferences zamer_storage → ключ zamers_json
(JSON-массив объектов Zamer); голосовые .m4a в context.filesDir
как файлы voice_<id>.m4a.

Сборка APK: GitHub Actions, workflow .github/workflows/build.yml
(name: Build APK, triggers: push to main + workflow_dispatch).
JDK 17 temurin, Gradle 8.9, команда gradle assembleDebug --no-daemon
--stacktrace, артефакт app-debug-apk (zip c app-debug.apk внутри).

Рабочий цикл разработки («вариант Б»): правки в Termux на телефоне
→ пуш в GitHub → Actions собирает (~2–4 мин) → скачиваешь APK из
Artifacts. Локальный Android SDK НЕ ставится, телефон не греется.
Репозиторий публичный → Actions бесплатный без лимитов.

Git-доступ: classic PAT с scope repo лежит в ~/.git-credentials
(credential.helper=store). Пуш одной строкой без интерактивного read.

───────────────────────────────────────────────────────────
КАРТА МОДУЛЕЙ (package com.zamerplan.app)
───────────────────────────────────────────────────────────
MainActivity.kt (298 строк)
  ComponentActivity. onCreate/onResume/onStop.
  Обработка OAuth-возврата: handleBackupAuth(intent), onNewIntent.
  scheme zamerplan://backup-auth (redirect_uri для Яндекса).
  Вызывает BackupManager.startPeriodic / onAppForeground /
  onAppBackground / onDataChanged. Содержит MainScreen (календарь
  + список карточек за день + FAB добавления).

ui/SettingsScreen.kt (~1351 строка)
  Экран настроек: виджет (число карточек), тема, напоминания и звук
  (слоты ±, своё время WheelNumberPicker, playMode, мелодия
  RingtonePicker), резервные копии (карточка «☁️ Резервные копии»),
  источники «от кого», разрешения, диагностика виджета.
  Здесь сосредоточена основная работа по бэкапам.

ui/ прочие Composables:
  CollapsibleCalendar, ZamerCard, ZamerFormDialog, RescheduleDialog,
  Orange/цвета (TextPrimary, TextSecondary, DarkCardBg, DarkCardBorder,
  Green, Red, Blue, Gray).

alarm/:
  SettingsStore — настройки хранения: slotOn/slotMinutes/customTimeOn/
    customReminderTime/playMode/ringtoneUri/autoBackup/themeMode/sources.
  ReminderScheduler — AlarmManager, scheduleAll/cancel.
  ReminderReceiver — BroadcastReceiver, будильник.
  BootReceiver — восстановление алармов после перезагрузки.
  VoiceRecorder — запись/воспроизведение .m4a.

backup/BackupManager.kt (~1053 строки)
  Объект-движок резервных копий. См. раздел «РЕЗЕРВНЫЕ КОПИИ».

widget/:
  ZamerWidget — RemoteViews, пагинация NEXT_PAGE/PREV_PAGE, точки,
    fillCard. Лог пишется в filesDir/widget_log.txt.
  VoicePlaybackService — фоновое воспроизведение голоса.

model/:
  Storage — load()/save(list)/deleteVoice(id).
  Zamer — data class: id(Long), date(LocalDate), time(String HH:MM),
    timeEnd(String), name(String), phone(String), contactFrom(String),
    address(String), area(String), thickness(String), price(String),
    comment(String), voiceFile(String), status(ZamerStatus).
  ZamerStatus — enum: PLANNED, DONE, CANCELLED.

res/: layout/xml виджета, drawable, mipmap, themes.

───────────────────────────────────────────────────────────
РЕЗЕРВНЫЕ КОПИИ — АРХИТЕКТУРА
───────────────────────────────────────────────────────────
Типы направлений (поле Destination.type):
  "local"   — Загрузки через MediaStore (направление по умолчанию,
              НЕ удаляется крестиком, всегда присутствует в списке).
  "saf"     — файл на телефоне через SAF (ACTION_CREATE_DOCUMENT).
              НА MIUI КРИВО: DocumentsProvider принимает запись молча,
              рисует ✓, а файл пустой/битый. НЕ использовать как основной.
  "telegram"— чат с ботом через Bot API sendDocument/getFile.
  "yandex"  — Яндекс Диск через OAuth + cloud-api.yandex.net.
  "mailru"  — Облако Mail.ru через WebDAV (webdav.cloud.mail.ru).

Destination data class: type, uri, login, password, name, lastOk, lastError.

Формат архива (ZamerPlan_backup.zip):
  zamers.json  — массив всех замеров (JSON).
  meta.json    — formatVersion, appVersion, createdAt, zamerCount, voiceCount.
  voices/<file>— голосовые .m4a (если есть).

Движок BackupManager (ключевые функции):
  destinations(ctx)         — список направлений; local всегда первым.
  backupAll(ctx)            — сборка zip + рассылка во все направления.
  flush(ctx, force)         — автокопия: debounce 20с, throttle 5мин,
                              periodic 15мин, force при onStop.
  onDataChanged(ctx)        — ставит dirty=true, запускает debounce.
  startPeriodic/stopPeriodic— периодический таймер.
  onAppForeground/onAppBackground — триггеры автокопии.
  saveToLocalDownloads(ctx) — ручная кнопка «💾 Локально».
  restoreFromFile/Saf/Cloud — восстановление из разных источников.
  listHistory(ctx)          — список снапшотов истории (HistoryEntry).
  restoreHistoryUri(ctx,uri)— восстановление конкретного снапшота.

ИСТОРИЯ КОПИЙ (слой поверх зеркала, коммит Step C):
  Подпапка: Загрузки/ZamerPlan_backups/
  Имена: ZamerPlan_backup_YYYYMMDD_HHmmss.zip (метка времени).
  Ротация: keep=10 (pruneHistory удаляет старые автоматически).
  Привязка: writeHistoryCopy вызывается ВНУТРИ writeToLocalDownloads,
    т.е. история пишется при КАЖДОМ локальном сейве (кнопка + автокопия).
  Зеркало ZamerPlan_backup.zip остаётся отдельным файлом в корне
    «Загрузок» (для облаков/Telegram/шаринга — им нужен фиксированный путь).

Константы токенов (вверху BackupManager.kt, сейчас заглушки):
  YANDEX_CLIENT_ID = "ВСТАВЬ_CLIENT_ID_СЮДА"
  YANDEX_CLIENT_SECRET = "ВСТАВЬ_CLIENT_SECRET_СЮДА"
  YANDEX_REDIRECT = "zamerplan://backup-auth"
  TELEGRAM_BOT_TOKEN = "ВСТАВЬ_ТОКЕН_БОТА_СЮДА"
  TELEGRAM_BOT_USERNAME = "@твой_бот"
  Пока стоят заглушки — кнопки «➕ Telegram»/«➕ Яндекс» честно говорят
  «не настроено». Это ОЖИДАЕМОЕ поведение до вставки реальных ключей.

───────────────────────────────────────────────────────────
ТЕКУЩИЙ СТАТУС (по состоянию git dd4ad64)
───────────────────────────────────────────────────────────
✅ ЗЕЛЁНОЕ И ПРОВЕРЕНО НА ТЕЛЕФОНЕ:
  • Все 4 файла синхронны (BM/MA/SS/manifest), Actions #120 GREEN.
  • Манифест: INTERNET + zamerplan://backup-auth + singleTask.
  • MainActivity: handleBackupAuth + onNewIntent + автокопия hooks.
  • BackupManager: telegramConnect/telegramUpload/yandexConfigured/
    finishYandexConnect/webdavPut/webdavGet/backupAll/restoreFromFile.
  • Направление «local»/Загрузки по умолчанию (Step A): кнопки Копии/
    Вернуть активны всегда, автокопия пишет в Загрузки сама.
  • История копий (Step C): timestamped снапшоты в подпапке, ротация
    keep=10, затирание УМЕРЛО (скрин папки ZamerPlan_backups подтвердил
    3 разных файла с метками времени).
  • Фикс охраны восстановления (guard fix): restoreLocal проходит null-check.
  • Фикс setIncludePending (#120): удалён redundant вызов, компилятор
    доволен, баланс скобок ровный.
  • Чтение свежего зеркала работает (ступень 1 каскада: saved local_uri).
  • Виджет: пагинация, точки, fillCard, пересчёт при удалении — эталонно.
  • SettingsScreen: showCloudPicker=0, hintTitle=0, listCloudOptions=0
    (промежуточная версия полностью вытеснена).

❌ PENDING (не начато или требует доработки):
  • Шаг D3/D4 визуал: вертикальные кнопки с полными подписями вместо
    обрезанных «+», список статусов с иконками backupIcon, логика
    «подключённый сервис не просит пароль заново», SAF в подвал,
    уход дубля «Загрузки» в диалоге «Откуда восстановить»,
    кнопка «🕘 Из истории…» + диалог списка listHistory.
  • Токены разработчика: @BotFather → TELEGRAM_BOT_TOKEN;
    oauth.yandex.ru → YANDEX_CLIENT_ID/SECRET. Без них облака не работают.
  • contactFrom («от кого») не сохраняется в JSON — поле пустое
    даже после ввода в форме. Отдельная диагностика модели/формы.
  • Мультиюз-защита Telegram-бота: при одновременных START пользователи
    могут привязаться к чужому чату (getUpdates берёт последнее обновление
    без очистки offset). Для себя/бригады не критично, для раздачи — да.
  • Авто-обновление UI после восстановления: сейчас нужно свернуть/
    развернуть приложение, чтобы замеры появились в списке (onResume
    перечитывает базу). После Шага D можно добавить колбэк onRestored.
  • Старые копии (до #120) без saved local_uri не читаются ступенью 1
    каскада — это НЕ баг, а отсутствие адреса у доисторического файла.
    Лечится одним пересохранением «💾 Локально».
  • Подтверждение email в GitHub (жёлтое предупреждение в Actions) —
    не блокер сборки, но стоит сделать.

───────────────────────────────────────────────────────────
ИЗВЕСТНЫЕ ЛОВУШКИ И «ПОЧЕМУ ТАК»
───────────────────────────────────────────────────────────
1. MediaStore vs кривой DocumentsProvider MIUI:
   SAF (ACTION_CREATE_DOCUMENT) на оболочке Xiaomi/Redmi/POCO принимает
   запись молча, рисует зелёную галку ✓ со свежей датой, а файл на диске
   остаётся пустым или битым (суффикс «(1)»). При чтении — «не похож на
   копию или повреждён». Поэтому основной путь — MediaStore.Downloads
   (работает стабильно на той же оболочке). SAF оставлен как fallback
   для чужих экзотических устройств, где MediaStore тоже может быть зарезан.

2. Pending/суффиксы/owner-фильтр:
   MediaStore insert создаёт файл в состоянии IS_PENDING=1 (скрыт до
   публикации). Если забыть update на IS_PENDING=0 — обычный query по имени
   его не найдёт. Мы публикуем явно (put(IS_PENDING,0) после copyTo).
   Суффикс «(1)» появляется когда файл с таким именем уже существует —
   поэтому история использует UNIQUE имена с меткой времени.
   Owner-фильтр MIUI скрывает от query файлы, созданные не этим процессом —
   поэтому ступень 1 каскада чтения идёт по saved URI напрямую (обходит фильтр).

3. Парадокс сигнатуры setIncludePending:
   MediaStore.setIncludePending(ContentResolver) по документации принимает
   ContentResolver, но компилятор AGP/Kotlin/compileSdk в этой связке резолвит
   перегрузку, ожидающую Uri → Type mismatch. Удаление безопасно, потому что
   мы публикуем pending явно при записи, и includePending не нужен ни для
   зеркала, ни для истории. Ступени 1/2/4 каскада работают без него.

4. Перехват чата Telegram при мультиюзе:
   telegramConnect вызывает getUpdates и берёт ПОСЛЕДНЕЕ обновление.
   Если два пользователя одновременно нажали START — второй подключится
   к чату первого. Лечится уникальным кодом (/link XXXX) + очисткой offset.
   Для одного пользователя/бригады не критично.

5. Токен бота в APK:
   Любой технарь распакует APK и вытащит TELEGRAM_BOT_TOKEN. С ним сможет
   спамить от имени бота всем подключившимся и видеть факт подключения
   (имена/chat_id из getUpdates). Но украсть сами бэкапы НЕ сможет:
   у ботов нет доступа к истории чата (getChatHistory — юзер-аккаунты),
   file_id чужих копий из входящих обновлений не извлекаются.

6. Лимиты:
   Telegram Bot API: sendDocument ≤ 50 МБ, getFile ≤ 20 МБ.
   Яндекс Disk: upload unlimited (chunked если >128 МБ).
   Mail.ru WebDAV: зависит от тарифа.
   Local/MediaStore: limited by device storage.

7. Двойной блок «Загрузки» в диалоге «Откуда восстановить»:
   Сейчас там три рабочие строки про одно и то же: старая «📱 Из Загрузок»
   (prepareRestoreLocal) + новая «☁️ Загрузки» (направление local) +
   «📂 Файл: ...» (SAF). Это путает. Шаг D4 уберёт дубль, оставив историю
   как основной источник, зеркало как fallback, SAF в подвал.

───────────────────────────────────────────────────────────
СОГЛАШЕНИЯ РАБОТЫ С КОДОМ В TERMUX (СОБЛЮДАЙ СТРОГО)
───────────────────────────────────────────────────────────
• Большие файлы писать heredoc-блоками в одинарных кавычках (<<'ZP_X'),
  разбитыми на 2–4 части: первая > (создаёт), остальные >> (дописывают).
  Termux ОБРЕЗАЕТ одну гигантскую вставку (>~12KB надёжный предел).

• Точечные правки — awk по УНИКАЛЬНЫМ маркерам (grep -nF перед вставкой,
  убедиться что якорь один), либо splice head/tail по двум границам с
  печатью SPLICE OK/FAIL.

• ВАЛИДАЦИЯ ДО ПУША ОБЯЗАТЕЛЬНА:
  - баланс фигурных скобок (open == close)
  - счётчики маркеров (grep -cF)
  - once-checks соседних функций (=1, не задвоились/не съе́лись)
  - tail показывает закрывающую }

• НИКОГДА не вставлять многострочный блок, содержащий read -s, целиком —
  read съест следующую строку блока и всё поедет (было дважды).
  Интерактивный токен вводить ОДНОЙ физической строкой через &&.

• Пуш одной строкой:
  git add <явные пути> && git commit -m "..." && git push origin main
  && git log --oneline -2 && git status -sb
  Критерий успеха: новый коммит с меткой origin/main,
  ## main...origin/main без (ahead) и без M.

• [ci skip] в сообщении коммита — когда сборку гонять не надо.
  Для рабочих правок кода — НЕ ставить, нужен запуск Actions.

• Длинный multiline-блок Termux иногда ретраит ЦЕЛИКОМ → дубли кода.
  Поэтому после каждого блока проверяй once-checks; при обнаружении дубля
  чисти точечно (удаление по номеру строки второго вхождения),
  НЕ переписывай весь файл.

───────────────────────────────────────────────────────────
ПЛАН ОСТАТКА (ПОРЯДОК ВЫПОЛНЕНИЯ)
───────────────────────────────────────────────────────────
Шаг D3 — витрина подключения (SettingsScreen.kt):
  • Список статусов подключённых сервисов с иконками backupIcon(type).
  • Вертикальные кнопки подключения с полными подписями
    («Подключить Telegram», «Подключить Яндекс Диск»,
    «Подключить Облако Mail.ru») вместо обрезанных горизонтальных «+».
  • Логика «подключённый сервис не просит пароль заново»:
    если dests.any { it.type == X }, тап показывает статус +
    «отключить / переподключить», а не пустую форму email/пароль.
  • SAF спрятан в подвальную ссылку «📂 Другая папка (если Загрузки не работают)».
  • Иконки: вектор ImageVector (путь X), цвета брендовые:
    Mail.ru синий #0C6DF2 + оранжевая @ #FFCC33;
    Яндекс красный круг #FC3F1D + белая Я;
    Telegram голубой круг #2AABEE + белый самолётик;
    Загрузки серо-синяя папка #607D8B; SAF серый файл #9E9E9E.

Шаг D4 — диалог истории + cleanup (SettingsScreen.kt):
  • Кнопка «🕘 Из истории…» открывает AlertDialog со списком listHistory
    (дата+размер на строке, сортировка свежие сверху).
  • Тап по строке → подтверждение с meta.json → restoreHistoryUri.
  • «📥 Вернуть» по умолчанию = последняя копия из истории (быстрый откат).
  • Убирается дубль «Загрузки» в диалоге «Откуда восстановить».
  • Старые пункты «Из Загрузок / ☁️ Загрузки» (зеркало) остаются внизу
    как fallback, но основным становится история.

Шаг E — contactFrom (model/ + ui/ZamerFormDialog):
  • Диагностика: почему поле contactFrom пустое в JSON даже после ввода.
  • Вероятные причины: форма кладёт в другое поле / модель не маппит при
    сериализации / Storage.save теряет поле. Починить точечно.

Шаг F — токены (@BotFather + oauth.yandex.ru):
  • @BotFather → /newbot → токен → вставить в TELEGRAM_BOT_TOKEN/USERNAME.
  • oauth.yandex.ru → создать приложение → ClientID + Secret → вставить
    в YANDEX_CLIENT_ID/YANDEX_CLIENT_SECRET.
  • Redirect URI: zamerplan://backup-auth (уже в манифесте).
  • После вставки — пуш → Actions → APK → облака оживают.

Шаг G — мультиюз-защита бота (BackupManager.telegramConnect):
  • Генерация уникального кода при подключении (/link XXXX).
  • Поиск в getUpdates именно этого кода, а не последнего обновления.
  • Очистка offset после успешного подключения.
  • Только если планируется раздавать приложение незнакомым людям.

Шаг H — авто-обновление UI после восстановления:
  • Колбэк onRestored в SettingsScreen → MainActivity.reloadZamers().
  • Пользователь видит результат без сворачивания приложения.

───────────────────────────────────────────────────────────
КАК ТЕСТИРОВАТЬ ЛОКАЛЬНУЮ КОПИЮ (после каждой сборки)
───────────────────────────────────────────────────────────
1. Ставишь APK.
2. ЖМЁШЬ «💾 Локально» ПЕРВЫМ ДЕЛОМ (сохраняет fresh local_uri).
3. «📥 Вернуть» → «Загрузки» → подтверждение → «Восстановить».
4. Ожидаемый отчёт: «Готово! Восстановлено замеров: N…».
5. Сверни домой → открой снова → замеры на месте.
6. Для проверки истории: повтори шаги 2–3 несколько раз с изменениями
   между ними → файловик → Загрузки → ZamerPlan_backups → несколько
   файлов с разными метками времени (затирание умерло).
7. Для проверки ротации: насоздавай 12+ копий → в папке останется ровно 10.

───────────────────────────────────────────────────────────
КЛЮЧЕВЫЕ КОММИТЫ ИСТОРИИ (chronological)
───────────────────────────────────────────────────────────
91f01ba Finalize backups: Telegram + Yandex OAuth + smart auto-copy
        (BM/MA/SS/manifest synced) — базовая финальная версия.
dd26fe4 Add local backup to Downloads via MediaStore (save + restore) — Steps 5–7.
4cf6cce Fix local restore guard: allow restoreLocal past d/u null check — Step 9.
4b96e13 Engine: make Downloads a default backup destination — Step A.
6d989a6 Engine fix: remember MediaStore uri + publish pending, robust
        4-stage local read — Step #119 (FAILED compile).
dd4ad64 Engine fix: drop redundant setIncludePending — unblock #119 → #120 GREEN.
[Step C] Engine: backup history layer (timestamped copies, rotation keep=10).
[D1-D2] Icons + History UI variables/functions/button (in progress, not pushed).

───────────────────────────────────────────────────────────
РАЗМЕРЫ ФАЙЛОВ (на момент dd4ad64 + Step C)
───────────────────────────────────────────────────────────
BackupManager.kt     ~1053 строки
SettingsScreen.kt    ~1351 строка
MainActivity.kt       ~298 строк
AndroidManifest.xml    ~76 строк
build.yml             ~40 строк (GitHub Actions workflow)

═══════════════════════════════════════════════════════════
КОНЕЦ ФАЙЛА AI_CONTEXT.txt
═══════════════════════════════════════════════════════════
