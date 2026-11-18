# FlagShip Server Verification Script
param(
    [string]$BaseUrl = "http://localhost:8080"
)

Write-Host "Verifying FlagShip Server at $BaseUrl..." -ForegroundColor Cyan

$routes = @(
    "/login",
    "/api/v1/sdk/snapshot?project=default&env=production",
    "/actuator/health"
)

$passed = 0
foreach ($route in $routes) {
    $url = "$BaseUrl$route"
    try {
        $res = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
        if ($res.StatusCode -eq 200) {
            Write-Host "[PASS] $route (HTTP 200)" -ForegroundColor Green
            $passed++
        } else {
            Write-Host "[FAIL] $route (HTTP $($res.StatusCode))" -ForegroundColor Red
        }
    } catch {
        Write-Host "[WARN] $route : $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

Write-Host "Verification complete: $passed routes responded." -ForegroundColor Cyan
