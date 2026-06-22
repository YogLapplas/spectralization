Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$assetRoot = Join-Path $repoRoot 'src/main/resources/assets/spectralization/textures/metamaterial'
$workRoot = Join-Path $repoRoot 'work'
$size = 32

function Safe-Remove-GeneratedDirectory([string] $name) {
    $target = Join-Path $assetRoot $name
    if (!(Test-Path -LiteralPath $target)) {
        return
    }

    $rootPath = (Resolve-Path -LiteralPath $assetRoot).Path
    $targetPath = (Resolve-Path -LiteralPath $target).Path
    if (!$targetPath.StartsWith($rootPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove outside metamaterial root: $targetPath"
    }
    Remove-Item -LiteralPath $targetPath -Recurse -Force
}

New-Item -ItemType Directory -Force -Path (Join-Path $assetRoot 'substrate') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $assetRoot 'corner') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $assetRoot 'lattice') | Out-Null
New-Item -ItemType Directory -Force -Path $workRoot | Out-Null
Safe-Remove-GeneratedDirectory 'lattice_filter'
Safe-Remove-GeneratedDirectory 'coupling'
Safe-Remove-GeneratedDirectory 'doping'
New-Item -ItemType Directory -Force -Path (Join-Path $assetRoot 'lattice_filter') | Out-Null

$palettes = @(
    @{ Name = 'graphite_amber'; Sub = @(@(28, 30, 32), @(70, 72, 72), @(166, 139, 86), @(230, 179, 74)); Corner = @(@(82, 62, 28), @(190, 141, 46), @(246, 208, 116)) },
    @{ Name = 'deep_aqua_silica'; Sub = @(@(24, 52, 62), @(64, 118, 128), @(132, 190, 194), @(70, 202, 194)); Corner = @(@(26, 86, 96), @(76, 188, 194), @(196, 244, 242)) },
    @{ Name = 'warm_ceramic'; Sub = @(@(92, 86, 76), @(178, 166, 142), @(248, 238, 204), @(130, 202, 190)); Corner = @(@(102, 84, 50), @(206, 168, 88), @(255, 232, 162)) },
    @{ Name = 'ruby_glass'; Sub = @(@(50, 10, 28), @(128, 34, 60), @(236, 92, 82), @(255, 174, 72)); Corner = @(@(92, 24, 36), @(214, 74, 64), @(255, 174, 94)) },
    @{ Name = 'fluorite_violet'; Sub = @(@(44, 28, 72), @(108, 72, 164), @(204, 168, 246), @(86, 226, 196)); Corner = @(@(70, 50, 130), @(156, 104, 232), @(224, 196, 255)) },
    @{ Name = 'deep_yag_olive'; Sub = @(@(28, 42, 20), @(68, 86, 34), @(116, 132, 56), @(188, 170, 78)); Corner = @(@(48, 62, 24), @(126, 132, 48), @(218, 202, 88)) },
    @{ Name = 'muted_rose'; Sub = @(@(66, 48, 58), @(126, 92, 106), @(186, 144, 154), @(226, 184, 188)); Corner = @(@(88, 58, 70), @(172, 116, 132), @(238, 178, 188)) },
    @{ Name = 'void_opal'; Sub = @(@(10, 12, 20), @(30, 38, 62), @(88, 84, 132), @(68, 214, 214)); Corner = @(@(20, 30, 58), @(86, 88, 160), @(128, 230, 224)) }
)

$latticeFilterSpecs = @(
    @{ Name = 'substrate_metal'; Offset = 0; Sat = 0.74; Contrast = 1.00 },
    @{ Name = 'cool_analog'; Offset = -24; Sat = 0.70; Contrast = 0.96 },
    @{ Name = 'warm_analog'; Offset = 24; Sat = 0.70; Contrast = 0.96 },
    @{ Name = 'wide_analog_cool'; Offset = -42; Sat = 0.54; Contrast = 0.90 },
    @{ Name = 'wide_analog_warm'; Offset = 42; Sat = 0.54; Contrast = 0.90 },
    @{ Name = 'subtle_split_a'; Offset = 72; Sat = 0.28; Contrast = 0.82 },
    @{ Name = 'subtle_split_b'; Offset = -72; Sat = 0.28; Contrast = 0.82 },
    @{ Name = 'low_chroma_complement'; Offset = 180; Sat = 0.16; Contrast = 0.74 }
)

function Save-Png($bitmap, [string] $path) {
    $tmp = "$path.tmp"
    if (Test-Path -LiteralPath $tmp) {
        Remove-Item -LiteralPath $tmp -Force
    }
    $bitmap.Save($tmp, [System.Drawing.Imaging.ImageFormat]::Png)
    Move-Item -LiteralPath $tmp -Destination $path -Force
}

