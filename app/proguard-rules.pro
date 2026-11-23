# ProGuard / R8 release keep rules for Cờ Tướng – Luyện Kỳ (Phase 4)

# Keep JNI native bridge methods (optional Pikafish native integration)
-keep class com.thanhvan.cotuongluyenky.engine.nativebridge.** {
    native <methods>;
    public <methods>;
}

# Keep Room generated database & entity constructors
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep DataStore enum names parsed via enumValueOf<T>(...)
-keepclassmembers enum com.thanhvan.cotuongluyenky.core.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Preserve line numbers for local crash diagnosis
-keepattributes SourceFile,LineNumberTable
