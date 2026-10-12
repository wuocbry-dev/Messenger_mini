package com.team503.messenger.call.service;

import java.util.UUID;

/**
 * Generates server-side Agora channel names. Names are short, human-debuggable
 * and unguessable from the outside.
 */
public final class AgoraChannelNameGenerator {

    private static final String PREFIX = "call-";

    private AgoraChannelNameGenerator() {
    }

    public static String generate(Long conversationId) {
        // Format: call-<conversationId>-<random>
        // Cap to 128 characters total (the column size in MySQL).
        String random = randomToken(16);
        String name = PREFIX + conversationId + "-" + random;
        if (name.length() > 128) {
            return name.substring(0, 128);
        }
        return name;
    }

    private static String randomToken(int length) {
        // hex without dashes; UUIDs are 32 chars
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }
}
