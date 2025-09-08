package ru.javaboys.huntyhr.view.questiontemplateentity;

import com.vaadin.flow.router.Route;

import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import ru.javaboys.huntyhr.entity.QuestionTemplateEntity;
import ru.javaboys.huntyhr.view.main.MainView;

@Route(value = "question-template-entities/:id", layout = MainView.class)
@ViewController(id = "QuestionTemplateEntity.detail")
@ViewDescriptor(path = "question-template-entity-detail-view.xml")
@EditedEntityContainer("questionTemplateEntityDc")
public class QuestionTemplateEntityDetailView extends StandardDetailView<QuestionTemplateEntity> {
}