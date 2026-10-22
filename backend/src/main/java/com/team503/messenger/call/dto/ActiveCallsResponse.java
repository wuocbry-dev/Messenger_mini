package com.team503.messenger.call.dto;

import java.util.List;

/**
 * Calls the user should know about right now, for clients that were offline
 * when the realtime events were sent.
 */
public record ActiveCallsResponse(
        /** Calls still ringing for this user (show the incoming-call screen). */
        List<CallResponse> ringing,
        /** Calls in progress in the user's conversations that they can join. */
        List<CallResponse> joinable
) {
}
