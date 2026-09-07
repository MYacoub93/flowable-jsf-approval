package com.example.approval.ucm;

import com.example.approval.ucm.config.AlfrescoProperties;
import com.example.approval.ucm.exception.AlfrescoUcmException;
import com.example.approval.ucm.service.AlfrescoUcmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the application-level document link scheme of
 * {@link AlfrescoUcmService}: Base64url refs that survive a URL path
 * (a raw CMIS id contains {@code / : ;} and can never round-trip) plus
 * backward compatibility with legacy percent-encoded refs. These tests need
 * no Alfresco connection - link building/decoding is pure string logic.
 */
class AlfrescoUcmServiceLinkTest {

    private AlfrescoUcmService service;

    @BeforeEach
    void setUp() {
        service = new AlfrescoUcmService(new AlfrescoProperties());
    }

    @Test
    void linkIsUrlSafeAndRoundTrips() {
        String cmisId = "workspace://SpacesStore/d0260c8a-7e9b-4f6e-a59b-0d3f1c2b9a11;1.0";
        String link = service.getDocumentLink(cmisId);

        assertThat(link).startsWith(AlfrescoUcmService.APP_DOCUMENT_URL_TEMPLATE);
        String ref = link.substring(AlfrescoUcmService.APP_DOCUMENT_URL_TEMPLATE.length());
        // URL-path-safe: only unreserved characters, no '/', ':', ';', '%'
        assertThat(ref).matches("[A-Za-z0-9_-]+");

        assertThat(service.decodeDocumentRef(ref)).isEqualTo(cmisId);
    }

    @Test
    void decodeUnderstandsLegacyPercentEncodedRefs() {
        String cmisId = "workspace://SpacesStore/d0260c8a-7e9b-4f6e-a59b-0d3f1c2b9a11;1.0";
        String legacyRef = URLEncoder.encode(cmisId, StandardCharsets.UTF_8);

        assertThat(service.decodeDocumentRef(legacyRef)).isEqualTo(cmisId);
    }

    @Test
    void decodeRejectsGarbage() {
        assertThatThrownBy(() -> service.decodeDocumentRef("not-a-valid-ref!!"))
                .isInstanceOf(AlfrescoUcmException.class);
    }

    @Test
    void decodeRejectsBase64OfNonCmisValue() {
        // Base64url of "hello world" - decodes fine but is not a CMIS id
        String ref = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("hello world".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.decodeDocumentRef(ref))
                .isInstanceOf(AlfrescoUcmException.class);
    }

    @Test
    void blankArgumentsRejected() {
        assertThatThrownBy(() -> service.getDocumentLink(" "))
                .isInstanceOf(AlfrescoUcmException.class);
        assertThatThrownBy(() -> service.decodeDocumentRef(""))
                .isInstanceOf(AlfrescoUcmException.class);
    }
}