# FAQ - Часто задаваемые вопросы

## Общие вопросы

### Q: Зачем нужен эмулятор термометра?

**A:** Эмулятор позволяет разрабатывать и тестировать приложение без физического термометра. Это особенно полезно когда:
- У вас нет доступа к физическому устройству
- Вы хотите протестировать различные сценарии (высокая/низкая температура)
- Нужно быстро проверить изменения в коде
- Разработка ведется на эмуляторе Android без USB

### Q: Изменялась ли библиотека usbSerialForAndroid?

**A:** Нет, библиотека не изменялась. Весь код работы с USB остался без изменений и просто обернут в интерфейс `IThermometerDevice`.

### Q: Можно ли использовать эмулятор и реальное устройство одновременно?

**A:** Нет, в текущей реализации можно использовать только одно устройство за раз. Для переключения нужно выбрать другое устройство из списка.

### Q: Как отличить эмулятор от реального устройства в списке?

**A:** Эмулятор отображается как "Emulated Thermometer" с подписью "Test Device (No Physical Hardware)". Он всегда находится в начале списка.

## Технические вопросы

### Q: Какой ID используется для эмулятора?

**A:** Эмулятор использует специальный ID: `999999`. Это значение сохраняется в SharedPreferences при выборе эмулятора.

### Q: Как часто обновляются данные от эмулятора?

**A:** Эмулятор обновляет температуру каждую секунду (1000 мс). Это значение можно изменить в константе `UPDATE_INTERVAL_MS` в классе `EmulatedThermometerDevice`.

### Q: В каком диапазоне генерируется температура?

**A:** Температура генерируется в диапазоне 15-35°C. Начальное значение - 22°C. Эти параметры можно изменить в полях `minTemp`, `maxTemp` и `currentTemperature`.

### Q: Почему температура изменяется плавно, а не скачками?

**A:** Это сделано для имитации реального поведения термометра. Эмулятор выбирает случайную целевую температуру и плавно движется к ней со скоростью до 0.5°C за итерацию.

### Q: Можно ли изменить параметры эмуляции?

**A:** Да, параметры находятся в классе `EmulatedThermometerDevice`:
```java
private float currentTemperature = 22.0f; // Начальная температура
private final float minTemp = 15.0f;      // Минимум
private final float maxTemp = 35.0f;      // Максимум
private final float changeRate = 0.5f;    // Скорость изменения
private static final int UPDATE_INTERVAL_MS = 1000; // Интервал обновления
```

## Проблемы и решения

### Q: Эмулятор не появляется в списке устройств

**Возможные причины:**
1. Изменения в `DevicesFragment.refresh()` не применены
2. Проект не пересобран после изменений

**Решение:**
```bash
./gradlew clean
./gradlew assembleDebug
./gradlew installDebug
```

### Q: При выборе эмулятора приложение падает

**Возможные причины:**
1. Класс `EmulatedThermometerDevice` не скомпилирован
2. Ошибка в `UsbService.onCreate()` при создании устройства

**Решение:**
1. Проверьте логи: `adb logcat -s AndroidRuntime:E`
2. Убедитесь, что все новые классы добавлены в проект
3. Пересоберите проект

### Q: Приложение крашится при повторном клике на эмулятор (ИСПРАВЛЕНО в v1.1)

**Проблема (в v1.0):**
```
NullPointerException: Attempt to invoke interface method 
'void com.hoho.android.usbserial.driver.UsbSerialPort.close()' 
on a null object reference
```

**Решение:**
Обновитесь до версии 1.1 или новее. В этой версии добавлены проверки на null во всех критических местах.

Подробности: [BUGFIX_NULL_POINTER.md](BUGFIX_NULL_POINTER.md)

### Q: Температура не обновляется

**Возможные причины:**
1. Слушатель данных не установлен
2. Метод `connect()` не вызван
3. Ошибка в таймере обновления

**Решение:**
1. Проверьте логи: `adb logcat -s EmulatedThermometer:D`
2. Убедитесь, что `setDataListener()` вызывается перед `connect()`
3. Проверьте, что `connected = true` после подключения

### Q: Реальное устройство перестало работать после добавления эмулятора

**A:** Это не должно происходить, так как код для реального устройства не изменялся. Проверьте:
1. Правильно ли сохраняется флаг `isEmulated = false` для реального устройства
2. Создается ли `RealThermometerDevice` вместо `EmulatedThermometerDevice`
3. Логи: `adb logcat -s RealThermometerDevice:D UsbService:D`

### Q: Как вернуться к старой версии без эмулятора?

**A:** Откатите изменения в файлах:
- `DevicesFragment.java` - удалите код, связанный с эмулятором
- `UsbService.java` - верните старую логику подключения
- Удалите новые файлы: `IThermometerDevice.java`, `RealThermometerDevice.java`, `EmulatedThermometerDevice.java`

## Разработка и расширение

### Q: Как добавить свой тип устройства?

**A:** Создайте класс, реализующий `IThermometerDevice`:

```java
public class MyDevice implements IThermometerDevice {
    // Реализуйте все методы интерфейса
    @Override
    public boolean connect() { /* ... */ }
    @Override
    public void disconnect() { /* ... */ }
    // ... остальные методы
}
```

Затем добавьте его в `DevicesFragment.refresh()` и `UsbService.onCreate()`.

