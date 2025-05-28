package com.example.guardian;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int USAGE_STATS_REQUEST_CODE = 1002;
    private static final int BATTERY_OPTIMIZATION_REQUEST_CODE = 1003;

    private Switch monitoringSwitch;
    private TextView statusText;
    private RecyclerView logsRecyclerView;
    private Button clearLogsButton;
    private Button settingsButton;

    private LogAdapter logAdapter;
    private List<LogEntry> logEntries = new ArrayList<>();
    private boolean isServiceRunning = false;

    // Receiver per ricevere aggiornamenti dal servizio
    private BroadcastReceiver usageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("MICROPHONE_USAGE_UPDATE".equals(intent.getAction())) {
                List<MicrophoneMonitoringService.MicrophoneUsageInfo> usageInfoList =
                        intent.getParcelableArrayListExtra("usage_info");
                if (usageInfoList != null) {
                    handleMicrophoneUsageUpdate(usageInfoList);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupRecyclerView();
        setupListeners();

        // Registra il receiver per gli aggiornamenti
        LocalBroadcastManager.getInstance(this).registerReceiver(
                usageReceiver, new IntentFilter("MICROPHONE_USAGE_UPDATE"));

        // Controlla i permessi all'avvio
        checkAndRequestPermissions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(usageReceiver);
    }

    private void initializeViews() {
        monitoringSwitch = findViewById(R.id.monitoring_switch);
        statusText = findViewById(R.id.status_text);
        logsRecyclerView = findViewById(R.id.logs_recycler_view);
        clearLogsButton = findViewById(R.id.clear_logs_button);
        settingsButton = findViewById(R.id.settings_button);

        updateStatusText("Servizio non attivo");
    }

    private void setupRecyclerView() {
        logAdapter = new LogAdapter(logEntries);
        logsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        logsRecyclerView.setAdapter(logAdapter);
    }

    private void setupListeners() {
        monitoringSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (checkAllPermissions()) {
                    startMonitoringService();
                } else {
                    monitoringSwitch.setChecked(false);
                    checkAndRequestPermissions();
                }
            } else {
                stopMonitoringService();
            }
        });

        clearLogsButton.setOnClickListener(v -> clearLogs());

        settingsButton.setOnClickListener(v -> openSettings());
    }

    private void checkAndRequestPermissions() {
        List<String> missingPermissions = new ArrayList<>();

        // Controlla permessi base
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            missingPermissions.add(Manifest.permission.RECORD_AUDIO);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // Richiedi permessi mancanti
        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    missingPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            return;
        }

        // Controlla Usage Stats permission
        if (!hasUsageStatsPermission()) {
            showUsageStatsPermissionDialog();
            return;
        }

        // Controlla ottimizzazione batteria
        if (!isBatteryOptimizationDisabled()) {
            showBatteryOptimizationDialog();
            return;
        }

        // Tutti i permessi sono OK
        updateStatusText("Permessi OK - Pronto per il monitoraggio");
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean isBatteryOptimizationDisabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }

    private boolean checkAllPermissions() {
        return hasPermission(Manifest.permission.RECORD_AUDIO) &&
                hasUsageStatsPermission() &&
                isBatteryOptimizationDisabled();
    }

    private void showUsageStatsPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permesso Usage Stats Richiesto")
                .setMessage("Per monitorare le app attive, è necessario concedere il permesso di accesso alle statistiche di utilizzo.")
                .setPositiveButton("Apri Impostazioni", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    startActivityForResult(intent, USAGE_STATS_REQUEST_CODE);
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    private void showBatteryOptimizationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Disabilita Ottimizzazione Batteria")
                .setMessage("Per garantire il funzionamento continuo del servizio, è consigliabile disabilitare l'ottimizzazione della batteria per questa app.")
                .setPositiveButton("Apri Impostazioni", (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST_CODE);
                    }
                })
                .setNegativeButton("Continua Comunque", (dialog, which) -> {
                    // Continua senza disabilitare l'ottimizzazione
                })
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
                checkAndRequestPermissions(); // Continua con altri controlli
            } else {
                Toast.makeText(this, "Permessi necessari per il funzionamento", Toast.LENGTH_LONG).show();
                updateStatusText("Permessi mancanti");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == USAGE_STATS_REQUEST_CODE) {
            if (hasUsageStatsPermission()) {
                checkAndRequestPermissions(); // Continua con altri controlli
            } else {
                Toast.makeText(this, "Permesso Usage Stats necessario", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == BATTERY_OPTIMIZATION_REQUEST_CODE) {
            // Continua indipendentemente dal risultato
            checkAndRequestPermissions();
        }
    }

    private void startMonitoringService() {
        if (!isServiceRunning) {
            Intent serviceIntent = new Intent(this, MicrophoneMonitoringService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }

            isServiceRunning = true;
            updateStatusText("Servizio di monitoraggio attivo");
            Log.d(TAG, "Monitoring service started");
        }
    }

    private void stopMonitoringService() {
        if (isServiceRunning) {
            Intent serviceIntent = new Intent(this, MicrophoneMonitoringService.class);
            stopService(serviceIntent);

            isServiceRunning = false;
            updateStatusText("Servizio di monitoraggio fermato");
            Log.d(TAG, "Monitoring service stopped");
        }
    }

    private void handleMicrophoneUsageUpdate(List<MicrophoneMonitoringService.MicrophoneUsageInfo> usageInfoList) {
        for (MicrophoneMonitoringService.MicrophoneUsageInfo info : usageInfoList) {
            addLogEntry(info);
        }
    }

    private void addLogEntry(MicrophoneMonitoringService.MicrophoneUsageInfo info) {
        String status = "";
        if (info.isForeground) {
            status = "FOREGROUND";
        } else if (info.isActive) {
            status = "BACKGROUND";
        } else {
            status = "INACTIVE";
        }

        LogEntry entry = new LogEntry(
                info.appName,
                info.packageName,
                status,
                info.timestamp
        );

        logEntries.add(0, entry); // Aggiungi all'inizio della lista

        // Mantieni solo gli ultimi 100 log
        if (logEntries.size() > 100) {
            logEntries.remove(logEntries.size() - 1);
        }

        runOnUiThread(() -> {
            logAdapter.notifyDataSetChanged();
            if (logEntries.size() > 0) {
                logsRecyclerView.scrollToPosition(0);
            }
        });

        Log.d(TAG, String.format("Log added: %s (%s) - %s",
                info.appName, info.packageName, status));
    }

    private void clearLogs() {
        logEntries.clear();
        logAdapter.notifyDataSetChanged();
        Toast.makeText(this, "Log cancellati", Toast.LENGTH_SHORT).show();
    }

    private void openSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    private void updateStatusText(String status) {
        if (statusText != null) {
            statusText.setText(status);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Aggiorna lo stato dello switch basandosi sul servizio
        monitoringSwitch.setChecked(isServiceRunning);

        // Ricontrolla i permessi quando l'utente torna all'app
        if (!isServiceRunning) {
            checkAndRequestPermissions();
        }
    }

    // Classe per rappresentare una voce di log
    public static class LogEntry {
        public final String appName;
        public final String packageName;
        public final String status;
        public final long timestamp;

        public LogEntry(String appName, String packageName, String status, long timestamp) {
            this.appName = appName;
            this.packageName = packageName;
            this.status = status;
            this.timestamp = timestamp;
        }
    }

    // Adapter per il RecyclerView dei log
    private static class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {
        private final List<LogEntry> logs;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        public LogAdapter(List<LogEntry> logs) {
            this.logs = logs;
        }

        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.log_item, parent, false);
            return new LogViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            LogEntry entry = logs.get(position);

            holder.appNameText.setText(entry.appName);
            holder.packageNameText.setText(entry.packageName);
            holder.statusText.setText(entry.status);
            holder.timestampText.setText(dateFormat.format(new Date(entry.timestamp)));

            // Colora lo status in base al tipo
            int statusColor;
            switch (entry.status) {
                case "FOREGROUND":
                    statusColor = ContextCompat.getColor(holder.itemView.getContext(), android.R.color.holo_green_dark);
                    break;
                case "BACKGROUND":
                    statusColor = ContextCompat.getColor(holder.itemView.getContext(), android.R.color.holo_orange_dark);
                    break;
                default:
                    statusColor = ContextCompat.getColor(holder.itemView.getContext(), android.R.color.darker_gray);
                    break;
            }
            holder.statusText.setTextColor(statusColor);
        }

        @Override
        public int getItemCount() {
            return logs.size();
        }

        static class LogViewHolder extends RecyclerView.ViewHolder {
            TextView appNameText;
            TextView packageNameText;
            TextView statusText;
            TextView timestampText;

            public LogViewHolder(@NonNull View itemView) {
                super(itemView);
                appNameText = itemView.findViewById(R.id.app_name_text);
                packageNameText = itemView.findViewById(R.id.package_name_text);
                statusText = itemView.findViewById(R.id.status_text);
                timestampText = itemView.findViewById(R.id.timestamp_text);
            }
        }
    }
}