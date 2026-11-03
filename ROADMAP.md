# Orbiter — Roadmap

What's built, and what's next, in "orbits": P0 sits closest to today, P3 furthest out.

Last updated 3 Nov 2026, after Orbiter's first releases. That update did two things:

- moved what the fork has built since 31 Oct into Shipped;
- compared the app against BookOrbit's web client (`bookorbit/bookorbit` at `76450d6`, 1 Nov 2026) to find what's still missing.

The earlier roadmap (Jul–Oct 2026) was deranjer's, for the community app this fork started from.

## Shipped

- **Sign-in:** username and password, or OIDC. The sign-in screen only shows the methods the server allows. A splash screen and a first-run tour come before it.
- **Design:** built on the web client's colours, fonts and components, with light and dark themes and optional Material You.
  - Navigation is Home, Library, Search, Notes and You.
  - On tablets and foldables there's a rail, readable-width content, and the open book shown beside the list from 840dp.
- **Offline first:** opening a book keeps it on the device. The library and reader work without the server.
  - Ratings, read status, progress, highlights and reading sessions made offline are queued and synced when the server is back.
  - You can choose a folder for downloads, limit downloads to Wi-Fi, and confirm before a download is removed.
- **Library:** browse, search, smart scopes, collections (adding books to existing ones), authors and series.
- **Home:** a Continue card, streak and goal, Continue reading, Continue listening and Recently added.
- **Book detail:**
  - Details, Files and Highlights tabs.
  - Narrators.
  - Tappable author and series.
  - A Goodreads link.
  - The synopsis renders its HTML.
- **Ebook reader (EPUB, MOBI, AZW3, FB2, CBZ):**
  - foliate.js, with themes and fonts, and settings synced with the web reader.
  - Page turns animated by the compositor, and chapters without a visible break.
  - In-book search.
  - Highlights and notes in five colours, synced with the web's annotations hub.
  - Bookmarks.
  - A selection toolbar: copy, define, translate, search and read aloud (the device's voice). Define uses offline Wiktionary dictionaries, one per language.
- **PDF reader:** its own renderer, with layout, zoom and text search.
- **Comics:** CBR and CB7 open in a native comic reader.
- **Audiobook player:**
  - Media3, playing in the background with notification and lock-screen controls.
  - Sleep timer, custom and per-book speed, chapter list and chapter progress.
  - Bookmarks.
  - Chromecast (`full` flavour only) and Android Auto.
  - Reopens the last audiobook on launch.
- **Book Drop:** upload a book, fetch its metadata on the server, then review and finalize it.
- **Stats under You:** totals, streak and goal, a 30-day chart, a 12-week heatmap and the split by source. Reading and listening time is reported as `source: android`, so it counts toward the web's streaks and goals.
- **Releases:**
  - GitHub Releases, with reproducible builds and one signing key for every channel.
  - F-Droid (submitted, [in review](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/55629)).
  - A Google Play closed test.

## P0: gaps when offline

- **Offline bookmarks.** Reader and audiobook bookmarks still go straight to the server. Offline, adding one fails. Queue them like highlights.
- **PDF highlights and bookmarks.** The server takes PDF annotations as page rectangles. The PDF reader has neither highlights nor bookmarks yet.
- **Comics offline.** CBR and CB7 are paged by the server, so they don't open offline. CBZ still opens in the ebook reader instead of the comic reader.

## P1: web client parity

Things the web client does that the app doesn't, roughly in order of how often they'd be used.

- **Collections and smart scopes:** create, rename and delete collections, and create or edit smart-scope rules. The app can only add books to collections that already exist.
- **Notes hub:**
  - filter by colour, style and source (web, KOReader, Kobo);
  - export as Markdown, CSV or JSON, like the web's annotations page.
- **Book detail:**
  - progress per file or format;
  - the user's review;
  - ratings from metadata providers.
- **Sign-in options:** magic links, registration and forgot password (the web's `/magic`, `/register` and `/forgot-password`). Magic links matter most: BookOrbit's public demo offers only them, so the app can't sign in there today.
- **Home widgets:** highlight of the day, neglected gems and the monthly challenge.
- **Statistics:**
  - a completion timeline and pace charts;
  - Reading DNA;
  - reading history (the web's `/history`);
  - achievements (`/achievements`).
- **Send to Kindle:** email delivery of a book, from the book page.
- **Book requests:** ask for a book, and follow requests (the web's `/requests`).
- **Edit metadata:** edit a book's details, and fetch metadata again for a book that's already in the library (the web's `/book/:id/edit`).
- **Server voices for read aloud:** the web reads aloud with the voices the server is set up with (`admin/tts`). Offer them next to the device's voice.
- **Notifications:** the account notifications the web shows.

Admin pages stay web-only on purpose: users, OIDC, libraries and scans, metadata providers, file naming and maintenance.

## P2: medium-term

- **Translations.** All UI text is in `res/values/` but no other language exists yet. The web client is translated on Crowdin; reusing that would need a string mapping. Chapter lengths in Android Auto still use English unit letters ("1m", "50s").
- **App lock (PIN or biometric).** For a personal library on a shared device.
- **ISBN barcode scan to search,** next to search and Book Drop.
- **Home-screen widget:** continue reading or listening.

## P3: later

- **Push notifications:** new books, downloads finished, Book Drop done.
- **Wear OS:** playback controls, and maybe offline audiobooks, as the official iOS app does on Apple Watch.
- **More than one server or account,** without signing out.

---

Sources:

- this repo's code under `app/src/main/java/com/bookorbit`;
- the web client's routes and features in `bookorbit/bookorbit` (`client/src/router`, `client/src/features`, the README's feature list).
