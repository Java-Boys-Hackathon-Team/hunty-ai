package ru.javaboys.huntyhr.entity;

import java.util.UUID;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@JmixEntity
@Table(name = "QUESTION_TEMPLATE_ENTITY")
@Entity
public class QuestionTemplateEntity {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Column(name = "TYPE_")
    private String type;

    @Column(name = "IDX")
    private Long idx;

    @Column(name = "DIFFICULTY")
    private Long difficulty;

    @Column(name = "TEXT")
    @Lob
    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Long getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Long difficulty) {
        this.difficulty = difficulty;
    }

    public Long getIdx() {
        return idx;
    }

    public void setIdx(Long idx) {
        this.idx = idx;
    }

    public QuestionTypeEnum getType() {
        return type == null ? null : QuestionTypeEnum.fromId(type);
    }

    public void setType(QuestionTypeEnum type) {
        this.type = type == null ? null : type.getId();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}