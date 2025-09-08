package ru.javaboys.huntyhr.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "INTERVIEW_SESSION_ENTITY", indexes = {
        @Index(name = "IDX_INTERVIEW_SESSION_ENTITY_APPLICATION", columnList = "APPLICATION_ID"),
        @Index(name = "IDX_INTERVIEW_SESSION_ENTITY_SCENARIO", columnList = "SCENARIO_ID"),
        @Index(name = "IDX_INTERVIEW_SESSION_ENTITY_TRANSCRIPTION", columnList = "TRANSCRIPTION_ID"),
        @Index(name = "IDX_INTERVIEW_SESSION_ENTITY_ANALYTICS", columnList = "ANALYTICS_ID"),
        @Index(name = "IDX_INTERVIEW_SESSION_ENTITY_VIDEO_SOURCE", columnList = "VIDEO_SOURCE_ID")
})
@Entity
public class InterviewSessionEntity {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @JoinColumn(name = "APPLICATION_ID")
    @OneToOne(fetch = FetchType.LAZY)
    private ApplicationEntity application;

    @JoinColumn(name = "SCENARIO_ID")
    @OneToOne(fetch = FetchType.LAZY)
    private InterviewScenarioEntity scenario;

    @Column(name = "STATE")
    private String state;

    @Column(name = "SCHEDULED_START_AT")
    private LocalDateTime scheduledStartAt;

    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt;

    @Column(name = "COMPLETED_AT")
    private LocalDateTime completedAt;

    @Column(name = "LANGUAGE_")
    private String language;

    @JoinColumn(name = "TRANSCRIPTION_ID")
    @OneToOne(fetch = FetchType.LAZY)
    private StorageObjectEntity transcription;

    @JoinColumn(name = "ANALYTICS_ID")
    @OneToOne(fetch = FetchType.LAZY)
    private StorageObjectEntity analytics;

    @Column(name = "NOTIFICATION_SENT")
    private Boolean notificationSent;

    public Boolean getNotificationSent() {
        return notificationSent;
    }

    public void setNotificationSent(Boolean notification_sent) {
        this.notificationSent = notification_sent;
    }

    @JoinColumn(name = "VIDEO_SOURCE_ID")
    @OneToOne(fetch = FetchType.LAZY)
    private StorageObjectEntity video_source;

    @Column(name = "INTERVIEW_LINK")
    @Lob
    private String interviewLink;

    public String getInterviewLink() {
        return interviewLink;
    }

    public void setInterviewLink(String interviewLink) {
        this.interviewLink = interviewLink;
    }

    public StorageObjectEntity getVideo_source() {
        return video_source;
    }

    public void setVideo_source(StorageObjectEntity video_source) {
        this.video_source = video_source;
    }

    public StorageObjectEntity getTranscription() {
        return transcription;
    }

    public void setTranscription(StorageObjectEntity transcription) {
        this.transcription = transcription;
    }

    public StorageObjectEntity getAnalytics() {
        return analytics;
    }

    public void setAnalytics(StorageObjectEntity analytics) {
        this.analytics = analytics;
    }

    public LanguageEnum getLanguage() {
        return language == null ? null : LanguageEnum.fromId(language);
    }

    public void setLanguage(LanguageEnum language) {
        this.language = language == null ? null : language.getId();
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getScheduledStartAt() {
        return scheduledStartAt;
    }

    public void setScheduledStartAt(LocalDateTime scheduledStartAt) {
        this.scheduledStartAt = scheduledStartAt;
    }

    public InterviewStateEnum getState() {
        return state == null ? null : InterviewStateEnum.fromId(state);
    }

    public void setState(InterviewStateEnum state) {
        this.state = state == null ? null : state.getId();
    }

    public InterviewScenarioEntity getScenario() {
        return scenario;
    }

    public void setScenario(InterviewScenarioEntity scenario) {
        this.scenario = scenario;
    }

    public ApplicationEntity getApplication() {
        return application;
    }

    public void setApplication(ApplicationEntity application) {
        this.application = application;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}