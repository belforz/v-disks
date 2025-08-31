# Run extended Postman collection with Newman or fallback using Invoke-RestMethod
# Usage: .\run-postman-v2.ps1

$collection = "$(Split-Path -Parent $MyInvocation.MyCommand.Definition)\..\postman\v-disk-extended.postman_collection.json"
$env = "$(Split-Path -Parent $MyInvocation.MyCommand.Definition)\..\postman\v-disk.postman_environment.json"

# Load environment early so we can attempt login and provide token to Newman if available
$envJson = Get-Content $env -Raw | ConvertFrom-Json
$baseUrl = ($envJson.values | Where-Object {$_.key -eq 'baseUrl'}).value
$token = ($envJson.values | Where-Object {$_.key -eq 'token'}).value
$testEmail = ($envJson.values | Where-Object {$_.key -eq 'testEmail'}).value
$testPassword = ($envJson.values | Where-Object {$_.key -eq 'testPassword'}).value
# Optional admin credentials in environment to let the script use existing admin in DB
$adminEmail = ($envJson.values | Where-Object {$_.key -eq 'adminEmail'}).value
$adminPassword = ($envJson.values | Where-Object {$_.key -eq 'adminPassword'}).value
if (-not $adminEmail) { $adminEmail = 'admin@example.com' }
if (-not $adminPassword) { $adminPassword = '123456' }

# Helper: base64url -> base64 padding
function Add-Base64Padding {
    param($s)
    $remainder = $s.Length % 4
    if ($remainder -eq 2) { return $s + '==' }
    if ($remainder -eq 3) { return $s + '=' }
    if ($remainder -eq 0) { return $s }
    return $s
}

# Helper: decode JWT header and payload (no validation) for debugging
function Decode-Jwt {
    param([string]$jwt)
    if (-not $jwt) { Write-Host "No token to decode"; return }
    $parts = $jwt -split '\.'
    if ($parts.Count -lt 2) { Write-Host "Token does not look like a JWT"; return }
    try {
        $hdr = $parts[0]
        $pl = $parts[1]
        $hdrPadded = Add-Base64Padding($hdr.Replace('-', '+').Replace('_', '/'))
        $plPadded = Add-Base64Padding($pl.Replace('-', '+').Replace('_', '/'))
        $hdrJson = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($hdrPadded)) | ConvertFrom-Json
        $plJson = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($plPadded)) | ConvertFrom-Json
        Write-Host "JWT header:" ($hdrJson | ConvertTo-Json -Depth 5)
        Write-Host "JWT payload:" ($plJson | ConvertTo-Json -Depth 5)
    } catch {
        Write-Warning "Unable to decode JWT: $_"
    }
}

# Try to obtain a fresh token before running Newman (so the collection uses a current token)
try {
    $loginUrl = "$baseUrl/api/auth/login"
    $loginBody = @{ email = $testEmail; password = $testPassword } | ConvertTo-Json
    $loginResp = Invoke-RestMethod -Method Post -Uri $loginUrl -Body $loginBody -ContentType 'application/json' -ErrorAction Stop
    Write-Host "Login response:" ($loginResp | ConvertTo-Json -Depth 6)
    if ($loginResp -and $loginResp.data -and $loginResp.data.token) {
        $token = $loginResp.data.token.Trim()
        Write-Host "Obtained token for Newman (len=$($token.Length))"
        # Debug: show token and decode
        Write-Host "Token (prefix): $($token.Substring(0,[Math]::Min(60,$token.Length)))..."
        Decode-Jwt $token
        # Parse payload to check roles
        try {
            $parts = $token -split '\.'
            if ($parts.Count -ge 2) {
                $pl = $parts[1]
                $plPadded = Add-Base64Padding($pl.Replace('-', '+').Replace('_', '/'))
                $plJson = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($plPadded)) | ConvertFrom-Json
                if ($plJson.roles -and ($plJson.roles -contains 'ADMIN')) {
                    Write-Host "Token contains ADMIN role; using it for smoke checks"
                } else {
                    Write-Host "Token does not contain ADMIN role; attempting admin login to obtain admin token for protected checks"
                    try {
                        $adminLoginBody = @{ email = $adminEmail; password = $adminPassword } | ConvertTo-Json
                        $adminLoginResp = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/auth/login") -Body $adminLoginBody -ContentType 'application/json' -ErrorAction Stop
                        if ($adminLoginResp -and $adminLoginResp.data -and $adminLoginResp.data.token) {
                            $token = $adminLoginResp.data.token.Trim()
                            Write-Host "Replaced token with admin token (len=$($token.Length))"
                            Decode-Jwt $token
                            foreach ($v in $envJson.values) { if ($v.key -eq 'token') { $v.value = $token } }
                            $envJson | ConvertTo-Json -Depth 10 | Set-Content -Path $env -Encoding UTF8
                        } else {
                            Write-Warning "Admin login did not return a token; continuing with original token"
                        }
                    } catch {
                        Write-Warning "Admin login attempt failed: $($_.Exception.Message)"
                    }
                }
            }
        } catch {
            Write-Warning "Unable to parse token payload: $_"
        }
        # update env file token value for consistency
        foreach ($v in $envJson.values) { if ($v.key -eq 'token') { $v.value = $token } }
        $envJson | ConvertTo-Json -Depth 10 | Set-Content -Path $env -Encoding UTF8
    } else {
        Write-Warning "Login did not return a token; Newman will use token from environment if any"
    }
} catch {
    Write-Warning "Pre-newman login failed: $($_.Exception.Message) -- Newman will use token from environment if any"
}

