package ru.javaboys.huntyhr.view.companyentity;

import com.vaadin.flow.router.Route;

import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import ru.javaboys.huntyhr.entity.CompanyEntity;
import ru.javaboys.huntyhr.view.main.MainView;

@Route(value = "company-entities/:id", layout = MainView.class)
@ViewController(id = "CompanyEntity.detail")
@ViewDescriptor(path = "company-entity-detail-view.xml")
@EditedEntityContainer("companyEntityDc")
public class CompanyEntityDetailView extends StandardDetailView<CompanyEntity> {
}