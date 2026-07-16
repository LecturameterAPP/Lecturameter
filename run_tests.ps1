# Ejecuta los tests unitarios del refac sin el test executor de Gradle.
# Motivo: gradle/gradle#12660 — el worker de tests falla en Windows cuando
# GRADLE_USER_HOME contiene caracteres no ASCII (usuario "Víctor").
# Requisito previo: compilar las clases de test una vez con
#   gradle.bat compileDebugUnitTestKotlin
$ErrorActionPreference = "Stop"
$java = "C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
# C:\gradle-home es una union (junction) ASCII hacia el .gradle del usuario
$cache = "C:\gradle-home\caches\modules-2\files-2.1"

function FindJar($group, $name, $version) {
    Get-ChildItem "$cache\$group\$name\$version" -Recurse -Filter "$name-$version.jar" |
        Select-Object -First 1 -ExpandProperty FullName
}

$junit    = FindJar "junit" "junit" "4.13.2"
$hamcrest = FindJar "org.hamcrest" "hamcrest-core" "1.3"
$gson     = FindJar "com.google.code.gson" "gson" "2.10.1"
# kotlin-stdlib: coger la 1.9.x que haya en la caché
$stdlib = Get-ChildItem "$cache\org.jetbrains.kotlin\kotlin-stdlib" -Recurse -Filter "kotlin-stdlib-1.9.*.jar" |
    Where-Object { $_.Name -notmatch "sources|javadoc|common" } |
    Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty FullName

$mainClasses = "$root\app\build\tmp\kotlin-classes\debug"
$testClasses = "$root\app\build\tmp\kotlin-classes\debugUnitTest"

$cp = "$mainClasses;$testClasses;$junit;$hamcrest;$stdlib;$gson"

& $java -cp $cp org.junit.runner.JUnitCore `
    com.lecturameter.CoreUtilsTest `
    com.lecturameter.WidgetStatsTest `
    com.lecturameter.RecapUtilsTest `
    com.lecturameter.BingoManagerTest `
    com.lecturameter.ModelsTest `
    com.lecturameter.EditionGuardTest `
    com.lecturameter.BackupV3Test `
    com.lecturameter.ChallengeHistoryTest `
    com.lecturameter.IsbnScanTest `
    com.lecturameter.RestoreDedupeTest
exit $LASTEXITCODE