package com.cashierapp.photocheckout.data.telemetry

import android.util.Log
import com.cashierapp.photocheckout.domain.telemetry.ScanTelemetry
import com.cashierapp.photocheckout.domain.telemetry.ScanTelemetryEvent
import javax.inject.Inject

/**
 * MVP telemetry sink: logs per-scan latency, cost, and item counts locally. No image
 * ever leaves the device via telemetry (Boundaries, X-3).
 */
public class LoggingScanTelemetry
    @Inject
    constructor() : ScanTelemetry {
        override fun record(event: ScanTelemetryEvent) {
            Log.i(
                TAG,
                "scan success=${event.success} latencyMs=${event.latencyMs} items=${event.itemCount} " +
                    "unidentified=${event.unidentifiedCount} costMicros=${event.estimatedCostMicros}",
            )
        }

        private companion object {
            const val TAG = "ScanTelemetry"
        }
    }
