package com.example.guardian;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Receiver per riavviare automaticamente il servizio di monitoraggio
 * in caso di riavvio del sistema o terminazione del servizio
 */
public class ServiceRestartReceiver extends BroadcastReceiver {

    private static final String TAG = "ServiceRestartReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_REBOOT.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
                "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {

            Log.d(TAG, "System boot detected, restarting monitoring service");
            restartService(context);
        }
    }

    private void restartService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, MicrophoneMonitoringService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            Log.d(TAG, "Monitoring service restarted successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart monitoring service", e);
        }
    }
}