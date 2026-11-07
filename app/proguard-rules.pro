# Manifest components and resources are retained by the Android Gradle plugin.
# Saved state uses an explicit data format, independent of Java class names.
# Android recreates DialogFragments through their public no-argument constructor.
-keepclassmembers class dev.exactrpn.AboutDialog { public <init>(); }