function Rgba([int] $r, [int] $g, [int] $b, [int] $a = 255) {
    return [System.Drawing.Color]::FromArgb($a, $r, $g, $b)
}

function Mix($a, $b, [double] $t) {
    if ($t -lt 0) { $t = 0 }
    if ($t -gt 1) { $t = 1 }
    return @(
        [int] [Math]::Round($a[0] + ($b[0] - $a[0]) * $t),
        [int] [Math]::Round($a[1] + ($b[1] - $a[1]) * $t),
        [int] [Math]::Round($a[2] + ($b[2] - $a[2]) * $t)
    )
}

function Clamp01([double] $value) {
    if ($value -lt 0) { return 0 }
    if ($value -gt 1) { return 1 }
    return $value
}

function Wrap-Hue([double] $hue) {
    $wrapped = $hue % 360
    if ($wrapped -lt 0) {
        $wrapped += 360
    }
    return $wrapped
}

function Get-Luminance($rgb) {
    return ($rgb[0] * 0.2126 + $rgb[1] * 0.7152 + $rgb[2] * 0.0722) / 255.0
}

function Convert-RgbToHsl($rgb) {
    $r = $rgb[0] / 255.0
    $g = $rgb[1] / 255.0
    $b = $rgb[2] / 255.0
    $max = [Math]::Max($r, [Math]::Max($g, $b))
    $min = [Math]::Min($r, [Math]::Min($g, $b))
    $lightness = ($max + $min) / 2.0

    if ([Math]::Abs($max - $min) -lt 0.000001) {
        return @{ H = 0.0; S = 0.0; L = $lightness }
    }

    $delta = $max - $min
    $saturation = if ($lightness -gt 0.5) {
        $delta / (2.0 - $max - $min)
    } else {
        $delta / ($max + $min)
    }

    if ($max -eq $r) {
        $hue = 60.0 * ((($g - $b) / $delta) + $(if ($g -lt $b) { 6 } else { 0 }))
    } elseif ($max -eq $g) {
        $hue = 60.0 * ((($b - $r) / $delta) + 2)
    } else {
        $hue = 60.0 * ((($r - $g) / $delta) + 4)
    }
    return @{ H = (Wrap-Hue $hue); S = $saturation; L = $lightness }
}

function Convert-HueToRgb([double] $p, [double] $q, [double] $t) {
    if ($t -lt 0) { $t += 1 }
    if ($t -gt 1) { $t -= 1 }
    if ($t -lt (1.0 / 6.0)) { return $p + ($q - $p) * 6.0 * $t }
    if ($t -lt 0.5) { return $q }
    if ($t -lt (2.0 / 3.0)) { return $p + ($q - $p) * ((2.0 / 3.0) - $t) * 6.0 }
    return $p
}

function Convert-HslToRgb([double] $hue, [double] $saturation, [double] $lightness) {
    $h = (Wrap-Hue $hue) / 360.0
    $s = Clamp01 $saturation
    $l = Clamp01 $lightness
    if ($s -le 0.000001) {
        $v = [int] [Math]::Round($l * 255)
        return @($v, $v, $v)
    }

    $q = if ($l -lt 0.5) { $l * (1 + $s) } else { $l + $s - $l * $s }
    $p = 2 * $l - $q
    return @(
        [int] [Math]::Round((Convert-HueToRgb $p $q ($h + 1.0 / 3.0)) * 255),
        [int] [Math]::Round((Convert-HueToRgb $p $q $h) * 255),
        [int] [Math]::Round((Convert-HueToRgb $p $q ($h - 1.0 / 3.0)) * 255)
    )
}

function Get-LatticeFilterRamp([int] $substrateIndex, [int] $filterIndex) {
    $palette = $palettes[$substrateIndex]
    $seed = Mix $palette.Sub[1] $palette.Corner[1] 0.42
    $hsl = Convert-RgbToHsl $seed
    $spec = $latticeFilterSpecs[$filterIndex]
    $hue = Wrap-Hue ($hsl.H + $spec.Offset)
    $seedSat = [Math]::Max(0.28, $hsl.S)
    $saturation = [Math]::Min(0.74, [Math]::Max(0.22, $seedSat * $spec.Sat + 0.12))
    $baseLum = (Get-Luminance $palette.Sub[1]) * 0.45 + (Get-Luminance $palette.Sub[2]) * 0.55
    $midLightness = if ($baseLum -lt 0.28) { 0.62 } elseif ($baseLum -gt 0.58) { 0.46 } else { 0.54 }
    $contrast = $spec.Contrast
    return @(
        (Convert-HslToRgb $hue ($saturation * 0.95) ($midLightness - 0.20 * $contrast)),
        (Convert-HslToRgb $hue $saturation $midLightness),
        (Convert-HslToRgb $hue ($saturation * 0.82) ($midLightness + 0.17 * $contrast))
    )
}

