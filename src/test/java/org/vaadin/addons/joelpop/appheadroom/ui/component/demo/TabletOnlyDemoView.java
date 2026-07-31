package org.vaadin.addons.joelpop.appheadroom.ui.component.demo;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.Route;
import org.vaadin.addons.joelpop.appheadroom.ui.component.AppHeadroom;

/**
 * Demo view restricting headroom to tablets only — phones (any orientation)
 * are always inactive. Used to verify that an inactive {@code AppHeadroom}
 * never forces layout changes (e.g. pinning the bottom bar to the viewport)
 * that only make sense while scroll-tracking is actually running.
 *
 * <p>Deliberately <em>not</em> touch-optimized: {@code --vaadin-app-layout-touch-optimized}
 * makes Vaadin's own styling pin the bottom bar to the viewport regardless of
 * {@code AppHeadroom}, which would mask whether {@code AppHeadroom}'s own
 * CSS is correctly gated on activation.
 */
@Route("headroom-demo-tablet-only")
public class TabletOnlyDemoView extends AppLayout {

    public TabletOnlyDemoView() {
        addToNavbar(new H3("Tablet-only demo"));

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
                .setActivationPredicate((deviceType, orientation) -> deviceType == AppHeadroom.DeviceType.TABLET);
    }
}
