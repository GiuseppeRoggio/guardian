package com.example.guardian;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import java.util.ArrayList;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.N)
public class MicrophoneMonitoringService extends Service {

    private static final String TAG = "MicMonitorService";
    public static final String ACTION_MIC_STATUS = "com.example.guardian.MIC_STATUS";
    public static final String EXTRA_MIC_ACTIVE = "mic_active";
    public static final String EXTRA_TIMESTAMP = "timestamp";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_ACTIVE_APPS = "active_apps";

    private AudioManager audioManager;
    private Handler handler;
    private boolean isMicrophoneActive = false;
    private long microphoneStartTime = 0;
    private ActivityManager activityManager;

    private AudioManager.AudioRecordingCallback recordingCallback = new AudioManager.AudioRecordingCallback() {
        @Override
        public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
            super.onRecordingConfigChanged(configs);
            handleMicrophoneStateChange(configs);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        handler = new Handler(Looper.getMainLooper());

        // Registra il callback per monitorare le registrazioni audio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            audioManager.registerAudioRecordingCallback(recordingCallback, handler);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        return START_STICKY; // Riavvia automaticamente se terminato dal sistema
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");

        // Deregistra il callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            audioManager.unregisterAudioRecordingCallback(recordingCallback);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Service non bindato
    }

    private void handleMicrophoneStateChange(List<AudioRecordingConfiguration> configs) {
        boolean currentlyActive = !configs.isEmpty();
        long currentTime = System.currentTimeMillis();

        // Se lo stato è cambiato
        if (currentlyActive != isMicrophoneActive) {
            if (currentlyActive) {
                // Microfono attivato
                isMicrophoneActive = true;
                microphoneStartTime = currentTime;
                Log.d(TAG, "Microphone activated at: " + microphoneStartTime);

                // Invia broadcast di attivazione
                sendMicrophoneStatusBroadcast(true, microphoneStartTime, 0);

            } else {
                // Microfono disattivato
                isMicrophoneActive = false;
                long duration = currentTime - microphoneStartTime;
                Log.d(TAG, "Microphone deactivated. Duration: " + duration + "ms");

                // Ottieni le app attive durante l'utilizzo del microfono
                List<String> activeApps = getActiveApplications();

                // Invia broadcast di disattivazione con durata e app attive
                sendMicrophoneStatusBroadcast(false, microphoneStartTime, duration, activeApps);
            }
        }
    }

    private void sendMicrophoneStatusBroadcast(boolean isActive, long timestamp, long duration) {
        sendMicrophoneStatusBroadcast(isActive, timestamp, duration, new ArrayList<>());
    }

    private void sendMicrophoneStatusBroadcast(boolean isActive, long timestamp, long duration, List<String> activeApps) {
        Intent intent = new Intent(ACTION_MIC_STATUS);
        intent.putExtra(EXTRA_MIC_ACTIVE, isActive);
        intent.putExtra(EXTRA_TIMESTAMP, timestamp);
        intent.putExtra(EXTRA_DURATION, duration);
        intent.putStringArrayListExtra(EXTRA_ACTIVE_APPS, new ArrayList<>(activeApps));

        sendBroadcast(intent);
        Log.d(TAG, "Broadcast sent - Active: " + isActive + ", Duration: " + duration + "ms");
    }

    private List<String> getActiveApplications() {
        List<String> activeApps = new ArrayList<>();

        try {
            // Ottieni i task in esecuzione (richiede permission)
            List<ActivityManager.RunningTaskInfo> runningTasks = activityManager.getRunningTasks(20);
            for (ActivityManager.RunningTaskInfo taskInfo : runningTasks) {
                if (taskInfo.topActivity != null) {
                    String packageName = taskInfo.topActivity.getPackageName();
                    String appName = getApplicationName(packageName);
                    if (appName != null && !activeApps.contains(appName)) {
                        activeApps.add(appName);
                    }
                }
            }

            // Ottieni anche i processi in esecuzione
            List<ActivityManager.RunningAppProcessInfo> runningApps = activityManager.getRunningAppProcesses();
            if (runningApps != null) {
                for (ActivityManager.RunningAppProcessInfo processInfo : runningApps) {
                    if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                            processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {

                        for (String process : processInfo.pkgList) {
                            String appName = getApplicationName(process);
                            if (appName != null && !activeApps.contains(appName)) {
                                activeApps.add(appName);
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting active applications", e);
        }

        return activeApps;
    }

    private String getApplicationName(String packageName) {
        try {
            return (String) getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0));
        } catch (Exception e) {
            return packageName; // Fallback al nome del package
        }
    }
}