function Colorize-Image([string] $sourcePath, [string] $destPath, $palette, [bool] $isCorner) {
    $source = [System.Drawing.Bitmap]::FromFile($sourcePath)
    if ($source.Width -ne $size -or $source.Height -ne $size) {
        $source.Dispose()
        throw "Expected 32x32 source: $sourcePath"
    }

    $target = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $size; $y++) {
        for ($x = 0; $x -lt $size; $x++) {
            $pixel = $source.GetPixel($x, $y)
            if ($pixel.A -eq 0) {
                $target.SetPixel($x, $y, [System.Drawing.Color]::Transparent)
                continue
            }

            $lum = ($pixel.R * 0.2126 + $pixel.G * 0.7152 + $pixel.B * 0.0722) / 255.0
            if ($isCorner) {
                $ramp = $palette.Corner
                if ($lum -lt 0.5) {
                    $rgb = Mix $ramp[0] $ramp[1] ($lum / 0.5)
                } else {
                    $rgb = Mix $ramp[1] $ramp[2] (($lum - 0.5) / 0.5)
                }
                $alpha = [Math]::Min(255, [int] [Math]::Round($pixel.A * 1.08))
            } else {
                $ramp = $palette.Sub
                if ($lum -lt 0.42) {
                    $rgb = Mix $ramp[0] $ramp[1] ($lum / 0.42)
                } elseif ($lum -lt 0.78) {
                    $rgb = Mix $ramp[1] $ramp[2] (($lum - 0.42) / 0.36)
                } else {
                    $rgb = Mix $ramp[2] $ramp[3] (($lum - 0.78) / 0.22)
                }
                $alpha = $pixel.A
            }
            $target.SetPixel($x, $y, (Rgba $rgb[0] $rgb[1] $rgb[2] $alpha))
        }
    }

    Save-Png $target $destPath
    $target.Dispose()
    $source.Dispose()
}

function Copy-Lattice([int] $sourceIndex, [int] $targetIndex) {
    $sourcePath = Join-Path $assetRoot "lattice$sourceIndex.png"
    if (!(Test-Path -LiteralPath $sourcePath)) {
        throw "Missing lattice source: $sourcePath"
    }
    Copy-Item -LiteralPath $sourcePath -Destination (Join-Path $assetRoot "lattice/lattice_$targetIndex.png") -Force
}

function Colorize-Lattice([int] $sourceIndex, [int] $targetIndex, [int] $substrateIndex, [int] $filterIndex) {
    $sourcePath = Join-Path $assetRoot "lattice$sourceIndex.png"
    if (!(Test-Path -LiteralPath $sourcePath)) {
        throw "Missing lattice source: $sourcePath"
    }

    $source = [System.Drawing.Bitmap]::FromFile($sourcePath)
    if ($source.Width -ne $size -or $source.Height -ne $size) {
        $source.Dispose()
        throw "Expected 32x32 source: $sourcePath"
    }

    $ramp = Get-LatticeFilterRamp $substrateIndex $filterIndex
    $target = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $size; $y++) {
        for ($x = 0; $x -lt $size; $x++) {
            $pixel = $source.GetPixel($x, $y)
            if ($pixel.A -eq 0) {
                $target.SetPixel($x, $y, [System.Drawing.Color]::Transparent)
                continue
            }

            $lum = ($pixel.R * 0.2126 + $pixel.G * 0.7152 + $pixel.B * 0.0722) / 255.0
            if ($lum -lt 0.56) {
                $rgb = Mix $ramp[0] $ramp[1] ($lum / 0.56)
            } else {
                $rgb = Mix $ramp[1] $ramp[2] (($lum - 0.56) / 0.44)
            }
            $target.SetPixel($x, $y, (Rgba $rgb[0] $rgb[1] $rgb[2] $pixel.A))
        }
    }

    Save-Png $target (Join-Path $assetRoot "lattice_filter/substrate_${substrateIndex}_lattice_${targetIndex}_filter_$filterIndex.png")
    $target.Dispose()
    $source.Dispose()
}

function Compose-Tile([int] $substrate, [int] $lattice, [int] $filter, [int] $corner) {
    $bitmap = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([System.Drawing.Color]::Transparent)
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half

    $paths = @(
        (Join-Path $assetRoot "substrate/substrate_$substrate.png"),
        (Join-Path $assetRoot "corner/corner_$corner.png"),
        (Join-Path $assetRoot "lattice_filter/substrate_${substrate}_lattice_${lattice}_filter_$filter.png"),
        (Join-Path $assetRoot "corner/corner_$corner.png")
    )

    foreach ($path in $paths) {
        $image = [System.Drawing.Image]::FromFile($path)
        $graphics.DrawImage($image, 0, 0, $size, $size)
        $image.Dispose()
    }

    $graphics.Dispose()
    return $bitmap
}

