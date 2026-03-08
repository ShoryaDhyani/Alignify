You are an expert Android developer specializing in Material Design 3 theming.

Task: Complete and fix the Dark Theme implementation for the Alignify fitness application (package: com.alignify).

## Current State (Updated)

Alignify now has a fully functional dark mode setup:
- `AlignifyApp.java` initializes `AppCompatDelegate` based on `SharedPreferences("dark_mode_enabled")`.
- `SettingsActivity.java` has a `SwitchMaterial` toggle with `isUpdatingUI` guard to prevent recreation loops.
- `res/values/themes.xml` uses `Theme.Material3.DayNight.NoActionBar` with full Material3 attributes.
- `res/values-night/themes.xml` overrides all theme attributes for dark mode.
- `res/values/colors.xml` defines the complete light-mode color palette.
- `res/values-night/colors.xml` provides dark-mode overrides for 30+ adaptive colors.
- All hardcoded hex colors replaced with named `@color/` resources in all 14 layout files.
- Key drawables updated to use adaptive color resources.
- Java programmatic colors use `getColor(R.color.xxx)` for dark mode adaptation.

## Requirements

1. Use Material Design 3 guidelines for dark theme color palette.
2. Support both Light Mode and Dark Mode with the existing toggle in SettingsActivity.
3. Optionally add system-setting-based theme switching as a third option (follow system).
4. Maintain the existing `Theme.Material3.DayNight.NoActionBar` parent theme.
5. Ensure WCAG AA contrast compliance (4.5:1 for text, 3:1 for large text/icons).

## Implementation Details

### 1. Color Resources ✅ DONE

`res/values-night/colors.xml` exists with dark-mode overrides:

| Color Name             | Light Value  | Dark Value   | Purpose                    |
|------------------------|-------------|-------------|----------------------------|
| `background_light`     | `#F5F7FA`   | `#0D0D0D`  | App background              |
| `surface`              | `#1A1A1A`   | `#1A1A1A`  | Card/surface background     |
| `surface_light`        | `#252525`   | `#252525`  | Elevated surface            |
| `text_primary`         | `#FFFFFF`   | `#FFFFFF`  | Primary text (keep white for dark) |
| `text_primary_dark`    | `#1A1A1A`   | `#E0E0E0` | Primary text on light bg    |
| `text_secondary`       | `#888888`   | `#AAAAAA`  | Secondary text              |
| `text_secondary_dark`  | `#666666`   | `#999999`  | Secondary text variant      |
| `text_hint`            | `#666666`   | `#888888`  | Hint text                   |
| `input_background`     | `#1A1A1A`   | `#1E1E1E`  | Input field background      |
| `input_border`         | `#00D9FF`   | `#00D9FF`  | Input field border (accent) |
| `card_background`      | `#1A1A1A`   | `#1E1E1E`  | Card background             |
| `card_background_light`| `#FFFFFF`   | `#1A1A1A`  | Card bg on light screens    |
| `divider`              | `#333333`   | `#333333`  | Divider line                |
| `divider_light`        | `#E0E0E0`   | `#333333`  | Divider on light bg         |
| `overlay_background`   | `#CC0D0D0D` | `#CC0D0D0D`| Overlay background          |
| `card_steps`           | `#E8F5E9`   | `#1B3A1F`  | Steps card bg               |
| `card_calories`        | `#FFF3E0`   | `#3A2A10`  | Calories card bg            |
| `card_water`           | `#E3F2FD`   | `#102A3A`  | Water card bg               |
| `card_workout`         | `#E8F5E9`   | `#1B3A1F`  | Workout card bg             |
| `card_running`         | `#E3F2FD`   | `#102A3A`  | Running card bg             |
| `card_coach`           | `#FFF3E0`   | `#3A2A10`  | Coach card bg               |
| `card_lime`            | `#C8E6C9`   | `#1B3A1F`  | Activity card (green)       |
| `card_pink`            | `#B3E5FC`   | `#0D3A5A`  | Activity card (blue)        |
| `card_purple`          | `#D1C4E9`   | `#2A1F3A`  | Activity card (purple)      |
| `card_light_pink`      | `#E8F5E9`   | `#1B3A1F`  | Activity card (light green) |
| `card_light_blue`      | `#E3F2FD`   | `#102A3A`  | Activity card (light blue)  |
| `streak_inactive`      | `#E0E0E0`   | `#333333`  | Inactive streak dot         |
| `switch_track`         | `#B0BEC5`   | `#546E7A`  | Switch track color          |

### 2. Light Theme ✅ DONE

`res/values/themes.xml` updated with full Material3 semantic color attributes:

```xml
<style name="Theme.MediaPose" parent="Theme.Material3.DayNight.NoActionBar">
    <item name="colorPrimary">@color/accent</item>
    <item name="colorOnPrimary">@color/black</item>
    <item name="colorPrimaryContainer">@color/accent_10</item>
    <item name="colorSecondary">@color/accent_green</item>
    <item name="colorSurface">@color/card_background_light</item>
    <item name="colorOnSurface">@color/text_primary_dark</item>
    <item name="colorError">@color/error_red</item>
    <item name="android:colorBackground">@color/background_light</item>
    <item name="android:statusBarColor">@color/background_light</item>
    <item name="android:navigationBarColor">@color/background_light</item>
    <item name="android:windowBackground">@color/background_light</item>
    <item name="android:windowLightStatusBar">true</item>
    <item name="android:fontFamily">@font/poppins</item>
    <item name="fontFamily">@font/poppins</item>
</style>
```

