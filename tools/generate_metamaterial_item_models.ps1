$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$modelRoot = Join-Path $repoRoot 'src/main/resources/assets/spectralization/models/item'
$textureRoot = Join-Path $repoRoot 'src/main/resources/assets/spectralization/textures'
$metamaterialTextureRoot = Join-Path $textureRoot 'metamaterial'
$itemMetamaterialTextureRoot = Join-Path $textureRoot 'item/metamaterial'
$generatedModelRoot = Join-Path $modelRoot 'metamaterial/generated'

function Write-Utf8NoBom([string] $path, [string] $text) {
    $tmp = "$path.tmp"
    if (Test-Path -LiteralPath $tmp) {
        Remove-Item -LiteralPath $tmp -Force
    }
    if (!$text.EndsWith("`n")) {
        $text = "$text`n"
    }
    $encoding = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($tmp, $text, $encoding)
    Move-Item -LiteralPath $tmp -Destination $path -Force
}

function Safe-Remove-Directory([string] $path, [string] $allowedRoot) {
    if (!(Test-Path -LiteralPath $path)) {
        return
    }

    $rootPath = (Resolve-Path -LiteralPath $allowedRoot).Path
    $targetPath = (Resolve-Path -LiteralPath $path).Path
    if (!$targetPath.StartsWith($rootPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove outside allowed root: $targetPath"
    }
    Remove-Item -LiteralPath $targetPath -Recurse -Force
}

function Sync-Item-TextureDirectory([string] $name) {
    $source = Join-Path $metamaterialTextureRoot $name
    $target = Join-Path $itemMetamaterialTextureRoot $name
    if (!(Test-Path -LiteralPath $source)) {
        throw "Missing generated metamaterial texture directory: $source"
    }

    Safe-Remove-Directory $target $textureRoot
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -LiteralPath $source -Destination $target -Recurse -Force
}

function New-TemplateFallbackModelJson() {
    return @'
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "spectralization:item/metamaterial/substrate/substrate_0",
    "layer1": "spectralization:item/metamaterial/corner/corner_0",
    "layer2": "spectralization:item/metamaterial/lattice_filter/substrate_0_lattice_0_filter_0",
    "layer3": "spectralization:item/metamaterial/corner/corner_0"
  }
}
'@
}

Safe-Remove-Directory $generatedModelRoot $modelRoot
Sync-Item-TextureDirectory 'substrate'
Sync-Item-TextureDirectory 'corner'
Sync-Item-TextureDirectory 'lattice_filter'

$templateModel = New-TemplateFallbackModelJson
Write-Utf8NoBom (Join-Path $modelRoot 'custom_metamaterial_template.json') $templateModel
Write-Utf8NoBom (Join-Path $modelRoot 'standard_metamaterial_template.json') $templateModel

Write-Output 'Prepared metamaterial item printing plates and fallback template models.'