$substrateSource = Join-Path $assetRoot 'substrate1.png'
$cornerSource = Join-Path $assetRoot 'corner1.png'
if (!(Test-Path -LiteralPath $substrateSource)) { throw "Missing substrate source: $substrateSource" }
if (!(Test-Path -LiteralPath $cornerSource)) { throw "Missing corner source: $cornerSource" }

for ($id = 0; $id -lt 8; $id++) {
    Colorize-Image $substrateSource (Join-Path $assetRoot "substrate/substrate_$id.png") $palettes[$id] $false
    Colorize-Image $cornerSource (Join-Path $assetRoot "corner/corner_$id.png") $palettes[$id] $true
    Copy-Lattice ($id + 1) $id
}

for ($substrate = 0; $substrate -lt 8; $substrate++) {
    for ($shape = 0; $shape -lt 8; $shape++) {
        for ($filter = 0; $filter -lt 8; $filter++) {
            Colorize-Lattice ($shape + 1) $shape $substrate $filter
        }
    }
}

$scale = 5
$gap = 8
$labelHeight = 18
$atlasWidth = 8 * $size * $scale + 7 * $gap
$atlasHeight = 5 * ($size * $scale + $labelHeight) + 4 * $gap
$atlas = [System.Drawing.Bitmap]::new($atlasWidth, $atlasHeight, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($atlas)
$graphics.Clear([System.Drawing.Color]::FromArgb(255, 8, 9, 12))
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$font = [System.Drawing.Font]::new('Consolas', 9, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Point)
$brush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 226, 232, 228))
$rows = @('substrate', 'corner', 'lattice', 'filter', 'composed')

for ($row = 0; $row -lt $rows.Count; $row++) {
    $rowTop = $row * ($size * $scale + $labelHeight + $gap)
    $graphics.DrawString($rows[$row], $font, $brush, 2, $rowTop)
    for ($id = 0; $id -lt 8; $id++) {
        $x = $id * ($size * $scale + $gap)
        $y = $rowTop + $labelHeight
        if ($row -eq 0) {
            $image = [System.Drawing.Image]::FromFile((Join-Path $assetRoot "substrate/substrate_$id.png"))
        } elseif ($row -eq 1) {
            $image = [System.Drawing.Image]::FromFile((Join-Path $assetRoot "corner/corner_$id.png"))
        } elseif ($row -eq 2) {
            $image = [System.Drawing.Image]::FromFile((Join-Path $assetRoot "lattice/lattice_$id.png"))
        } elseif ($row -eq 3) {
            $image = [System.Drawing.Image]::FromFile((Join-Path $assetRoot "lattice_filter/substrate_${id}_lattice_0_filter_$id.png"))
        } else {
            $image = Compose-Tile $id $id $id $id
        }
        $graphics.DrawImage($image, $x, $y, $size * $scale, $size * $scale)
        $image.Dispose()
    }
}
$graphics.Dispose()
$font.Dispose()
$brush.Dispose()
Save-Png $atlas (Join-Path $workRoot 'metamaterial_layer_stack_atlas_preview.png')
$atlas.Dispose()

$comboScale = 4
$comboGap = 6
$comboWidth = 8 * $size * $comboScale + 7 * $comboGap
$comboHeight = 8 * $size * $comboScale + 7 * $comboGap
$combo = [System.Drawing.Bitmap]::new($comboWidth, $comboHeight, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($combo)
$graphics.Clear([System.Drawing.Color]::FromArgb(255, 8, 9, 12))
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
for ($substrate = 0; $substrate -lt 8; $substrate++) {
    for ($lattice = 0; $lattice -lt 8; $lattice++) {
        $filter = ($substrate + $lattice) % 8
        $corner = ($substrate * 3 + $lattice * 5) % 8
        $tile = Compose-Tile $substrate $lattice $filter $corner
        $x = $lattice * ($size * $comboScale + $comboGap)
        $y = $substrate * ($size * $comboScale + $comboGap)
        $graphics.DrawImage($tile, $x, $y, $size * $comboScale, $size * $comboScale)
        $tile.Dispose()
    }
}
$graphics.Dispose()
Save-Png $combo (Join-Path $workRoot 'metamaterial_layer_stack_combinations_preview.png')
$combo.Dispose()

Write-Output 'Generated substrate-harmony filtered lattice metamaterial layers.'
