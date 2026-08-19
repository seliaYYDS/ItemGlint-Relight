[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9]+(?:\.[0-9]+)+$')]
    [string] $Version,

    [Parameter(ValueFromRemainingArguments)]
    [string[]] $GradleArgs
)

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$module = Join-Path $repositoryRoot "versions\$Version"
$metadataPath = Join-Path $module "toolchain.properties"
$wrapper = Join-Path $module "gradlew.bat"

if (-not (Test-Path -LiteralPath $metadataPath)) {
    throw "Unknown or incomplete version module: $Version"
}
if (-not (Test-Path -LiteralPath $wrapper)) {
    throw "Missing Gradle Wrapper: $wrapper"
}

$metadataText = (Get-Content -LiteralPath $metadataPath |
    Where-Object { $_ -match '^\s*[^#\s].*=' }) -join [Environment]::NewLine
$metadata = ConvertFrom-StringData -StringData $metadataText
$jdkVariable = "ITEMGLINT_JDK_$($metadata.gradle_java_version)"
$javaHome = [Environment]::GetEnvironmentVariable($jdkVariable, 'Process')
if ([string]::IsNullOrWhiteSpace($javaHome)) {
    $javaHome = [Environment]::GetEnvironmentVariable($jdkVariable, 'User')
}
if ([string]::IsNullOrWhiteSpace($javaHome)) {
    $javaHome = [Environment]::GetEnvironmentVariable($jdkVariable, 'Machine')
}

$javaExecutable = if ([string]::IsNullOrWhiteSpace($javaHome)) { $null } else { Join-Path $javaHome 'bin\java.exe' }
if (-not $javaExecutable -or -not (Test-Path -LiteralPath $javaExecutable)) {
    throw "Set $jdkVariable to the JDK $($metadata.gradle_java_version) root before building $Version."
}

$env:JAVA_HOME = $javaHome
$env:PATH = "$(Join-Path $javaHome 'bin');$env:PATH"
Push-Location $module
try {
    & $wrapper @GradleArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
