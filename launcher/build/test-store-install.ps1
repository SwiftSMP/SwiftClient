# Installs the local TEST MSIX (dist\SwiftClient-Store.appx) as a Developer Mode loose-layout
# package and starts it, for checking the Store build on this PC. Needs Windows Developer Mode.
# Remove it again with:  Get-AppxPackage SwiftSMP.SwiftClientTest | Remove-AppxPackage
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$log = Join-Path $root 'dist\store-test-install.log'
$layout = Join-Path $root 'dist\store-test-layout'
"start $(Get-Date -Format s)" | Out-File $log -Encoding utf8
try {
  # The normal SwiftClient holds the single-instance lock: close any copy first.
  Get-Process SwiftClient -ErrorAction SilentlyContinue | Where-Object { $_.Path -like '*store-test-layout*' } | Stop-Process -Force
  Start-Sleep -Seconds 1
  Get-AppxPackage SwiftSMP.SwiftClientTest | Remove-AppxPackage
  if (Test-Path $layout) { Remove-Item $layout -Recurse -Force }
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  [System.IO.Compression.ZipFile]::ExtractToDirectory((Join-Path $root 'dist\SwiftClient-Store.appx'), $layout)
  # Package part names are URL-encoded (e.g. '+' is stored as %2B); a real install decodes them.
  Get-ChildItem $layout -Recurse | Where-Object Name -like '*%*' | Sort-Object { $_.FullName.Length } -Descending |
    ForEach-Object { Rename-Item -LiteralPath $_.FullName -NewName ([Uri]::UnescapeDataString($_.Name)) }
  # Package signature files only apply to the packed .appx, not a loose layout.
  Remove-Item (Join-Path $layout 'AppxSignature.p7x'), (Join-Path $layout '[Content_Types].xml'), (Join-Path $layout 'AppxBlockMap.xml') -ErrorAction SilentlyContinue
  Add-AppxPackage -Register (Join-Path $layout 'AppxManifest.xml')
  $pkg = Get-AppxPackage SwiftSMP.SwiftClientTest
  "installed $($pkg.PackageFullName)" | Out-File $log -Append -Encoding utf8
  "family $($pkg.PackageFamilyName)" | Out-File $log -Append -Encoding utf8
  "location $($pkg.InstallLocation)" | Out-File $log -Append -Encoding utf8
  Start-Process "shell:AppsFolder\$($pkg.PackageFamilyName)!SwiftClient"
  "launched" | Out-File $log -Append -Encoding utf8
} catch {
  "error $($_.Exception.Message)" | Out-File $log -Append -Encoding utf8
}
