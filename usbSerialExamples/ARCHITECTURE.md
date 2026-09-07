# Архитектура системы эмуляции термометра

## Диаграмма компонентов

```
┌─────────────────────────────────────────────────────────────────┐
│                        DevicesFragment                          │
│  - Отображает список устройств (реальных + эмулятор)           │
│  - Сохраняет выбор в SharedPreferences                         │
│  - Запускает/перезапускает UsbService                          │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         │ startService()
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                          UsbService                             │
│  - Читает настройки из SharedPreferences                       │
│  - Создает нужный тип устройства (Real/Emulated)              │
│  - Управляет уведомлениями и оверлеем                         │
│  - Обрабатывает данные от устройства                          │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         │ создает и управляет
                         ▼
              ┌──────────────────────┐
              │ IThermometerDevice   │
              │   (интерфейс)        │
              │                      │
              │ + connect()          │
              │ + disconnect()       │
              │ + sendCommand()      │
              │ + setDataListener()  │
              └──────────┬───────────┘
                         │
         ┌───────────────┴───────────────┐
         │                               │
         ▼                               ▼
┌────────────────────┐         ┌────────────────────┐
│RealThermometer     │         │EmulatedThermometer │
│Device              │         │Device              │
│                    │         │                    │
│- UsbSerialPort     │         │- Handler           │
│- SerialIOManager   │         │- Random            │
│- BroadcastReceiver │         │- Timer             │
│                    │         │                    │
│Работает с          │         │Генерирует          │
│физическим USB      │         │случайные данные    │
└────────────────────┘         └────────────────────┘
```

## Поток данных

### Подключение к устройству

```
User clicks device
       │
       ▼
DevicesFragment.onListItemClick()
       │
       ├─► Saves to SharedPreferences:
       │   - deviceId (999999 для эмулятора)
       │   - isEmulated (true/false)
       │   - port, baud, withIoManager
       │
       ▼
Restarts UsbService
       │
       ▼
UsbService.onCreate()
       │
       ├─► Reads SharedPreferences
       │
       ├─► if (isEmulated)
       │       creates EmulatedThermometerDevice
       │   else
       │       creates RealThermometerDevice
       │
       ▼
UsbService.onStartCommand()
       │
       ▼
thermometerDevice.connect()
       │
       ├─► RealThermometerDevice:
       │   - Requests USB permission
       │   - Opens USB connection
       │   - Starts SerialIOManager
       │
       └─► EmulatedThermometerDevice:
           - Immediately "connects"
           - Starts temperature timer
```

### Получение данных

```
┌─────────────────────────────────────────────────────────┐
│                  RealThermometerDevice                  │
│                                                         │
│  USB Device → SerialIOManager → onNewData()            │
│                                      │                  │
│                                      ▼                  │
│                              Parse "~G25.5"            │
│                                      │                  │
│                                      ▼                  │
│                      dataListener.onTemperatureReceived()│
└─────────────────────────────────────┬───────────────────┘
                                      │
                                      │
┌─────────────────────────────────────┴───────────────────┐
│                EmulatedThermometerDevice                │
│                                                         │
│  Timer (1s) → updateTemperature()                      │
│                      │                                  │
│                      ▼                                  │
│              Generate random temp                       │
│                      │                                  │
│                      ▼                                  │
│      dataListener.onTemperatureReceived()              │
└─────────────────────────────────────┬───────────────────┘
                                      │
                                      │ Both call
                                      ▼
                    ┌─────────────────────────────┐
                    │ UsbService.status()         │
                    │                             │
                    │ - Updates notification      │
                    │ - Updates overlay           │
                    │ - Shows custom notification │
                    └─────────────────────────────┘
```

## Классы и их ответственность

### IThermometerDevice (интерфейс)
**Ответственность:** Определяет контракт для работы с термометром

**Методы:**
- `connect()` - подключение к устройству
- `disconnect()` - отключение
- `isConnected()` - проверка состояния
- `sendCommand(String)` - отправка команды
- `setDataListener()` - установка слушателя данных

### RealThermometerDevice
**Ответственность:** Работа с физическим USB термометром

**Зависимости:**
- `UsbManager` - управление USB
- `UsbSerialPort` - связь с устройством
- `SerialInputOutputManager` - асинхронное чтение/запись
- `BroadcastReceiver` - получение разрешений USB

**Особенности:**
- Не изменяет существующую логику
- Обертка над оригинальным кодом
- Обрабатывает разрешения USB
- Парсит протокол "~G{temperature}"

### EmulatedThermometerDevice
**Ответственность:** Эмуляция термометра без физического устройства

**Зависимости:**
- `Handler` - для таймера
- `Random` - для генерации данных

**Особенности:**
- Плавное изменение температуры
- Случайный выбор целевой температуры
- Добавление шума для реалистичности
- Обновление каждую секунду

### UsbService
**Ответственность:** Управление подключением и отображением данных

**Функции:**
- Создание нужного типа устройства
- Обработка данных от устройства
- Управление уведомлениями
- Управление оверлеем
- Мониторинг активного приложения
- Применение настроек для приложений

### DevicesFragment
**Ответственность:** Отображение списка устройств и выбор

**Функции:**
- Сканирование USB устройств
- Добавление эмулятора в список
- Сохранение выбора пользователя
- Запуск UsbService

## Расширяемость

### Добавление нового типа устройства

1. Создайте класс, реализующий `IThermometerDevice`
2. Реализуйте все методы интерфейса
3. Добавьте логику подключения и получения данных
4. Обновите `DevicesFragment.refresh()` для добавления в список
5. Обновите `UsbService.onCreate()` для создания экземпляра

### Пример: Bluetooth термометр

```java
public class BluetoothThermometerDevice implements IThermometerDevice {
    private BluetoothSocket socket;
    private InputStream inputStream;
    
    @Override
    public boolean connect() {
        // Подключение через Bluetooth
        return true;
    }
    
    @Override
    public void sendCommand(String command) {
        // Отправка через Bluetooth
    }
    
    // ... остальные методы
}
```

## Преимущества архитектуры

✅ **Разделение ответственности** - каждый класс имеет четкую роль  
✅ **Расширяемость** - легко добавить новые типы устройств  
✅ **Тестируемость** - можно тестировать без физического оборудования  
✅ **Обратная совместимость** - существующий код не изменен  
✅ **Гибкость** - легко переключаться между устройствами  

## Недостатки и ограничения

⚠️ **Дублирование кода** - некоторая логика дублируется между Real и Emulated  
⚠️ **Зависимость от SharedPreferences** - настройки хранятся в одном месте  
⚠️ **Нет фабрики** - создание устройств в UsbService (можно вынести в Factory)  

## Возможные улучшения

1. **Factory Pattern** - создать фабрику для устройств
2. **Dependency Injection** - использовать DI для управления зависимостями
3. **Repository Pattern** - абстрагировать работу с SharedPreferences
4. **State Machine** - управление состояниями подключения
5. **RxJava/Coroutines** - для асинхронной работы
