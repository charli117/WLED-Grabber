package com.abrenoch.wledgrabber.common.startup;

import android.content.ComponentName;
import android.content.Intent;

import com.abrenoch.wledgrabber.common.BootActivity;
import com.abrenoch.wledgrabber.common.WledScreenService;

import java.util.concurrent.atomic.AtomicInteger;

/** Controls only Android service/consent dispatch; inherited production lifecycle stays real. */
public final class BootStatusHarnessActivity extends BootActivity {
    static final AtomicInteger statusQueries = new AtomicInteger();
    static final AtomicInteger consentRequests = new AtomicInteger();

    @Override public ComponentName startService(Intent intent) {
        if (WledScreenService.GET_STATUS.equals(intent.getAction())) {
            statusQueries.incrementAndGet();
            return new ComponentName(this, WledScreenService.class);
        }
        return super.startService(intent);
    }

    @Override public void startActivityForResult(Intent intent, int requestCode) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            consentRequests.incrementAndGet();
            return;
        }
        super.startActivityForResult(intent, requestCode);
    }
}
