$shell = New-Object -ComObject Shell.Application
$recycleBin = $shell.NameSpace(0xa)
$items = $recycleBin.Items()
foreach ($item in $items) {
    if ($item.Name -like "*selectmoney*") {
        Write-Host "Found: $($item.Name) at $($item.Path)"
    }
}
Write-Host "Search complete"
