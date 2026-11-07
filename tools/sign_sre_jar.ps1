# SRE JAR 密钥签名工具（PowerShell 版，与 sign_sre_jar.py 等效；适用于无 Python 的主机）
# 用法: powershell -File tools/sign_sre_jar.ps1 build/libs/star_rail_express-x.y.z.jar [-KeyFile tools/sre_auth_key.secret]
# 在 jar 中嵌入认证密钥 sre_auth_key.txt；服务端配置 "ENABLE_JAR_KEY_AUTH": true 后，
# 服务端与所有客户端必须运行同一份签名 jar（详见 docs/api.md「客户端 JAR 密钥认证」）。
param(
    [Parameter(Mandatory = $true)][string]$Jar,
    [string]$KeyFile = (Join-Path $PSScriptRoot 'sre_auth_key.secret')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not (Test-Path $Jar)) { Write-Error "找不到 jar 文件: $Jar" }

if (Test-Path $KeyFile) {
    $key = (Get-Content $KeyFile -Raw).Trim()
    Write-Output "使用现有密钥文件: $KeyFile"
} else {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $key = ($bytes | ForEach-Object { $_.ToString('x2') }) -join ''
    [System.IO.File]::WriteAllText($KeyFile, $key + "`n")
    Write-Output "已生成新密钥并保存到: $KeyFile"
    Write-Output "!! 请妥善保管该文件，且不要提交到版本库 !!"
}

$zip = [System.IO.Compression.ZipFile]::Open((Resolve-Path $Jar).Path, 'Update')
try {
    $existing = $zip.GetEntry('sre_auth_key.txt')
    if ($null -ne $existing) { $existing.Delete() }
    $entry = $zip.CreateEntry('sre_auth_key.txt')
    $writer = New-Object System.IO.StreamWriter($entry.Open())
    try { $writer.Write($key + "`n") } finally { $writer.Dispose() }
} finally {
    $zip.Dispose()
}

Write-Output "签名完成: $Jar"
Write-Output '请将该 jar 同时部署到服务端与所有客户端，并在服务端配置中开启:'
Write-Output '  "ENABLE_JAR_KEY_AUTH": true   (config/starrailexpress-config.json)'
