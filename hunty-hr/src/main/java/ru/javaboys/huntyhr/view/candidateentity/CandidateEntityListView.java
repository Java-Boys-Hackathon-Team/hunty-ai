package ru.javaboys.huntyhr.view.candidateentity;

import com.vaadin.flow.router.Route;

import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import ru.javaboys.huntyhr.entity.CandidateEntity;
import ru.javaboys.huntyhr.view.main.MainView;


@Route(value = "candidate-entities", layout = MainView.class)
@ViewController(id = "CandidateEntity.list")
@ViewDescriptor(path = "candidate-entity-list-view.xml")
@LookupComponent("candidateEntitiesDataGrid")
@DialogMode(width = "64em")
public class CandidateEntityListView extends StandardListView<CandidateEntity> {
}