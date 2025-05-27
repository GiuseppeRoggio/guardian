package com.example.guardian;

import android.Manifest;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int USAGE_STATS_REQUEST_CODE = 1002;

    private LinearLayout logsContainer;
    private ScrollView scrollView;
    private MicrophoneBroadcastReceiver microphoneReceiver;

    // Permessi richiesti
    private String[] requiredPermissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.GET_TASKS
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();

        // Controlla e richiedi permessi
        if (checkAndRequestPermissions()) {
            startMonitoringServices();
        }

        Log.d(TAG, "MainActivity created");
    }

    private boolean checkAndRequestPermissions() {
        // Controlla permessi standard
        ArrayList<String> missingPermissions = new ArrayList<>();

        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        // Richiedi permessi mancanti
        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    missingPermissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
            return false;
        }

        // Controlla permesso Usage Stats
        if (!hasUsageStatsPermission()) {
            showUsageStatsDialog();
            return false;
        }

        return true;
    }

    private boolean hasUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long time = System.currentTimeMillis();
            try {
                // Prova ad accedere alle usage stats
                usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                        time - 1000 * 60, time);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private void showUsageStatsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permesso Statistiche Utilizzo")
                .setMessage("Per monitorare le app attive, è necessario abilitare l'accesso alle statistiche di utilizzo.\n\n" +
                        "Vai in Impostazioni > App > Accesso Speciale > Statistiche Utilizzo > Guardian")
                .setPositiveButton("Apri Impostazioni", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    startActivityForResult(intent, USAGE_STATS_REQUEST_CODE);
                })
                .setNegativeButton("Salta", (dialog, which) -> {
                    Toast.makeText(this, "Alcune funzionalità potrebbero non funzionare correttamente",
                            Toast.LENGTH_LONG).show();
                    startMonitoringServices();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                if (hasUsageStatsPermission()) {
                    startMonitoringServices();
                } else {
                    showUsageStatsDialog();
                }
            } else {
                showPermissionDeniedDialog();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == USAGE_STATS_REQUEST_CODE) {
            if (hasUsageStatsPermission()) {
                Toast.makeText(this, "Permessi configurati correttamente!", Toast.LENGTH_SHORT).show();
                startMonitoringServices();
            } else {
                Toast.makeText(this, "Permesso non concesso. Alcune funzionalità potrebbero non funzionare.",
                        Toast.LENGTH_LONG).show();
                startMonitoringServices(); // Avvia comunque il servizio base
            }
        }
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permessi Richiesti")
                .setMessage("L'app ha bisogno dei permessi per funzionare correttamente. " +
                        "Puoi concederli dalle impostazioni dell'app.")
                .setPositiveButton("Impostazioni", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Esci", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void startMonitoringServices() {
        startMicrophoneMonitoringService();
        registerMicrophoneReceiver();
        addWelcomeMessage();
    }

    private void initializeViews() {
        scrollView = findViewById(R.id.scrollView);
        logsContainer = findViewById(R.id.logsContainer);
    }

    private void addWelcomeMessage() {
        View welcomeCard = createWelcomeCard();
        logsContainer.addView(welcomeCard);
    }

    private void startMicrophoneMonitoringService() {
        Intent serviceIntent = new Intent(this, MicrophoneMonitoringService.class);
        startService(serviceIntent);
        Log.d(TAG, "Microphone monitoring service started");
    }

    private void registerMicrophoneReceiver() {
        microphoneReceiver = new MicrophoneBroadcastReceiver();
        IntentFilter filter = new IntentFilter(MicrophoneMonitoringService.ACTION_MIC_STATUS);
        registerReceiver(microphoneReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        Log.d(TAG, "Microphone broadcast receiver registered");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (microphoneReceiver != null) {
            unregisterReceiver(microphoneReceiver);
        }
    }

    private class MicrophoneBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MicrophoneMonitoringService.ACTION_MIC_STATUS.equals(intent.getAction())) {
                boolean isActive = intent.getBooleanExtra(MicrophoneMonitoringService.EXTRA_MIC_ACTIVE, false);
                long timestamp = intent.getLongExtra(MicrophoneMonitoringService.EXTRA_TIMESTAMP, 0);
                long duration = intent.getLongExtra(MicrophoneMonitoringService.EXTRA_DURATION, 0);
                ArrayList<String> activeApps = intent.getStringArrayListExtra(MicrophoneMonitoringService.EXTRA_ACTIVE_APPS);

                Log.d(TAG, "Received microphone status: " + isActive + ", Duration: " + duration);

                if (!isActive && duration > 0) {
                    // Microfono disattivato - crea la card del log
                    addMicrophoneLogCard(timestamp, duration, activeApps);
                }
            }
        }
    }

    private void addMicrophoneLogCard(long timestamp, long duration, ArrayList<String> activeApps) {
        runOnUiThread(() -> {
            View logCard = createMicrophoneLogCard(timestamp, duration, activeApps);
            logsContainer.addView(logCard, 0); // Aggiungi in cima

            // Scroll automatico verso l'alto per mostrare la nuova card
            scrollView.post(() -> scrollView.smoothScrollTo(0, 0));
        });
    }

    private View createWelcomeCard() {
        View cardView = LayoutInflater.from(this).inflate(R.layout.card_welcome, null);
        return cardView;
    }

    private View createMicrophoneLogCard(long timestamp, long duration, ArrayList<String> activeApps) {
        View cardView = LayoutInflater.from(this).inflate(R.layout.card_microphone_log, null);

        // Formatta timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault());
        String formattedTime = sdf.format(new Date(timestamp));

        // Formatta durata
        String formattedDuration = formatDuration(duration);

        // Imposta i dati nella card
        TextView timeText = cardView.findViewById(R.id.activationTime);
        TextView durationText = cardView.findViewById(R.id.usageDuration);
        TextView appsText = cardView.findViewById(R.id.activeApps);

        timeText.setText("Attivato alle: " + formattedTime);
        durationText.setText("Durata utilizzo: " + formattedDuration);

        // Formatta lista app
        if (activeApps != null && !activeApps.isEmpty()) {
            StringBuilder appsBuilder = new StringBuilder("App attive:\n");
            for (int i = 0; i < activeApps.size(); i++) {
                appsBuilder.append("• ").append(activeApps.get(i));
                if (i < activeApps.size() - 1) {
                    appsBuilder.append("\n");
                }
            }
            appsText.setText(appsBuilder.toString());
        } else {
            appsText.setText("Nessuna app rilevata");
        }

        return cardView;
    }

    private String formatDuration(long durationMs) {
        if (durationMs < 1000) {
            return durationMs + " ms";
        } else if (durationMs < 60000) {
            return String.format(Locale.getDefault(), "%.1f secondi", durationMs / 1000.0);
        } else {
            long minutes = durationMs / 60000;
            long seconds = (durationMs % 60000) / 1000;
            return String.format(Locale.getDefault(), "%d min %d sec", minutes, seconds);
        }
    }
}