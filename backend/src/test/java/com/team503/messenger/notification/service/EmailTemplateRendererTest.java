package com.team503.messenger.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team503.messenger.notification.config.MailProperties;
import com.team503.messenger.notification.dto.EmailContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailTemplateRendererTest {

    private EmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        MailProperties properties = new MailProperties();
        properties.setFromName("Messenger Mini");
        properties.setAppUrl("https://chat.example.com");
        renderer = new EmailTemplateRenderer(properties);
    }

    @Test
    void rendersContentIntoLayout() {
        EmailContent content = EmailContent.of("Xin chào Khôi", "Dòng 1\nDòng 2")
                .withPreheader("Xem trước")
                .withAction("Mở ứng dụng", "https://chat.example.com/login")
                .withFootnote("Ghi chú");

        String html = renderer.renderHtml("Tiêu đề", content);

        assertThat(html)
                .contains("<title>Tiêu đề</title>")
                .contains("Xin chào Khôi")
                .contains("Dòng 1<br>Dòng 2")
                .contains("href=\"https://chat.example.com/login\"")
                .contains("Mở ứng dụng")
                .contains("Xem trước")
                .contains("Ghi chú")
                .doesNotContain("{{");
    }

    @Test
    void escapesUserSuppliedText() {
        String html = renderer.renderHtml("s", EmailContent.of("<script>x</script>", "a & b"));

        assertThat(html)
                .doesNotContain("<script>")
                .contains("&lt;script&gt;x&lt;/script&gt;")
                .contains("a &amp; b");
    }

    @Test
    void omitsButtonWhenNoAction() {
        String html = renderer.renderHtml("s", EmailContent.of("Heading", "Body"));

        assertThat(html).doesNotContain("Nếu nút không hoạt động");
    }

    @Test
    void rejectsNonHttpActionUrl() {
        EmailContent content = EmailContent.of("h").withAction("Click", "javascript:alert(1)");

        assertThatThrownBy(() -> renderer.renderHtml("s", content))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rendersPlainTextAlternative() {
        String text = renderer.renderText(EmailContent.of("Heading", "Body")
                .withAction("Open", "https://chat.example.com"));

        assertThat(text).contains("Heading").contains("Body").contains("Open: https://chat.example.com");
    }
}
