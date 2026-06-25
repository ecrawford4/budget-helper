param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)

$ErrorActionPreference = 'Stop'

$mavenVersion = '3.9.9'
$wrapperRoot = Join-Path $PSScriptRoot '.mvn'
$mavenHome = Join-Path $wrapperRoot "apache-maven-$mavenVersion"
$mavenZip = Join-Path $wrapperRoot "apache-maven-$mavenVersion-bin.zip"
$mavenUrl = "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip"

if (-not (Test-Path $mavenHome)) {
    New-Item -ItemType Directory -Force -Path $wrapperRoot | Out-Null
    if (-not (Test-Path $mavenZip)) {
        Invoke-WebRequest -Uri $mavenUrl -OutFile $mavenZip
    }
    Expand-Archive -Path $mavenZip -DestinationPath $wrapperRoot -Force
}

$mvnCmd = Join-Path $mavenHome 'bin/mvn.cmd'
& $mvnCmd @MavenArgs
exit $LASTEXITCODE