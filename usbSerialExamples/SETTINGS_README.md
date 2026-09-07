# Система настроек приложения

## Обзор

Реализована система настроек с поддержкой индивидуальных настроек для каждого приложения.

**Значения по умолчанию для новых приложений:**
- Оверлей: **включен**
- Уведомления: **включены**
- Позиция по горизонтали: **50%** (центр)
- Позиция по вертикали: **5%** (верх экрана)

## Особенности

- ✅ **Иконки приложений** отображаются во всех списках для лучшей визуализации
- ✅ **Асинхронная загрузка** списка приложений с индикатором прогресса
- ✅ **Расширенный список приложений** - показываются все пользовательские приложения и приложения с launcher activity
- ✅ **Разрешение QUERY_ALL_PACKAGES** для доступа ко всем приложениям на Android 11+
- ✅ **Режим редактирования** с множественным выбором
- ✅ **Настройка позиции оверлея** через ползунки (0-100%)

## Структура

### Activity

1. **ConfiguredAppsListActivity** - Список настроенных приложений
   - Отображает все приложения с индивидуальными настройками
   - **Иконки приложений** для визуальной идентификации
   - Режим редактирования с множественным выбором через чекбоксы
   - Кнопка удаления выбранных приложений с подтверждением
   - Кнопка добавления нового приложения в заголовке

2. **AllAppsListActivity** - Список всех приложений на телефоне
   - **Асинхронная загрузка** списка приложений с индикатором прогресса
   - Показывает все пользовательские приложения и приложения с launcher activity
   - **Иконки приложений** для лучшей визуализации
   - Отсортированы по имени
   - При выборе приложение добавляется в настроенные
   - Использует разрешение QUERY_ALL_PACKAGES для полного доступа

3. **AppSpecificSettingsActivity** - Настройки конкретного приложения
   - **Иконка и название приложения** в заголовке
   - Включение/отключение оверлея
   - Включение/отключение уведомлений
   - Ползунки для настройки позиции оверлея (горизонталь и вертикаль, 0-100%)
   - Возможность ввода значения позиции с клавиатуры
   - Кнопка сброса позиции оверлея к центру (50%, 50%)
   - Кнопка удаления настроек приложения в заголовке

### Классы данных

- **AppSettings** - Модель настроек приложения с полями:
  - packageName, appName
  - overlayEnabled, notificationsEnabled
  - overlayPositionXPercent, overlayPositionYPercent (0-100)
  - isSelected (для режима редактирования)
  
- **SettingsManager** - Менеджер для работы с настройками (SharedPreferences)

### Адаптеры

- **ConfiguredAppsAdapter** - Адаптер для списка настроенных приложений с поддержкой режима редактирования
- **AllAppsAdapter** - Адаптер для списка всех приложений

## Маршруты навигации

1. Main → Список настроенных приложений → Список всех приложений
2. Main → Список настроенных приложений → Настройки конкретного приложения

## Режим редактирования

В списке настроенных приложений:
- Кнопка "Редактировать" в заголовке включает режим редактирования
- В режиме редактирования появляются чекбоксы для выбора приложений
- Кнопки в заголовке меняются на "Удалить" и "Отменить"
- При нажатии "Удалить" показывается модальное окно подтверждения
- При подтверждении удаляются все выбранные приложения
- Кнопка "Отменить" выходит из режима редактирования

## Хранение данных

Все настройки сохраняются в **SharedPreferences** с именем `AppSettingsPrefs`:

### Настройки приложений
- `app_settings` (JSON string) - массив настроек для конкретных приложений

Формат JSON для настроек приложений:
```json
[
  {
    "packageName": "com.example.app",
    "appName": "Example App",
    "overlayEnabled": true,
    "notificationsEnabled": false,
    "overlayPositionXPercent": 50,
    "overlayPositionYPercent": 5
  }
]
```

## API SettingsManager

### Настройки приложений
```java
List<AppSettings> getAppSettingsList()
AppSettings getAppSettings(String packageName)
void addOrUpdateAppSettings(AppSettings settings)
void removeAppSettings(String packageName)
```

### Эффективные настройки
```java
boolean isOverlayEnabledForApp(String packageName)
boolean isNotificationsEnabledForApp(String packageName)
int getOverlayPositionXPercentForApp(String packageName)
int getOverlayPositionYPercentForApp(String packageName)
```

