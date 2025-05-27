package com.example.guardian;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
                Intent.ACTION_PACKAGE_REPLACED.equals(action)) {

            // Riavvia il servizio di monitoraggio del microfono
            Intent serviceIntent = new Intent(context, MicrophoneMonitoringService.class);
            context.startService(serviceIntent);

            Log.d(TAG, "Microphone monitoring service restarted after boot/update");
        }
    }
}