### 3. Dark Theme ✅ DONE

`res/values-night/themes.xml` already had complete dark theme overrides:

```xml
<style name="Theme.MediaPose" parent="Theme.Material3.DayNight.NoActionBar">
    <item name="colorPrimary">@color/accent</item>
    <item name="colorOnPrimary">@color/black</item>
    <item name="colorPrimaryContainer">@color/accent_10</item>
    <item name="colorSecondary">@color/accent_green</item>
    <item name="colorSurface">@color/surface</item>
    <item name="colorOnSurface">@color/text_primary</item>
    <item name="colorError">@color/error_red</item>
    <item name="android:colorBackground">@color/background</item>
    <item name="android:statusBarColor">@color/background</item>
    <item name="android:navigationBarColor">@color/surface</item>
    <item name="android:windowBackground">@color/background</item>
    <item name="android:windowLightStatusBar">false</item>
    <item name="android:fontFamily">@font/poppins</item>
    <item name="fontFamily">@font/poppins</item>
</style>
```

### 4. Named Color Resources ✅ DONE

All hardcoded hex values replaced with named color resources in `res/values/colors.xml`:

```xml
<!-- Icon tint colors -->
<color name="icon_orange">#F57C00</color>
<color name="icon_purple">#7B1FA2</color>
<color name="icon_purple_accent">#7C4DFF</color>
<color name="icon_deep_purple">#9C27B0</color>
<color name="icon_deep_orange">#E65100</color>
<color name="icon_teal">#00897B</color>
<color name="icon_red">#D32F2F</color>
<color name="icon_flame_orange">#FF6B35</color>

<!-- Button colors -->
<color name="btn_stop_red">#F44336</color>
<color name="btn_disabled_grey">#757575</color>

<!-- Text variants -->
<color name="text_danger">#C62828</color>
<color name="text_light_grey">#AAAAAA</color>
<color name="text_tertiary">#CCCCCC</color>
<color name="text_black_50">#80000000</color>

<!-- Card extras -->
<color name="card_overlay_dark">#E6000000</color>
<color name="card_select_blue">#E8F5FE</color>
```

### 5. Layout Hardcoded Colors ✅ DONE

All 54+ hardcoded hex colors replaced with `@color/` references. 42 card backgrounds fixed.

| File | Hardcoded Count | Key Replacements |
|------|-----------------|------------------|
| `activity_settings_new.xml` | 19 | `#E0E0E0` → `@color/divider_light`, icon tints → `@color/icon_*` |
| `activity_edit_profile.xml` | 10 | `#E0E0E0` → `@color/divider_light`, icon tints → `@color/icon_*` |
| `activity_exercise.xml` | 8 | `#888888` → `@color/text_secondary`, `#AAAAAA` → `@color/text_light_grey` |
| `activity_exercise_new.xml` | 8 | Same as `activity_exercise.xml` |
| `activity_run.xml` | 5 | `#4CAF50` → `@color/streak_active`, `#F57C00` → `@color/icon_orange` |
| `activity_dashboard_new.xml` | 4 | `#E0E0E0` → `@color/divider_light`, `#4CAF50` → `@color/streak_active` |
| `activity_step_new.xml` | 2 | Icon tints → `@color/icon_orange`, `@color/icon_deep_purple` |
| `bottom_sheet_steps.xml` | 1 | `#FF6B35` → `@color/icon_flame_orange` |
| `activity_select_exercise.xml` | 1 | `#E8F5FE` → `@color/card_select_blue` |
| `item_chat_message_user.xml` | 1 | `#80000000` → `@color/text_black_50` |

### 6. Theme Toggle ⏳ OPTIONAL ENHANCEMENT

`AlignifyApp.java` currently supports on/off. Consider adding a third "Follow System" option:

```java
public static final String KEY_THEME_MODE = "theme_mode";
// Values: "light", "dark", "system"

@Override
public void onCreate() {
    super.onCreate();
    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    String themeMode = prefs.getString(KEY_THEME_MODE, "system");

    switch (themeMode) {
        case "dark":
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            break;
        case "light":
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            break;
        default:
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            break;
    }
}
```

### 7. Settings UI ⏳ OPTIONAL ENHANCEMENT

Currently uses `SwitchMaterial` toggle (on/off). Could upgrade to a three-option selector dialog:
- Light
- Dark
- System Default (recommended)

### 8. Drawable Adaptation ✅ MOSTLY DONE

Key drawables updated: `bg_rounded_card.xml`, `bg_streak_inactive.xml`, `bg_tag_gray.xml` now use adaptive color resources. Icon drawables with semantic accent colors (orange, purple, teal) intentionally keep fixed colors for brand consistency.

### 9. Handle BottomSheetDialogTheme for dark mode

The current `BottomSheetDialogTheme` in `themes.xml` uses `Theme.Design.Light.BottomSheetDialog` as parent. Add a night override using `Theme.Design.BottomSheetDialog`:

```xml
<!-- In values-night/themes.xml -->
<style name="BottomSheetDialogTheme" parent="Theme.Design.BottomSheetDialog">
    <item name="bottomSheetStyle">@style/BottomSheetStyle</item>
    <item name="android:windowIsFloating">false</item>
    <item name="android:windowSoftInputMode">adjustResize</item>
</style>
```

## Output Format

1. Step-by-step implementation with exact file paths relative to `app/src/main/`
2. Complete XML code for all resource files
3. Java code changes for `AlignifyApp.java` and `SettingsActivity.java`
4. Layout migration checklist with find/replace pairs
5. Testing checklist: verify each screen in both light and dark mode
