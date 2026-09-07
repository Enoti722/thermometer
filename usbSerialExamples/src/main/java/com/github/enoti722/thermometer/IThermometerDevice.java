package com.github.enoti722.thermometer;

/**
 * Интерфейс для работы с термометром (реальным или эмулированным)
 */
public interface IThermometerDevice {
    /**
     * Подключиться к устройству
     * @return true если подключение успешно
     */
    boolean connect();
    
    /**
     * Отключиться от устройства
     */
    void disconnect();
    
    /**
     * Проверить, подключено ли устройство
     */
    boolean isConnected();
    
    /**
     * Отправить команду устройству
     * @param command команда для отправки
     */
    void sendCommand(String command);
    
    /**
     * Получить ID устройства для отображения
     */
    int getDeviceId();
    
    /**
     * Получить номер порта
     */
    int getPortNum();
    
    /**
     * Получить название устройства
     */
    String getDeviceName();
    
    /**
     * Проверить, является ли устройство эмулятором
     */
    boolean isEmulated();
    
    /**
     * Установить слушателя данных
     */
    void setDataListener(ThermometerDataListener listener);
    
    /**
     * Интерфейс для получения данных от термометра
     */
    interface ThermometerDataListener {
        void onTemperatureReceived(String temperature);
        void onError(String error);
        void onStatusChanged(String status, String statusIcon);
    }
}
