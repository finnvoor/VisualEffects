# VisualEffects

`VisualEffects` is an Android library for CABackdropLayer-style surfaces.

It provides a `BackdropView` that samples content behind it inside a `BackdropContainer`, then applies a GPU `RenderEffect` to that sampled content.

Minimum supported SDK is API 31. Some effects require newer Android versions.

<img width="810" height="607" alt="demo" src="https://github.com/user-attachments/assets/8d1f8354-7fb9-4cb2-be85-e5fe244fe849" />

## Installation

Add the JitPack repository in your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

Then add the library dependency in your app or module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.finnvoor:VisualEffects:1.0")
}
```

This version is published through JitPack from the `1.0` GitHub release/tag.

## XML usage

Use `BackdropContainer` for the sampled source content, then place `BackdropView` as a sibling overlay and point it at that container in code.

```xml
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.finnvoorhees.visualeffects.BackdropContainer
        android:id="@+id/backdrop_container"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <com.finnvoorhees.visualeffects.BackdropView
        android:id="@+id/backdrop"
        android:layout_width="180dp"
        android:layout_height="120dp"
        android:layout_gravity="bottom|center_horizontal"
        app:backdropCornerRadius="24dp"
        app:backdropBlurRadius="20dp"
        app:backdropDownsampleFactor="4" />
</FrameLayout>
```

Then wire the target:

```kotlin
val container = findViewById<BackdropContainer>(R.id.backdrop_container)
val backdrop = findViewById<BackdropView>(R.id.backdrop)
backdrop.targetContainer = container
```

## Compose usage

Use `BackdropContainer(content = ..., overlay = ...)` so the sampled content and blur overlays are separate sibling layers.

```kotlin
BackdropContainer(
    modifier = Modifier.fillMaxSize(),
    content = {
        ContentBehind()
    },
    overlay = {
        Backdrop(
            modifier = Modifier.size(180.dp, 120.dp),
            effect = BlurBackdropEffect(radiusDp = 20f),
            cornerRadius = 24.dp,
            downsampleFactor = 4f,
        )
    },
)
```

## Available effects

- `BlurBackdropEffect`
  A standard Gaussian blur using `RenderEffect.createBlurEffect(...)`.
- `SaturationBackdropEffect`
  A color-filter-based saturation adjustment. Use `0f` for grayscale, `1f` for neutral, and values above `1f` for boosted saturation.
- `MaskedVariableBlurBackdropEffect`
  API 33+ only. Uses a mask image’s alpha channel to vary blur radius across the view. Alpha `0` means no blur and alpha `1` means maximum blur.

## Custom effects

Consumers can provide their own effect by subclassing `BackdropEffect` and returning any `RenderEffect` they want.

```kotlin
class SepiaBackdropEffect : BackdropEffect() {
    override fun createHardwareRenderEffect(view: BackdropView): RenderEffect? {
        val matrix = ColorMatrix().apply {
            set(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
        }
        return RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix))
    }
}
```

That means you can build custom effects with:

- standard `RenderEffect` blur/color transforms
- chained `RenderEffect`s
- `RuntimeShader`-backed effects on newer Android versions
