# Simple end-to-end smoke test script for the V-Disks API
# Run in PowerShell on the machine where the API is reachable (default http://localhost:8080)
# Usage:
#   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
#   .\scripts\test_flow.ps1

$base = 'http://localhost:8080'

function pretty($obj) {
    $obj | ConvertTo-Json -Depth 10
}

Try {
    Write-Host "1) Create test user..."
    $userPayload = @{
        name = "Teste Fluxo"
        email = "test+flow1@example.com"
        password = "Pass123!"
        roles = @("USER")
    } | ConvertTo-Json

    $userResp = Invoke-RestMethod -Method Post -Uri "$base/api/users" -Body $userPayload -ContentType 'application/json' -ErrorAction Stop
    Write-Host "User created:"; Write-Host (pretty $userResp)
    $userId = $userResp.data.id

    Write-Host "\n2) Trigger resend verification (this will send an email if mail is configured)..."
    $resend = Invoke-RestMethod -Method Post -Uri "$base/api/users/resend-verification/$userId" -ErrorAction Stop
    Write-Host (pretty $resend)

    Write-Host "\n3) Obtain token for verification (choose one):"
    Write-Host "   A) Check the verification email received by the user and copy the token from the link."
    Write-Host "   B) If you have DB access, run a mongo query to read the token from collection 'email_verify_tokens'."
    Write-Host "      Example (mongo shell / Atlas):"
    Write-Host "      mongo --username <user> --password <pw> --host <host> --eval \"db.getSiblingDB('v-disk').email_verify_tokens.find({userId:'$userId'}).pretty()\""
    Write-Host "\n   Enter token now (paste token value) or press Enter to skip and continue tests that don't need verification:"

    $token = Read-Host "Token (paste here)"

    if (![string]::IsNullOrWhiteSpace($token)) {
        Write-Host "\n4) Verify token via POST /auth/verify-email..."
        $body = @{ token = $token } | ConvertTo-Json
        $verifyResp = Invoke-RestMethod -Method Post -Uri "$base/auth/verify-email" -Body $body -ContentType 'application/json' -ErrorAction Stop
        Write-Host (pretty $verifyResp)
    } else {
        Write-Host "Skipping verification step (no token provided).";
    }

    Write-Host "\n5) Create a test vinyl (record) to order..."
    $vinylPayload = @{
        title = "Test Vinyl 1"
        artist = "Various"
        stock = 5
        price = 29.9
        coverPath = ""
        gallery = @()
    } | ConvertTo-Json

    $vinylResp = Invoke-RestMethod -Method Post -Uri "$base/api/vinyls" -Body $vinylPayload -ContentType 'application/json' -ErrorAction Stop
    Write-Host "Vinyl created:"; Write-Host (pretty $vinylResp)
    $vinylId = $vinylResp.data.id

    Write-Host "\n6) Add item to cart (quantity=2)..."
    $cartBody = @{ quantity = 2 } | ConvertTo-Json
    $addCartResp = Invoke-RestMethod -Method Post -Uri "$base/api/cart/$userId/item/$vinylId" -Body $cartBody -ContentType 'application/json' -ErrorAction Stop
    Write-Host (pretty $addCartResp)

    Write-Host "\n7) Get cart contents..."
    $cartGet = Invoke-RestMethod -Method Get -Uri "$base/api/cart/$userId" -ErrorAction Stop
    Write-Host (pretty $cartGet)

    Write-Host "\n8) Create an order from the cart (simple create with userId + vinylIds)..."
    $orderPayload = @{ userId = $userId; vinylIds = @($vinylId) } | ConvertTo-Json
    $orderResp = Invoke-RestMethod -Method Post -Uri "$base/api/orders" -Body $orderPayload -ContentType 'application/json' -ErrorAction Stop
    Write-Host "Order created:"; Write-Host (pretty $orderResp)
    $orderId = $orderResp.data.id

    Write-Host "\n9) Attach a paymentId to the order (PATCH) and simulate approval..."
    $paymentId = "pay-" + ([guid]::NewGuid().Guid)
    $patchBody = @{ paymentId = $paymentId } | ConvertTo-Json
    $patchResp = Invoke-RestMethod -Method Patch -Uri "$base/api/orders/$orderId" -Body $patchBody -ContentType 'application/json' -ErrorAction Stop
    Write-Host (pretty $patchResp)

    Write-Host "\n10) Approve payment (this will decrement stock if available)..."
    $approveResp = Invoke-RestMethod -Method Post -Uri "$base/api/orders/payment/$paymentId/approve" -ErrorAction Stop
    Write-Host (pretty $approveResp)

    Write-Host "\n11) Fetch updated order and vinyl to assert status and stock..."
    $orderGet = Invoke-RestMethod -Method Get -Uri "$base/api/orders/$orderId" -ErrorAction Stop
    Write-Host "Order:"; Write-Host (pretty $orderGet)

    $vinylGet = Invoke-RestMethod -Method Get -Uri "$base/api/vinyls/$vinylId" -ErrorAction Stop
    Write-Host "Vinyl:"; Write-Host (pretty $vinylGet)

    Write-Host "\n12) List orders..."
    $orders = Invoke-RestMethod -Method Get -Uri "$base/api/orders" -ErrorAction Stop
    Write-Host (pretty $orders)

    Write-Host "\nFlow complete. Clean-up suggestions: delete created vinyl and user if desired."

} Catch {
    Write-Host "ERROR: " $_.Exception.Message -ForegroundColor Red
    if ($_.InvocationInfo -ne $null) { Write-Host $_.InvocationInfo.Line }
}
