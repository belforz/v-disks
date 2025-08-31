param([string]$file = "./run-postman.ps1")
$full = (Resolve-Path -Path $file).ProviderPath
$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($full,[ref]$tokens,[ref]$errors)
if ($errors -and $errors.Count -gt 0) {
    Write-Host ("Syntax errors found in {0}`n" -f $full)
    foreach ($err in $errors) { Write-Host $err.Message }
    exit 1
} else {
    Write-Host ("Parsed OK: {0}" -f $full)
    exit 0
}
