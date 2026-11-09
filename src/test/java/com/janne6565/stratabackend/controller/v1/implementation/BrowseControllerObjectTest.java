package com.janne6565.stratabackend.controller.v1.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.janne6565.stratabackend.entity.UserEntity;
import com.janne6565.stratabackend.services.auth.CurrentUser;
import com.janne6565.stratabackend.services.engine.BrowseService;
import com.janne6565.stratabackend.services.engine.StoredObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class BrowseControllerObjectTest {

    private final BrowseService browseService = mock(BrowseService.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final BrowseController controller = new BrowseController(browseService, currentUser);
    private final UUID id = UUID.randomUUID();
    private final UserEntity user = new UserEntity();

    private ResponseEntity<StreamingResponseBody> serve(
            String key, String contentType, String body) {
        when(currentUser.require()).thenReturn(user);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        when(browseService.openObject(id, "site", key, user))
                .thenReturn(
                        new StoredObject(
                                contentType, bytes.length, new ByteArrayInputStream(bytes)));
        return controller.objectContent(id, "site", key);
    }

    @Test
    void streamsBytesAsASandboxedAttachment() throws Exception {
        ResponseEntity<StreamingResponseBody> response =
                serve("pages/index.html", "text/html", "<script>alert(1)</script>");

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(25L);
        assertThat(response.getHeaders().getContentDisposition().isAttachment()).isTrue();
        assertThat(response.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("index.html");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst("Content-Security-Policy")).startsWith("sandbox");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    void fallsBackToOctetStreamForMissingOrBogusContentTypes() {
        assertThat(serve("blob", null, "x").getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(serve("blob2", "not a type", "x").getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }
}
