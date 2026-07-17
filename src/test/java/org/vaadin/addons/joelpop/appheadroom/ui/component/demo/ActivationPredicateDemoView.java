package org.vaadin.addons.joelpop.appheadroom.ui.component.demo;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.Route;
import org.vaadin.addons.joelpop.appheadroom.ui.component.AppHeadroom;

/**
 * Demo view restricting headroom to phones and landscape-oriented tablets via
 * {@link AppHeadroom#setActivationPredicate} — the default behavior this had
 * before being extracted into this library.
 */
@Route("headroom-demo-activation-predicate")
public class ActivationPredicateDemoView extends AppLayout {

    public ActivationPredicateDemoView() {
        addToNavbar(new H3("Activation predicate demo"));

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
                .setActivationPredicate((deviceType, orientation) ->
                        deviceType == AppHeadroom.DeviceType.PHONE
                                || (deviceType == AppHeadroom.DeviceType.TABLET
                                        && orientation == AppHeadroom.Orientation.LANDSCAPE));
    }
}
