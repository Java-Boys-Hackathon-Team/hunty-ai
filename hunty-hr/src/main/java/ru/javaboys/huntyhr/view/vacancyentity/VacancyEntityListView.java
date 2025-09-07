package ru.javaboys.huntyhr.view.vacancyentity;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.upload.FileStorageUploadField;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import ru.javaboys.huntyhr.ai.OpenAiService;
import ru.javaboys.huntyhr.entity.VacancyEntity;
import ru.javaboys.huntyhr.service.impl.VacancyImportService;
import ru.javaboys.huntyhr.view.main.MainView;

import java.time.OffsetDateTime;


@Route(value = "vacancy-entities", layout = MainView.class)
@ViewController(id = "VacancyEntity.list")
@ViewDescriptor(path = "vacancy-entity-list-view.xml")
@LookupComponent("vacancyEntitiesDataGrid")
@DialogMode(width = "64em")
public class VacancyEntityListView extends StandardListView<VacancyEntity> {

    @ViewComponent
    private DataGrid<VacancyEntity> vacancyEntitiesDataGrid;

    @Autowired
    private UiComponents uiComponents;

    @Autowired
    private Downloader downloader;

    @Autowired
    private DataManager dataManager;

    @ViewComponent
    private CollectionLoader<VacancyEntity> vacancyEntitiesDl;

    @Autowired
    private VacancyImportService vacancyImportService;

    @ViewComponent("cvUpload")
    FileStorageUploadField cvUpload;

    @Subscribe
    public void onInit(final InitEvent event) {
        vacancyEntitiesDataGrid.addComponentColumn(attachment -> {
            Button button = uiComponents.create(Button.class);
            button.setText("Скачать");
            button.addThemeName("tertiary-inline");
            button.addClickListener(clickEvent -> {
                downloader.download(attachment.getFileRef());
            });
            return button;
        });
    }

    @Subscribe("cvUpload")
    public void onCvUploadValueChange(
            AbstractField.ComponentValueChangeEvent<FileStorageUploadField, FileRef> event) {
        if (!event.isFromClient()) {
            return;
        }

        FileRef ref = event.getValue();
        if (ref == null) return;

        vacancyImportService.importFromFile(ref);

        cvUpload.setValue(null);

        if (vacancyEntitiesDl != null) {
            vacancyEntitiesDl.load();
        }
    }
}