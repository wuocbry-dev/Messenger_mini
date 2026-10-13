package com.team503.messenger.call.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically marks calls that rang past the timeout as MISSED. */
@Component
public class CallTimeoutScheduler {

    private final CallService callService;

    public CallTimeoutScheduler(CallService callService) {
        this.callService = callService;
    }

    @Scheduled(fixedDelayString = "${app.agora.ring-check-interval-ms:5000}")
    void expireStaleRinging() {
        callService.expireStaleRinging();
    }
}
