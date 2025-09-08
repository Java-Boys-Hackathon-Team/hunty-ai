package ru.javaboys.huntyhr.view.vacancyentity;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.action.inputdialog.InputDialogAction;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.upload.FileStorageUploadField;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.OpenMode;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import ru.javaboys.huntyhr.entity.ApplicationEntity;
import ru.javaboys.huntyhr.entity.CandidateEntity;
import ru.javaboys.huntyhr.entity.ResumeVersionEntity;
import ru.javaboys.huntyhr.entity.VacancyEntity;
import ru.javaboys.huntyhr.service.impl.InterviewSessionService;
import ru.javaboys.huntyhr.service.impl.ResumeImportService;
import ru.javaboys.huntyhr.service.impl.ScoringService;
import ru.javaboys.huntyhr.view.candidateentity.CandidateEntityDetailView;
import ru.javaboys.huntyhr.view.main.MainView;

import java.time.LocalDateTime;

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

    @Autowired
    private Notifications notifications;

    @Autowired
    private ScoringService scoringService;

    @Autowired
    private Dialogs dialogs;

    @Autowired
    private DataManager dataManager;

    @Autowired
    private Downloader downloader;

    @Autowired
    private InterviewSessionService interviewSessionService;


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

        applicationsGrid.addComponentColumn(application -> {
            Button button = uiComponents.create(Button.class);
            button.setText("Открыть");
            button.addClickListener(click -> {
                CandidateEntity candidate = application.getCandidate();
                if (candidate != null && candidate.getId() != null) {
                    viewNavigators.detailView(this, CandidateEntity.class)
                            .withViewClass(CandidateEntityDetailView.class)
                            .editEntity(candidate)
                            .withViewClass(CandidateEntityDetailView.class)
                            .navigate();
                }
            });
            return button;
        }).setHeader("Кандидат");

        applicationsGrid.addComponentColumn(app -> {
            Button b = uiComponents.create(Button.class);
            b.setText("Анализ");
            b.addClickListener(e -> {
                String summary = app.getScreeningSummaryHtml();

                String html = (summary == null || summary.isBlank())
                        ? "<div style='color:gray'>Анализ отсутствует</div>"
                        : summary;

                dialogs.createMessageDialog()
                        .withHeader("Анализ резюме")
                        .withContent(new com.vaadin.flow.component.Html(html))
                        .withWidth("60em")
                        .withHeight("30em")
                        .withResizable(true)
                        .open();
            });
            return b;
        }).setHeader("Анализ резюме");

        applicationsGrid.addComponentColumn(app -> {
            Button btn = uiComponents.create(Button.class);
            btn.setText("Скачать");

            btn.addClickListener(click -> {
                CandidateEntity candidate = app.getCandidate();
                if (candidate == null || candidate.getId() == null) {
                    notifications.create("У кандидата нет резюме")
                            .withType(Notifications.Type.WARNING)
                            .show();
                    return;
                }

                // достаём последнюю версию резюме
                ResumeVersionEntity version = dataManager.load(ResumeVersionEntity.class)
                        .query("select r from ResumeVersionEntity r where r.candidate = :c order by r.createdAt desc")
                        .parameter("c", candidate)
                        .maxResults(1)
                        .optional()
                        .orElse(null);

                if (version == null || version.getFile() == null || version.getFile().getRef() == null) {
                    notifications.create("Резюме не найдено").withType(Notifications.Type.WARNING).show();
                    return;
                }

                FileRef fileRef = version.getFile().getRef();
                // отправляем файл пользователю
                downloader.download(fileRef);
            });

            return btn;
        }).setHeader("Резюме");

        /*applicationsGrid.addComponentColumn(app -> {
            Button btn = uiComponents.create(Button.class);
            btn.setText("Интервью");
            btn.addClickListener(e -> openInterviewDialog(app));
            return btn;
        }).setHeader("Интервью");*/
    }

    @Autowired
    private UiComponents uiComponents;

    @Autowired
    private ViewNavigators viewNavigators;


    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    @Subscribe("resumeUpload")
    public void onResumeUpload(AbstractField.ComponentValueChangeEvent<FileStorageUploadField, FileRef> event) {
        if (!event.isFromClient()) return;

        FileRef ref = event.getValue();
        if (ref == null) return;

        VacancyEntity vac = getEditedEntity();
        if (vac.getId() == null) {
            notifications.create("Сначала сохраните вакансию, прежде чем загружать резюме")
                    .withType(Notifications.Type.WARNING)
                    .show();
            return;
        }

        ResumeImportService.Result result = resumeImportService.importFromFile(vac.getId(), ref);
        scoringService.scoreWithLlm(result.getApplicationId());

        resumeUpload.setValue(null);
        applicationsDl.load();
    }

    /*private void openInterviewDialog(ApplicationEntity app) {
        // мини-диалог с датой/временем и языком
        var dateTime = uiComponents.create(com.vaadin.flow.component.datetimepicker.DateTimePicker.class);
        dateTime.setLabel("Начало");
        dateTime.setStep(java.time.Duration.ofMinutes(15));
        dateTime.setValue(LocalDateTime.now().plusDays(1).withHour(11).withMinute(0));

        var lang = uiComponents.create(com.vaadin.flow.component.combobox.ComboBox.class);

        dialogs.createInputDialog(this)
                .withHeader("Назначить интервью")
                .withActions(
                        InputDialogAction.action("create")
                                .withText("Создать")
                                .withHandler(actionEvent -> {
                                    LocalDateTime when = dateTime.getValue();
                                    String language = (String) lang.getValue();
                                    if (when == null || language == null) {
                                        notifications.create("Укажи дату/время и язык")
                                                .withType(Notifications.Type.WARNING)
                                                .show();
                                        return;
                                    }
                                    var session = interviewSessionService.createOrUpdateSession(
                                            app.getId(), when
                                    );

                                    String joinUrl = session.getInterviewLink();
                                    dialogs.createMessageDialog()
                                            .withHeader("Ссылка для подключения")
                                            .withContent(new com.vaadin.flow.component.Html(
                                                    "<div>Скопируй ссылку и отправь кандидату:</div>" +
                                                            "<div style='margin-top:8px'><a href='" + joinUrl +
                                                            "' target='_blank'>" + joinUrl + "</a></div>"
                                            ))
                                            .withWidth("42em")
                                            .open();

                                    actionEvent.();
                                }),
                        InputDialogAction.action("cancel")
                                .withCaption("Отмена")
                                .withHandler(InputDialogCloseAction::closeDialog)
                )
                .withContent(new com.vaadin.flow.component.orderedlayout.VerticalLayout(dateTime, lang))
                .open();
    }*/
}