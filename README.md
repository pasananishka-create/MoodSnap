# MoodSnap

A free, open-source Android film camera app inspired by [mood.camera](https://mood.camera). Capture natural photos with authentic film character, grain, and halation — no editing required.

## Features

### Film Emulations
12 meticulously crafted film presets:

| Category | Emulations |
|----------|------------|
| **Filmic** | Portra, Cinestill 800T, Ektar, Fuji 400H, Gold 200, Ultramax |
| **Natural** | Velvia, Provia |
| **Stylistic** | Tri-X, HP5+, Arizona, Metro |

### Image Processing
- **Film Grain** — Procedural luminance-dependent grain
- **Halation** — Warm bloom effect around bright highlights
- **Tone Curves** — 8 tone profiles (Neutral, Crush, Ultra, Dynamic, Faded, Expired, Bright, Moody)
- **Vignette** — Configurable edge darkening
- **Temperature/Tint** — Warm/cool color adjustment
- **Saturation & Contrast** — Per-emulation color science

### AI Auto-Filter
ML Kit Image Labeling analyzes your scene and recommends the best film emulation:
- Portrait → Portra
- Night/Neon → Cinestill 800T
- Landscape → Velvia
- Urban/Street → Metro
- Food → Gold 200
- Architecture → Tri-X

### Camera Controls
- Exposure compensation dial
- Front/back camera switching
- Flash toggle
- Grid overlay (rule of thirds)
- Multiple aspect ratios (4:3, 1:1, 16:9, XPan, 3:2)

### Gallery
- In-app photo gallery with grid view
- Photo detail view with metadata
- Delete functionality

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Camera | CameraX 1.6 |
| Image Processing | Pure Kotlin/Android Bitmap |
| AI | Google ML Kit Image Labeling |
| Database | Room |
| Preferences | DataStore |
| DI | Hilt |
| Navigation | Navigation Compose |
| Image Loading | Coil |

## Architecture

```
com.moodcamera/
├── ai/filter/          ML Kit scene analysis
├── data/
│   ├── local/          Room DB, DataStore, DAOs
│   ├── model/          Entities
│   └── repository/     Photo, Preset repositories
├── di/                 Hilt modules
├── domain/
│   └── model/          Emulations, tones, settings
├── processing/
│   ├── engine/         Image processing pipeline
│   ├── grain/          Film grain simulation
│   └── halation/       Halation bloom effect
└── ui/
    ├── camera/         Camera screen + ViewModel
    ├── gallery/        Gallery + Photo detail
    ├── presets/        Film emulation selector
    ├── settings/       App settings
    └── theme/          Material 3 dark theme
```

## Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2.1) or later
- Android SDK 35
- Kotlin 2.1.0

### Build & Run
1. Clone the repository:
   ```bash
   git clone https://github.com/YOUR_USERNAME/MoodSnap.git
   ```
2. Open in Android Studio
3. Sync Gradle
4. Run on device or emulator (API 26+)

## Project Structure

```
MoodSnap/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/moodcamera/
│       └── res/
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── .gitignore
└── README.md
```

## Permissions

- **Camera** — Required for photo capture

No internet, location, or contacts permissions needed. All processing happens on-device.

## Roadmap

- [ ] 3D LUT engine for advanced color grading
- [ ] OpenGL ES real-time preview
- [ ] Custom preset creation UI
- [ ] Preset sharing/import
- [ ] Portrait mode with bokeh
- [ ] Custom frame designs
- [ ] Photo export to device gallery
- [ ] ProRAW/DNG support

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [mood.camera](https://mood.camera) by Alex Fox — Original iOS app inspiration
- [CameraX](https://developer.android.com/media/camera/camerax) — Android camera library
- [ML Kit](https://developers.google.com/ml-kit) — On-device machine learning
- [PhotonCam](https://github.com/cinethe-zs/photoncam) — Android film camera reference
