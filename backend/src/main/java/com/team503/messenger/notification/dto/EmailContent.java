package com.team503.messenger.notification.dto;

import java.util.List;

/**
 * Plain-text content poured into the shared HTML email layout. All fields are
 * escaped by the renderer, so callers never pass raw HTML.
 *
 * @param preheader   short preview text shown by Gmail next to the subject (optional)
 * @param heading     large title at the top of the card
 * @param paragraphs  body paragraphs
 * @param actionLabel button text (optional, requires {@code actionUrl})
 * @param actionUrl   button target, must be http(s) (optional)
 * @param footnote    small grey text under the button (optional)
 */
public record EmailContent(
        String preheader,
        String heading,
        List<String> paragraphs,
        String actionLabel,
        String actionUrl,
        String footnote) {

    public EmailContent {
        paragraphs = paragraphs == null ? List.of() : List.copyOf(paragraphs);
    }

    public static EmailContent of(String heading, String... paragraphs) {
        return new EmailContent(null, heading, List.of(paragraphs), null, null, null);
    }

    public EmailContent withAction(String label, String url) {
        return new EmailContent(preheader, heading, paragraphs, label, url, footnote);
    }

    public EmailContent withPreheader(String text) {
        return new EmailContent(text, heading, paragraphs, actionLabel, actionUrl, footnote);
    }

    public EmailContent withFootnote(String text) {
        return new EmailContent(preheader, heading, paragraphs, actionLabel, actionUrl, text);
    }
}
