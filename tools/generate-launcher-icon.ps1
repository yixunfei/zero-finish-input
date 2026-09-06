[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$sourcePath = Join-Path $repositoryRoot "logo.jpg"
$outputDirectory = Join-Path $repositoryRoot "app\src\main\res\drawable-nodpi"
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

$source = [System.Drawing.Image]::FromFile($sourcePath)
$bitmap = [System.Drawing.Bitmap]::new(432, 432)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
try {
    if ($source.Width -ne 1024 -or $source.Height -ne 984) {
        throw "The launcher crop requires the original 1024 x 984 logo.jpg."
    }
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    # Keep the subject inside adaptive icon masks and omit the small caption.
    $sourceRectangle = [System.Drawing.Rectangle]::new(40, 0, 944, 944)
    $destinationRectangle = [System.Drawing.Rectangle]::new(0, 0, 432, 432)
    $graphics.DrawImage($source, $destinationRectangle, $sourceRectangle, [System.Drawing.GraphicsUnit]::Pixel)
    $bitmap.Save((Join-Path $outputDirectory "launcher_artwork.png"), [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $graphics.Dispose()
    $bitmap.Dispose()
    $source.Dispose()
}
Write-Output "Launcher artwork generated from logo.jpg."
