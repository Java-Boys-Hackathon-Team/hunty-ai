package ru.javaboys.huntyhr.view.vacancyentity;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.FileRef;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.upload.FileStorageUploadField;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import ru.javaboys.huntyhr.entity.ApplicationEntity;
import ru.javaboys.huntyhr.entity.VacancyEntity;
import ru.javaboys.huntyhr.service.impl.ResumeImportService;
import ru.javaboys.huntyhr.view.main.MainView;

@Route(value = "vacancy-entities/:id", layout = MainView.class)
@ViewController(id = "VacancyEntity.detail")
@ViewDescriptor(path = "vacancy-entity-detail-view.xml")
@EditedEntityContainer("vacancyEntityDc")
public class VacancyEntityDetailView extends StandardDetailView<VacancyEntity> {

    @ViewComponent
    private CollectionLoader<ApplicationEntity> applicationsDl;

    @ViewComponent("resumeUpload")
    private FileStorageUploadField resumeUpload;

    @Autowired
    private ResumeImportService resumeImportService;

    @ViewComponent
    private DataGrid<ApplicationEntity> applicationsGrid;

    @Subscribe
    public void onInit(InitEvent event) {
        // Имя
        DataGrid.Column<ApplicationEntity> nameColumn = applicationsGrid.getColumnByKey("candidate.name");
        if (nameColumn != null) {
            nameColumn.setRenderer(new TextRenderer<>(app ->
                    app.getCandidate() != null && notBlank(app.getCandidate().getName())
                            ? app.getCandidate().getName()
                            : "(заполнить вручную)"
            ));
        }

        // Фамилия
        DataGrid.Column<ApplicationEntity> surnameColumn = applicationsGrid.getColumnByKey("candidate.surname");
        if (surnameColumn != null) {
            surnameColumn.setRenderer(new TextRenderer<>(app ->
                    app.getCandidate() != null && notBlank(app.getCandidate().getSurname())
                            ? app.getCandidate().getSurname()
                            : "(заполнить вручную)"
            ));
        }

        // Email
        DataGrid.Column<ApplicationEntity> emailColumn = applicationsGrid.getColumnByKey("candidate.email");
        if (emailColumn != null) {
            emailColumn.setRenderer(new TextRenderer<>(app ->
                    app.getCandidate() != null && notBlank(app.getCandidate().getEmail())
                            ? app.getCandidate().getEmail()
                            : "(заполнить вручную)"
            ));
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    @Subscribe("resumeUpload")
    public void onResumeUpload(AbstractField.ComponentValueChangeEvent<FileStorageUploadField, FileRef> event) {
        if (!event.isFromClient()) return;

        FileRef ref = event.getValue();
        if (ref == null) return;

        VacancyEntity vac = getEditedEntity();
        resumeImportService.importFromFile(vac.getId(), ref);

        resumeUpload.setValue(null);
        applicationsDl.load();
    }
}