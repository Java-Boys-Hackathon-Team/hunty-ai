package ru.javaboys.huntyhr.view.main;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.app.main.StandardMainView;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.theme.ThemeUtils;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route("")
@ViewController(id = "MainView")
@ViewDescriptor(path = "main-view.xml")
public class MainView extends StandardMainView {

    @Subscribe("themeSwitcher.systemThemeItem.systemThemeAction")
    public void onSystemTheme( ActionPerformedEvent e ) {
        ThemeUtils.applySystemTheme();
    }

    @Subscribe("themeSwitcher.lightThemeItem.lightThemeAction")
    public void onLightTheme( ActionPerformedEvent e ) {
        ThemeUtils.applyLightTheme();
    }

    @Subscribe("themeSwitcher.darkThemeItem.darkThemeAction")
    public void onDarkTheme( ActionPerformedEvent e ) {
        ThemeUtils.applyDarkTheme();
    }
}
