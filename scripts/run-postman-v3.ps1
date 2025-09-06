# Run Postman email tests v3
# Usage: .\run-postman-v3.ps1

$collection = "$(Split-Path -Parent $MyInvocation.MyCommand.Definition)\..\postman\v-disk-extended-v3.postman_collection.json"
$env = "$(Split-Path -Parent $MyInvocation.MyCommand.Definition)\..\postman\v-disk.postman_environment.json"

$envJson = Get-Content $env -Raw | ConvertFrom-Json
$baseUrl = ($envJson.values | Where-Object {$_.key -eq 'baseUrl'}).value
$testEmail = ($envJson.values | Where-Object {$_.key -eq 'testEmail'}).value
$testPassword = ($envJson.values | Where-Object {$_.key -eq 'testPassword'}).value
$token = ($envJson.values | Where-Object {$_.key -eq 'token'}).value
$adminEmail = ($envJson.values | Where-Object {$_.key -eq 'adminEmail'}).value
$adminPassword = ($envJson.values | Where-Object {$_.key -eq 'adminPassword'}).value

# Try to login to refresh token (non-admin)
try {
    $loginBody = @{ email = $testEmail; password = $testPassword } | ConvertTo-Json
    $resp = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/auth/login") -Body $loginBody -ContentType 'application/json' -ErrorAction Stop
    if ($resp -and $resp.data -and $resp.data.token) {
        $token = $resp.data.token.Trim()
        foreach ($v in $envJson.values) { if ($v.key -eq 'token') { $v.value = $token } }
        $envJson | ConvertTo-Json -Depth 10 | Set-Content -Path $env -Encoding UTF8
        Write-Host "Refreshed token"
    }
} catch {
    Write-Warning "Login failed: $($_.Exception.Message)"
}

# Run collection with newman if present
if (Get-Command newman -ErrorAction SilentlyContinue) {
    Write-Host "Running email tests with newman..."
    newman run $collection -e $env --reporters cli
    exit
}

# Fallback: call endpoints directly
    $headers = @{ 'Accept' = 'application/json' }
if ($token) { $headers['Authorization'] = "Bearer $token" }

# Skipping public/test/send to focus only on change-password flow
Write-Host "GET $baseUrl/api/mail/public"
try { $p = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/mail/public") -Headers $headers -ErrorAction Stop; Write-Host ($p | ConvertTo-Json -Depth 5) } catch { Write-Warning "public mail failed: $($_.Exception.Message)" }

Write-Host "POST $baseUrl/api/mail/test?to=$testEmail"
try { $t = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/mail/test?to=$testEmail") -Headers $headers -ErrorAction Stop; Write-Host ($t | ConvertTo-Json -Depth 5) } catch { Write-Warning "mail test failed: $($_.Exception.Message)" }

Write-Host "POST $baseUrl/api/mail/send?to=$testEmail&subject=Smoke+Subject&body=Hello"
try { $s = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/mail/send?to=$testEmail&subject=Smoke+Subject&body=Hello") -Headers $headers -ErrorAction Stop; Write-Host ($s | ConvertTo-Json -Depth 5) } catch { Write-Warning "mail send failed: $($_.Exception.Message)" }

Write-Host "POST $baseUrl/api/mail/change-password?to=$testEmail"
try { $c = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/mail/change-password?to=$testEmail") -Headers $headers -ErrorAction Stop; Write-Host ($c | ConvertTo-Json -Depth 5) } catch { Write-Warning "change-password failed: $($_.Exception.Message)" }