# If we don't have a token yet, try admin login (useful for protected smoke checks)
if (-not $token -or $token -eq '') {
    try {
        Write-Host "No token from pre-login; attempting admin login with $adminEmail"
        $adminLoginBody = @{ email = $adminEmail; password = $adminPassword } | ConvertTo-Json
        $adminLoginResp = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/auth/login") -Body $adminLoginBody -ContentType 'application/json' -ErrorAction Stop
        if ($adminLoginResp -and $adminLoginResp.data -and $adminLoginResp.data.token) {
            $token = $adminLoginResp.data.token.Trim()
            Write-Host "Obtained admin token for smoke checks (len=$($token.Length))"
            Decode-Jwt $token
            # update env file token value for consistency
            foreach ($v in $envJson.values) { if ($v.key -eq 'token') { $v.value = $token } }
            $envJson | ConvertTo-Json -Depth 10 | Set-Content -Path $env -Encoding UTF8
        } else {
            Write-Warning "Admin login did not return a token; smoke checks may see 403 for protected endpoints"
        }
    } catch {
        Write-Warning "Admin login attempt failed: $($_.Exception.Message)"
    }
}

# Try Newman
if (Get-Command newman -ErrorAction SilentlyContinue) {
    Write-Host "Running extended collection with newman..."
    if ($token) {
        newman run $collection -e $env --env-var "token=$token" --reporters cli
    } else {
        newman run $collection -e $env --reporters cli
    }
}

# Prepare headers (used by smoke checks)
$headers = @{ 'Accept' = 'application/json' }
if ($token) { $headers['Authorization'] = "Bearer $token" }

