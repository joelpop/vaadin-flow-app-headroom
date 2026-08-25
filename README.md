# AppHeadroom (vaadin-flow-app-headroom)

`AppHeadroom` is a mobile-friendly Vaadin component that hides your `AppLayout` header and footer when you scroll down and restores them when you scroll up — the "headroom" pattern. An optional floating or ribbon component can be rendered as a condensed context cue when the header and footer are hidden.

## Table of Contents

- [Usage](#usage)
  - [Preparing your main layout](#preparing-your-main-layout)
  - [Attaching to your main layout](#attaching-to-your-main-layout)
  - [Detaching](#detaching)
  - [Restricting activation by device type/orientation](#restricting-activation-by-device-typeorientation)
  - [Providing condensed context cues](#providing-condensed-context-cues)
    - [Clicking to expand](#clicking-to-expand)
  - [Listening to collapsed state changes](#listening-to-collapsed-state-changes)
  - [Overriding bar collapsibility](#overriding-bar-collapsibility)
  - [Overriding configuration defaults](#overriding-configuration-defaults)
- [How it works](#how-it-works)
- [Feature list](#feature-list)
  - [Automatic](#automatic)
  - [Customizable](#customizable)
- [Requirements](#requirements)
- [Development](#development)
  - [Running the demo](#running-the-demo)
  - [Integration tests](#integration-tests)
- [Credits](#credits)
- [Publishing to Vaadin Directory](#publishing-to-vaadin-directory)

## Usage

### Preparing your main layout

Have your main layout extend `AppLayout` or a subclass of it.

```java
@Layout
public class MainLayout extends AppLayout {
    public MainLayout() {
        // ...
    }
}
```

`AppLayout` has a top navbar, a bottom navbar, a side drawer, and a content area.
The top and bottom navbar are the header and footer "bars" `AppHeadroom` will hide and show on scroll; the drawer and content area are untouched.

On all devices, components added to `AppLayout` via `addToNavbar(Component...)` (or
`addToNavbar(false, Component...)`) get added to its top navbar. Components added to `AppLayout` via `addToNavbar(true, Component...)` get added to its bottom navbar on touch devices, but on non-touch devices, these components get added inline with the existing top navbar components.

```java
@Layout
public class MainLayout extends AppLayout {
    public MainLayout() {
        addToNavbar(new Span("Always on top"));
        addToNavbar(true, new Span("Second top item on desktop, bottom item on touch devices"));
        addToNavbar(false, new Span("Also always on top"));
    }
}
```

### Attaching to your main layout

The simplest usage of `AppHeadroom` is to just integrate it with your main layout instance — which must be an `AppLayout`.

```java
@Layout
public class MainLayout extends AppLayout {
    public MainLayout() {
        AppHeadroom.applyTo(this);
    }
}
```

This will turn on the headroom effect in your application for all devices in all orientations.

`applyTo` is the only entry point: it validates that the provided component is backed by
the standard `vaadin-app-layout` web component, creates an `AppHeadroom` instance, and
returns the instance for chaining (as do all the setters). This works the same regardless of whether
your main layout is a plain `AppLayout` or a subclass. `AppHeadroom` attaches itself as a peer element alongside the layout, so it never
shows up in `myAppLayout.getChildren()` and needs no cooperation from
whatever the layout subclass does with its own children.

### Detaching

If for some reason you need to detach your `AppHeadroom` instance from your main layout, call `headroom.remove()` to remove the headroom integration without affecting the layout itself.

### Restricting activation by device type/orientation

By default the effect is active on every device. To restrict it, supply a
predicate over the session's detected device type and orientation. It is
re-evaluated automatically whenever either becomes known or changes (e.g. on
rotation), with nothing further to wire up:

```java
@Layout
public class MainLayout extends AppLayout {
    public MainLayout() {
        AppHeadroom.applyTo(this)
                .setActivationPredicate((deviceType, orientation) ->
                        deviceType == AppHeadroom.DeviceType.PHONE
                                || (deviceType == AppHeadroom.DeviceType.TABLET && orientation == AppHeadroom.Orientation.LANDSCAPE));
    }
}
```

The device type (`PHONE` / `TABLET` / `DESKTOP`) is classified from touch
capability and physical screen size (not viewport width, which fluctuates as
a desktop user resizes the browser window). `isActive()` reflects the
predicate's last evaluation.

### Providing condensed context cues

Sometimes you may want to show something like a small context cue in place of a header or footer when it is hidden. `AppHeadroom` allows you to supply a Component to show as a condensed alternate rendering that cross-fades into place exactly as the original bar slides away (and back out as it returns). `getCondensedTop()`/`getCondensedBottom()`
return an accessor for picking that bar's shape — `asFloating()` or
`asRibbon()` — each of which returns a shape-specific accessor exposing
only the methods that make sense for it, including `setComponent(...)` to
supply the actual content.

Both shapes are positioned and sized entirely independently of `AppLayout`
— the rendering becomes a peer element positioned relative to the viewport, not
a descendant of the layout — and both handle safe-area clearance (a
notch/Dynamic Island or home indicator) automatically, so you're never
asked to get that right yourself:

- **`asFloating()`** — a small, compact rendering that centers itself
  automatically. `AppHeadroom` owns the whole gap around it, reconciled
  (not simply added) against the safe area, so there's nothing to configure
  beyond content.
- **`asRibbon()`** — a full-width, opaque bar whose background extends all
  the way into the safe area while only its content is inset from the
  unsafe zone, the same treatment the real bar's own landscape rule already
  uses. Defaults to `var(--vaadin-background-container)` — a base-theme
  property present under any theme, light/dark-aware automatically — so it
  fits your app out of the box; override it via the returned accessor's
  `getStyle()` if you want something else, as shown in the example below.

```java
@Layout
public class MainLayout extends AppLayout {
    public MainLayout() {
        var headroom = AppHeadroom.applyTo(this);

        headroom.getCondensedTop().asFloating()
                .setComponent(new CondensedHeader());

        headroom.getCondensedBottom().asRibbon()
                .setComponent(new CondensedFooter())
                .getStyle().setBackgroundColor("var(--my-brand-color)");
    }
}
```

For content that needs to change over the layout's lifetime, such as a
title that follows the current route, instead of replacing the component each time navigation changes, build the Component once and have
it react to a `Signal` that your own navigation-aware code updates —
`AppHeadroom` itself never calls `setComponent(...)` again on your
behalf:

```java
public class CondensedHeader extends Span {
    public CondensedHeader(Signal<String> pageTitle) {
        Signal.effect(this, () -> setText(pageTitle.get()));
    }
}
```

```java
@Layout
public class MainLayout extends AppLayout implements AfterNavigationObserver {
    private final ValueSignal<String> pageTitle = new ValueSignal<>("");

    public MainLayout() {
        var headroom = AppHeadroom.applyTo(this);
        headroom.getCondensedTop().asFloating()
                .setComponent(new CondensedHeader(pageTitle));
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        pageTitle.set(currentViewTitle(event));
    }
}
```

Unlike the real bar, the condensed rendering never slides — it only fades
in and out at a fixed position, timed with `setTransitionDuration`.

It fades purely on scroll position, the same as the real bar — including for a
bar the automatic pinned-rail geometry check keeps from ever hiding (see
[Overriding bar collapsibility](#overriding-bar-collapsibility)), since a condensed
Component has no structural relationship to whatever made the real bar
rail-shaped. The one thing that does suppress it: an explicit
`setTopBarCollapsible(false)`/`setBottomBarCollapsible(false)` — a deliberate
"never hide this" declaration from your own code, unlike the automatic
geometry guess.

#### Clicking to expand

Clicking (or tapping) a condensed rendering always brings the real bars back —
both together, regardless of which one was clicked, since there's a single
pinned state for the whole layout, not one per bar. Nothing to configure:
the cursor changes to `var(--vaadin-clickable-cursor, pointer)` on devices
with a fine pointer (a mouse; touch-only devices show no cursor change,
having no cursor to change), and the condensed rendering's text is never
selectable, guarding against an accidental tap-and-hold or drag-select
gesture firing instead of the click.

### Listening to collapsed state changes

```java
@Layout
public class MainLayout extends AppLayout {
    public MainLayout() {
        var headroom = AppHeadroom.applyTo(this);
        headroom.addCollapseChangeListener(event -> System.out.println("collapsed: " + event.isCollapsed()));

        boolean currentlyCollapsed = headroom.isCollapsed();
    }
}
```

`isCollapsed()` reflects whether the chrome is currently hidden, kept in sync
with the client's own scroll-driven state (also reset to `false` if `headroom`
is detached via `remove()`). `addCollapseChangeListener` notifies on every
change instead of polling.

### Overriding bar collapsibility

Custom `AppLayout` extensions could reposition and reshape one of these bars — for example, turning the bottom navbar into a side rail on tablets. A bar that's already pinned to the viewport (`position: fixed`) and shaped like a vertical rail rather than a horizontal bar (taller than wide) is treated as non-collapsible by `AppHeadroom` automatically. For cases where that inference isn't right or the effect is not desired, override it explicitly:

```java
@Layout
public class MainLayout extends AppLayout {
    public MainLayout() {
        AppHeadroom.applyTo(this)
                .setTopBarCollapsible(false)
                .setBottomBarCollapsible(true);
    }
}
```

### Overriding configuration defaults

`AppHeadroom` allows you to manage its triggering tolerances, offset, transition duration, tablet/phone screen-size threshold, and stacking/spacing of its condensed and landscape renderings if the defaults don't match your application's needs.

Call them before or after attaching — they map to HTML attributes read by the web component. The one exception is `setTabletMinShortSidePx`, which must be called before the layout attaches, since device type is only ever detected once.

| Method                            | Default | Effect                                                                         |
|-----------------------------------|---------|--------------------------------------------------------------------------------|
| `setTopOffset(int px)`            | 100     | Distance from page top within which chrome is always shown                     |
| `setHideTolerance(int px)`        | 30      | Scroll-down distance required to trigger hide                                  |
| `setShowTolerance(int px)`        | 30      | Scroll-up distance required to trigger restore                                 |
| `setTransitionDuration(int ms)`   | 600     | Duration of the show/hide slide and padding transitions                        |
| `setTabletMinShortSidePx(int px)` | 768     | Physical-screen-size threshold (shorter side) separating `TABLET` from `PHONE` |

```java
@Layout
public class MainLayout extends AppLayout {
    public MainLayout() {
        AppHeadroom.applyTo(this)
                .setTopOffset(64)
                .setHideTolerance(10)
                .setShowTolerance(10)
                .setTabletMinShortSidePx(600);
    }
}
```

A few more defaults fall outside this pattern, exposed as plain CSS custom
properties instead of Java methods: nothing in this library ever reads or
sets them, since they only matter for how these bars sit relative to your
own app's other fixed-position chrome. Set these directly on your
`vaadin-app-layout`:

| Custom property                           | Default | Effect                                                     |
|-------------------------------------------|---------|------------------------------------------------------------|
| `--headroom-landscape-bottom-bar-z-index` | 200     | Landscape-mode bottom bar's stacking order                 |
| `--headroom-condensed-top-z-index`        | 200     | Condensed top rendering's stacking order                   |
| `--headroom-condensed-bottom-z-index`     | 200     | Condensed bottom rendering's stacking order                |
| `--headroom-condensed-gap`                | 8px     | Gap `asFloating()` reserves around its condensed rendering |

Here's how you would adjust them using Java:

```java
@Layout
public class MainLayout extends AppLayout {
    public MainLayout() {
        getStyle()
                .set("--headroom-landscape-bottom-bar-z-index", "1000")
                .set("--headroom-condensed-gap", "16px");
    }
}
```

## How it works

`AppHeadroom` is a LitElement web component (`<app-headroom>`) backed by `AppHeadroom.java`. It attaches alongside your `vaadin-app-layout` and animates its `::part(navbar-top)`/`::part(navbar-bottom)` slots directly, driven entirely by the page's own scroll position — no polling from the server. It also injects global CSS that enables body-scrolling on touch devices; see [Requirements](#requirements) for why that's needed.

Chrome always shows within the first `topOffset` px from the top of the page (default: **100 px**). Beyond that, hiding and restoring are driven from a high-water mark: a bar hides once the user has scrolled down more than `hideTolerance` px past it (default: **30 px**), and restores once they've scrolled back up more than `showTolerance` px from the hidden position (default: **30 px**). A guard against bottom overscroll/bounce on iOS keeps that bounce from triggering a false restore.

The hide/restore transition animates `transform: translateY(±100%)`, `opacity`, and `visibility` together, all at the same `transitionDuration` (default 600 ms, ease) — no layout shifts, no JavaScript-driven height recalculations. `opacity` produces the actual fade; `visibility` (becoming `hidden` only once the fade fully completes, `visible` again from the very start of restoring) is what makes a bar truly disappear rather than just going transparent, including any of its own content that doesn't respond to `transform` at all — e.g. a nav framework's own popover/overlay-based UI that escapes the normal paint hierarchy, since `visibility` is inherited regardless of how a descendant is rendered.

On touch devices in landscape orientation, the bottom bar is additionally kept `position: fixed` to the viewport — rather than scrolling away with the page, which body-scrolling mode would otherwise cause — with its horizontal padding reconciled, not simply added, against the safe area, so it isn't clipped by a notch or rounded corner.

## Feature list

### Automatic

- **Scroll-driven hide/show** — the top and/or bottom navbar slides and fades away on scroll-down, and back on scroll-up, with no layout shift.
- **Automatic pinned-rail detection** — a bar that's already pinned to the viewport and shaped like a rail rather than a bar is left alone, with no configuration needed to get this.
- **Safe area reconciliation on touch devices** — the bottom bar stays fixed to the viewport and its padding is reconciled against the safe area, instead of scrolling away or getting clipped by a notch/rounded corner.
- **Body-scrolling mode on touch devices** — enabled automatically via injected CSS; see [Requirements](#requirements).
- **Theme-agnostic styling** — no Lumo- or Aura-specific styling anywhere; everything themeable defaults to a base-theme property, so it fits any theme out of the box.
- **Server-visible collapsed state** — `isCollapsed()`/`addCollapseChangeListener` keep server-side code in sync with the client's own scroll-driven state, no polling required.

### Customizable

- **Thresholds and timing** — how far from the top chrome always shows, how far you must scroll to hide/restore it, and how long the transition takes (`setTopOffset`/`setHideTolerance`/`setShowTolerance`/`setTransitionDuration`).
- **Per-bar collapsibility override** — force a bar to never hide, overriding the automatic rail detection above (`setTopBarCollapsible`/`setBottomBarCollapsible`).
- **Condensed renderings** — show a small floating rendering or a full-width "ribbon" in place of a hidden bar instead of nothing, safe-area aware either way, with click/tap-to-expand built in (`getCondensedTop().asFloating()`/`asRibbon()`).
- **Restricted activation** — limit the whole effect to specific device types or orientations, re-evaluated live on rotation (`setActivationPredicate`).
- **Tablet breakpoint** — the physical-screen-size threshold that distinguishes a tablet from a phone (`setTabletMinShortSidePx`).
- **Stacking and spacing** — the landscape bottom bar's and condensed renderings' z-index, and the gap `asFloating()` reserves around itself (`--headroom-landscape-bottom-bar-z-index`/`--headroom-condensed-top-z-index`/`--headroom-condensed-bottom-z-index`/`--headroom-condensed-gap`).

## Requirements

- Vaadin Flow **25.1** or later
- Java **21** or later
- Targets a plain `vaadin-app-layout`-backed `AppLayout` — see [Usage](#usage) for what that means if you're using an `AppLayout` subclass
- Works under any theme (Lumo, Aura, or a custom one) out of the box — nothing here depends on theme-specific styling

The component also attaches a passive `scroll` listener to `window`, so **body-scrolling mode must be active on touch devices** (`window.scrollY` must be meaningful). This is handled automatically via an injected media-query rule:

```css
@media (pointer: coarse) {
    html { height: auto; }
}
```

No explicit configuration is needed in the consuming app for this.

## Development

### Running the demo

```
mvn jetty:run
```

Starts the test/demo server at http://localhost:8080.

### Integration tests

```
mvn verify -Pit
```

## Credits

The "headroom" name and hide-on-scroll-down/show-on-scroll-up interaction
pattern originate from [Headroom.js](https://github.com/WickyNilliams/headroom.js)
by Nick Williams (MIT License). This is an independent implementation built
for Vaadin Flow/Lit — it doesn't use any of that project's code — but the
pattern and name are its idea.

## Publishing to Vaadin Directory

You can create the zip package needed for [Vaadin Directory](https://vaadin.com/directory/) using

```
mvn versions:set -DnewVersion=1.0.0 # You cannot publish snapshot versions
mvn clean install -Pdirectory
```

The package is created as `target/{project-name}-1.0.0.zip`

For more information or to upload the package, visit https://vaadin.com/directory/my-components?uploadNewComponent
