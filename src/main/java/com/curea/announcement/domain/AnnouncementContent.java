package com.curea.announcement.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface AnnouncementContent {

    String getTitle();
    String getSummary();
    String getBody();
    LocalDate getPublishDate();
    LocalDate getExpireDate();
    boolean isPinRequested();
    AudienceType getAudienceType();
    Set<String> getAudienceRoles();
    List<Attachment> getAttachments();

    void setTitle(String title);
    void setSummary(String summary);
    void setBody(String body);
    void setPublishDate(LocalDate publishDate);
    void setExpireDate(LocalDate expireDate);
    void setPinRequested(boolean pinRequested);
    void setAudienceType(AudienceType audienceType);

    default void copyFrom(AnnouncementContent source) {
        setTitle(source.getTitle());
        setSummary(source.getSummary());
        setBody(source.getBody());
        setPublishDate(source.getPublishDate());
        setExpireDate(source.getExpireDate());
        setPinRequested(source.isPinRequested());
        setAudienceType(source.getAudienceType());
        getAudienceRoles().clear();
        getAudienceRoles().addAll(source.getAudienceRoles());
        getAttachments().clear();
        getAttachments().addAll(source.getAttachments());
    }
}
