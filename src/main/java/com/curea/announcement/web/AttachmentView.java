package com.curea.announcement.web;

import com.curea.announcement.domain.Attachment;

public record AttachmentView(String id, String name, String mimeType, Long size) {

    public static AttachmentView of(Attachment attachment) {
        return new AttachmentView(attachment.getDocumentId(), attachment.getFileName(), attachment.getMimeType(), attachment.getSizeBytes());
    }
}
