package ru.javaboys.huntyhr.view.candidateentity;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import ru.javaboys.huntyhr.entity.CandidateEntity;
import ru.javaboys.huntyhr.view.main.MainView;

@Route(value = "candidate-entities/:id", layout = MainView.class)
@ViewController(id = "CandidateEntity.detail")
@ViewDescriptor(path = "candidate-entity-detail-view.xml")
@EditedEntityContainer("candidateEntityDc")
public class CandidateEntityDetailView extends StandardDetailView<CandidateEntity> {
}