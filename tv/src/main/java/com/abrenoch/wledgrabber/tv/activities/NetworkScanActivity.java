package com.abrenoch.wledgrabber.tv.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;

import com.abrenoch.wledgrabber.common.network.wled.ChannelHint;
import com.abrenoch.wledgrabber.common.network.wled.DiscoveryCandidate;
import com.abrenoch.wledgrabber.common.network.wled.ProbeResult;
import com.abrenoch.wledgrabber.common.network.wled.WledDevice;
import com.abrenoch.wledgrabber.common.network.wled.WledHttpClient;
import com.abrenoch.wledgrabber.common.network.wled.WledNsdDiscovery;
import com.abrenoch.wledgrabber.common.network.wled.WledScannerTask;
import com.abrenoch.wledgrabber.tv.R;
import com.abrenoch.wledgrabber.tv.databinding.ActivityNetworkScanBinding;

import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkScanActivity extends LeanbackActivity implements WledScannerTask.Listener {
    private boolean isScanning = false;
    private ActivityNetworkScanBinding binding;
    private WledScannerTask scannerTask;
    private WledNsdDiscovery nsdDiscovery;
    private final ExecutorService probeExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNetworkScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // only if back was pressed on this Activity will we not be configured when we finish
        setResult(RESULT_OK);
        nsdDiscovery = new WledNsdDiscovery();
        nsdDiscovery.start(this, this::probeNsdCandidate);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MainActivity.REQUEST_INITIAL_SETUP) {
            if (resultCode == RESULT_OK) {
                setResult(RESULT_OK);
                finish();
            }
        }
    }

    public void onClick(View v) {
        if (v.getId() == R.id.startScanButton) {
            if (!isScanning) {
                scannerTask = new WledScannerTask();
                scannerTask.start(this);
            }
        } else if (v.getId() == R.id.manualSetupButton) {
            Intent intent = new Intent(this, ManualSetupActivity.class);
            startActivityForResult(intent, MainActivity.REQUEST_INITIAL_SETUP);
        }
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }

    @SuppressLint("StringFormatInvalid")
    @Override
    public void onScanStarted() {
        isScanning = true;
        binding.startScanButton.setText(com.abrenoch.wledgrabber.common.R.string.scanner_scan_in_progress_button);
        binding.scannerDescriptionText.setGravity(Gravity.CENTER);
        binding.scannerDescriptionText.setText(getString(
                com.abrenoch.wledgrabber.common.R.string.scanner_scan_in_progress_text, "🕵️"));
        binding.progressBar.setIndeterminate(true);
    }

    @Override
    public void onScanFinished(@Nullable WledDevice device) {
        isScanning = false;
        binding.progressBar.setIndeterminate(false);
        if (device == null) {
            binding.startScanButton.setText(com.abrenoch.wledgrabber.common.R.string.scanner_retry_button);
            binding.manualSetupButton.requestFocus();
            binding.scannerDescriptionText.setText(getString(
                    com.abrenoch.wledgrabber.common.R.string.scanner_no_results, "😩"));
            return;
        }
        openResult(device, 80);
    }

    @Override
    public void onScanError(String error) {
        isScanning = false;
        binding.progressBar.setIndeterminate(false);
        binding.startScanButton.setText(com.abrenoch.wledgrabber.common.R.string.scanner_retry_button);
        binding.scannerDescriptionText.setText(error);
    }

    private void probeNsdCandidate(DiscoveryCandidate candidate) {
        probeExecutor.execute(() -> {
            try (WledHttpClient client = new WledHttpClient()) {
                ProbeResult result = client.probe(candidate.host(), candidate.httpPort());
                if (result.isSuccess()) {
                    runOnUiThread(() -> openResult(result.device(), candidate.httpPort()));
                }
            }
        });
    }

    private void openResult(WledDevice device, int httpPort) {
        Intent intent = new Intent(this, ScanResultActivity.class);
        intent.putExtra(ScanResultActivity.EXTRA_RESULT_HOST_NAME, device.address());
        intent.putExtra(ScanResultActivity.EXTRA_RESULT_HTTP_PORT, httpPort);
        intent.putExtra(ScanResultActivity.EXTRA_RESULT_UDP_PORT, device.udpPort());
        intent.putExtra(ScanResultActivity.EXTRA_RESULT_COUNT, device.observedLedCount());
        intent.putExtra(ScanResultActivity.EXTRA_RESULT_VERSION, device.firmwareVersion());
        ChannelHint hint = device.capabilities() == null
                ? ChannelHint.UNKNOWN : device.capabilities().channelHint();
        intent.putExtra(ScanResultActivity.EXTRA_RESULT_HINT, hint.name());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (scannerTask != null) {
            scannerTask.cancel();
        }
        if (nsdDiscovery != null) {
            nsdDiscovery.close();
        }
        probeExecutor.shutdownNow();
        super.onDestroy();
    }
}
