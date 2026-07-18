package org.vaadin.addons.joelpop.appheadroom.ui.component.demo;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.Route;
import org.vaadin.addons.joelpop.appheadroom.ui.component.AppHeadroom;

/**
 * Demo view exercising the two configurable Java-side thresholds:
 * {@link AppHeadroom#setTabletMinShortSidePx} (raised well above a touch
 * device that would otherwise classify as TABLET, forcing it to PHONE
 * instead) and {@link AppHeadroom#setTransitionDuration} (shortened so its
 * effect on computed CSS is easy to assert against directly).
 */
@Route("headroom-demo-custom-thresholds")
public class CustomThresholdsDemoView extends AppLayout {

    public CustomThresholdsDemoView() {
        addToNavbar(new H3("Custom thresholds demo"));

        var content = new Div();
        content.getStyle().set("min-height", "6000px");
        for (int i = 0; i < 80; i++) {
            content.add(new Paragraph("Filler content line " + i));
        }
        setContent(content);

        AppHeadroom.applyTo(this)
                .setTopOffset(100)
                .setHideTolerance(40)
                .setShowTolerance(40)
                .setTabletMinShortSidePx(1000)
                .setTransitionDuration(150)
                .setActivationPredicate((deviceType, orientation) -> deviceType == AppHeadroom.DeviceType.PHONE);
    }
}
