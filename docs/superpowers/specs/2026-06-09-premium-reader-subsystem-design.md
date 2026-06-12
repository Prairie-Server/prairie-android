# Premium Reader Subsystem Design

## Goal

Rebuild the mobile reader as a premium dedicated reading app inside Silo, not a basic file viewer.

The reader must support every ebook/comic format the server exposes on mobile. EPUB, PDF, and CBZ are native in-app engines in this pass; MOBI, AZW/AZW3, CBR, FB2/FBZ, TXT, Markdown, and other recognized original ebook files must remain selectable, downloadable, and openable through Android's external app flow until native engines are added. Android TV remains ebook-free.

## Product Direction

The reader opens into content-first immersive mode:

- no bottom navigation;
- no permanent toolbar;
- no floating debug-style page text;
- content owns the screen;
- a center tap toggles reader chrome;
- controls auto-hide after a short delay;
- back exits the reader;
- left/right tap zones and swipe navigation work where appropriate.

When chrome is visible, the reader shows temporary overlays:

- top overlay: back, title/context, bookmark toggle, sections, search where supported, display settings, overflow;
- bottom overlay: current location, progress, scrubber, and page/chapter context;
- sheets for sections, bookmarks/notes, display settings, and more actions.

The default reading style should feel calm and book-like. EPUB should default to a warm/light reading surface with premium typography controls available. PDF and comics should use the same premium chrome, with format-appropriate content stages and controls.

## Architecture

The reader is split into four layers.

### Reader Shell

The shell owns the app experience:

- immersive layout;
- chrome visibility and auto-hide;
- gestures and tap zones;
- top and bottom overlays;
- shared sheets;
- loading and error presentation;
- progress UI;
- shared actions such as back, bookmark, sections, display settings, open externally, and manage download.

The shell must not contain EPUB, PDF, or comic-specific rendering logic.

### Reader Engine Interface

The shell talks to engines through a common contract. The contract should expose:

- load state;
- current location;
- total progress;
- next and previous commands;
- go-to-location command;
- sections or table of contents;
- supported capabilities;
- display settings accepted by the engine;
- bookmark/highlight support flags;
- render surface.

The exact Kotlin type names can be chosen during implementation, but the boundary should make it possible to replace a format engine without rewriting the shell.

### Format Engines

Each native in-app readable format gets its own engine. Externally handled formats are still supported reader targets: the reader shell must resolve/download the original file, show a premium unsupported-in-app state, and offer Open With / Manage Download without implying the format is unavailable.

EPUB engine:

- parse package, spine, manifest, and TOC/nav;
- render chapters through a controlled WebView-based engine or a future EPUB library wrapper;
- support theme, font size, margins, and line height from the first production pass;
- track stable locations, not only page indexes;
- preserve a path to CFI-like anchors for future highlights and notes.

PDF engine:

- wrap `PdfRenderer` behind an engine;
- own page renderer/cache/lifecycle;
- support page count, current page, fit page/width, and a zoom/pan path;
- avoid heavy decoding on the main thread;
- track progress by page.

Comic/manga engine:

- own archive loading, page list, image decode, and memory lifecycle;
- support CBZ first while keeping the interface ready for additional archive support later;
- support fit page/width and LTR/RTL reading direction;
- keep a path for double-page mode and vertical scroll mode later;
- track progress by page.

External-format handler:

- covers MOBI, AZW, AZW3, CBR, FB2/FBZ, TXT, Markdown, and any future recognized server ebook container that lacks a native engine;
- resolves remote/offline files through the same authenticated reader resolver;
- opens the original file with Android intents and MIME types so dedicated reader apps can use it;
- stores downloaded files in their original format with discoverable names;
- avoids "format not supported" dead ends for recognized formats.

### Reader State

Reader state is local-first and syncs opportunistically:

- opening a book reads local progress, settings, and bookmarks first;
- server progress and annotations merge after load;
- location changes write locally immediately;
- server sync is debounced and non-blocking;
- offline reading keeps working when server sync fails;
- sync issues appear subtly in reader chrome, not as blocking modal errors;
- display settings are scoped by server/profile and format, with a future path for global defaults;
- remote and offline file resolution go through one resolver so engines do not care where bytes came from.

## File And Network Foundation

The reader file resolver should be shared across formats. It must support:

- `file://`;
- `content://`;
- absolute `http(s)://`;
- server-relative `/api/...` paths resolved against the active server URL.

Reader network requests must mirror normal authenticated API requests:

- `Authorization: Bearer ...`;
- `X-Profile-Id`;
- `X-Profile-Token` when present;
- refresh-on-401 behavior.

Reader load failures should surface useful product errors. Debug details can be logged, but users should not be left on infinite spinners.

## Progress Model

Existing server APIs can continue to store `file_id`, `location`, and `progress`.

Format-specific locations should become more meaningful:

- EPUB: stable chapter/spine/resource anchor, with future CFI-like support;
- PDF: `page:<index>`;
- comic: `page:<index>`.

Progress updates should be debounced and should never block navigation or reading.

## Scope

Included:

- shared premium reader shell;
- engine interface and format-backed engines for EPUB, PDF, and comics;
- shared file resolver;
- shared authenticated reader network path;
- local-first reader state;
- reader chrome, sheets, gestures, and polished loading/error states;
- tests around engine selection, file resolution, media auth headers, progress mapping, and chrome state.

Out of scope for the first implementation plan:

- Android TV ebook support;
- full text selection/highlight editing UI;
- public notes export;
- cloud conflict-resolution UI beyond last-write/merge rules;
- CBR rendering if no archive library is selected;
- replacing the server reader API shape unless implementation proves a concrete gap.

## Testing

Unit tests should cover:

- engine selection by format;
- unsupported formats never entering in-app engines;
- server-relative reader URL resolution;
- authenticated reader/media requests include profile headers;
- progress location mapping for EPUB/PDF/comics;
- local-first state merge behavior;
- chrome visibility and auto-hide state reducer;
- archive/page list handling for comics.

Device verification should cover:

- EPUB opens and displays content;
- PDF opens and pages;
- CBZ opens and pages;
- reader chrome toggles and auto-hides;
- sections/settings/bookmarks sheets open and dismiss;
- progress survives close/reopen;
- offline file path opens;
- failed server/file loads show a visible error instead of spinning.
