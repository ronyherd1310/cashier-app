package com.cashierapp.photocheckout.domain.telemetry

/**
 * Records per-scan telemetry for the prototype window (X-3): latency, cost, and
 * item-level fields that feed the L6 accuracy benchmark and go/no-go decision.
 * Implementations never transmit images.
 */
public interface ScanTelemetry {
    public fun record(event: ScanTelemetryEvent)
}

public data class ScanTelemetryEvent(
    val latencyMs: Long,
    val success: Boolean,
    val itemCount: Int,
    val unidentifiedCount: Int,
    val estimatedCostMicros: Long? = null,
)

/** Default no-op used where telemetry is irrelevant (e.g. unit tests). */
public object NoOpScanTelemetry : ScanTelemetry {
    override fun record(event: ScanTelemetryEvent) {
        // intentionally empty
    }
}
