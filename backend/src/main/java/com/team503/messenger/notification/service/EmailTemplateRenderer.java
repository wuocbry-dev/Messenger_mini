package com.team503.messenger.notification.service;

import com.team503.messenger.notification.config.MailProperties;
import com.team503.messenger.notification.dto.EmailContent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Fills {@code mail/layout.html} with escaped content. Styles are inline because
 * Gmail and most clients drop or rewrite class-based CSS.
 */
@Component
public class EmailTemplateRenderer {

    private static final String LAYOUT_PATH = "mail/layout.html";

    private static final String PARAGRAPH =
            "<p style=\"margin:0 0 16px;font-size:15px;line-height:24px;color:#3a3f4b;\">%s</p>";

    private static final String BUTTON = """
            <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="margin:8px 0 24px;">
              <tr>
                <td align="center" bgcolor="#0866ff" style="border-radius:999px;background:#0866ff;">
                  <a href="%s" target="_blank" style="display:inline-block;padding:13px 30px;font-size:15px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:999px;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">%s</a>
                </td>
              </tr>
            </table>
            <p style="margin:0 0 16px;font-size:12px;line-height:18px;color:#8a8f98;">Nếu nút không hoạt động, hãy mở liên kết sau:<br><a href="%s" target="_blank" style="color:#0866ff;word-break:break-all;">%s</a></p>""";

    private static final String FOOTNOTE =
            "<p style=\"margin:0;font-size:13px;line-height:20px;color:#8a8f98;\">%s</p>";

    private final MailProperties properties;
    private final String layout;

    public EmailTemplateRenderer(MailProperties properties) {
        this.properties = properties;
        this.layout = loadLayout();
    }

    public String renderHtml(String subject, EmailContent content) {
        StringBuilder body = new StringBuilder();
        for (String paragraph : content.paragraphs()) {
            body.append(PARAGRAPH.formatted(escapeMultiline(paragraph)));
        }

        String action = "";
        if (hasAction(content)) {
            String url = escape(content.actionUrl());
            action = BUTTON.formatted(url, escape(content.actionLabel()), url, url);
        }

        String footnote = isBlank(content.footnote()) ? "" : FOOTNOTE.formatted(escapeMultiline(content.footnote()));

        return layout
                .replace("{{subject}}", escape(subject))
                .replace("{{preheader}}", escape(content.preheader()))
                .replace("{{appName}}", escape(properties.getFromName()))
                .replace("{{appUrl}}", escape(properties.getAppUrl()))
                .replace("{{heading}}", escape(content.heading()))
                .replace("{{year}}", String.valueOf(Year.now().getValue()))
                .replace("{{body}}", body)
                .replace("{{action}}", action)
                .replace("{{footnote}}", footnote);
    }

    /** Plain-text alternative part; improves deliverability and serves text-only clients. */
    public String renderText(EmailContent content) {
        StringBuilder text = new StringBuilder();
        if (!isBlank(content.heading())) {
            text.append(content.heading()).append("\n\n");
        }
        for (String paragraph : content.paragraphs()) {
            text.append(paragraph).append("\n\n");
        }
        if (hasAction(content)) {
            text.append(content.actionLabel()).append(": ").append(content.actionUrl()).append("\n\n");
        }
        if (!isBlank(content.footnote())) {
            text.append(content.footnote()).append("\n\n");
        }
        text.append("— ").append(properties.getFromName());
        return text.toString();
    }

    private static boolean hasAction(EmailContent content) {
        if (isBlank(content.actionLabel()) || isBlank(content.actionUrl())) {
            return false;
        }
        String url = content.actionUrl().trim().toLowerCase();
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw new IllegalArgumentException("Email action URL must be http(s): " + content.actionUrl());
        }
        return true;
    }

    private static String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name());
    }

    private static String escapeMultiline(String value) {
        return escape(value).replace("\n", "<br>");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String loadLayout() {
        try (InputStream in = new ClassPathResource(LAYOUT_PATH).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot load email layout " + LAYOUT_PATH, ex);
        }
    }
}
