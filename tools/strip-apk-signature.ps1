param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$Target
)

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$temp = Join-Path ([System.IO.Path]::GetDirectoryName($Target)) ("unsigned-" + [System.IO.Path]::GetRandomFileName() + ".apk")
$inputZip = [System.IO.Compression.ZipFile]::OpenRead($Source)
$outputZip = [System.IO.Compression.ZipFile]::Open($temp, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($entry in $inputZip.Entries) {
        if ($entry.FullName -match '^META-INF/.*\.(RSA|SF|EC)$') { continue }
        $newEntry = $outputZip.CreateEntry($entry.FullName, [System.IO.Compression.CompressionLevel]::Optimal)
        if (-not $entry.FullName.EndsWith('/')) {
            $input = $entry.Open()
            $output = $newEntry.Open()
            try { $input.CopyTo($output) } finally { $output.Dispose(); $input.Dispose() }
        }
    }
} finally {
    $outputZip.Dispose()
    $inputZip.Dispose()
}
Move-Item -LiteralPath $temp -Destination $Target -Force
