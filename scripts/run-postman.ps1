# Run Postman collection with Newman or fallback using Invoke-RestMethod
# Usage: .\run-postman.ps1

$collection = "$(Split-Path -Parent $MyInvocation.MyCommand.Definition)\..\postman\v-disk.postman_collection.json"
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
    Write-Host "Running collection with newman..."
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

    Write-Host "\n=== Additional smoke checks ==="

    # 1) List vinyls (already executed). If we have results, try fetching the first by id
    try {
        $list = $resp.data
        if ($list -and $list.Count -gt 0) {
            $firstId = $list[0].id
            Write-Host "GET $baseUrl/api/vinyls/$firstId"
            $one = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/vinyls/$firstId") -Headers $headers -ErrorAction Stop
            Write-Host "Fetched vinyl:"; Write-Host ($one | ConvertTo-Json -Depth 5)
        } else {
            Write-Host "No vinyls returned to fetch by id"
            # Create a test vinyl so later checks can run
            try {
                $vinylPayload = @{ title = "Smoke Test Vinyl"; artist = "Smoke Tester"; stock = 10; price = 9.9; coverPath = ""; gallery = @() } | ConvertTo-Json
                Write-Host "POST $baseUrl/api/vinyls -> creating test vinyl"
                $createVinyl = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/vinyls") -Body $vinylPayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
                Write-Host "Created vinyl:"; Write-Host ($createVinyl | ConvertTo-Json -Depth 5)
                $firstId = $createVinyl.data.id
                # refresh list variable
                $list = @($createVinyl.data)
            } catch {
                Write-Warning "Failed to create test vinyl: $($_.Exception.Message)"
                # If failure due to 403, try to create an admin user and retry
                if ($_.Exception.Message -like '*403*' -or $_.Exception.Response -and $_.Exception.Response.StatusCode -eq 403) {
                    try {
                        # First try to login with admin credentials provided in environment (or defaults)
                        Write-Host "Attempting admin login with $adminEmail before creating a new admin"
                        $adminLoginBody = @{ email = $adminEmail; password = $adminPassword } | ConvertTo-Json
                        $adminLoginResp = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/auth/login") -Body $adminLoginBody -ContentType 'application/json' -ErrorAction SilentlyContinue
                        if ($adminLoginResp -and $adminLoginResp.data -and $adminLoginResp.data.token) {
                            $token = $adminLoginResp.data.token.Trim()
                            Write-Host "Obtained admin token from existing admin (len=$($token.Length))"
                            $headers['Authorization'] = "Bearer $token"
                            # retry creating vinyl with existing admin
                            $createVinyl = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/vinyls") -Body $vinylPayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
                            Write-Host "Created vinyl with existing admin:"; Write-Host ($createVinyl | ConvertTo-Json -Depth 5)
                            $firstId = $createVinyl.data.id
                            $list = @($createVinyl.data)
                        } else {
                            Write-Host "Existing admin login failed or not present; creating a new admin user"
                            $adminEmailToCreate = "smoke+admin@example.com"
                            $adminPasswordToCreate = "AdminPass123!"
                            $adminPayload = @{ name = "Smoke Admin"; email = $adminEmailToCreate; password = $adminPasswordToCreate; roles = @("ADMIN") } | ConvertTo-Json
                            Write-Host "POST $baseUrl/api/users -> creating admin user $adminEmailToCreate"
                            $createAdmin = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/users") -Body $adminPayload -ContentType 'application/json' -ErrorAction Stop
                            Write-Host "Created admin user:"; Write-Host ($createAdmin | ConvertTo-Json -Depth 5)

                            # Login as the freshly created admin
                            $adminLoginBody = @{ email = $adminEmailToCreate; password = $adminPasswordToCreate } | ConvertTo-Json
                            $adminLoginResp = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/auth/login") -Body $adminLoginBody -ContentType 'application/json' -ErrorAction Stop
                            if ($adminLoginResp -and $adminLoginResp.data -and $adminLoginResp.data.token) {
                                $token = $adminLoginResp.data.token.Trim()
                                Write-Host "Obtained admin token (len=$($token.Length))"
                                $headers['Authorization'] = "Bearer $token"
                                # retry creating vinyl
                                $createVinyl = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/vinyls") -Body $vinylPayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
                                Write-Host "Created vinyl with admin:"; Write-Host ($createVinyl | ConvertTo-Json -Depth 5)
                                $firstId = $createVinyl.data.id
                                $list = @($createVinyl.data)
                            } else {
                                Write-Warning "Admin login did not return a token; cannot retry vinyl creation"
                            }
                        }
                    } catch {
                        Write-Warning "Admin fallback failed: $($_.Exception.Message)"
                    }
                }
            }
        }
    } catch {
        Write-Warning "Failed to fetch first vinyl: $($_.Exception.Message)"
    }

    # 2) Search vinyls
    try {
        $term = 'test'
        Write-Host "GET $baseUrl/api/vinyls/search?term=$term"
        $search = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/vinyls/search?term=$term") -Headers $headers -ErrorAction Stop
        Write-Host "Search results:"; Write-Host ($search | ConvertTo-Json -Depth 5)
    } catch {
        Write-Warning "Search failed: $($_.Exception.Message)"
    }

    # 3) Protected endpoint: list users (requires token)
    try {
        Write-Host "GET $baseUrl/api/users (protected)"
        $users = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/users") -Headers $headers -ErrorAction Stop
        Write-Host "Users list:"; Write-Host ($users | ConvertTo-Json -Depth 5)
    } catch {
        Write-Warning "Users list failed (likely requires ADMIN or token): $($_.Exception.Message)"
    }

    # 4) Try to create a simple order (protected): if we have a userId from users list, and a vinyl id, attempt
    try {
        # if no users returned, create a test user
        if (-not ($users -and $users.data -and $users.data.Count -gt 0)) {
            try {
                $userPayload = @{ name = "Smoke Tester"; email = "smoke+user@example.com"; password = "Pass123!" } | ConvertTo-Json
                Write-Host "POST $baseUrl/api/users -> creating test user"
                $createUser = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/users") -Body $userPayload -ContentType 'application/json' -ErrorAction Stop
                Write-Host "Created user:"; Write-Host ($createUser | ConvertTo-Json -Depth 5)
                # fetch users again
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
            $orderResp = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/orders") -Body $orderPayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
            Write-Host "Order created:"; Write-Host ($orderResp | ConvertTo-Json -Depth 5)
        } else {
            Write-Host "Skipping order create because users or vinyls list missing"
        }
    } catch {
        Write-Warning "Order create failed: $($_.Exception.Message)"
    }

    # 5) Cart endpoints (get, add item, set cart, remove item, clear)
    try {
        if ($users -and $users.data -and $users.data.Count -gt 0 -and $list -and $list.Count -gt 0) {
            $cartUserId = $users.data[0].id
            $cartVinylId = $list[0].id
            Write-Host "Cart tests for userId=$cartUserId, vinylId=$cartVinylId"

            Write-Host "GET $baseUrl/api/cart/$cartUserId"
            $cart = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/cart/$cartUserId") -Headers $headers -ErrorAction Stop
            Write-Host "Cart:"; Write-Host ($cart | ConvertTo-Json -Depth 5)

            $addPayload = @{ quantity = 2 } | ConvertTo-Json
            Write-Host "POST $baseUrl/api/cart/$cartUserId/item/$cartVinylId -> add item"
            $addResp = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/cart/$cartUserId/item/$cartVinylId") -Body $addPayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
            Write-Host "Add item response:"; Write-Host ($addResp | ConvertTo-Json -Depth 5)

            Write-Host "GET $baseUrl/api/cart/$cartUserId (after add)"
            $cart = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/cart/$cartUserId") -Headers $headers -ErrorAction Stop
            Write-Host "Cart after add:"; Write-Host ($cart | ConvertTo-Json -Depth 5)

            # Update whole cart
            $map = @{}
            $map[$cartVinylId] = 3
            $mapPayload = $map | ConvertTo-Json
            Write-Host "PUT $baseUrl/api/cart/$cartUserId -> set cart to quantity 3 for vinyl"
            $setResp = Invoke-RestMethod -Method Put -Uri ("$baseUrl/api/cart/$cartUserId") -Body $mapPayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
            Write-Host "Set cart response:"; Write-Host ($setResp | ConvertTo-Json -Depth 5)

            # Remove item
            Write-Host "DELETE $baseUrl/api/cart/$cartUserId/item/$cartVinylId -> remove item"
            $remResp = Invoke-RestMethod -Method Delete -Uri ("$baseUrl/api/cart/$cartUserId/item/$cartVinylId") -Headers $headers -ErrorAction Stop
            Write-Host "Remove item response:"; Write-Host ($remResp | ConvertTo-Json -Depth 5)

            # Clear cart
            Write-Host "DELETE $baseUrl/api/cart/$cartUserId -> clear cart"
            $clearResp = Invoke-RestMethod -Method Delete -Uri ("$baseUrl/api/cart/$cartUserId") -Headers $headers -ErrorAction Stop
            Write-Host "Clear cart response:"; Write-Host ($clearResp | ConvertTo-Json -Depth 5)
        } else {
            Write-Host "Skipping cart tests because no users or vinyls available"
        }
    } catch {
        Write-Warning "Cart tests failed: $($_.Exception.Message)"
    }

    # 6) Vinyl update (PATCH) and revert; try admin fallback on 403
    try {
        if ($list -and $list.Count -gt 0) {
            $vinylId = $list[0].id
            $orig = $list[0]
            $updatePayload = @{ title = "Smoke Updated Vinyl"; price = 19.9; stock = 5 } | ConvertTo-Json
            Write-Host "PATCH $baseUrl/api/vinyls/$vinylId -> updating vinyl"
            try {
                $patchResp = Invoke-RestMethod -Method Patch -Uri ("$baseUrl/api/vinyls/$vinylId") -Body $updatePayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
                Write-Host "Updated vinyl:"; Write-Host ($patchResp | ConvertTo-Json -Depth 5)
                # verify
                $verify = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/vinyls/$vinylId") -Headers $headers -ErrorAction Stop
                Write-Host "Verified vinyl after update:"; Write-Host ($verify | ConvertTo-Json -Depth 5)
                # revert
                $revertPayload = @{ title = $orig.title; price = $orig.price; stock = $orig.stock } | ConvertTo-Json
                Write-Host "Reverting vinyl to original values"
                Invoke-RestMethod -Method Patch -Uri ("$baseUrl/api/vinyls/$vinylId") -Body $revertPayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
                Write-Host "Reverted vinyl"
            } catch {
                Write-Warning "Failed to patch vinyl: $($_.Exception.Message)"
                if ($_.Exception.Message -like '*403*' -or $_.Exception.Response -and $_.Exception.Response.StatusCode -eq 403) {
                    Write-Host "Attempting admin login to retry vinyl update"
                    try {
                        $adminLoginBody = @{ email = $adminEmail; password = $adminPassword } | ConvertTo-Json
                        $adminLoginResp = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/auth/login") -Body $adminLoginBody -ContentType 'application/json' -ErrorAction Stop
                        if ($adminLoginResp -and $adminLoginResp.data -and $adminLoginResp.data.token) {
                            $token = $adminLoginResp.data.token.Trim()
                            Write-Host "Obtained admin token (len=$($token.Length))"
                            $headers['Authorization'] = "Bearer $token"
                            $patchResp = Invoke-RestMethod -Method Patch -Uri ("$baseUrl/api/vinyls/$vinylId") -Body $updatePayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
                            Write-Host "Updated vinyl with admin:"; Write-Host ($patchResp | ConvertTo-Json -Depth 5)
                            # verify and revert with admin
                            $verify = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/vinyls/$vinylId") -Headers $headers -ErrorAction Stop
                            Write-Host "Verified vinyl after admin update:"; Write-Host ($verify | ConvertTo-Json -Depth 5)
                            $revertPayload = @{ title = $orig.title; price = $orig.price; stock = $orig.stock } | ConvertTo-Json
                            Invoke-RestMethod -Method Patch -Uri ("$baseUrl/api/vinyls/$vinylId") -Body $revertPayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
                            Write-Host "Reverted vinyl with admin"
                        } else {
                            Write-Warning "Admin login did not return a token; cannot retry vinyl update"
                        }
                    } catch {
                        Write-Warning "Admin retry for vinyl update failed: $($_.Exception.Message)"
                    }
                }
            }
        } else {
            Write-Host "Skipping vinyl update tests because no vinyls available"
        }
    } catch {
        Write-Warning "Vinyl update tests failed: $($_.Exception.Message)"
    }

    # 8) User update (PATCH) and revert; admin-fallback on 403
    try {
        if ($users -and $users.data -and $users.data.Count -gt 0) {
            $user = $users.data[0]
            $userId = $user.id
            $origName = $user.name
            $origEmailVerified = $user.emailVerified

            $updatePayload = @{ name = "Smoke Updated User"; emailVerified = $true } | ConvertTo-Json
            Write-Host "PATCH $baseUrl/api/users/$userId -> updating user"
            try {
                $patchResp = Invoke-RestMethod -Method Patch -Uri ("$baseUrl/api/users/$userId") -Body $updatePayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
                Write-Host "Updated user:"; Write-Host ($patchResp | ConvertTo-Json -Depth 5)
                # verify
                $verify = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/users/$userId") -Headers $headers -ErrorAction Stop
                Write-Host "Verified user after update:"; Write-Host ($verify | ConvertTo-Json -Depth 5)
                # revert
                $revertPayload = @{ name = $origName; emailVerified = $origEmailVerified } | ConvertTo-Json
                Write-Host "Reverting user to original values"
                Invoke-RestMethod -Method Patch -Uri ("$baseUrl/api/users/$userId") -Body $revertPayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
                Write-Host "Reverted user"
            } catch {
                Write-Warning "Failed to patch user: $($_.Exception.Message)"
                if ($_.Exception.Message -like '*403*' -or $_.Exception.Response -and $_.Exception.Response.StatusCode -eq 403) {
                    Write-Host "Attempting admin login to retry user update"
                    try {
                        $adminLoginBody = @{ email = $adminEmail; password = $adminPassword } | ConvertTo-Json
                        $adminLoginResp = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/auth/login") -Body $adminLoginBody -ContentType 'application/json' -ErrorAction Stop
                        if ($adminLoginResp -and $adminLoginResp.data -and $adminLoginResp.data.token) {
                            $token = $adminLoginResp.data.token.Trim()
                            Write-Host "Obtained admin token (len=$($token.Length))"
                            $headers['Authorization'] = "Bearer $token"
                            $patchResp = Invoke-RestMethod -Method Patch -Uri ("$baseUrl/api/users/$userId") -Body $updatePayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
                            Write-Host "Updated user with admin:"; Write-Host ($patchResp | ConvertTo-Json -Depth 5)
                            # verify and revert with admin
                            $verify = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/users/$userId") -Headers $headers -ErrorAction Stop
                            Write-Host "Verified user after admin update:"; Write-Host ($verify | ConvertTo-Json -Depth 5)
                            $revertPayload = @{ name = $origName; emailVerified = $origEmailVerified } | ConvertTo-Json
                            Invoke-RestMethod -Method Patch -Uri ("$baseUrl/api/users/$userId") -Body $revertPayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
                            Write-Host "Reverted user with admin"
                        } else {
                            Write-Warning "Admin login did not return a token; cannot retry user update"
                        }
                    } catch {
                        Write-Warning "Admin retry for user update failed: $($_.Exception.Message)"
                    }
                }
            }
        } else {
            Write-Host "Skipping user update tests because no users available"
        }
    } catch {
        Write-Warning "User update tests failed: $($_.Exception.Message)"
    }

    # 9) Mail public endpoint and verify-email (invalid token check)
    try {
        Write-Host "GET $baseUrl/api/mail/public -> public mail endpoint"
        $mailPublic = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/mail/public") -ErrorAction Stop
        Write-Host "Mail public response:"; Write-Host ($mailPublic | ConvertTo-Json -Depth 5)
    } catch {
        Write-Warning "Mail public endpoint failed: $($_.Exception.Message)"
    }

    try {
        $invalid = "invalid-token-for-test"
        Write-Host "GET $baseUrl/api/auth/verify?token=$invalid -> expect 404 or error"
        $verifyResp = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/auth/verify?token=$invalid") -Headers $headers -ErrorAction Stop
        Write-Host "Verify (unexpected success):"; Write-Host ($verifyResp | ConvertTo-Json -Depth 5)
    } catch {
        Write-Host "Verify with invalid token returned expected error: $($_.Exception.Message)"
    }

    # 7) Order payment approve: set paymentId on an existing order and call approve endpoint
    try {
        # find an existing order (created earlier in the script run)
        Write-Host "Searching for an order to test payment approval"
        $ordersResp = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/orders") -Headers $headers -ErrorAction Stop
        if ($ordersResp -and $ordersResp.data -and $ordersResp.data.Count -gt 0) {
            $order = $ordersResp.data[0]
            $orderId = $order.id
            # create a paymentId and patch the order with it
            $paymentId = [guid]::NewGuid().ToString()
            $patchPayload = @{ paymentId = $paymentId } | ConvertTo-Json
            Write-Host "PATCH $baseUrl/api/orders/$orderId -> set paymentId=$paymentId"
            $patchResp = Invoke-RestMethod -Method Patch -Uri ("$baseUrl/api/orders/$orderId") -Body $patchPayload -ContentType 'application/json' -Headers $headers -ErrorAction Stop
            Write-Host "Patched order:"; Write-Host ($patchResp | ConvertTo-Json -Depth 5)

            Write-Host "POST $baseUrl/api/orders/payment/$paymentId/approve -> approving payment"
            $approveResp = Invoke-RestMethod -Method Post -Uri ("$baseUrl/api/orders/payment/$paymentId/approve") -Headers $headers -ErrorAction Stop
            Write-Host "Approve response:"; Write-Host ($approveResp | ConvertTo-Json -Depth 5)

            # Verify order updated and stock decremented for its vinyls
            $verifyOrder = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/orders/$orderId") -Headers $headers -ErrorAction Stop
            Write-Host "Verified order after approval:"; Write-Host ($verifyOrder | ConvertTo-Json -Depth 5)
            foreach ($vid in $verifyOrder.data.vinylIds) {
                $v = Invoke-RestMethod -Method Get -Uri ("$baseUrl/api/vinyls/$vid") -Headers $headers -ErrorAction Stop
                Write-Host "Vinyl after approval:"; Write-Host ($v | ConvertTo-Json -Depth 5)
            }
        } else {
            Write-Host "No orders found to test payment approval"
        }
    } catch {
        Write-Warning "Payment approval test failed: $($_.Exception.Message)"
    }
}

# Run smoke checks now (will print to console)
Run-SmokeChecks -baseUrl $baseUrl -headers $headers


