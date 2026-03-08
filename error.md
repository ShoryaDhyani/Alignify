Executing tasks: [:app:assembleDebug] in project Z:\Projects\MajorProject\lightweightMediaPose\Alghinify

Starting Gradle Daemon...
Gradle Daemon started in 5 s 88 ms
Calculating task graph as configuration cache cannot be reused because the set of Gradle properties has changed.

> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:writeDebugSigningConfigVersions UP-TO-DATE
> Task :app:writeDebugAppMetadata UP-TO-DATE
> Task :app:createDebugVariantModel UP-TO-DATE
> Task :app:createDebugCompatibleScreenManifests UP-TO-DATE
> Task :app:desugarDebugFileDependencies UP-TO-DATE
> Task :app:generateDebugResValues UP-TO-DATE
> Task :app:javaPreCompileDebug UP-TO-DATE
> Task :app:generateDebugResources UP-TO-DATE
> Task :app:validateSigningDebug UP-TO-DATE
> Task :app:checkKotlinGradlePluginConfigurationErrors
> Task :app:mergeDebugShaders UP-TO-DATE
> Task :app:mergeDebugJniLibFolders UP-TO-DATE
> Task :app:mergeDebugNativeDebugMetadata NO-SOURCE
> Task :app:processDebugGoogleServices UP-TO-DATE
> Task :app:compileDebugShaders NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:extractDeepLinksDebug UP-TO-DATE
> Task :app:checkDebugAarMetadata UP-TO-DATE
> Task :app:mergeDebugAssets UP-TO-DATE
> Task :app:checkDebugDuplicateClasses UP-TO-DATE
> Task :app:compressDebugAssets UP-TO-DATE
> Task :app:mergeLibDexDebug UP-TO-DATE
> Task :app:mergeDebugNativeLibs UP-TO-DATE
> Task :app:stripDebugDebugSymbols UP-TO-DATE
> Task :app:mapDebugSourceSetPaths
> Task :app:dataBindingMergeDependencyArtifactsDebug UP-TO-DATE
> Task :app:mergeExtDexDebug UP-TO-DATE

> Task :app:processDebugMainManifest
> package="com.alignify" found in source AndroidManifest.xml: Z:\Projects\MajorProject\lightweightMediaPose\Alghinify\app\src\main\AndroidManifest.xml.
> Setting the namespace via the package attribute in the source AndroidManifest.xml is no longer supported, and the value is ignored.
> Recommendation: remove package="com.alignify" from the source AndroidManifest.xml: Z:\Projects\MajorProject\lightweightMediaPose\Alghinify\app\src\main\AndroidManifest.xml.
> [org.tensorflow:tensorflow-lite:2.14.0] C:\Users\ASUS\.gradle\caches\transforms-3\b2d1ead7a22c3be9685bb1ef4bfc6481\transformed\tensorflow-lite-2.14.0\AndroidManifest.xml Warning:

    Namespace 'org.tensorflow.lite' is used in multiple modules and/or libraries: org.tensorflow:tensorflow-lite:2.14.0, org.tensorflow:tensorflow-lite-api:2.14.0. Please ensure that all modules and libraries have a unique namespace. For more information, See https://developer.android.com/studio/build/configure-app-module#set-namespace

[org.tensorflow:tensorflow-lite-support:0.4.4] C:\Users\ASUS\.gradle\caches\transforms-3\fcbc00027a12d0fa07e1cf2531bd84f7\transformed\tensorflow-lite-support-0.4.4\AndroidManifest.xml Warning:
Namespace 'org.tensorflow.lite.support' is used in multiple modules and/or libraries: org.tensorflow:tensorflow-lite-support:0.4.4, org.tensorflow:tensorflow-lite-support-api:0.4.4. Please ensure that all modules and libraries have a unique namespace. For more information, See https://developer.android.com/studio/build/configure-app-module#set-namespace

> Task :app:processDebugManifest
> Task :app:processDebugManifestForPackage
> Task :app:packageDebugResources
> Task :app:parseDebugLocalResources
> Task :app:mergeDebugResources
> Task :app:dataBindingGenBaseClassesDebug
> Task :app:processDebugResources
> Task :app:compileDebugKotlin NO-SOURCE

> Task :app:compileDebugJavaWithJavac
> warning: [options] source value 8 is obsolete and will be removed in a future release
> warning: [options] target value 8 is obsolete and will be removed in a future release
> warning: [options] To suppress warnings about obsolete options, use -Xlint:-options.
> Z:\Projects\MajorProject\lightweightMediaPose\Alghinify\app\src\main\java\com\alignify\SettingsActivity.java:105: error: cannot find symbol

        tvThemeMode = findViewById(R.id.tvThemeMode);
                                       ^

symbol: variable tvThemeMode
location: class id
Z:\Projects\MajorProject\lightweightMediaPose\Alghinify\app\src\main\java\com\alignify\SettingsActivity.java:246: error: cannot find symbol
View settingTheme = findViewById(R.id.settingDarkMode);
^
symbol: variable settingDarkMode
location: class id
Note: Some input files use or override a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
2 errors
3 warnings

> Task :app:compileDebugJavaWithJavac FAILED

FAILURE: Build failed with an exception.

- What went wrong:
  Execution failed for task ':app:compileDebugJavaWithJavac'.

  > Compilation failed; see the compiler error output for details.

- Try:
  > Run with --info option to get more log output.
  > Run with --scan to get full insights.

BUILD FAILED in 48s
32 actionable tasks: 11 executed, 21 up-to-date
Configuration cache entry stored.
