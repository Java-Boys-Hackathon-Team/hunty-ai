package ru.javaboys.huntyhr.view.interviewsessionentity;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import ru.javaboys.huntyhr.entity.InterviewSessionEntity;
import ru.javaboys.huntyhr.entity.StorageObjectEntity;
import ru.javaboys.huntyhr.view.main.MainView;


@Route(value = "interview-session-entities", layout = MainView.class)
@ViewController(id = "InterviewSessionEntity.list")
@ViewDescriptor(path = "interview-session-entity-list-view.xml")
@LookupComponent("interviewSessionEntitiesDataGrid")
@DialogMode(width = "64em")
public class InterviewSessionEntityListView extends StandardListView<InterviewSessionEntity> {

    @ViewComponent
    private DataGrid<InterviewSessionEntity> interviewSessionEntitiesDataGrid;

    @Autowired
    private UiComponents uiComponents;

    @Autowired
    private Downloader downloader;

    @Subscribe
    public void onInit(final InitEvent event) {
        // ССЫЛКА
        var linkCol = interviewSessionEntitiesDataGrid.getColumnByKey("linkCol");
        if (linkCol != null) {
            linkCol.setRenderer(new ComponentRenderer<>(session -> {
                String url = session.getInterviewLink();
                Button btn = uiComponents.create(Button.class);
                btn.setText("Открыть");
                btn.addThemeName("tertiary");
                btn.setEnabled(url != null && !url.isBlank());
                btn.addClickListener(e -> {
                    if (url != null && !url.isBlank()) {
                        UI.getCurrent().getPage().open(url);
                    }
                });
                return btn;
            }));
        }

        // ВИДЕО
        var videoCol = interviewSessionEntitiesDataGrid.getColumnByKey("videoCol");
        if (videoCol != null) {
            videoCol.setRenderer(new ComponentRenderer<>(session ->
                    buildDownloadButton("Скачать", session.getVideo_source())));
        }

        // ТРАНСКРИПТ
        var transCol = interviewSessionEntitiesDataGrid.getColumnByKey("transCol");
        if (transCol != null) {
            transCol.setRenderer(new ComponentRenderer<>(session ->
                    buildDownloadButton("Скачать", session.getTranscription())));
        }

        // АНАЛИТИКА
        var analCol = interviewSessionEntitiesDataGrid.getColumnByKey("analCol");
        if (analCol != null) {
            analCol.setRenderer(new ComponentRenderer<>(session ->
                    buildDownloadButton("Скачать", session.getAnalytics())));
        }
    }

    private Component buildDownloadButton(String caption, StorageObjectEntity so) {
        Button btn = uiComponents.create(Button.class);
        btn.setText(caption);
        btn.addThemeName("tertiary");
        boolean enabled = so != null && so.getRef() != null;
        btn.setEnabled(enabled);
        if (enabled) {
            btn.addClickListener(e -> downloader.download(so.getRef()));
        }
        return btn;
    }

}