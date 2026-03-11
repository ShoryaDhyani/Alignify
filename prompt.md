You are a senior Android engineer and debugging specialist.

Review the Android project "Alignify". The app follows a layered architecture:

Package: com.alignify

UI Layer

- Activities: ExerciseActivity, DashboardActivity, SettingsActivity
- OverlayView for camera overlays
- Material3 DayNight theming

Engine Layer

- ActivityEngine
- CaloriesEngine
- Exercise detectors (BicepCurlDetector, PlankDetector, LungeDetector, SquatDetector)

ML / Infra Layer

- PoseLandmarkerHelper
- TFLiteInterpreter
- ModelManager
- Uses MediaPipe + TFLite inference

Persistence / Background Layer

- FitnessDataManager
- StepCounterService
- BootReceiver
- WaterReminderService
- Firestore sync, alarms, and step tracking

Camera pipeline:
ExerciseActivity → PoseLandmarkerHelper → detectors → ActivityEngine/CaloriesEngine → UI updates → FitnessDataManager persistence.

Recent stability fixes already applied:

- Font certificate fix for downloadable fonts
- AppCompatDelegate recreation loop fixed using isUpdatingUI guard
- BootReceiver deduplication + AlarmManager retries for Android 12+ foreground service restrictions
- Adaptive dark theme using values-night/colors.xml with Material3 tokens

Problem:
The Google Map inside the application is not working.

Your task:

1. Analyze the project structure and identify why the map is not loading or rendering.
2. Check the following common failure points:
   - Google Maps API key configuration
   - AndroidManifest permissions (INTERNET, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
   - meta-data tag for com.google.android.geo.API_KEY
   - MapFragment or SupportMapFragment initialization
   - GoogleMap lifecycle callbacks (OnMapReadyCallback)
   - Google Play Services dependency
   - Gradle dependencies for maps
   - FragmentManager usage inside the Activity
   - API key restrictions in Google Cloud Console
   - runtime location permissions
3. Verify if theme or Material3 configuration is interfering with the map fragment.
4. Provide:
   - exact files that likely contain the issue
   - corrected code snippets
   - required manifest changes
   - correct Gradle dependencies
   - debugging steps to verify the fix.

Output format:

- Root cause
- Files to modify
- Corrected code
- Manifest changes
- Gradle dependency fixes
- Step-by-step verification

use qa agent to review the identified code changes for correctness and completeness.
