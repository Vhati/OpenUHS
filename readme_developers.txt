The build process is automated by Gradle.


Build System Requirements

  Gradle 2.14 requires at least Java 1.6.

  Gradle 3.00 requires at least Java 1.7.

  Android development requires at least Java 1.8, but that subproject is optional. (See: settings.gradle)

  The code within projects here is less demanding, for legacy compatibility.

  Installing multiple Java Development Kits is recommended (one to run Gradle, another to compile code), but optional.

  For non-android builds, Java 1.6 is sufficient to both run Gradle and compile code.

  Gradle 4 is not yet supported. Gradle 2.14 and 3.5.1 are known to work.


Projects

  core - UHS parser library.
    Libraries
      - Java 1.6
          http://docs.oracle.com/javase/6/docs/api/
      - SLF4J (api)
          http://www.slf4j.org/

  desktop-reader - Swing-GUI UHS reader.
    Libraries
      - Java 1.6
          http://docs.oracle.com/javase/6/docs/api/
      - SLF4J (api)
          http://www.slf4j.org/
      - Logback (classic, core)
          http://logback.qos.ch/
      - JOpt Simple 4.x
          http://pholser.github.io/jopt-simple/
      - JDOM 2.x
          http://www.jdom.org/

  android-reader - Android UHS reader.
    Libraries
      - Android Level 10 (2.3.3 Gingerbread)
          https://developer.android.com/reference/packages.html
          (Above the sidebar, there's a dropdown list to set the API level.)
      - SLF4J (api)
          http://www.slf4j.org/
      - SLF4J-Android
          http://www.slf4j.org/android/
      - Jackson (databind, core)
          https://github.com/FasterXML/jackson-docs/wiki/Finding-Javadoc


Build Environment

WARNING: All text and source code here is encoded in UTF-8 (no BOM). For most purposes, this is identical to ANSI Windows-1252. However, accented characters in a few files may confuse text editors if they're expecting ANSI. Java tools ordinarily default to the OS's standard encoding, but gradle scripts will override that. The specs for UTF-8 allow for an optional binary header, called a byte-order mark (BOM). Make sure your text editors DO NOT save UTF-8 w/BOM!

Here's a Windows batch file that spawns a gradle-capable prompt when double-clicked (edit the vars).
- - - -
@ECHO OFF
SETLOCAL

REM The Android SDK folder. (Only needed by android projects)
SET ANDROID_HOME=D:\Apps\android-sdk-windows

REM This builds projects to *ensure* 1.6 compatibility. (Optional)
SET JDK6_HOME=D:\Apps\j2sdk1.6.0_45

REM This runs gradle and is the default compiler.
SET JAVA_HOME=D:\Apps\j2sdk1.8.0_102

REM The Gradle folder.
SET GRADLE_HOME=D:\Apps\gradle-2.14.1

SET PATH=%PATH%;%GRADLE_HOME%\bin

REM Spawn the prompt here.
CD /D "%~dp0"
cmd /K

REM Alternatively, you could comment out the "cmd" line above and just build.
REM Remember to CALL gradle, since it's really "gradle.bat".

REM CALL gradle clean && CALL gradle build
REM PAUSE

ENDLOCAL & EXIT /B
- - - -


Android SDK Setup

  Be warned, Android development will demand over a gig of disk space, hundreds of megs of RAM, and the latest Java.

  Download and extract the Android SDK somewhere.

  Start the SDK Manager
    Tick these...
      Tools/
        Android SDK Platform-tools
        Android SDK Build-tools

      [Latest API]/
        SDK Platform

      Android 2.3.3 (API 10)/
        SDK Platform
        Intel x86 Atom System Image

      Extras/
        Android Support Repository
        Intel x86 Emulator Accelerator (HAXM installer)

  Emulating an x86 device (as opposed to ARM), combined with HAXM, is considerably faster.

  The SDK won't actually install HAXM, merely download it. Look in the SDK's "extras/" folder. To confirm your hardware supports Intel virtualization, run "haxm_check.exe" from a prompt. Run "intelhaxm-android.exe" to install.

  If you have a physical device, technically you /could/ forgo emulation.

  Physical devices require a driver. The SDK offers "Extras/Google USB Driver" for Nexus devices. To install it, or find other manufacturers' installers, see this guide. ( https://developer.android.com/studio/run/oem-usb.html )


  Start the AVD Manager
    Create a virtual device. (I used: 3.2" HVGA slider, 320x480 mdpi)
    API: Level 10
    CPU: Intel Atom (x86)
    Skin: HVGA
    SD Card: I arbitrarily chose 50 MB.
    Snapshot: This can save time by skipping boot-up, when launched using the last snapshot.

  When fuzzing with settings, it's probably better to delete and recreate devices, rather than edit.

  Note: A snapshot file will take up as mush space as the RAM allotted.


About Gradle

  Running these commands from a subdirectory limits executed actions to tasks within that module (and their dependencies), although build scripts from all subprojects will be parsed and evaluated.

  gradle tasks - Lists available tasks
  gradle clean - Deletes cruft of previous builds.

  The following are the standard metatasks (provided by a Base plugin).

  gradle assemble - Compiles code and generates packaged outputs.
  gradle check - Runs checks.
  gradle build[ConfigurationName] - Assembles and checks.

  A "build/" directory will be created in each project, containing all the files that gradle generates.

  Under the hood, the Java plugin has a configuration named 'archives', with a jar file artifact. Running "assemble" finds the tasks needed to produce the artifact: looking back to a "jar" task, then to "classes", then to a "compileJava" task that compiles code.

  Multiple configurations can exist, each with their own artifacts. Running "buildArchives" will create the jar. Running "buildDists" will create tar and zip files defined by a custom 'dists' configuration. Running "build" will create everything.

  I'm not familiar with "check", but it involves Java's "test" for unit tests, which depends on "classes" and "testClasses" tasks.

  The Android plugin is... more elaborate. A project can have multiple flavors (e.g., paid & free), each flavor with multiple build-types (debug & release). (Conceivably 'flavor groups' could multiply things further.) Each combination is called a build-variant, and the plugin dynamically generates tasks for it (e.g., "assembleDebug" & "assembleRelease").

  The android plugin also defines the following tasks to (re)install/delete the app on a running [virtual] device. Reinstalling will preserve files on the device, which would have otherwise been deleted during an uninstall.

  gradle installDebug
  gradle installRelease
  gradle uninstallDebug
  gradle uninstallRelease

  Sidenote: the first attempt to communicate with a device will probably fail, as the adb daemon needs a few seconds to initially find devices. After that, you can repeatedly use installDebug to compile & send in one step.

  The android plugin doesn't run the app once it's there, so a custom task fills the role. Manually clicking the app on the device is faster though.

  gradle runAndroidReader - Runs the android reader on a device. (Debug variant)


Android Notes

  The *.uhs files downloaded by the app are kept in 'external' storage. It resolves to: "/sdcard/Android/data/net.vhati.openuhs.androidreader/files/".

  The "adb" command is found in the Android SDK under "platform-tools/". With "adb", you can (un)install apks, transfer files, or run commands in the device's shell.

  It can also dump the device's log: "adb logcat". The dump can be made less messy with filters: "adb logcat -s OpenUHS AndroidRuntime". These are a list of "tags" apps use to identify themselves when logging.

  Side note: By default, the OS prevents the SLF4J-Android logging library from reporting severities below "info". There's a trick to adjust that level: "adb shell setprop log.tag.OpenUHS [level]". (Level can be: VERBOSE, DEBUG, INFO, WARN, ERROR, ASSERT, or SUPPRESS) This change will not survive a reboot. Such minor feedback is not yet necessary for this project.

  Testing multi-touch requires either a physical device or emulating API 22+. Hold ctrl and drag: two circles, equidistant from the screen's center, will appear and diverge/converge, mimicking a pinch gesture. Google has an issue submitted for backporting the feature to earlier emulator kernels but, as of 2016, this has not been done.
