package com.github.enoti722.thermometer;

/**
 * Источник скорости в км/ч для оверлея и логики (реальный GPS или эмуляция).
 */
public interface ISpeedProvider {

    interface Listener {
        /** Некорректное/нет данных — см. реализации (напр. GPS без разрешения). */
        float SPEED_UNAVAILABLE = Float.NaN;

        void onSpeedKmh(float kmh);
    }

    void setListener(Listener listener);

    void start();

    void stop();
}
