package com.github.enoti722.thermometer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Короткий сигнал при показе окна предупреждения поверх экрана.
 *
 * <p>Способ проигрывания вынесен в настройку ({@link SystemSettingsPreferences#notificationSoundMode}),
 * потому что итог зависит от прошивки магнитолы и проверяется только на живом ГУ:
 * <ul>
 *   <li>{@link SystemSettingsPreferences#SOUND_MODE_NOTIFICATION_FOCUS} — исторический вариант:
 *       {@code USAGE_NOTIFICATION} и кратковременный {@code AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK}.
 *       MAY_DUCK — это только просьба: если прошивка (или старый штатный плеер с targetSdk &lt; 26)
 *       приглушать не умеет, плеер получает потерю фокуса, уходит в паузу и гасит свою MediaSession.
 *       После этого музыку не поднимают ни кнопки руля, ни виджет — только само приложение плеера.</li>
 *   <li>{@link SystemSettingsPreferences#SOUND_MODE_SONIFICATION} — аудиофокус не запрашивается вообще,
 *       {@code USAGE_ASSISTANCE_SONIFICATION}: сигнал подмешивается к уже играющему звуку, плееру не
 *       приходит ни одного focus-события и терять сессию ему не с чего.</li>
 *   <li>{@link SystemSettingsPreferences#SOUND_MODE_MEDIA} — тоже без фокуса, но прямо в медиаканал:
 *       запасной вариант, если прошивка переключает источник усилителя и на системный канал тоже.</li>
 * </ul>
 *
 * <p>Диагностика ({@link #playTest(ResultListener)}) пишет в {@link LocalDiagLog}, что ответила система
 * и что стало с воспроизведением, — на магнитоле это единственный способ увидеть происходящее без ADB.
 */
public final class NotificationAlertSound {

    private static final String TAG = "AlertSound";

    /** Запас после конца сигнала перед контрольным замером: музыка «отваливается» не мгновенно. */
    private static final long DIAG_PROBE_TAIL_MS = 1500L;
    private static final long DIAG_PROBE_MIN_MS = 2000L;

    /** Системный щелчок короткий и тихий — одиночный можно и не заметить. */
    private static final int UI_EFFECT_REPEATS = 3;
    private static final long UI_EFFECT_STEP_MS = 200L;

    /** Прошивке нужно время доиграть своё переключение источника, иначе команда уйдёт в никуда. */
    private static final long RESTORE_PLAYBACK_DELAY_MS = 1200L;

    /** Канал для режима «системное уведомление»; версия в идентификаторе — параметры канала неизменны. */
    private static final String ALERT_CHANNEL_ID = "usb_thermometer_alert_sound_v1";
    private static final int ALERT_NOTIFICATION_ID = 4711;
    private static final long ALERT_NOTIFICATION_HOLD_MS = 4000L;

    /** Итог тестового проигрывания одной строкой — для Toast; полная картина уходит в лог. */
    public interface ResultListener {
        void onResult(String summary);
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable private MediaPlayer player;
    @Nullable private AudioManager audioManager;
    @Nullable private AudioFocusRequest focusRequest;
    @Nullable private Runnable pendingDiagnosticProbe;
    /** Режим последнего проигрыша — чтобы ответ владельца лёг в лог рядом с тем, что проверялось. */
    private int lastMode = SystemSettingsPreferences.DEFAULT_NOTIFICATION_SOUND_MODE;

    public NotificationAlertSound(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** Обычный показ окна уведомления: один проигрыш, одна строка в лог. */
    public void play(String reason) {
        playInternal(reason, false, null);
    }

    /** Кнопка «Проверить звук»: то же самое плюс замер состояния музыки до и после. */
    public void playTest(@Nullable ResultListener listener) {
        playInternal("проверка из настроек", true, listener);
    }

    /**
     * Ответ владельца по итогам проверки. Автоматический вердикт на этой магнитоле слеп — ни радио,
     * ни Bluetooth, ни флешка не видны Android как воспроизведение, — поэтому решает то, что слышно ушами.
     */
    public void logOwnerVerdict(String answer) {
        LocalDiagLog.line(appContext, "I", TAG,
                "ответ владельца (режим=" + describeMode(lastMode) + "): " + answer
                        + "; состояние сейчас [" + snapshot().describe() + "]");
    }

    /** Полная остановка: плеер, аудиофокус и незавершённый замер диагностики. */
    public void release() {
        cancelDiagnosticProbe();
        releasePlayer();
    }

    private void playInternal(String reason, boolean diagnostic, @Nullable ResultListener listener) {
        release();
        int mode = SystemSettingsPreferences.notificationSoundMode(appContext);
        lastMode = mode;
        AudioManager am = audioManager();
        PlaybackSnapshot before = snapshot();
        if (mode == SystemSettingsPreferences.SOUND_MODE_UI_EFFECT) {
            playSystemUiEffect(reason, diagnostic, before, listener);
            return;
        }
        if (mode == SystemSettingsPreferences.SOUND_MODE_SYSTEM_NOTIFICATION) {
            playViaSystemNotification(reason, diagnostic, before, listener);
            return;
        }
        if (mode == SystemSettingsPreferences.SOUND_MODE_SILENT_CONTROL) {
            // Контроль: ни своего плеера, ни системного эффекта, ни уведомления — вообще ни одного
            // обращения к звуку. Если музыка встаёт и здесь, виноват не сигнал.
            LocalDiagLog.line(appContext, "I", TAG, "контрольный прогон без звука, повод=" + reason
                    + ", состояние [" + before.describe() + "]");
            if (diagnostic) {
                scheduleDiagnosticProbe(mode, before, 0, listener);
            }
            return;
        }
        AssetFileDescriptor afd = null;
        try {
            afd = appContext.getResources().openRawResourceFd(R.raw.bmw_meloboom);
            if (afd == null) {
                LocalDiagLog.line(appContext, "W", TAG, "звуковой файл недоступен в ресурсах");
                report(listener, "Звуковой файл недоступен");
                return;
            }
            AudioAttributes attrs = attributesForMode(mode);
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            mp.setAudioAttributes(attrs);
            mp.setOnCompletionListener(m -> {
                releasePlayer();
                restorePlaybackIfEnabled();
            });
            mp.setOnErrorListener((m, what, extra) -> {
                LocalDiagLog.line(appContext, "W", TAG, "ошибка MediaPlayer what=" + what + " extra=" + extra);
                releasePlayer();
                return true;
            });
            mp.prepare();
            try {
                afd.close();
            } catch (Exception ignored) {}
            afd = null;

            int durationMs = mp.getDuration();
            // Фокус запрашивается только в историческом режиме и строго с теми же AudioAttributes, что у плеера.
            String focusInfo = mode == SystemSettingsPreferences.SOUND_MODE_NOTIFICATION_FOCUS
                    ? requestFocus(attrs)
                    : "аудиофокус не запрашивался";
            player = mp;
            mp.start();

            LocalDiagLog.line(appContext, "I", TAG, "сигнал: режим=" + describeMode(mode)
                    + ", повод=" + reason
                    + ", до сигнала [" + before.describe() + "]"
                    + ", " + focusInfo
                    + ", длительность=" + (durationMs > 0 ? durationMs + " мс" : "неизвестна")
                    + ", " + describeVolumes(am, mode));

            if (diagnostic) {
                scheduleDiagnosticProbe(mode, before, durationMs, listener);
            }
        } catch (Exception e) {
            Log.w(TAG, "playInternal failed", e);
            LocalDiagLog.w(appContext, TAG, "не удалось проиграть сигнал (режим=" + describeMode(mode) + ")", e);
            releasePlayer();
            report(listener, "Не удалось проиграть сигнал: " + e.getClass().getSimpleName());
            if (afd != null) {
                try {
                    afd.close();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Единственный вариант, где приложение не открывает собственный аудиопоток: звук берётся из
     * системного пула тем же способом, каким магнитола щёлкает по нажатию на экран. Клики по экрану
     * источник не переключают — значит и этот путь не должен. Свой файл так проиграть нельзя,
     * поэтому щелчок повторяется несколько раз, чтобы его было слышно.
     */
    private void playSystemUiEffect(String reason, boolean diagnostic, PlaybackSnapshot before,
                                    @Nullable ResultListener listener) {
        AudioManager am = audioManager();
        if (am == null) {
            report(listener, "Нет AudioManager");
            return;
        }
        boolean effectsEnabled = uiSoundEffectsEnabled();
        am.loadSoundEffects();
        for (int i = 0; i < UI_EFFECT_REPEATS; i++) {
            mainHandler.postDelayed(() -> am.playSoundEffect(AudioManager.FX_KEY_CLICK),
                    UI_EFFECT_STEP_MS * i);
        }
        LocalDiagLog.line(appContext, "I", TAG, "сигнал: режим=" + describeMode(SystemSettingsPreferences.SOUND_MODE_UI_EFFECT)
                + ", повод=" + reason
                + ", до сигнала [" + before.describe() + "]"
                + ", свой аудиопоток не открывался"
                + ", звуки интерфейса в системе " + (effectsEnabled ? "включены" : "ВЫКЛЮЧЕНЫ — щелчка не будет")
                + ", повторов=" + UI_EFFECT_REPEATS);
        if (diagnostic) {
            scheduleDiagnosticProbe(SystemSettingsPreferences.SOUND_MODE_UI_EFFECT, before,
                    (int) (UI_EFFECT_STEP_MS * UI_EFFECT_REPEATS), listener);
        }
    }

    /**
     * Сигнал проигрывает система как звук обычного уведомления: файл наш, но аудиопоток открывает
     * системный процесс, а не мы. Промежуточный вариант между «своим плеером» и щелчком интерфейса —
     * прошивка видит ровно то же, что при любом системном уведомлении.
     *
     * <p>Уведомление снимаем не сразу: отмена гасит и звук, если он ещё играет.
     */
    private void playViaSystemNotification(String reason, boolean diagnostic, PlaybackSnapshot before,
                                           @Nullable ResultListener listener) {
        NotificationManager nm =
                (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            report(listener, "Нет NotificationManager");
            return;
        }
        try {
            Uri sound = Uri.parse("android.resource://" + appContext.getPackageName()
                    + "/" + R.raw.bmw_meloboom);
            // Параметры канала после создания неизменны, поэтому версия зашита в идентификатор.
            NotificationChannel channel = new NotificationChannel(ALERT_CHANNEL_ID,
                    "Сигнал предупреждений", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setSound(sound, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            channel.enableVibration(false);
            nm.createNotificationChannel(channel);

            Notification n = new NotificationCompat.Builder(appContext, ALERT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_usb)
                    .setContentTitle(reason)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build();
            nm.notify(ALERT_NOTIFICATION_ID, n);
            mainHandler.postDelayed(() -> nm.cancel(ALERT_NOTIFICATION_ID), ALERT_NOTIFICATION_HOLD_MS);

            LocalDiagLog.line(appContext, "I", TAG, "сигнал: режим="
                    + describeMode(SystemSettingsPreferences.SOUND_MODE_SYSTEM_NOTIFICATION)
                    + ", повод=" + reason
                    + ", до сигнала [" + before.describe() + "]"
                    + ", свой аудиопоток не открывался, звук проигрывает система"
                    + ", важность канала=" + nm.getNotificationChannel(ALERT_CHANNEL_ID).getImportance());

            if (diagnostic) {
                scheduleDiagnosticProbe(SystemSettingsPreferences.SOUND_MODE_SYSTEM_NOTIFICATION,
                        before, (int) ALERT_NOTIFICATION_HOLD_MS, listener);
            }
        } catch (Exception e) {
            Log.w(TAG, "playViaSystemNotification failed", e);
            LocalDiagLog.w(appContext, TAG, "не удалось показать уведомление со звуком", e);
            report(listener, "Не удалось: " + e.getClass().getSimpleName());
        }
    }

    /**
     * Попытка поднять музыку обратно: магнитола после нашего сигнала переключает источник и закрывает
     * плеер, поэтому шлём системе «играть» — как это сделала бы кнопка руля. Дойдёт только если плеер
     * пережил закрытие окна и сохранил медиасессию; если прошивка убила его насовсем, команде некуда идти.
     */
    private void restorePlaybackIfEnabled() {
        if (!SystemSettingsPreferences.restorePlaybackAfterAlert(appContext)) {
            return;
        }
        AudioManager am = audioManager();
        if (am == null) {
            return;
        }
        mainHandler.postDelayed(() -> {
            try {
                long now = SystemClock.uptimeMillis();
                am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_MEDIA_PLAY, 0));
                am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_MEDIA_PLAY, 0));
                LocalDiagLog.line(appContext, "I", TAG, "после сигнала отправлена команда «играть»");
            } catch (Exception e) {
                LocalDiagLog.w(appContext, TAG, "не удалось отправить команду «играть»", e);
            }
        }, RESTORE_PLAYBACK_DELAY_MS);
    }

    /** Системный тумблер «звуки при нажатии»: при нём выключенном playSoundEffect молча ничего не делает. */
    private boolean uiSoundEffectsEnabled() {
        try {
            return Settings.System.getInt(appContext.getContentResolver(),
                    Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0;
        } catch (Exception e) {
            return true;
        }
    }

    private void releasePlayer() {
        MediaPlayer mp = player;
        player = null;
        if (mp != null) {
            try {
                mp.stop();
            } catch (Exception ignored) {}
            mp.release();
        }
        abandonFocus();
    }

    private void abandonFocus() {
        AudioManager am = audioManager;
        if (focusRequest != null && am != null) {
            am.abandonAudioFocusRequest(focusRequest);
        }
        focusRequest = null;
    }

    /**
     * Исторический режим: кратковременный фокус с просьбой приглушить медиа.
     * Слушатель раньше был пустым — теперь ответ системы виден в логе, иначе на ГУ его не поймать.
     */
    private String requestFocus(AudioAttributes attrs) {
        AudioManager am = audioManager();
        if (am == null) {
            return "аудиофокус: нет AudioManager";
        }
        abandonFocus();
        AudioFocusRequest req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(this::onFocusChange, mainHandler)
                .build();
        int result = am.requestAudioFocus(req);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusRequest = req;
            return "аудиофокус MAY_DUCK: выдан";
        }
        return "аудиофокус MAY_DUCK: отказано (код " + result + ")";
    }

    private void onFocusChange(int focusChange) {
        LocalDiagLog.line(appContext, "I", TAG, "наш аудиофокус изменился: " + describeFocusChange(focusChange));
    }

    private void scheduleDiagnosticProbe(int mode, PlaybackSnapshot before, int durationMs,
                                         @Nullable ResultListener listener) {
        long delay = Math.max(DIAG_PROBE_MIN_MS, Math.max(durationMs, 0) + DIAG_PROBE_TAIL_MS);
        Runnable probe = () -> {
            pendingDiagnosticProbe = null;
            PlaybackSnapshot after = snapshot();
            String verdict = verdict(before, after);
            LocalDiagLog.line(appContext, "I", TAG, "проверка звука (режим=" + describeMode(mode)
                    + "): до [" + before.describe() + "], после [" + after.describe() + "] — " + verdict);
            report(listener, verdict);
        };
        pendingDiagnosticProbe = probe;
        mainHandler.postDelayed(probe, delay);
    }

    private void cancelDiagnosticProbe() {
        if (pendingDiagnosticProbe != null) {
            mainHandler.removeCallbacks(pendingDiagnosticProbe);
            pendingDiagnosticProbe = null;
        }
    }

    private static String verdict(PlaybackSnapshot before, PlaybackSnapshot after) {
        if (!before.anythingPlaying()) {
            return "до сигнала ничего не звучало — включите музыку и повторите проверку";
        }
        return after.anythingPlaying()
                ? "воспроизведение продолжается — сигнал его не сбил"
                : "воспроизведение прекратилось после сигнала — этот режим магнитоле не подходит";
    }

    /**
     * Что звучит прямо сейчас.
     *
     * <p>Одного {@link AudioManager#isMusicActive()} мало: клиент слушает музыку с телефона по Bluetooth,
     * и увидим мы её только если магнитола принимает A2DP средствами Android (профиль-приёмник рендерит
     * поток обычным плеером). Поэтому вдобавок берём список активных воспроизведений системы — там
     * A2DP-приём виден даже под другим usage. Если же прошивка гонит Bluetooth или радио в усилитель
     * мимо Android, не поможет ничего: замер честно покажет «ничего не звучало», и судить придётся
     * на слух и по кнопке руля.
     *
     * <p>Сюда же попадают громкости каналов, и не для красоты: на этой магнитоле громкость медиаканала
     * скачет между нулём и максимумом сама, без участия пользователя. Похоже, так прошивка глушит и
     * возвращает звук Android при переключении источника усилителя — то есть разница громкостей
     * «до» и «после» и есть единственный видимый нам след этого переключения.
     */
    private PlaybackSnapshot snapshot() {
        AudioManager am = audioManager();
        if (am == null) {
            return new PlaybackSnapshot(false, 0, "нет AudioManager");
        }
        boolean music = am.isMusicActive();
        int players = 0;
        Set<String> usages = new LinkedHashSet<>();
        String note = null;
        try {
            List<AudioPlaybackConfiguration> configs = am.getActivePlaybackConfigurations();
            for (AudioPlaybackConfiguration config : configs) {
                players++;
                usages.add(describeUsage(config.getAudioAttributes().getUsage()));
            }
        } catch (Exception e) {
            // Список чужих воспроизведений — не гарантия платформы, на кастомной прошивке может и не быть.
            note = "список воспроизведений недоступен (" + e.getClass().getSimpleName() + ")";
        }
        String details = "медиапоток=" + yesNo(music) + ", активных плееров=" + players
                + (usages.isEmpty() ? "" : " " + usages)
                + ", громкости медиа/система/уведомления/будильник="
                + am.getStreamVolume(AudioManager.STREAM_MUSIC) + "/"
                + am.getStreamVolume(AudioManager.STREAM_SYSTEM) + "/"
                + am.getStreamVolume(AudioManager.STREAM_NOTIFICATION) + "/"
                + am.getStreamVolume(AudioManager.STREAM_ALARM)
                + (note != null ? ", " + note : "");
        return new PlaybackSnapshot(music, players, details);
    }

    /** Срез состояния звука до и после сигнала; сравнение этих двух срезов и есть вердикт проверки. */
    private static final class PlaybackSnapshot {
        private final boolean musicActive;
        private final int activePlayers;
        private final String details;

        PlaybackSnapshot(boolean musicActive, int activePlayers, String details) {
            this.musicActive = musicActive;
            this.activePlayers = activePlayers;
            this.details = details;
        }

        boolean anythingPlaying() {
            return musicActive || activePlayers > 0;
        }

        String describe() {
            return details;
        }
    }

    private static String describeUsage(int usage) {
        switch (usage) {
            case AudioAttributes.USAGE_MEDIA: return "медиа";
            case AudioAttributes.USAGE_NOTIFICATION: return "уведомление";
            case AudioAttributes.USAGE_ASSISTANCE_SONIFICATION: return "системный";
            case AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE: return "навигация";
            case AudioAttributes.USAGE_VOICE_COMMUNICATION: return "разговор";
            case AudioAttributes.USAGE_ALARM: return "будильник";
            case AudioAttributes.USAGE_UNKNOWN: return "usage не указан";
            default: return "usage " + usage;
        }
    }

    private void report(@Nullable ResultListener listener, String summary) {
        if (listener != null) {
            listener.onResult(summary);
        }
    }

    @Nullable
    private AudioManager audioManager() {
        if (audioManager == null) {
            audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        }
        return audioManager;
    }

    private static AudioAttributes attributesForMode(int mode) {
        AudioAttributes.Builder b = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION);
        switch (mode) {
            case SystemSettingsPreferences.SOUND_MODE_NOTIFICATION_FOCUS:
                b.setUsage(AudioAttributes.USAGE_NOTIFICATION);
                break;
            case SystemSettingsPreferences.SOUND_MODE_MEDIA:
                b.setUsage(AudioAttributes.USAGE_MEDIA);
                break;
            case SystemSettingsPreferences.SOUND_MODE_NAVIGATION:
                b.setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
                break;
            case SystemSettingsPreferences.SOUND_MODE_ALARM:
                b.setUsage(AudioAttributes.USAGE_ALARM);
                break;
            case SystemSettingsPreferences.SOUND_MODE_SYSTEM_ENFORCED:
                b.setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED);
                break;
            case SystemSettingsPreferences.SOUND_MODE_SONIFICATION:
            default:
                b.setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION);
                break;
        }
        return b.build();
    }

    /** Канал громкости, на который ляжет сигнал в этом режиме (нулевая громкость = «звука нет»). */
    private static int legacyStreamForMode(int mode) {
        switch (mode) {
            case SystemSettingsPreferences.SOUND_MODE_NOTIFICATION_FOCUS:
            case SystemSettingsPreferences.SOUND_MODE_SYSTEM_NOTIFICATION:
                return AudioManager.STREAM_NOTIFICATION;
            case SystemSettingsPreferences.SOUND_MODE_MEDIA:
            case SystemSettingsPreferences.SOUND_MODE_NAVIGATION:
                return AudioManager.STREAM_MUSIC;
            case SystemSettingsPreferences.SOUND_MODE_ALARM:
                return AudioManager.STREAM_ALARM;
            case SystemSettingsPreferences.SOUND_MODE_SONIFICATION:
            case SystemSettingsPreferences.SOUND_MODE_SYSTEM_ENFORCED:
            default:
                return AudioManager.STREAM_SYSTEM;
        }
    }

    private static String describeVolumes(@Nullable AudioManager am, int mode) {
        if (am == null) {
            return "громкость: неизвестна";
        }
        int stream = legacyStreamForMode(mode);
        StringBuilder sb = new StringBuilder("громкость: медиа ")
                .append(am.getStreamVolume(AudioManager.STREAM_MUSIC)).append('/')
                .append(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                .append(", система ")
                .append(am.getStreamVolume(AudioManager.STREAM_SYSTEM)).append('/')
                .append(am.getStreamMaxVolume(AudioManager.STREAM_SYSTEM))
                .append(", уведомления ")
                .append(am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)).append('/')
                .append(am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION))
                .append(", будильник ")
                .append(am.getStreamVolume(AudioManager.STREAM_ALARM)).append('/')
                .append(am.getStreamMaxVolume(AudioManager.STREAM_ALARM))
                .append("; наш канал=").append(describeStream(stream));
        if (am.getStreamVolume(stream) == 0) {
            sb.append(" (нулевая — сигнала не будет слышно)");
        }
        sb.append(", профиль звонка=").append(describeRingerMode(am.getRingerMode()));
        return sb.toString();
    }

    private static String describeStream(int stream) {
        switch (stream) {
            case AudioManager.STREAM_MUSIC: return "медиа";
            case AudioManager.STREAM_NOTIFICATION: return "уведомления";
            case AudioManager.STREAM_SYSTEM: return "система";
            case AudioManager.STREAM_ALARM: return "будильник";
            default: return "поток " + stream;
        }
    }

    private static String describeRingerMode(int ringerMode) {
        switch (ringerMode) {
            case AudioManager.RINGER_MODE_SILENT: return "тихий (системные каналы заглушены)";
            case AudioManager.RINGER_MODE_VIBRATE: return "вибро (системные каналы заглушены)";
            case AudioManager.RINGER_MODE_NORMAL: return "обычный";
            default: return String.valueOf(ringerMode);
        }
    }

    static String describeMode(int mode) {
        switch (mode) {
            case SystemSettingsPreferences.SOUND_MODE_NOTIFICATION_FOCUS:
                return "уведомление + аудиофокус";
            case SystemSettingsPreferences.SOUND_MODE_MEDIA:
                return "медиаканал без фокуса";
            case SystemSettingsPreferences.SOUND_MODE_NAVIGATION:
                return "навигационная подсказка";
            case SystemSettingsPreferences.SOUND_MODE_ALARM:
                return "канал будильника";
            case SystemSettingsPreferences.SOUND_MODE_SYSTEM_ENFORCED:
                return "системный обязательно слышимый";
            case SystemSettingsPreferences.SOUND_MODE_UI_EFFECT:
                return "системный эффект интерфейса";
            case SystemSettingsPreferences.SOUND_MODE_SYSTEM_NOTIFICATION:
                return "системное уведомление со звуком";
            case SystemSettingsPreferences.SOUND_MODE_SILENT_CONTROL:
                return "контроль без звука";
            case SystemSettingsPreferences.SOUND_MODE_SONIFICATION:
            default:
                return "системный сигнал без фокуса";
        }
    }

    private static String describeFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN: return "GAIN (получен обратно)";
            case AudioManager.AUDIOFOCUS_LOSS: return "LOSS (потерян насовсем)";
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: return "LOSS_TRANSIENT";
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: return "LOSS_TRANSIENT_CAN_DUCK";
            default: return String.valueOf(focusChange);
        }
    }

    private static String yesNo(boolean value) {
        return value ? "да" : "нет";
    }
}
