# Документация по эмуляции термометра

## 📚 Навигация по документации

Эта документация описывает систему эмуляции термометра для приложения USB Serial.

### Быстрый старт

- **[QUICK_START_EMULATOR.md](QUICK_START_EMULATOR.md)** - Начните здесь! Быстрое руководство по использованию эмулятора

### Основная документация

- **[EMULATOR_README.md](EMULATOR_README.md)** - Подробное описание архитектуры и реализации
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Диаграммы и детальная архитектура системы
- **[CHANGES_SUMMARY.md](CHANGES_SUMMARY.md)** - Полный список изменений в коде

### Примеры и рецепты

- **[API_EXAMPLES.md](API_EXAMPLES.md)** - Примеры использования API для различных сценариев
- **[FAQ.md](FAQ.md)** - Часто задаваемые вопросы и решения проблем

## 🎯 Что это такое?

Система эмуляции термометра позволяет тестировать приложение без физического USB термометра. Эмулятор генерирует реалистичные данные о температуре и полностью интегрирован в приложение.

## ✨ Ключевые возможности

- ✅ Работа без физического устройства
- ✅ Реалистичная генерация температуры (15-35°C)
- ✅ Плавное изменение значений
- ✅ Полная интеграция с существующим кодом
- ✅ Легкое переключение между реальным и эмулированным устройством
- ✅ Не требует изменений в библиотеке usbSerialForAndroid

## 🚀 Быстрый старт

1. Соберите проект:
   ```bash
   ./gradlew clean assembleDebug
   ```

2. Установите на устройство:
   ```bash
   ./gradlew installDebug
   ```

3. Запустите приложение и выберите "Emulated Thermometer" из списка устройств

4. Наблюдайте за обновлением температуры каждую секунду

## 📖 Рекомендуемый порядок чтения

### Для начинающих

1. [QUICK_START_EMULATOR.md](QUICK_START_EMULATOR.md) - узнайте, как использовать эмулятор
2. [FAQ.md](FAQ.md) - ответы на частые вопросы
3. [API_EXAMPLES.md](API_EXAMPLES.md) - простые примеры использования

### Для разработчиков

1. [EMULATOR_README.md](EMULATOR_README.md) - понимание архитектуры
2. [ARCHITECTURE.md](ARCHITECTURE.md) - детальная архитектура
3. [CHANGES_SUMMARY.md](CHANGES_SUMMARY.md) - что именно изменилось
4. [API_EXAMPLES.md](API_EXAMPLES.md) - расширенные примеры

### Для контрибьюторов

1. [ARCHITECTURE.md](ARCHITECTURE.md) - понимание структуры
2. [CHANGES_SUMMARY.md](CHANGES_SUMMARY.md) - текущее состояние
3. [API_EXAMPLES.md](API_EXAMPLES.md) - примеры расширения
4. [FAQ.md](FAQ.md) - известные проблемы

## 🏗️ Архитектура (кратко)

```
IThermometerDevice (интерфейс)
    ├── RealThermometerDevice (реальное USB устройство)
    └── EmulatedThermometerDevice (эмулятор)
```

Оба типа устройств используют один интерфейс, что позволяет легко переключаться между ними.

## 📁 Структура файлов

### Исходный код

```
usbSerialExamples/src/main/java/com/hoho/android/usbserial/examples/
├── IThermometerDevice.java           # Интерфейс устройства
├── RealThermometerDevice.java        # Реальное USB устройство
├── EmulatedThermometerDevice.java    # Эмулятор
├── DevicesFragment.java              # Список устройств (изменен)
└── UsbService.java                   # Сервис (изменен)
```

### Документация

```
usbSerialExamples/
├── EMULATOR_INDEX.md                 # Этот файл - навигация
├── QUICK_START_EMULATOR.md           # Быстрый старт
├── EMULATOR_README.md                # Основная документация
├── ARCHITECTURE.md                   # Архитектура
├── CHANGES_SUMMARY.md                # Список изменений
├── API_EXAMPLES.md                   # Примеры кода
└── FAQ.md                            # Вопросы и ответы
```

## 🔧 Основные компоненты

### IThermometerDevice
Интерфейс для работы с термометром (реальным или эмулированным).

**Ключевые методы:**
- `connect()` - подключение
- `disconnect()` - отключение
- `sendCommand(String)` - отправка команды
- `setDataListener(ThermometerDataListener)` - установка слушателя

