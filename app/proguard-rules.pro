# Reelbox ProGuard / R8 rules
-keepattributes SourceFile,LineNumberTable

# Media3 ExoPlayer & UI
-keep class androidx.media3.ui.PlayerView { *; }
-keep class androidx.media3.exoplayer.** { *; }

# Coil video frame decoder
-keep class coil.decode.VideoFrameDecoder { *; }
