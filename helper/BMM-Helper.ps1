param(
    [string]$SteamRoot,
    [string]$Maker,
    [int]$Port = 0,
    [switch]$Json,
    [switch]$NoServer
)
$exe = Join-Path $PSScriptRoot 'BMM.Helper.exe'
if (-not (Test-Path -LiteralPath $exe)) { throw "BMM.Helper.exe is missing from $PSScriptRoot" }
$args = @()
if ($SteamRoot) { $args += @('--steam-root', $SteamRoot) }
if ($Maker) { $args += @('--maker', $Maker) }
if ($Port -gt 0) { $args += @('--port', $Port) }
if ($Json) { $args += '--json' }
if ($NoServer) { $args += '--no-server' }
& $exe @args
exit $LASTEXITCODE
