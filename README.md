# Doc Scanner (Android, Kotlin + Jetpack Compose)

All-in-one scanner and PDF toolbox. Works offline, no ads, no account. Modelled on the flow of popular scanner apps (CamScanner, Adobe Scan, Genius Scan): a library of scans, capture modes, a per-page editor and many export formats.

## How it works

**Home** shows your scans as cards with thumbnails, search, and quick buttons. Tap **Scan** (camera button) and pick a mode:

| Mode | What it does |
|---|---|
| Document | Auto edge detection and auto capture, crop, clean-up. Up to 30 pages. Auto color filter applied. |
| ID card | Front and back (2 pages). Export puts both sides on one A4 page. |
| Book | Up to 100 pages in a row. |
| Whiteboard | Whiteboard filter (high contrast) applied. |
| Import images | Make a scan from gallery photos. |
| Import PDF | Turn a PDF into editable pages. |

The camera screen is Google's ML Kit Document Scanner: live edge detection, auto capture, crop, rotate, cleaning and gallery import.

**Editor** (opens after every scan, and when you tap a scan):
- swipe between pages
- filters per page or for all pages: Original, Auto color, No shadow, Grayscale, Black & white, Whiteboard
- brightness and contrast sliders
- crop (drag the corners), rotate, add pages (scan or gallery), reorder and delete pages, rename
- **Extract text** (on-device OCR, copy it)
- **Share** as PDF in one tap, or **Export**
- edits are non-destructive: the original page image is kept, so you can always go back

**Export formats:** PDF (A4 / Letter / fit, quality, optional password, ID card both sides on one page), JPG, PNG (optionally as one ZIP), TXT, Word .docx (text only).

**Tools tab:** Images to PDF, PDF to images, Resize image (percent / pixels / target KB), Compress PDF (preset or target KB), Merge, Split, Organize pages, Insert image or signature, Watermark and page numbers, Image to text, QR scan and create, ID on one page, Lock PDF, Unlock PDF.

**Files tab:** everything exported, with open, rename, share, save to Downloads, delete.

**Settings:** light / dark / system theme, auto enhance, default export quality and page size, OCR language (English or Hindi), storage.

Also: "Open with Doc Scanner" on any PDF imports it into the editor.

## Build

1. Install Android Studio (Koala or newer).
2. **File > Open** the `DocScanner` folder and let Gradle sync.
3. Run on a phone or on an emulator (use an image with the Google Play icon, API 34, and set the back camera to Webcam0).
4. Share APK: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

Requirements: Android 7.0+ (API 24) and Google Play services (scanner and QR scanner use it).

## Project layout

```
com/example/docscanner/
  MainActivity.kt      app entry and screen routing
  data/                DocStore (library on disk), ExportTools, AppPrefs (settings)
  util/                ImageTools (filters, resize), PdfTools, PdfProtect, OcrTools, DocxWriter, FileStore
  ui/                  AppShell (home + bottom nav), DocEditorScreen, CropScreen, ExportSheet,
                       ToolsTab, SettingsTab, and one file per tool screen
  ui/theme/Theme.kt    colours, shapes, dark mode
```

## Good to know

* PDF output is built from page images, so text in exported or compressed PDFs is not selectable. Use TXT or Word export for text.
* Word export contains the recognized text only, not the page layout.
* Password-protected PDFs must be unlocked (Tools > Unlock PDF) before other tools can open them.
* Change the package name `com.example.docscanner` before publishing to the Play Store.
