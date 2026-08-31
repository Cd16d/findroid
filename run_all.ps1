$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$branches = @("branch1-network-module", "branch2-tv-quick-connect", "branch3-phone-account-menu", "branch4-phone-quick-connect", "branch5-phone-multi-users")

foreach ($branch in $branches) {
    Write-Host "Verifying $branch"
    git checkout $branch
    ./gradlew ktfmtFormat
    if ($LASTEXITCODE -ne 0) { Write-Host "Failed ktfmtFormat on $branch"; exit 1 }
    
    git add -A
    git diff-index --quiet HEAD
    if ($LASTEXITCODE -ne 0) { git commit -m "style: apply ktfmtFormat" }
    
    ./gradlew app:phone:compileDebugKotlin app:tv:compileDebugKotlin
    if ($LASTEXITCODE -ne 0) { Write-Host "Failed compile on $branch"; exit 1 }
}

Write-Host "All branches verified. Pushing..."
git push -u origin branch1-network-module branch2-tv-quick-connect branch3-phone-account-menu branch4-phone-quick-connect branch5-phone-multi-users
Write-Host "ALL DONE"
