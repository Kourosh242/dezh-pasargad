# Dezh-e Pasargad — R8 rules
#
# The app is reflection-free by design: kotlinx.serialization uses generated
# serializers (called directly), Room ships its own consumer rules, and Compose
# needs none. Keep only what the platforms require.

# Defensive: keep serialization generated companions if future code switches to
# reflection-based serializer lookup (currently NOT used).
-keepclassmembers class com.pasargad.dezh.** {
    *** Companion;
}
-keepclasseswithmembers class com.pasargad.dezh.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Never obfuscate crash-report-relevant entry points for readable stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Release hardening: strip framework/library Log calls entirely (our own code
# performs zero logging; this removes residual library chatter like
# profileinstaller's Log.d from the shipped dex).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
