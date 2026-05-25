param(
  [string]$WorkspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path,
  [string]$CanonicalRoot = "catchpro",
  [switch]$Json
)

$ErrorActionPreference = "Stop"

function Get-RelativePath {
  param(
    [string]$BasePath,
    [string]$TargetPath
  )
  $baseUri = [Uri]((Resolve-Path -LiteralPath $BasePath).Path.TrimEnd('\') + '\')
  $targetUri = [Uri]((Resolve-Path -LiteralPath $TargetPath).Path)
  [Uri]::UnescapeDataString($baseUri.MakeRelativeUri($targetUri).ToString()).Replace('/', '\')
}

function Get-SourceFiles {
  param([string]$Path)
  if (!(Test-Path -LiteralPath $Path)) {
    return @()
  }
  Get-ChildItem -LiteralPath $Path -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object {
      $_.FullName -notmatch '\\build\\' -and
      $_.FullName -notmatch '\\\.gradle\\' -and
      $_.FullName -notmatch '\\\.kotlin\\' -and
      $_.FullName -notmatch '\\node_modules\\'
    }
}

Set-Location -LiteralPath $WorkspaceRoot

$shadowPairs = @(
  @{ Shadow = "app"; Canonical = "$CanonicalRoot/app" },
  @{ Shadow = "docs"; Canonical = "$CanonicalRoot/docs" },
  @{ Shadow = "gradle"; Canonical = "$CanonicalRoot/gradle" },
  @{ Shadow = "server"; Canonical = "$CanonicalRoot/server" }
)

$result = foreach ($pair in $shadowPairs) {
  $shadowPath = Join-Path $WorkspaceRoot $pair.Shadow
  $canonicalPath = Join-Path $WorkspaceRoot $pair.Canonical
  if (!(Test-Path -LiteralPath $shadowPath) -or !(Test-Path -LiteralPath $canonicalPath)) {
    continue
  }

  $differences = New-Object System.Collections.Generic.List[object]
  $shadowFiles = Get-SourceFiles -Path $shadowPath
  foreach ($shadowFile in $shadowFiles) {
    $relative = Get-RelativePath -BasePath $shadowPath -TargetPath $shadowFile.FullName
    $canonicalFile = Join-Path $canonicalPath $relative
    if (!(Test-Path -LiteralPath $canonicalFile)) {
      $differences.Add([ordered]@{
        kind = "only_shadow"
        path = Join-Path $pair.Shadow $relative
      })
      continue
    }
    $shadowHash = (Get-FileHash -LiteralPath $shadowFile.FullName -Algorithm SHA256).Hash
    $canonicalHash = (Get-FileHash -LiteralPath $canonicalFile -Algorithm SHA256).Hash
    if ($shadowHash -ne $canonicalHash) {
      $differences.Add([ordered]@{
        kind = "different"
        path = Join-Path $pair.Shadow $relative
      })
    }
  }

  $canonicalFiles = Get-SourceFiles -Path $canonicalPath
  foreach ($canonicalFile in $canonicalFiles) {
    $relative = Get-RelativePath -BasePath $canonicalPath -TargetPath $canonicalFile.FullName
    $shadowFile = Join-Path $shadowPath $relative
    if (!(Test-Path -LiteralPath $shadowFile)) {
      $differences.Add([ordered]@{
        kind = "only_canonical"
        path = Join-Path $pair.Canonical $relative
      })
    }
  }

  [ordered]@{
    shadow = $pair.Shadow
    canonical = $pair.Canonical
    differenceCount = $differences.Count
    differences = $differences
  }
}

if ($Json) {
  $result | ConvertTo-Json -Depth 6
  exit 0
}

foreach ($item in $result) {
  Write-Host "[$($item.shadow) -> $($item.canonical)] differences=$($item.differenceCount)"
  $item.differences | Select-Object -First 40 | ForEach-Object {
    Write-Host "  $($_.kind) $($_.path)"
  }
}
