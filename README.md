# vaadin-flow-app-headroom

A Vaadin Flow component that hides the app header when the user scrolls down and restores it when they scroll up — the "headroom" pattern.

## Table of Contents

- [How it works](#how-it-works)
- [Requirement](#requirement)
- [Usage](#usage)
- [Configuration](#configuration)
- [Pinning individual bars](#pinning-individual-bars)
- [Server-visible pinned state](#server-visible-pinned-state)
- [Restricting activation by device type/orientation](#restricting-activation-by-device-typeorientation)
- [Development](#development)
  - [Running the demo](#running-the-demo)
  - [Integration tests](#integration-tests)
- [Credits](#credits)
- [Publishing to Vaadin Directory](#publishing-to-vaadin-directory)

## How it works

- Always shown within the first `topOffset` px from the top of the page (default: **100 px**)
- Hides after the user scrolls down more than `hideTolerance` px past a high-water mark (default: **30 px**)
- Restores after the user scrolls up more than `showTolerance` px from the hidden position (default: **30 px**)
- Guards against bottom overscroll/bounce on iOS causing a false restore
- Animates with `transform: translateY(±100%)` and a 600 ms ease transition — no layout shifts, no JavaScript-driven height recalculations

The component is a LitElement web component (`<app-headroom>`) backed by `AppHeadroom.java`. It injects global CSS that enables body-scrolling on touch devices and animates `vaadin-app-layout`'s internal `::part(navbar-top)` and `::part(navbar-bottom)` slots.

## Requirement

The component attaches a passive `scroll` listener to `window`, so **body-scrolling mode must be active on touch devices** (`window.scrollY` must be meaningful). The component sets this automatically via an injected media-query rule:

```css
@media (pointer: coarse) {
    html { height: auto; }
}
```

No explicit configuration is needed in the consuming app.

## Usage

```java
AppHeadroom.applyTo(myAppLayout);
```

`applyTo` is the only entry point: it validates that `myAppLayout` is backed by
the standard `vaadin-app-layout` web component, creates an `AppHeadroom`, and
returns the instance for chaining. This works the same regardless of whether
`myAppLayout` is a plain `AppLayout` or a subclass (e.g. `vaadin-flow-app-nav-layout`'s
`AppNavLayout`) — `AppHeadroom` attaches itself as a peer element alongside the
layout (never as a light-DOM child of it), so it never shows up in
`myAppLayout.getChildren()` and needs no cooperation from whatever the layout
subclass does with its own children.

Call `headroom.remove()` to detach headroom behavior from the layout without
affecting the layout itself.

## Configuration

All setters return `this` for chaining. Call them before or after attaching — they map to HTML attributes read by the web component.

| Method                            | Default | Effect                                                     |
|-----------------------------------|---------|------------------------------------------------------------|
| `setTopOffset(int px)`            | 100     | Distance from page top within which chrome is always shown |
| `setHideTolerance(int px)`        | 30      | Scroll-down distance required to trigger hide              |
| `setShowTolerance(int px)`        | 30      | Scroll-up distance required to trigger restore             |
| `setTransitionDuration(int ms)`   | 600     | Duration of the show/hide slide and padding transitions    |

```java
AppHeadroom.applyTo(myAppLayout)
    .setTopOffset(64)
    .setHideTolerance(10)
    .setShowTolerance(10);
```

## Pinning individual bars

A bar that's already pinned to the viewport (`position: fixed`) and shaped
like a vertical rail rather than a horizontal bar (taller than wide) is left
alone automatically — a plain, observable geometry fact, not something
anything has to declare. For cases where that inference isn't right, override
it explicitly:

```java
AppHeadroom.applyTo(myAppLayout).setBottomBarPinned(true);
```

`setTopBarPinned(boolean)` / `setBottomBarPinned(boolean)` take precedence
over the automatic geometry check.

## Server-visible pinned state

```java
var headroom = AppHeadroom.applyTo(myAppLayout);
headroom.addPinnedChangeListener(event -> System.out.println("pinned: " + event.isPinned()));
boolean currentlyPinned = headroom.isPinned();
```

`isPinned()` reflects whether the chrome is currently shown, kept in sync
with the client's own scroll-driven state (also reset to `true` if `headroom`
is detached via `remove()`). `addPinnedChangeListener` notifies on every
change instead of polling.

## Restricting activation by device type/orientation

By default the effect is active on every device. To restrict it, supply a
predicate over the session's detected device type and current orientation —
re-evaluated automatically whenever either becomes known or changes (e.g. on
rotation), with nothing further to wire up:

```java
AppHeadroom.applyTo(myAppLayout).setActivationPredicate((deviceType, orientation) ->
    deviceType == AppHeadroom.DeviceType.PHONE
        || (deviceType == AppHeadroom.DeviceType.TABLET && orientation == AppHeadroom.Orientation.LANDSCAPE));
```

Device type (`PHONE` / `TABLET` / `DESKTOP`) is classified from touch
capability and physical screen size (not viewport width, which fluctuates as
a desktop user resizes their browser window). `isActive()` reflects the
predicate's last evaluation. The screen-size threshold that separates
`TABLET` from `PHONE` (default `768`px, the shorter physical screen side)
is itself overridable — must be called before the layout attaches, since
device type is only ever detected once:

```java
AppHeadroom.applyTo(myAppLayout).setTabletMinShortSidePx(600);
```

The landscape-mode bottom bar's stacking order is a plain CSS custom
property, not a Java API — set `--headroom-landscape-bottom-bar-z-index`
directly on your `vaadin-app-layout` (default `200`) if it needs to sit
above or below other fixed-position chrome in your app.

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
