package com.curea.announcement.client;

import com.curea.announcement.service.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Component
public class DocumentClient {

    private static final Logger logger = LoggerFactory.getLogger(DocumentClient.class);
    private static final String OWNER_SERVICE = "announcement";
    private static final Set<String> FORWARDED_CODES = Set.of(
            "DOCUMENT_NOT_FOUND", "DOCUMENT_OWNED_BY_ANOTHER_RECORD", "DOCUMENT_NOT_UPLOADED_BY_USER");

    public record DocumentAccess(String id, String visibility, List<String> roles) {
    }

    public record DocumentMetadata(String id, String name, String mimeType, Long size) {
    }

    public record AttachedDocument(String id, String ownerId) {
    }

    private record RemoveRequest(String ownerService, String ownerId, List<String> ids) {
    }

    private record SyncRequest(String ownerService, String ownerId, List<DocumentAccess> documents,
                               Long actorId, Set<String> actorRoles) {
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DocumentClient(@Value("${document.service-url}") String baseUrl,
                          @Value("${gateway.trust-secret:}") String trustSecret,
                          ObjectMapper objectMapper) {
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
        if (trustSecret != null && !trustSecret.isBlank()) {
            builder.defaultHeader("X-Gateway-Trust", trustSecret.trim());
        }
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public List<DocumentMetadata> sync(Long announcementId, List<DocumentAccess> documents, Long actorId, Set<String> actorRoles) {
        try {
            DocumentMetadata[] response = restClient.put()
                    .uri("/internal/documents/sync")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SyncRequest(OWNER_SERVICE, String.valueOf(announcementId), documents, actorId, actorRoles))
                    .retrieve()
                    .body(DocumentMetadata[].class);
            return response == null ? List.of() : List.of(response);
        } catch (RestClientResponseException ex) {
            String code = extractCode(ex.getResponseBodyAsString());
            if (code != null && FORWARDED_CODES.contains(code)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, code);
            }
            logger.error("Document service sync failed: status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "DOCUMENT_SERVICE_ERROR");
        } catch (RuntimeException ex) {
            logger.error("Document service sync failed", ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "DOCUMENT_SERVICE_ERROR");
        }
    }

    public List<AttachedDocument> attachedSyncedBefore(Instant before) {
        AttachedDocument[] response = restClient.get()
                .uri(uri -> uri.path("/internal/documents/attached")
                        .queryParam("ownerService", OWNER_SERVICE)
                        .queryParam("syncedBefore", before.toString())
                        .build())
                .retrieve()
                .body(AttachedDocument[].class);
        return response == null ? List.of() : List.of(response);
    }

    public void remove(String ownerId, List<String> ids) {
        restClient.post()
                .uri("/internal/documents/remove")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RemoveRequest(OWNER_SERVICE, ownerId, ids))
                .retrieve()
                .toBodilessEntity();
    }

    private String extractCode(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            return node.hasNonNull("code") ? node.get("code").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
