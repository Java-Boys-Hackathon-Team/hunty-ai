package ru.javaboys.huntyhr.security;

import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.security.role.annotation.SpecificPolicy;
import io.jmix.securityflowui.role.UiMinimalPolicies;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;
import ru.javaboys.huntyhr.entity.ApplicationEntity;
import ru.javaboys.huntyhr.entity.CandidateEntity;
import ru.javaboys.huntyhr.entity.CompanyEntity;
import ru.javaboys.huntyhr.entity.InterviewQuestionEntity;
import ru.javaboys.huntyhr.entity.InterviewScenarioEntity;
import ru.javaboys.huntyhr.entity.InterviewSessionEntity;
import ru.javaboys.huntyhr.entity.QuestionTemplateEntity;
import ru.javaboys.huntyhr.entity.ResumeEducationEntity;
import ru.javaboys.huntyhr.entity.ResumeExperienceEntity;
import ru.javaboys.huntyhr.entity.ResumeSkillEntity;
import ru.javaboys.huntyhr.entity.ResumeVersionEntity;
import ru.javaboys.huntyhr.entity.StorageObjectEntity;
import ru.javaboys.huntyhr.entity.User;
import ru.javaboys.huntyhr.entity.VacancyEntity;
import ru.javaboys.huntyhr.entity.VacancyPipelineStageEntity;

@ResourceRole(name = "UI: minimal access", code = UiMinimalRole.CODE)
public interface UiMinimalRole extends UiMinimalPolicies {

    String CODE = "ui-minimal";

    @SpecificPolicy(resources = "ui.loginToUi")
    void login();

    @MenuPolicy(menuIds = { "VacancyEntity.list", "ApplicationEntity.list", "CandidateEntity.list", "CompanyEntity.list", "QuestionTemplateEntity.list" })
    @ViewPolicy(viewIds = { "VacancyEntity.list", "ApplicationEntity.list", "CandidateEntity.list", "CompanyEntity.list", "QuestionTemplateEntity.list", "ApplicationEntity.detail", "CandidateEntity.detail", "CompanyEntity.detail", "VacancyEntity.detail", "QuestionTemplateEntity.detail", "MainView", "LoginView", "ResumeVersionEntity.detail" })
    void screens();

    @EntityAttributePolicy(entityClass = VacancyEntity.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = VacancyEntity.class, actions = EntityPolicyAction.ALL)
    void vacancyEntity();

    @EntityAttributePolicy(entityClass = VacancyPipelineStageEntity.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = VacancyPipelineStageEntity.class, actions = EntityPolicyAction.ALL)
    void vacancyPipelineStageEntity();

    @EntityAttributePolicy(entityClass = StorageObjectEntity.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = StorageObjectEntity.class, actions = EntityPolicyAction.ALL)
    void storageObjectEntity();

    @EntityAttributePolicy(entityClass = ResumeVersionEntity.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ResumeVersionEntity.class, actions = EntityPolicyAction.ALL)
    void resumeVersionEntity();

    @EntityAttributePolicy(entityClass = ResumeExperienceEntity.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ResumeExperienceEntity.class, actions = EntityPolicyAction.ALL)
    void resumeExperienceEntity();

    @EntityAttributePolicy(entityClass = ResumeSkillEntity.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ResumeSkillEntity.class, actions = EntityPolicyAction.ALL)
    void resumeSkillEntity();

    @EntityAttributePolicy(entityClass = QuestionTemplateEntity.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = QuestionTemplateEntity.class, actions = EntityPolicyAction.ALL)
    void questionTemplateEntity();

    @EntityAttributePolicy(entityClass = ResumeEducationEntity.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ResumeEducationEntity.class, actions = EntityPolicyAction.ALL)
    void resumeEducationEntity();

    @EntityAttributePolicy(entityClass = InterviewSessionEntity.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = InterviewSessionEntity.class, actions = EntityPolicyAction.ALL)
    void interviewSessionEntity();

    @EntityAttributePolicy(entityClass = InterviewScenarioEntity.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = InterviewScenarioEntity.class, actions = EntityPolicyAction.ALL)
    void interviewScenarioEntity();

    @EntityAttributePolicy(entityClass = CompanyEntity.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = CompanyEntity.class, actions = EntityPolicyAction.ALL)
    void companyEntity();

    @EntityAttributePolicy(entityClass = InterviewQuestionEntity.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = InterviewQuestionEntity.class, actions = EntityPolicyAction.ALL)
    void interviewQuestionEntity();

    @EntityAttributePolicy(entityClass = ApplicationEntity.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = ApplicationEntity.class, actions = EntityPolicyAction.ALL)
    void applicationEntity();

    @EntityAttributePolicy(entityClass = CandidateEntity.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = CandidateEntity.class, actions = EntityPolicyAction.ALL)
    void candidateEntity();

    @EntityAttributePolicy(entityClass = User.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @EntityPolicy(entityClass = User.class, actions = EntityPolicyAction.ALL)
    void user();
}
