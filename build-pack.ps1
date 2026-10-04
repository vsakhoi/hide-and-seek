# Build resource pack súng lục từ "Create Guns 1.2.2 - 1.19.2.zip" -> HideSeekGun.zip
# Chạy: powershell -ExecutionPolicy Bypass -File build-pack.ps1
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$src  = Join-Path $env:TEMP "cg_src"
$out  = Join-Path $root "resourcepack"
$zip  = Join-Path $root "HideSeekGun.zip"

Remove-Item $src, $out -Recurse -Force -ErrorAction SilentlyContinue
Expand-Archive (Join-Path $root "Create Guns 1.2.2 - 1.19.2.zip") $src

$ns = "$out\assets\hideseek"
New-Item -ItemType Directory -Force "$ns\models\item", "$ns\items", "$ns\textures\item", "$ns\sounds\gun" | Out-Null

# --- model: đổi tham chiếu texture sang namespace hideseek ---
$model = Get-Content "$src\assets\cgm\models\special\gun\pistol.json" -Raw
$model = $model.Replace("cgm:items/3d_textures/pistol", "hideseek:item/pistol")
$model = $model.Replace("cgm:items/3d_textures/iron_sights", "hideseek:item/iron_sights")
# thêm display (cầm tay) lấy từ pistol.json gốc của mod
$disp = (Get-Content "$src\assets\cgm\models\item\pistol.json" -Raw | ConvertFrom-Json).display | ConvertTo-Json -Depth 5 -Compress
$model = $model.TrimEnd().TrimEnd('}') + ",`n`"display`": $disp`n}"
[IO.File]::WriteAllText("$ns\models\item\pistol.json", $model, (New-Object Text.UTF8Encoding $false))

Copy-Item "$src\assets\cgm\textures\items\3d_textures\pistol.png" "$ns\textures\item\pistol.png"
Copy-Item "$src\assets\cgm\textures\items\3d_textures\iron_sights.png" "$ns\textures\item\iron_sights.png"
Copy-Item "$src\assets\cgm\sounds\item\pistol\fire.ogg" "$ns\sounds\gun\fire.ogg"

# --- item definition (1.21.4+): dùng với item_model = hideseek:pistol ---
[IO.File]::WriteAllText("$ns\items\pistol.json",
 '{"model":{"type":"minecraft:model","model":"hideseek:item/pistol"}}', (New-Object Text.UTF8Encoding $false))

# --- sound ---
[IO.File]::WriteAllText("$ns\sounds.json",
 '{"gun.fire":{"sounds":[{"name":"hideseek:gun/fire"}]}}', (New-Object Text.UTF8Encoding $false))

# --- pack.mcmeta (1.21.11 = format 75; cho phép dải rộng để dùng được trên bản mới hơn) ---
[IO.File]::WriteAllText("$out\pack.mcmeta",
 '{"pack":{"pack_format":75,"min_format":75,"max_format":999,"supported_formats":{"min_inclusive":34,"max_inclusive":999},"description":"HideSeek Gun"}}',
 (New-Object Text.UTF8Encoding $false))

Remove-Item $zip -Force -ErrorAction SilentlyContinue
# Không dùng Compress-Archive: nó ghi đường dẫn bằng '\' nên Minecraft không đọc được pack
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$za = [IO.Compression.ZipFile]::Open($zip, [IO.Compression.ZipArchiveMode]::Create)
Get-ChildItem $out -Recurse -File | ForEach-Object {
    $rel = $_.FullName.Substring($out.Length + 1).Replace('\', '/')
    [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($za, $_.FullName, $rel)
}
$za.Dispose()
Write-Host "OK -> $zip"
Write-Host ("SHA1 = " + (Get-FileHash $zip -Algorithm SHA1).Hash.ToLower())
