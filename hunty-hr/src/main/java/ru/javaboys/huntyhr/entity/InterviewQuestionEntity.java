package ru.javaboys.huntyhr.entity;

import java.time.LocalDateTime;
import java.util.UUID;

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

@JmixEntity
@Table(name = "INTERVIEW_QUESTION_ENTITY", indexes = {
        @Index(name = "IDX_INTERVIEW_QUESTION_ENTITY_TEMPLATE", columnList = "TEMPLATE_ID")
})
@Entity
public class InterviewQuestionEntity {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @JoinColumn(name = "TEMPLATE_ID")
    @OneToOne(fetch = FetchType.LAZY)
    private QuestionTemplateEntity template;

    @Column(name = "TYPE_")
    private String type;

    @Column(name = "TEXT")
    @Lob
    private String text;

    @Column(name = "ASKED_AT")
    private LocalDateTime askedAt;

    @Column(name = "ANSWER")
    @Lob
    private String answer;

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public LocalDateTime getAskedAt() {
        return askedAt;
    }

    public void setAskedAt(LocalDateTime askedAt) {
        this.askedAt = askedAt;
    }

    public String getText() {
        return text;
    }

    public void setText(String textt) {
        this.text = textt;
    }

    public QuestionTypeEnum getType() {
        return type == null ? null : QuestionTypeEnum.fromId(type);
    }

    public void setType(QuestionTypeEnum type) {
        this.type = type == null ? null : type.getId();
    }

    public QuestionTemplateEntity getTemplate() {
        return template;
    }

    public void setTemplate(QuestionTemplateEntity template) {
        this.template = template;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}