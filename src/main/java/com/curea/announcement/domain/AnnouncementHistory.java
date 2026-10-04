package com.curea.announcement.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "announcement_history", indexes = @Index(name = "ix_announcement_history_announcement", columnList = "announcement_id"))
public class AnnouncementHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "announcement_id", nullable = false)
    private Long announcementId;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "created_on", nullable = false)
    private Instant createdOn;

    protected AnnouncementHistory() {
    }

    public AnnouncementHistory(Long announcementId, String action, Long actorId, String note) {
        this.announcementId = announcementId;
        this.action = action;
        this.actorId = actorId;
        this.note = note;
        this.createdOn = Instant.now();
    }

    public Long getId() { return id; }
    public Long getAnnouncementId() { return announcementId; }
    public String getAction() { return action; }
    public Long getActorId() { return actorId; }
    public String getNote() { return note; }
    public Instant getCreatedOn() { return createdOn; }
}
