package ru.javaboys.huntyhr.view.questiontemplateentity;

import com.vaadin.flow.router.Route;

import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import ru.javaboys.huntyhr.entity.QuestionTemplateEntity;
import ru.javaboys.huntyhr.view.main.MainView;


@Route(value = "question-template-entities", layout = MainView.class)
@ViewController(id = "QuestionTemplateEntity.list")
@ViewDescriptor(path = "question-template-entity-list-view.xml")
@LookupComponent("questionTemplateEntitiesDataGrid")
@DialogMode(width = "64em")
public class QuestionTemplateEntityListView extends StandardListView<QuestionTemplateEntity> {
}