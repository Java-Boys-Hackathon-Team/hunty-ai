package ru.javaboys.huntyhr.entity;

import java.util.List;
import java.util.UUID;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@JmixEntity
@Table(name = "INTERVIEW_SCENARIO_ENTITY")
@Entity
public class InterviewScenarioEntity {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @InstanceName
    @Column(name = "NAME")
    private String name;

    @Column(name = "LANGUAGE_")
    private String language;

    @OneToMany(mappedBy = "interviewScenarioEntity")
    private List<QuestionTemplateEntity> questions;

    public List<QuestionTemplateEntity> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuestionTemplateEntity> questions) {
        this.questions = questions;
    }

    public LanguageEnum getLanguage() {
        return language == null ? null : LanguageEnum.fromId(language);
    }

    public void setLanguage(LanguageEnum language) {
        this.language = language == null ? null : language.getId();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

}