# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.stashly.app.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.stashly.app.**$$serializer { *; }
# Retrofit
-keepattributes Signature, Exceptions
-keep interface com.stashly.app.domain.provider.archive.ArchiveApi { *; }
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
