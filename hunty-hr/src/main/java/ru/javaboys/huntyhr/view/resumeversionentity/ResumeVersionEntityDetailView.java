package ru.javaboys.huntyhr.view.resumeversionentity;

import com.vaadin.flow.router.Route;

import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import ru.javaboys.huntyhr.entity.ResumeVersionEntity;
import ru.javaboys.huntyhr.view.main.MainView;

@Route(value = "resume-version-entities/:id", layout = MainView.class)
@ViewController(id = "ResumeVersionEntity.detail")
@ViewDescriptor(path = "resume-version-entity-detail-view.xml")
@EditedEntityContainer("resumeVersionEntityDc")
public class ResumeVersionEntityDetailView extends StandardDetailView<ResumeVersionEntity> {
}