# Project memory

## Memories

<!-- memory:start -->
- Antes de mergear em develop, executar lint (`./gradlew spotlessCheck`) + build e corrigir erros.
- Before every debug install/build (adb install, assembleDebug, compileDebugKotlin), automatically bump versionCode by 1 in app/build.gradle.kts; set versionName minor to the Specter change ordinal and patch to the current task number from tasks.md, updating versionName only when those change.
<!-- memory:end -->