# Function: run smoke checks (will be executed regardless of Newman availability)
function Run-SmokeChecks {
    param($baseUrl, $headers)

    try {
        Write-Host "GET $baseUrl/api/vinyls"
        $resp = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/vinyls") -Headers $headers -ErrorAction Stop
        Write-Host "Status: OK"
        Write-Host ($resp | ConvertTo-Json -Depth 5)
    } catch {
        Write-Error "Request failed: $($_.Exception.Message)"
        return
    }

    Write-Host "\n=== Additional smoke checks (extended) ==="

    # Try to use more extended flows: checkout marker test, orders payment approve, etc.
    try {
        $list = $resp.data
        if ($list -and $list.Count -gt 0) {
            $firstId = $list[0].id
            Write-Host "GET $baseUrl/api/vinyls/$firstId"
            $one = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/vinyls/$firstId") -Headers $headers -ErrorAction Stop
            Write-Host "Fetched vinyl:"; Write-Host ($one | ConvertTo-Json -Depth 5)
        } else {
            Write-Host "No vinyls returned; attempting to create a test vinyl (requires admin)"
            $vinylPayload = @{ title = "Smoke Test Vinyl"; artist = "Smoke Tester"; stock = 10; price = 9.9; coverPath = ""; gallery = @() } | ConvertTo-Json
            try {
                $createVinyl = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/vinyls") -Body $vinylPayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
                Write-Host "Created vinyl:"; Write-Host ($createVinyl | ConvertTo-Json -Depth 5)
                $firstId = $createVinyl.data.id
                $list = @($createVinyl.data)
            } catch {
                Write-Warning "Failed to create test vinyl: $($_.Exception.Message)"
            }
        }
    } catch {
        Write-Warning "Failed to fetch or create vinyl: $($_.Exception.Message)"
    }

    # Extended: search, users list, order creation and payment approve, checkout marker
    try {
        Write-Host "GET $baseUrl/api/vinyls/search?term=test"
        $search = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/vinyls/search?term=test") -Headers $headers -ErrorAction Stop
        Write-Host "Search results:"; Write-Host ($search | ConvertTo-Json -Depth 5)
    } catch {
        Write-Warning "Search failed: $($_.Exception.Message)"
    }

    try {
        Write-Host "GET $baseUrl/api/users (protected)"
        $users = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/users") -Headers $headers -ErrorAction Stop
        Write-Host "Users list:"; Write-Host ($users | ConvertTo-Json -Depth 5)
    } catch {
        Write-Warning "Users list failed (likely requires ADMIN or token): $($_.Exception.Message)"
    }

    try {
        # create or find user
        if (-not ($users -and $users.data -and $users.data.Count -gt 0)) {
            try {
                $userPayload = @{ name = "Smoke Tester"; email = "smoke+user@example.com"; password = "Pass123!" } | ConvertTo-Json
                Write-Host "POST $baseUrl/api/users -> creating test user"
                $createUser = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/users") -Body $userPayload -ContentType 'application/json' -ErrorAction Stop
                Write-Host "Created user:"; Write-Host ($createUser | ConvertTo-Json -Depth 5)
                $users = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/users") -Headers $headers -ErrorAction SilentlyContinue
            } catch {
                Write-Warning "Failed to create test user: $($_.Exception.Message)"
            }
        }

        if ($users -and $users.data -and $users.data.Count -gt 0 -and $list -and $list.Count -gt 0) {
            $userId = $users.data[0].id
            $vinylId = $list[0].id
            $orderPayload = @{ userId = $userId; vinylIds = @($vinylId) } | ConvertTo-Json
            Write-Host "POST $baseUrl/api/orders -> payload userId=$userId, vinylId=$vinylId"
            try {
                $orderResp = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/orders") -Body $orderPayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
                Write-Host "Order created:"; Write-Host ($orderResp | ConvertTo-Json -Depth 5)
                # attempt payment approve flow
                $ordersResp = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/orders") -Headers $headers -ErrorAction Stop
                if ($ordersResp -and $ordersResp.data -and $ordersResp.data.Count -gt 0) {
                    $order = $ordersResp.data[0]
                    $orderId = $order.id
                    $paymentId = [guid]::NewGuid().ToString()
                    $patchPayload = @{ paymentId = $paymentId } | ConvertTo-Json
                    Write-Host "PATCH $baseUrl/api/orders/$orderId -> set paymentId=$paymentId"
                    Invoke-RestMethod -Method Patch -Uri ("$baseUrl/api/orders/$orderId") -Body $patchPayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
                    Write-Host "POST $baseUrl/api/orders/payment/$paymentId/approve -> approving payment"
                    $approveResp = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/orders/payment/$paymentId/approve") -Headers $headers -ErrorAction Stop
                    Write-Host "Approve response:"; Write-Host ($approveResp | ConvertTo-Json -Depth 5)
                }
            } catch {
                Write-Warning "Order create or payment approve failed: $($_.Exception.Message)"
            }
        } else {
            Write-Host "Skipping order create because users or vinyls list missing"
        }
    } catch {
        Write-Warning "Order/payment flow failed: $($_.Exception.Message)"
    }

    # Checkout marker test (best-effort)
    try {
    $paymentIdTest = [guid]::NewGuid().ToString()
    # send a nested 'payload' object so CheckoutController.save will persist its entries
    $checkoutPayload = @{ paymentId = $paymentIdTest; payload = @{ userId = "test-user"; vinylIds = @() } } | ConvertTo-Json
        Write-Host "POST $baseUrl/api/checkout -> save payload"
        try {
            Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/checkout") -Body $checkoutPayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
        } catch {
            # If forbidden, try admin login and retry once
            if ($_.Exception.Message -like '*403*' -or ($_.Exception.Response -and $_.Exception.Response.StatusCode -eq 403)) {
                Write-Warning "Checkout POST returned 403; attempting admin login to retry"
                try {
                    $adminLoginBody = @{ email = $adminEmail; password = $adminPassword } | ConvertTo-Json
                    $adminLoginResp = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/auth/login") -Body $adminLoginBody -ContentType 'application/json' -ErrorAction Stop
                    if ($adminLoginResp -and $adminLoginResp.data -and $adminLoginResp.data.token) {
                        $token = $adminLoginResp.data.token.Trim()
                        Write-Host "Obtained admin token (len=$($token.Length)) for checkout retry"
                        $headers['Authorization'] = "Bearer $token"
                        foreach ($v in $envJson.values) { if ($v.key -eq 'token') { $v.value = $token } }
                        $envJson | ConvertTo-Json -Depth 10 | Set-Content -Path $env -Encoding UTF8
                        Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/checkout") -Body $checkoutPayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
                    } else {
                        Write-Warning "Admin login did not return a token; checkout POST will be skipped"
                        throw $_
                    }
                } catch {
                    Write-Warning "Admin retry for checkout POST failed: $($_.Exception.Message)"
                    throw $_
                }
            } else {
                throw $_
            }
        }

        Write-Host "GET $baseUrl/api/checkout/$paymentIdTest -> verify"
        $ck = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/checkout/$paymentIdTest") -Headers $headers -ErrorAction Stop
        Write-Host "Checkout payload:"; Write-Host ($ck | ConvertTo-Json -Depth 5)
        Write-Host "DELETE $baseUrl/api/checkout/$paymentIdTest -> clear"
        Invoke-RestMethod -Method Delete -Uri ("$baseUrl/api/checkout/$paymentIdTest") -Headers $headers -ErrorAction Stop
        Write-Host "Cleared checkout payload"
    } catch {
        Write-Warning "Checkout marker flow failed (requires Redis and endpoints): $($_.Exception.Message)"
    }
}

# Run smoke checks now (will print to console)
Run-SmokeChecks -baseUrl $baseUrl -headers $headers
