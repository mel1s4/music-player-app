# jaudiotagger resolves tag readers/writers by format at runtime. Keeping the whole
# library is cheap insurance for release builds (the alternative is a NoClassDefFoundError
# only when someone opens an MP4 or a Vorbis file).
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# Desktop-only classes referenced by jaudiotagger's artwork helpers.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**

-keepattributes Signature,InnerClasses,EnclosingMethod
