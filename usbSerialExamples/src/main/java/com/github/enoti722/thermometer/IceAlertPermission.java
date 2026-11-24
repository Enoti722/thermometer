package com.github.enoti722.thermometer;

/**
 * Невидимое разрешение на вывод предупреждения об обледенении дорожного полотна.
 *
 * <p>Разрешение внутреннее: система о нём не знает, никакого окна и запроса пользователю не показывается.
 * Смысл — не давать окну появляться бесконечно, пока температура колеблется у порога, но и не терять
 * предупреждение до конца поездки: показ расходует разрешение, а выдаётся оно заново только по явным
 * событиям — включение магнитолы, переподключение к датчику и возврат температуры выше порога с запасом.
 *
 * <p>Сравнивается та температура, которая выведена на экран, а не сырое значение с датчика: в приложении
 * это разные величины (поправка датчика и удержание показания по правилу средней скорости), и водитель
 * видит именно показанное значение.
 */
final class IceAlertPermission {

    /** Порог предупреждения (°C): ниже него окно обледенения уместно. */
    private final float thresholdCelsius;
    /** Запас (°C) выше порога, после которого разрешение выдаётся заново. */
    private final float rearmDeltaCelsius;

    private boolean granted;

    IceAlertPermission(float thresholdCelsius, float rearmDeltaCelsius) {
        this.thresholdCelsius = thresholdCelsius;
        this.rearmDeltaCelsius = rearmDeltaCelsius;
    }

    /** Выдать разрешение: включение магнитолы или переподключение к датчику. */
    void grant() {
        granted = true;
    }

    boolean isGranted() {
        return granted;
    }

    /**
     * Выдаёт разрешение заново, если показанная температура поднялась выше порога с запасом
     * (порог +5 °C, запас 3 °C → повторное разрешение при значении на экране выше +8 °C).
     *
     * @return {@code true}, если разрешение выдано именно этим вызовом — для записи события в лог.
     */
    boolean rearmIfWarmEnough(float displayedCelsius) {
        if (granted || displayedCelsius <= thresholdCelsius + rearmDeltaCelsius) {
            return false;
        }
        granted = true;
        return true;
    }

    /** Разрешение выдано и показанная температура ниже порога — предупреждение можно выводить. */
    boolean allowsWarning(float displayedCelsius) {
        return granted && displayedCelsius < thresholdCelsius;
    }

    /** Окно показано: разрешение израсходовано до следующего события. */
    void consume() {
        granted = false;
    }
}
