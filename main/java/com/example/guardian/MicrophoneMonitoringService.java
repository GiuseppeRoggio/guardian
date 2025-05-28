package com.example.guardian;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MicrophoneMonitoringService extends Service {

    private static final String TAG = "MicMonitorService";
    private static final String CHANNEL_ID = "MicrophoneMonitoringChannel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long MONITORING_INTERVAL = 1000; // 1 secondo
    private static final long USAGE_STATS_INTERVAL = 5000; // 5 secondi per usage stats

    private AudioManager audioManager;
    private UsageStatsManager usageStatsManager;
    private PackageManager packageManager;
    private ScheduledExecutorService scheduler;
    private Handler mainHandler;
    private NotificationManager notificationManager;

    private boolean isMonitoring = false;
    private List<String> currentRecordingApps = new ArrayList<>();
    private long lastUsageStatsCheck = 0;

    // Callback per il monitoraggio delle registrazioni audio
    private AudioManager.AudioRecordingCallback audioRecordingCallback = new AudioManager.AudioRecordingCallback() {
        @Override
        public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
            super.onRecordingConfigChanged(configs);
            handleAudioRecordingChange(configs);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        packageManager = getPackageManager();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mainHandler = new Handler(getMainLooper());

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("Servizio di monitoraggio attivo"));

        Log.d(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isMonitoring) {
            startMonitoring();
        }
        return START_STICKY; // Riavvia automaticamente se terminato dal sistema
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Servizio non vincolato
    }

    @Override
    public void onDestroy() {
        stopMonitoring();
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
    }

    private void startMonitoring() {
        if (isMonitoring) return;

        Log.d(TAG, "Starting microphone monitoring");
        isMonitoring = true;

        // Registra il callback per le registrazioni audio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            audioManager.registerAudioRecordingCallback(audioRecordingCallback, mainHandler);
        }

        // Avvia il monitoraggio periodico
        scheduler = Executors.newScheduledThreadPool(2);

        // Controllo periodico delle app attive
        scheduler.scheduleAtFixedRate(this::checkActiveApps, 0, MONITORING_INTERVAL, TimeUnit.MILLISECONDS);

        // Controllo meno frequente delle usage stats
        scheduler.scheduleAtFixedRate(this::updateUsageStats, 0, USAGE_STATS_INTERVAL, TimeUnit.MILLISECONDS);

        updateNotification("Monitoraggio attivo - Nessuna registrazione");
    }

    private void stopMonitoring() {
        if (!isMonitoring) return;

        Log.d(TAG, "Stopping microphone monitoring");
        isMonitoring = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            audioManager.unregisterAudioRecordingCallback(audioRecordingCallback);
        }

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    private void handleAudioRecordingChange(List<AudioRecordingConfiguration> configs) {
        List<String> recordingApps = new ArrayList<>();

        for (AudioRecordingConfiguration config : configs) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int clientUid = config.getClientAudioSource();
                String packageName = getPackageNameFromUid(clientUid);
                if (packageName != null && !packageName.equals(getPackageName())) {
                    recordingApps.add(packageName);
                }
            }
        }

        // Aggiorna la lista delle app che stanno registrando
        synchronized (currentRecordingApps) {
            if (!currentRecordingApps.equals(recordingApps)) {
                currentRecordingApps.clear();
                currentRecordingApps.addAll(recordingApps);

                if (!recordingApps.isEmpty()) {
                    Log.d(TAG, "Recording apps detected: " + recordingApps);
                    handleMicrophoneUsage(recordingApps);
                    updateNotification("Registrazione attiva: " + recordingApps.size() + " app");
                } else {
                    updateNotification("Monitoraggio attivo - Nessuna registrazione");
                }
            }
        }
    }

    private void checkActiveApps() {
        try {
            synchronized (currentRecordingApps) {
                if (!currentRecordingApps.isEmpty()) {
                    // Se ci sono app che stanno registrando, controlla quali sono attive
                    List<String> activeApps = getActiveApplications();
                    handleMicrophoneUsage(currentRecordingApps, activeApps);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking active apps", e);
        }
    }

    private void updateUsageStats() {
        // Aggiorna le statistiche di utilizzo meno frequentemente
        lastUsageStatsCheck = System.currentTimeMillis();
    }

    private List<String> getActiveApplications() {
        List<String> activeApps = new ArrayList<>();

        if (usageStatsManager != null) {
            long currentTime = System.currentTimeMillis();
            long startTime = currentTime - 10000; // Ultimi 10 secondi

            List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST, startTime, currentTime);

            if (usageStatsList != null && !usageStatsList.isEmpty()) {
                // Ordina per ultimo utilizzo
                Collections.sort(usageStatsList, new Comparator<UsageStats>() {
                    @Override
                    public int compare(UsageStats us1, UsageStats us2) {
                        return Long.compare(us2.getLastTimeUsed(), us1.getLastTimeUsed());
                    }
                });

                // Prendi le app più recentemente utilizzate
                for (int i = 0; i < Math.min(5, usageStatsList.size()); i++) {
                    UsageStats stats = usageStatsList.get(i);
                    if (stats.getLastTimeUsed() > startTime) {
                        activeApps.add(stats.getPackageName());
                    }
                }
            }
        }

        return activeApps;
    }

    private void handleMicrophoneUsage(List<String> recordingApps) {
        List<String> activeApps = getActiveApplications();
        handleMicrophoneUsage(recordingApps, activeApps);
    }

    private void handleMicrophoneUsage(List<String> recordingApps, List<String> activeApps) {
        try {
            List<MicrophoneUsageInfo> usageInfoList = new ArrayList<>();

            for (String packageName : recordingApps) {
                String appName = getAppName(packageName);
                boolean isActive = activeApps.contains(packageName);
                boolean isForeground = isAppInForeground(packageName, activeApps);

                MicrophoneUsageInfo info = new MicrophoneUsageInfo(
                        packageName,
                        appName,
                        System.currentTimeMillis(),
                        isActive,
                        isForeground
                );

                usageInfoList.add(info);

                Log.d(TAG, String.format("Mic usage: %s (%s) - Active: %b, Foreground: %b",
                        appName, packageName, isActive, isForeground));
            }

            // Invia i dati all'attività principale
            sendUsageInfoToActivity(usageInfoList);

        } catch (Exception e) {
            Log.e(TAG, "Error handling microphone usage", e);
        }
    }

    private boolean isAppInForeground(String packageName, List<String> activeApps) {
        // L'app più recente nella lista delle usage stats è probabilmente in foreground
        return !activeApps.isEmpty() && activeApps.get(0).equals(packageName);
    }

    private String getAppName(String packageName) {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            return packageManager.getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private String getPackageNameFromUid(int uid) {
        String[] packages = packageManager.getPackagesForUid(uid);
        return (packages != null && packages.length > 0) ? packages[0] : null;
    }

    private void sendUsageInfoToActivity(List<MicrophoneUsageInfo> usageInfoList) {
        Intent intent = new Intent("MICROPHONE_USAGE_UPDATE");
        intent.putParcelableArrayListExtra("usage_info", new ArrayList<>(usageInfoList));
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Monitoraggio Microfono",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Servizio di monitoraggio accesso al microfono");
            channel.enableVibration(false);
            channel.setSound(null, null);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String content) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Guardian - Monitoraggio Microfono")
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_mic_monitoring)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void updateNotification(String content) {
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, createNotification(content));
        }
    }

    // Classe per le informazioni sull'utilizzo del microfono
    public static class MicrophoneUsageInfo implements android.os.Parcelable {
        public final String packageName;
        public final String appName;
        public final long timestamp;
        public final boolean isActive;
        public final boolean isForeground;

        public MicrophoneUsageInfo(String packageName, String appName, long timestamp,
                                   boolean isActive, boolean isForeground) {
            this.packageName = packageName;
            this.appName = appName;
            this.timestamp = timestamp;
            this.isActive = isActive;
            this.isForeground = isForeground;
        }

        // Implementazione Parcelable
        protected MicrophoneUsageInfo(android.os.Parcel in) {
            packageName = in.readString();
            appName = in.readString();
            timestamp = in.readLong();
            isActive = in.readByte() != 0;
            isForeground = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeString(packageName);
            dest.writeString(appName);
            dest.writeLong(timestamp);
            dest.writeByte((byte) (isActive ? 1 : 0));
            dest.writeByte((byte) (isForeground ? 1 : 0));
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<MicrophoneUsageInfo> CREATOR = new Creator<MicrophoneUsageInfo>() {
            @Override
            public MicrophoneUsageInfo createFromParcel(android.os.Parcel in) {
                return new MicrophoneUsageInfo(in);
            }

            @Override
            public MicrophoneUsageInfo[] newArray(int size) {
                return new MicrophoneUsageInfo[size];
            }
        };
    }
}