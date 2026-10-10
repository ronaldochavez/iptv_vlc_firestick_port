<#
.SYNOPSIS
    Compila TV Abierta (si hace falta) y la instala en un Fire TV por ADB Wi-Fi.

.EXAMPLE
    .\instalar-firestick.ps1 -Ip 192.168.1.50
    .\instalar-firestick.ps1 -Ip 192.168.1.50 -Compilar   # fuerza recompilar
#>
param(
    [Parameter(Mandatory = $true)][string]$Ip,
    [switch]$Compilar
)

$ErrorActionPreference = "Stop"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\release\app-release.apk"
$device = if ($Ip -match ":") { $Ip } else { "${Ip}:5555" }

if ($Compilar -or -not (Test-Path $apk)) {
    if (-not $env:JAVA_HOME) {
        # Sin JAVA_HOME: prueba el JDK de Android Studio y otros JDK 17 conocidos.
        $candidatos = @(
            (Join-Path $env:ProgramFiles "Android\Android Studio\jbr"),
            (Join-Path $env:LOCALAPPDATA "Programs\Android Studio\jbr"),
            (Join-Path $env:LOCALAPPDATA "ereader-devtools\jdk17")
        )
        $jdk = $candidatos | Where-Object { Test-Path (Join-Path $_ "bin\java.exe") } | Select-Object -First 1
        if (-not $jdk) { throw "No encontré un JDK 17. Define JAVA_HOME y vuelve a ejecutar." }
        $env:JAVA_HOME = $jdk
    }
    Write-Host "Compilando APK release..." -ForegroundColor Cyan
    & (Join-Path $PSScriptRoot "gradlew.bat") -p $PSScriptRoot ":app:assembleRelease" --console=plain
    if ($LASTEXITCODE -ne 0) { throw "La compilación falló." }
}

Write-Host "Conectando con el Fire TV en $device (acepta el aviso 'Permitir depuración USB' en la TV)..." -ForegroundColor Cyan
& $adb connect $device
Start-Sleep -Seconds 2
$state = (& $adb -s $device get-state 2>$null)
if ($state -ne "device") {
    throw "No se pudo conectar. Revisa que la depuración ADB esté activada y acepta el aviso en la TV; luego vuelve a ejecutar."
}

Write-Host "Instalando $apk ..." -ForegroundColor Cyan
& $adb -s $device install -r $apk
if ($LASTEXITCODE -ne 0) { throw "La instalación falló." }

& $adb -s $device shell am start -n com.tvabierta.app/.MainActivity | Out-Null
Write-Host "Listo: TV Abierta está abierta en tu Fire TV." -ForegroundColor Green
