package ru.javaboys.huntyhr.view.vacancyentity;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.action.inputdialog.InputDialogAction;
import io.jmix.flowui.app.inputdialog.InputDialog;
import io.jmix.flowui.app.inputdialog.InputParameter;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import ru.javaboys.huntyhr.entity.ApplicationEntity;
import ru.javaboys.huntyhr.entity.CandidateEntity;
import ru.javaboys.huntyhr.entity.InterviewSessionEntity;
import ru.javaboys.huntyhr.entity.InterviewStateEnum;
import ru.javaboys.huntyhr.entity.ResumeVersionEntity;
import ru.javaboys.huntyhr.entity.VacancyEntity;
import ru.javaboys.huntyhr.service.impl.InterviewSessionService;
import ru.javaboys.huntyhr.service.impl.MailService;
import ru.javaboys.huntyhr.service.impl.ResumeImportService;
import ru.javaboys.huntyhr.service.impl.ScoringService;
import ru.javaboys.huntyhr.service.impl.TelegramBotService;
import ru.javaboys.huntyhr.view.candidateentity.CandidateEntityDetailView;
import ru.javaboys.huntyhr.view.main.MainView;

import java.time.LocalDateTime;

@Route(value = "vacancy-entities/:id", layout = MainView.class)
@ViewController(id = "VacancyEntity.detail")
@ViewDescriptor(path = "vacancy-entity-detail-view.xml")
@EditedEntityContainer("vacancyEntityDc")
@Slf4j
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

    @Autowired
    private MailService mailService;

    @Autowired
    private TelegramBotService telegramBotService;

    @Autowired
    private UiComponents uiComponents;

    @Autowired
    private ViewNavigators viewNavigators;


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

        applicationsGrid.addComponentColumn(app -> {
            Button btn = uiComponents.create(Button.class);
            btn.setText("Интервью");
            btn.addClickListener(e -> openInterviewDialog(app));
            return btn;
        }).setHeader("Интервью");
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

    private void openInterviewDialog(ApplicationEntity app) {
        // мини-диалог с датой/временем и языком
        var dateTime = uiComponents.create(com.vaadin.flow.component.datetimepicker.DateTimePicker.class);
        dateTime.setLabel("Дата и время интервью");
        dateTime.setStep(java.time.Duration.ofMinutes(15));
        dateTime.setValue(LocalDateTime.now().plusDays(1).withHour(11).withMinute(0));

        var lang = uiComponents.create(com.vaadin.flow.component.combobox.ComboBox.class);

        var startAtParam = InputParameter
                .parameter("startAt")
                .withRequired(true)
                .withField(() -> dateTime);

        dialogs.createInputDialog(this)
                .withHeader("Создать встречу")
                .withParameter(startAtParam)
                .withActions(
                        InputDialogAction.action("ok")
                                .withText("Создать")
                                .withHandler(e -> {
                                    // 1) создаём сессию
                                    InterviewSessionEntity session = dataManager.create(InterviewSessionEntity.class);
                                    session.setApplication(app);
                                    session.setScenario(app.getVacancy().getScenario());
                                    session.setState(InterviewStateEnum.PENDING);
                                    dataManager.save(session);

                                    String joinUrl = "https://hunty-ai.javaboys.ru/meetings/" + session.getId();

                                    Span title = new Span("Скопируй ссылку и отправь кандидату:");
                                    Anchor link = new Anchor(joinUrl, joinUrl);
                                    link.setTarget("_blank");

                                    Button copyBtn = uiComponents.create(Button.class);
                                    copyBtn.setText("Копировать");
                                    copyBtn.addClickListener(ev -> copyToClipboard(joinUrl, copyBtn));

                                    Button notifyBtn = uiComponents.create(Button.class);
                                    notifyBtn.setText("Отправить приглашение");
                                    notifyBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                                    notifyBtn.addClickListener(ev -> {
                                        // дата/время из поля (если нужно передавать во внешнее уведомление)
                                        LocalDateTime startAt = dateTime.getValue();
                                        sendInterviewInvite(app, joinUrl, startAt);
                                        notifications.create("Приглашение отправлено").show();
                                    });

                                    VerticalLayout content = new VerticalLayout(title, link, copyBtn, notifyBtn);
                                    content.setPadding(false);
                                    content.setSpacing(true);

                                    dialogs.createMessageDialog()
                                            .withHeader("Ссылка для подключения")
                                            .withContent(content)
                                            .withWidth("42em")
                                            .open();


                                }),
                        InputDialogAction.action("cancel")
                                .withText("Отмена")
                                .withHandler(event -> {
                                    // Явно закрыть диалог если нужно, или оставить пусто:
                                    event.getComponent().getUI().ifPresent(UI::close);
                                })
                ).open();
    }

    private void copyToClipboard(String text, Component anyComponent) {
        // скопировать в буфер обмена через браузерный API
        anyComponent.getElement()
                .executeJs("navigator.clipboard.writeText($0)", text);
        // показать уведомление
        notifications.create("Ссылка скопирована в буфер обмена")
                .withType(Notifications.Type.SYSTEM)
                .show();
    }

    private void sendInterviewInvite(ApplicationEntity app, String joinUrl, LocalDateTime startAt) {
        if (app == null || app.getId() == null) {
            log.warn("sendInterviewInvite: application is null");
            safeUiWarn("Не удалось отправить приглашение: заявка не найдена");
            return;
        }

        try {
            // найти/создать связанную сессию
            InterviewSessionEntity session = dataManager.load(InterviewSessionEntity.class)
                    .query("select s from InterviewSessionEntity s where s.application = :app")
                    .parameter("app", app)
                    .optional()
                    .orElseGet(() -> {
                        InterviewSessionEntity s = dataManager.create(InterviewSessionEntity.class);
                        s.setApplication(app);
                        if (app.getVacancy() != null) {
                            s.setScenario(app.getVacancy().getScenario());
                        }
                        s.setState(InterviewStateEnum.PENDING);
                        return s;
                    });

            // обновить ключевые поля
            session.setScheduledStartAt(startAt);
            session.setInterviewLink(joinUrl);
            session = dataManager.save(session);

            // лог
            String email = app.getCandidate() != null ? app.getCandidate().getEmail() : null;
            log.info("Interview invite prepared for application={}, email={}, startAt={}, link={}",
                    app.getId(), email, startAt, joinUrl);

            // 1) e-mail
            try {
                mailService.sendInterviewScheduled(session);
                log.info("Interview email notification sent: application={}", app.getId());
            } catch (Exception mailEx) {
                log.error("Failed to send email invite for application {}: {}", app.getId(), mailEx.getMessage(), mailEx);
                safeUiWarn("Не удалось отправить приглашение по email. Проверьте журнал.");
            }

            // 2) мессенджер/телеграм/смс — второй канал
            try {
                telegramBotService.sendInterviewScheduled(session);
                log.info("Interview messenger notification sent: application={}", app.getId());
            } catch (Exception msgEx) {
                log.error("Failed to send messenger invite for application {}: {}", app.getId(), msgEx.getMessage(), msgEx);
                safeUiWarn("Не удалось отправить приглашение через мессенджер. Проверьте журнал.");
            }

            safeUiInfo("Приглашение сформировано. Ссылка скопирована/доступна в карточке встречи.");

        } catch (Exception ex) {
            log.error("sendInterviewInvite fatal for application {}: {}", app.getId(), ex.getMessage(), ex);
            safeUiWarn("Ошибка при подготовке приглашения. Попробуйте ещё раз или обратитесь к администратору.");
        }
    }

    /* вспомогательные уведомления в UI (без NPE, если вызвано вне UI) */
    private void safeUiWarn(String msg) {
        try {
            if (notifications != null) notifications.create(msg).withType(Notifications.Type.WARNING).show();
        } catch (Exception ignore) { /* no-op */ }
    }
    private void safeUiInfo(String msg) {
        try {
            if (notifications != null) notifications.create(msg).withType(Notifications.Type.SUCCESS).show();
        } catch (Exception ignore) { /* no-op */ }
    }
}