### RealThermometerDevice
Обертка над существующим кодом работы с USB. Не изменяет оригинальную логику.

### EmulatedThermometerDevice
Генератор случайных температур с реалистичным поведением.

**Параметры:**
- Диапазон: 15-35°C
- Обновление: каждую секунду
- Плавное изменение к целевой температуре

## 💡 Примеры использования

### Создание эмулятора

```java
IThermometerDevice emulator = new EmulatedThermometerDevice();
emulator.setDataListener(new IThermometerDevice.ThermometerDataListener() {
    @Override
    public void onTemperatureReceived(String temperature) {
        Log.d("Temp", "Received: " + temperature + "°C");
    }
    // ... другие методы
});
emulator.connect();
```

### Создание реального устройства

```java
IThermometerDevice realDevice = new RealThermometerDevice(
    context, deviceId, portNum, baudRate, true
);
realDevice.setDataListener(listener);
realDevice.connect();
```

Больше примеров в [API_EXAMPLES.md](API_EXAMPLES.md).

## 🐛 Решение проблем

### Эмулятор не появляется в списке

1. Пересоберите проект: `./gradlew clean assembleDebug`
2. Проверьте, что изменения в `DevicesFragment` применены
3. Смотрите логи: `adb logcat -s DevicesFragment:D`

### Температура не обновляется

1. Проверьте подключение: `device.isConnected()`
2. Убедитесь, что слушатель установлен перед `connect()`
3. Смотрите логи: `adb logcat -s EmulatedThermometer:D`

Больше решений в [FAQ.md](FAQ.md).

## 📊 Статистика изменений

- **Новых файлов:** 3 (IThermometerDevice, RealThermometerDevice, EmulatedThermometerDevice)
- **Измененных файлов:** 2 (DevicesFragment, UsbService)
- **Строк кода добавлено:** ~800
- **Строк документации:** ~2000
- **Библиотек изменено:** 0

## 🎓 Обучающие материалы

### Видео (если будут)
- Введение в эмуляцию термометра
- Создание собственного типа устройства
- Интеграция в существующий проект

### Статьи
- Паттерн Strategy в Android
- Тестирование USB устройств без оборудования
- Архитектура расширяемых приложений

## 🤝 Вклад в проект

Мы приветствуем вклад в проект! Вот как вы можете помочь:

1. **Сообщить об ошибке** - создайте issue с описанием проблемы
2. **Предложить улучшение** - создайте feature request
3. **Написать код** - отправьте pull request
4. **Улучшить документацию** - исправьте опечатки или добавьте примеры

### Идеи для улучшения

- [ ] UI для настройки параметров эмуляции
- [ ] Поддержка нескольких эмуляторов одновременно
- [ ] Запись и воспроизведение данных
- [ ] Графики температуры в реальном времени
- [ ] Экспорт данных в CSV/JSON
- [ ] Профили эмуляции (стабильная, растущая, падающая)
- [ ] Эмуляция ошибок и отключений
- [ ] Bluetooth термометр
- [ ] Network термометр

## 📞 Контакты и поддержка

- **Issues:** Создайте issue в репозитории
- **Discussions:** Обсудите в разделе Discussions
- **Email:** (если есть)

## 📜 Лицензия

Этот код распространяется под той же лицензией, что и основной проект.

## 🙏 Благодарности

- Авторам библиотеки usbSerialForAndroid
- Сообществу Android разработчиков
- Всем, кто тестирует и улучшает этот код

## 📅 История версий

### Версия 1.0 (2026-05-01)
- ✅ Первый релиз
- ✅ Базовая эмуляция термометра
- ✅ Интерфейс IThermometerDevice
- ✅ Интеграция с DevicesFragment и UsbService
- ✅ Полная документация

### Планы на будущее
- Версия 1.1: UI для настроек эмуляции
- Версия 1.2: Поддержка нескольких эмуляторов
- Версия 2.0: Запись и воспроизведение данных

## 🔗 Полезные ссылки

- [Основной README проекта](../README.md)
- [Документация usbSerialForAndroid](https://github.com/mik3y/usb-serial-for-android)
- [Android Developer Guide](https://developer.android.com/)

---

**Начните с [QUICK_START_EMULATOR.md](QUICK_START_EMULATOR.md) для быстрого старта!** 🚀
