param(
    [Parameter(Mandatory = $true)]
    [string]$SourcePath,
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,
    [string]$MaliciousText = "<img src=x onerror=alert('cqcp-xss')>恶意合同正文"
)

$ErrorActionPreference = "Stop"
if ($PSVersionTable.PSVersion.Major -lt 7) {
    throw "PowerShell 7 or newer is required."
}

$source = [System.IO.Path]::GetFullPath($SourcePath)
$output = [System.IO.Path]::GetFullPath($OutputPath)
if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
    throw "Source DOCX does not exist."
}
if ([System.IO.Path]::GetExtension($source) -ne ".docx" `
        -or [System.IO.Path]::GetExtension($output) -ne ".docx") {
    throw "Source and output must both be DOCX files."
}
if ([System.StringComparer]::OrdinalIgnoreCase.Equals($source, $output)) {
    throw "Output must not overwrite the source fixture."
}
if (Test-Path -LiteralPath $output) {
    throw "Output already exists; remove the exact evidence file explicitly before regenerating."
}

$outputDirectory = [System.IO.Path]::GetDirectoryName($output)
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

$sourceArchive = [System.IO.Compression.ZipFile]::OpenRead($source)
$outputStream = [System.IO.File]::Open(
    $output,
    [System.IO.FileMode]::CreateNew,
    [System.IO.FileAccess]::Write,
    [System.IO.FileShare]::None)
$outputArchive = [System.IO.Compression.ZipArchive]::new(
    $outputStream,
    [System.IO.Compression.ZipArchiveMode]::Create,
    $false)
$documentUpdated = $false

try {
    foreach ($entry in $sourceArchive.Entries) {
        $newEntry = $outputArchive.CreateEntry(
            $entry.FullName,
            [System.IO.Compression.CompressionLevel]::Optimal)
        $newEntry.LastWriteTime = $entry.LastWriteTime
        $input = $entry.Open()
        $destination = $newEntry.Open()
        try {
            if ($entry.FullName -eq "word/document.xml") {
                $reader = [System.IO.StreamReader]::new(
                    $input,
                    [System.Text.UTF8Encoding]::new($false),
                    $true)
                try {
                    $xml = $reader.ReadToEnd()
                } finally {
                    $reader.Dispose()
                }
                if (-not $xml.Contains("</w:body>")) {
                    throw "word/document.xml has no w:body terminator."
                }
                $escaped = [System.Security.SecurityElement]::Escape($MaliciousText)
                $paragraph = "<w:p><w:r><w:t xml:space=`"preserve`">$escaped</w:t></w:r></w:p>"
                $updated = $xml.Replace("</w:body>", "$paragraph</w:body>")
                $writer = [System.IO.StreamWriter]::new(
                    $destination,
                    [System.Text.UTF8Encoding]::new($false))
                try {
                    $writer.Write($updated)
                    $writer.Flush()
                } finally {
                    $writer.Dispose()
                }
                $documentUpdated = $true
            } else {
                $input.CopyTo($destination)
            }
        } finally {
            $destination.Dispose()
            $input.Dispose()
        }
    }
} finally {
    $outputArchive.Dispose()
    $outputStream.Dispose()
    $sourceArchive.Dispose()
}

if (-not $documentUpdated) {
    throw "DOCX did not contain word/document.xml."
}

[pscustomobject]@{
    outputPath = $output
    sha256 = (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash.ToLowerInvariant()
    maliciousText = $MaliciousText
} | ConvertTo-Json -Compress
