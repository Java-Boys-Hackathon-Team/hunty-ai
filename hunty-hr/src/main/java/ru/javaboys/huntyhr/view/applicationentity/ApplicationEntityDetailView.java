package ru.javaboys.huntyhr.view.applicationentity;

import com.vaadin.flow.router.Route;

import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import ru.javaboys.huntyhr.entity.ApplicationEntity;
import ru.javaboys.huntyhr.view.main.MainView;

@Route(value = "application-entities/:id", layout = MainView.class)
@ViewController(id = "ApplicationEntity.detail")
@ViewDescriptor(path = "application-entity-detail-view.xml")
@EditedEntityContainer("applicationEntityDc")
public class ApplicationEntityDetailView extends StandardDetailView<ApplicationEntity> {
}