$ErrorActionPreference = 'Stop'
$taskOriginalJavaHome = $env:JAVA_HOME
$taskOriginalGradleHome = $env:GRADLE_USER_HOME
$taskOriginalLocation = Get-Location

try {
    Set-Location -LiteralPath $PSScriptRoot
    $taskPortableJdk = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '.tools\jdk25') -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } |
        Select-Object -First 1
    if ($taskPortableJdk) { $env:JAVA_HOME = $taskPortableJdk.FullName }
    $env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-user-home'
    & .\gradlew.bat build
    if ($LASTEXITCODE -ne 0) { throw 'The mod build failed. Please check the build log.' }
    Write-Host 'Built mod: build\libs\mc-item-finder-1.4.0.jar'
} finally {
    $env:JAVA_HOME = $taskOriginalJavaHome
    $env:GRADLE_USER_HOME = $taskOriginalGradleHome
    Set-Location -LiteralPath $taskOriginalLocation.Path
}