### Q: Можно ли добавить настройки эмуляции в UI?

**A:** Да, можно создать Activity с настройками:
1. Создайте `EmulatorSettingsActivity`
2. Добавьте поля для минимальной/максимальной температуры, скорости изменения
3. Сохраняйте настройки в SharedPreferences
4. Читайте их в `EmulatedThermometerDevice.onCreate()`

### Q: Как записывать данные от эмулятора в файл?

**A:** Используйте паттерн Decorator или создайте обертку над слушателем:

```java
public class LoggingListener implements IThermometerDevice.ThermometerDataListener {
    private ThermometerDataListener delegate;
    private FileWriter writer;
    
    public LoggingListener(ThermometerDataListener delegate, File logFile) {
        this.delegate = delegate;
        this.writer = new FileWriter(logFile, true);
    }
    
    @Override
    public void onTemperatureReceived(String temperature) {
        // Записываем в файл
        writer.write(System.currentTimeMillis() + "," + temperature + "\n");
        writer.flush();
        
        // Передаем дальше
        delegate.onTemperatureReceived(temperature);
    }
    
    // ... остальные методы
}
```

### Q: Можно ли эмулировать ошибки и отключения?

**A:** Да, добавьте в `EmulatedThermometerDevice`:

```java
private Random random = new Random();
private static final float ERROR_PROBABILITY = 0.05f; // 5% шанс ошибки

private void sendTemperatureData() {
    // Случайная ошибка
    if (random.nextFloat() < ERROR_PROBABILITY) {
        notifyError("Simulated read error");
        return;
    }
    
    // Случайное отключение
    if (random.nextFloat() < 0.01f) { // 1% шанс
        disconnect();
        notifyStatus("Simulated disconnection", "❌");
        return;
    }
    
    // Нормальная отправка данных
    String temperatureStr = String.format("%.1f", currentTemperature);
    notifyTemperature(temperatureStr);
}
```

## Производительность

### Q: Влияет ли эмулятор на производительность?

**A:** Минимально. Эмулятор использует один таймер с интервалом 1 секунда и простые математические операции. Влияние на производительность незначительно.

### Q: Можно ли уменьшить интервал обновления?

**A:** Да, но не рекомендуется делать его меньше 100 мс, так как это может создать излишнюю нагрузку на UI и батарею.

### Q: Сколько памяти использует эмулятор?

**A:** Очень мало - несколько килобайт. Эмулятор не хранит историю данных и использует только примитивные типы.

## Совместимость

### Q: На каких версиях Android работает эмулятор?

**A:** На всех версиях, поддерживаемых приложением. Эмулятор не использует специфичные для версии API.

### Q: Работает ли эмулятор на эмуляторе Android?

**A:** Да, эмулятор термометра отлично работает на эмуляторе Android, так как не требует физического USB.

### Q: Можно ли использовать эмулятор в автоматических тестах?

**A:** Да, это одно из основных применений. Эмулятор идеален для unit и integration тестов:

```java
@Test
public void testTemperatureReading() {
    IThermometerDevice device = new EmulatedThermometerDevice();
    
    final String[] receivedTemp = {null};
    device.setDataListener(new IThermometerDevice.ThermometerDataListener() {
        @Override
        public void onTemperatureReceived(String temperature) {
            receivedTemp[0] = temperature;
        }
        // ... остальные методы
    });
    
    device.connect();
    
    // Ждем данных
    Thread.sleep(2000);
    
    assertNotNull(receivedTemp[0]);
    float temp = Float.parseFloat(receivedTemp[0]);
    assertTrue(temp >= 15.0f && temp <= 35.0f);
}
```

## Безопасность

### Q: Безопасно ли использовать эмулятор в production?

**A:** Эмулятор предназначен только для разработки и тестирования. В production сборке рекомендуется:
1. Скрыть эмулятор из списка устройств
2. Или полностью удалить код эмулятора

### Q: Как скрыть эмулятор в release сборке?

**A:** В `DevicesFragment.refresh()`:

```java
void refresh() {
    // ...
    listItems.clear();
    
    // Добавляем эмулятор только в debug сборке
    if (BuildConfig.DEBUG) {
        listItems.add(new ListItem(true));
    }
    
    // ... остальной код
}
```

## Дополнительные ресурсы

### Q: Где найти больше примеров?

**A:** Смотрите файл `API_EXAMPLES.md` в папке проекта.

### Q: Где описана архитектура?

**A:** Подробная архитектура описана в `ARCHITECTURE.md`.

### Q: Как начать работу с эмулятором?

**A:** Читайте `QUICK_START_EMULATOR.md` для быстрого старта.

### Q: Где список всех изменений?

**A:** Полный список изменений в `CHANGES_SUMMARY.md`.

## Поддержка

### Q: Где сообщить об ошибке?

**A:** Создайте issue в репозитории проекта с описанием:
- Версия Android
- Шаги для воспроизведения
- Ожидаемое поведение
- Фактическое поведение
- Логи (если есть)

### Q: Как предложить улучшение?

**A:** Создайте feature request в репозитории или отправьте pull request с реализацией.

### Q: Есть ли примеры использования в других проектах?

**A:** Пока нет, но вы можете стать первым! Поделитесь своим опытом в issues.
