# OkHttp ships its own consumer rules. Nothing app-specific needs to be kept.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Glance instantiates widget action callbacks by class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# AGP 9 runs R8 in "strict" keep-rule mode: "-keep class X" no longer keeps X's default
# constructor implicitly. Room (used by WorkManager) creates its generated database
# class by reflection through that constructor -> crash on start without this rule.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
