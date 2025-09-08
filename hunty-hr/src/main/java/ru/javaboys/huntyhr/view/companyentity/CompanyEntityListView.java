package ru.javaboys.huntyhr.view.companyentity;

import com.vaadin.flow.router.Route;

import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import ru.javaboys.huntyhr.entity.CompanyEntity;
import ru.javaboys.huntyhr.view.main.MainView;


@Route(value = "company-entities", layout = MainView.class)
@ViewController(id = "CompanyEntity.list")
@ViewDescriptor(path = "company-entity-list-view.xml")
@LookupComponent("companyEntitiesDataGrid")
@DialogMode(width = "64em")
public class CompanyEntityListView extends StandardListView<CompanyEntity> {
}