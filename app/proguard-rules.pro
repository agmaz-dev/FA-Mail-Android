# JavaMail / Activation nutzen Reflection für Provider und Datenhandler
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class myjava.awt.datatransfer.** { *; }
-dontwarn java.awt.**
-dontwarn javax.security.**
-dontwarn com.sun.mail.**
-dontwarn javax.activation.**
-dontwarn myjava.awt.datatransfer.**
