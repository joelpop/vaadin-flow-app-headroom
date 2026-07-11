# vaadin-flow-app-headroom

A Vaadin Flow component that hides the app header when the user scrolls down and restores it when they scroll up — the "headroom" pattern.

## Table of Contents

- [How it works](#how-it-works)
- [Requirement](#requirement)
- [Usage](#usage)
  - [With vaadin-flow-app-nav-layout (recommended)](#with-vaadin-flow-app-nav-layout-recommended)
  - [Standalone](#standalone)
- [Configuration](#configuration)
- [Development](#development)
  - [Running the demo](#running-the-demo)
  - [Integration tests](#integration-tests)
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

### With `vaadin-flow-app-nav-layout` (recommended)

Override `createHeadroomComponent()` in the `AppNavLayout` subclass:

```java
@Override
protected Component createHeadroomComponent() {
    return AppHeadroom.create();
}
```

`AppNavLayout` calls this during nav setup and appends the returned component to itself.

### Standalone

```java
AppHeadroom.applyTo(myAppLayout);
```

`applyTo` creates an `AppHeadroom`, appends it to the layout, and returns the instance for chaining.

## Configuration

All setters return `this` for chaining. Call them before or after attaching — they map to HTML attributes read by the web component.

| Method | Default | Effect |
|--------|---------|--------|
| `setTopOffset(int px)` | 100 | Distance from page top within which chrome is always shown |
| `setHideTolerance(int px)` | 30 | Scroll-down distance required to trigger hide |
| `setShowTolerance(int px)` | 30 | Scroll-up distance required to trigger restore |

```java
AppHeadroom.create()
    .setTopOffset(64)
    .setHideTolerance(10)
    .setShowTolerance(10);
```

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

## Publishing to Vaadin Directory

You should change the `organisation.name` property in `pom.xml` to your own name/organization.

```xml
<organization>
    <name>###author###</name>
</organization>
```

You can create the zip package needed for [Vaadin Directory](https://vaadin.com/directory/) using

```
mvn versions:set -DnewVersion=1.0.0 # You cannot publish snapshot versions
mvn package -Pdirectory
```

The package is created as `target/{project-name}-1.0.0.zip`

For more information or to upload the package, visit https://vaadin.com/directory/my-components?uploadNewComponent