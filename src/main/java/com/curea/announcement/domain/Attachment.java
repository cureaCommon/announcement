package com.curea.announcement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Attachment {

    @Column(name = "document_id", nullable = false, length = 36)
    private String documentId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "mime_type", length = 120)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    protected Attachment() {
    }

    public Attachment(String documentId, String fileName, String mimeType, Long sizeBytes) {
        this.documentId = documentId;
        this.fileName = fileName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
    }

    public String getDocumentId() { return documentId; }
    public String getFileName() { return fileName; }
    public String getMimeType() { return mimeType; }
    public Long getSizeBytes() { return sizeBytes; }
}
