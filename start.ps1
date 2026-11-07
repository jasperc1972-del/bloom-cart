# 朵拉花店一鍵啟動腳本（由「啟動朵拉花店.bat」呼叫）
# 埠號、網址、帳密一律從 config\application.yml 讀取，本檔不寫任何連線資訊
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$Host.UI.RawUI.WindowTitle = '朵拉花店 Dora Floral（關閉此視窗即停止網站）'

function Fail($msg) {
    Write-Host ''
    Write-Host "✖ $msg" -ForegroundColor Red
    Read-Host '按 Enter 關閉'
    exit 1
}

Write-Host '🌷 朵拉花店 Dora Floral 啟動中...' -ForegroundColor Magenta

# 1. 讀取本機設定檔
$cfgPath = Join-Path $PSScriptRoot 'config\application.yml'
if (-not (Test-Path $cfgPath)) {
    Fail '找不到 config\application.yml，請先複製 application-example.yml 為 config\application.yml 並填入設定'
}
$cfg = Get-Content $cfgPath -Raw -Encoding UTF8
$m = [regex]::Match($cfg, '(?m)^\s+site-url:\s*(\S+)')
if (-not $m.Success -or $m.Groups[1].Value -notmatch '^https?://') {
    Fail 'config\application.yml 缺少 app.site-url（網站網址）'
}
$url = $m.Groups[1].Value.Trim('"', "'")

# 2. 網站已經在執行 → 直接開瀏覽器
function Test-Site {
    try { Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 2 | Out-Null; return $true } catch { return $false }
}
if (Test-Site) {
    Write-Host '網站已經在執行，直接開啟瀏覽器' -ForegroundColor Green
    Start-Process $url
    Start-Sleep -Seconds 2
    exit 0
}

# 3. 尋找 JDK 21（JAVA_HOME 或常見安裝位置）
$candidates = @()
if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
foreach ($base in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Zulu")) {
    if (Test-Path $base) {
        $candidates += Get-ChildItem $base -Directory -Filter 'jdk-21*' | Sort-Object Name -Descending | ForEach-Object FullName
    }
}
$javaHome = $candidates | Where-Object {
    (Test-Path (Join-Path $_ 'release')) -and ((Get-Content (Join-Path $_ 'release') -Raw) -match 'JAVA_VERSION="21')
} | Select-Object -First 1
if (-not $javaHome) { Fail '找不到 JDK 21，請先安裝（例如 winget install EclipseAdoptium.Temurin.21.JDK）' }
$env:JAVA_HOME = $javaHome
$java = Join-Path $javaHome 'bin\java.exe'
Write-Host "使用 JDK：$javaHome"

# 4. 沒有執行檔、或程式碼比執行檔新 → 重新編譯
# 重新讀取系統與使用者 PATH，剛安裝的 Maven 不用重開機也找得到
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
$jar = Join-Path $PSScriptRoot 'target\flowerstore-1.0.0.jar'
$needBuild = -not (Test-Path $jar)
if (-not $needBuild) {
    $jarTime = (Get-Item $jar).LastWriteTime
    $newer = Get-ChildItem (Join-Path $PSScriptRoot 'src'), (Join-Path $PSScriptRoot 'pom.xml') -Recurse -File |
             Where-Object { $_.LastWriteTime -gt $jarTime } | Select-Object -First 1
    $needBuild = [bool]$newer
}
if ($needBuild) {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { Fail '程式碼有更新需要重新編譯，但找不到 Maven（mvn）' }
    Write-Host '程式碼有更新，重新編譯中（約 10~30 秒）...' -ForegroundColor Yellow
    & mvn -q -B -DskipTests package
    if ($LASTEXITCODE -ne 0) { Fail '編譯失敗，請查看上方訊息' }
}

# 5. 背景等待網站就緒後自動開瀏覽器
$waitCmd = "for(`$i=0;`$i -lt 90;`$i++){try{Invoke-WebRequest '$url' -UseBasicParsing -TimeoutSec 2|Out-Null;Start-Process '$url';break}catch{Start-Sleep 1}}"
Start-Process powershell -WindowStyle Hidden -ArgumentList '-NoProfile', '-Command', $waitCmd

# 6. 在這個視窗啟動網站；關閉視窗就會停止網站
Write-Host ''
Write-Host '網站啟動中，完成後會自動開啟瀏覽器。' -ForegroundColor Green
Write-Host '要停止網站：直接關閉這個視窗。' -ForegroundColor Green
Write-Host ''
& $java -jar $jar
Read-Host '網站已停止，按 Enter 關閉'
