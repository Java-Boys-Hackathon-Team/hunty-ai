package ru.javaboys.huntyhr.view.applicationentity;

import com.vaadin.flow.router.Route;

import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import ru.javaboys.huntyhr.entity.ApplicationEntity;
import ru.javaboys.huntyhr.view.main.MainView;

@Route(value = "application-entities", layout = MainView.class)
@ViewController(id = "ApplicationEntity.list")
@ViewDescriptor(path = "application-entity-list-view.xml")
@LookupComponent("applicationEntitiesDataGrid")
@DialogMode(width = "64em")
public class ApplicationEntityListView extends StandardListView<ApplicationEntity> {
}