package com.team503.messenger.call.service;

import java.util.UUID;

/**
 * Generates server-side Agora channel names: {@code call-<conversationId>-<16 hex>}.
 * Clients never supply the channel name.
 */
public final class AgoraChannelNameGenerator {

    private static final String PREFIX = "call-";

    private AgoraChannelNameGenerator() {
    }

    public static String generate(Long conversationId) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return PREFIX + conversationId + "-" + random;
    }
}