Эти методы возвращают настройки для конкретного приложения, если они есть, иначе возвращают значения по умолчанию:
- `isOverlayEnabledForApp`: **true** (включено)
- `isNotificationsEnabledForApp`: **true** (включено)
- `getOverlayPositionXPercentForApp`: **50** (центр по горизонтали)
- `getOverlayPositionYPercentForApp`: **5** (верх экрана)

## Использование

### Проверка настроек в коде

```java
SettingsManager settingsManager = new SettingsManager(context);

// Проверить, нужно ли показывать оверлей для текущего приложения
String currentPackage = getCurrentAppPackageName();
if (settingsManager.isOverlayEnabledForApp(currentPackage)) {
    // Показать оверлей
    int xPercent = settingsManager.getOverlayPositionXPercentForApp(currentPackage);
    int yPercent = settingsManager.getOverlayPositionYPercentForApp(currentPackage);
    // Вычислить абсолютные координаты на основе процентов
}

// Проверить, нужно ли показывать уведомления
if (settingsManager.isNotificationsEnabledForApp(currentPackage)) {
    // Показать уведомление
}
```

### Вычисление позиции оверлея

```java
SettingsManager settingsManager = new SettingsManager(context);
String currentPackage = getCurrentAppPackageName();

int xPercent = settingsManager.getOverlayPositionXPercentForApp(currentPackage); // по умолчанию 50
int yPercent = settingsManager.getOverlayPositionYPercentForApp(currentPackage); // по умолчанию 5

// Получить размеры экрана
DisplayMetrics displayMetrics = new DisplayMetrics();
windowManager.getDefaultDisplay().getMetrics(displayMetrics);
int screenWidth = displayMetrics.widthPixels;
int screenHeight = displayMetrics.heightPixels;

// Вычислить абсолютные координаты
int x = (screenWidth * xPercent) / 100;
int y = (screenHeight * yPercent) / 100;

// Установить позицию оверлея
WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlayView.getLayoutParams();
params.x = x;
params.y = y;
windowManager.updateViewLayout(overlayView, params);
```

**Примеры позиций:**
- X=50%, Y=5% → центр по горизонтали, верх экрана (по умолчанию)
- X=0%, Y=0% → левый верхний угол
- X=100%, Y=100% → правый нижний угол
- X=50%, Y=50% → центр экрана

## Особенности UI

### Ползунки позиции
- Диапазон: 0-100%
- 0% - левый край (X) / верх (Y)
- 50% - центр (по умолчанию для X)
- 5% - верх экрана (по умолчанию для Y)
- 100% - правый край (X) / низ (Y)
- Синхронизация между ползунком и полем ввода
- Автоматическое ограничение значений в диапазоне 0-100
- Кнопка "Сбросить позицию" устанавливает X=50%, Y=5%

### Модальные окна подтверждения
- При удалении из списка настроенных приложений
- При удалении из настроек конкретного приложения
- Показывают количество удаляемых приложений или название приложения

## Производительность

### Асинхронная загрузка приложений

Список всех приложений загружается асинхронно с использованием `AsyncTask`:

1. **При открытии AllAppsListActivity:**
   - Сразу отображается индикатор загрузки (ProgressBar)
   - Текст "Загрузка приложений..."
   - Список приложений скрыт

2. **В фоновом потоке:**
   - Загружаются все установленные приложения
   - Фильтруются по критериям
   - Загружаются иконки
   - Сортируются по имени

3. **После завершения загрузки:**
   - Индикатор скрывается
   - Отображается список приложений
   - Пользователь может выбрать приложение

Это обеспечивает плавный UX без зависания интерфейса.

## Разрешения

В `AndroidManifest.xml` добавлено разрешение:
```xml
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
```

Это разрешение необходимо для доступа к полному списку установленных приложений на Android 11 и выше. Без него будут видны только некоторые приложения.

**Важно:** При публикации в Google Play Store это разрешение требует специального обоснования. Укажите, что приложение использует список приложений для настройки индивидуальных параметров отображения оверлея.

## Добавленные зависимости

В `build.gradle` добавлена зависимость:
```gradle
implementation 'androidx.recyclerview:recyclerview:1.3.2'
```

## Регистрация в AndroidManifest.xml

Все Activity зарегистрированы с правильной иерархией:
- ConfiguredAppsListActivity → parentActivityName: MainActivity
- AllAppsListActivity → parentActivityName: ConfiguredAppsListActivity
- AppSpecificSettingsActivity → parentActivityName: ConfiguredAppsListActivity
