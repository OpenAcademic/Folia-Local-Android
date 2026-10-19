# kotlinx.serialization 生成的序列化器通过反射查找，需要保留。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.folialocal.app.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class dev.folialocal.app.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
