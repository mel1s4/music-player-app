# Music Player — Facebook launch kit

Spanish social assets for **Music Player 0.6.1**, branded for **Viroz Studio**.

## What is here

- [facebook-posts.md](facebook-posts.md) — the 12 post copies, hashtags, alt text and a suggested publishing plan.
- [captions.txt](captions.txt) — the same copies in a plain-text, copy-paste file.
- [images/](images/) — twelve finished **1080 × 1080** PNG posts.
- [preview-contact-sheet.jpg](preview-contact-sheet.jpg) — a single visual index of the series.
- [source/](source/) — the HTML source for every post, useful for small copy or layout edits.
- [generate_posts.py](generate_posts.py) — regenerates all images from the app screenshots and the brand assets.
- [assets/](assets/) — Viroz Studio logo and the brand fonts used by the layouts.

## Brand sources

The look follows the Viroz Studio design system from the parent studio folder:

| Token | Value |
|---|---|
| Background | `#05070c` |
| Ink | `#eef6ff` |
| Cyan | `#0ebbee` |
| Blue | `#2b8cff` |
| Green | `#22e584` |
| Yellow | `#edbb30` |
| Violet | `#b06bff` |
| Red | `#ff2d55` |

- **Display:** Chakra Petch (Bold / SemiBold / Regular).
- **Body:** Inter.
- **Telemetry / labels:** JetBrains Mono.
- **Logo:** `vz-logo.svg` from the Viroz Studio website, rendered on the dark background.

The layout is deliberately rectangular and restrained: dark grid, small radius, accent line, device frame, official wordmark.

## Regenerate

Requirements: Python 3, Google Chrome, and the app screenshots in `docs/screenshots/current/`.

```sh
python3 marketing/facebook/generate_posts.py
```

The script renders the HTML files in `source/` with headless Chrome at 1080 × 1080 into `images/`. It reads the current screenshots from `docs/screenshots/current/` and the fonts/logo in `assets/`.

## Notes

- The screenshots show a test library in Spanish, with `Moonlight` and `Café Tacvba` starred and frequent tags seeded for the editor shot.
- The app store link used in the copy is the GitHub **latest release** URL, so it keeps working as versions advance: <https://github.com/mel1s4/music-player-app/releases/latest>
- These are social assets, not app-store screenshots. Keep the logo and accent line intact if cropping for other placements.
