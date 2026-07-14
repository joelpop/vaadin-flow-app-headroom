package org.vaadin.addons.joelpop.appheadroom.ui.component.demo;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.Route;
import org.vaadin.addons.joelpop.appheadroom.ui.component.AppHeadroom;

@Route("headroom-demo-no-ancestor")
public class NoAncestorDemoView extends Div {
    public NoAncestorDemoView() {
        getElement().appendChild(AppHeadroom.create().getElement());
    }
}
