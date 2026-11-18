# FlagShip Demo Application Verification Script
param(
    [string]$BaseUrl = "http://localhost:8081"
)

Write-Host "Verifying FlagShip Demo Microservice at $BaseUrl..." -ForegroundColor Cyan

$routes = @(
    "/api/demo/evaluate?userId=nayem1541",
    "/api/demo/pay?userId=nayem1541",
    "/actuator/flagship",
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

Write-Host "Demo verification complete: $passed routes responded." -ForegroundColor Cyan
