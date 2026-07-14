package org.vaadin.addons.joelpop.appheadroom.ui.component.demo;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.router.Route;
import org.vaadin.addons.joelpop.appheadroom.ui.component.AppHeadroom;

@Route("headroom-demo-duplicate")
public class DuplicateAttachDemoView extends AppLayout {
    public DuplicateAttachDemoView() {
        AppHeadroom.applyTo(this);
        AppHeadroom.applyTo(this);
    }
}
