package io.myclaw.server.audit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

class OperationAuditAspectTest {
    @Test
    void redactsCredentialsAndMessageBodies() {
        String result = OperationAuditAspect.safeArguments(
                new String[]{"sessionId", "credentials", "content"},
                new Object[]{"session-1", new Object(), "private prompt"}
        );

        assertThat(result).contains("sessionId=session-1");
        assertThat(result).contains("credentials=<redacted>", "content=<redacted>");
        assertThat(result).doesNotContain("private prompt");
    }

    @Test
    void logsOnlySafeFileMetadata() {
        var file = new MockMultipartFile("file", "report.txt", "text/plain", "secret body".getBytes());

        String result = OperationAuditAspect.safeArguments(new String[]{"file"}, new Object[]{file});

        assertThat(result).contains("report.txt", "text/plain", "size=11");
        assertThat(result).doesNotContain("secret body");
    }
}