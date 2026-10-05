# scripts/run_all_tiered_benchmarks.ps1
# Script per eseguire la suite completa di 4 Tier x 3 Benchmark (12 esecuzioni)
# Parametri rigorosi richiesti:
# - Fork: 3 (-f 3)
# - Warmup: 5 iterazioni da 2s ciascuna (-wi 5 -w 2s, 10s per fork)
# - Measurement: 5 iterazioni da 2s ciascuna (-i 5 -r 2s, 10s per fork)
# - Heap: 8GB fissi (-Xms8g -Xmx8g)
# - Cores: 4 fissi (-XX:ActiveProcessorCount=4)
# - Persistenza: ogni lancio salvato in log e summary dedicati, più file riepilogativo finale

$ErrorActionPreference = "Continue"

$projectRoot = "C:\Users\Leonardo\Desktop\code\stream-fusion"
$resultsDir = "$projectRoot\benchmarks\results"

if (-not (Test-Path $resultsDir)) {
    New-Item -ItemType Directory -Force -Path $resultsDir | Out-Null
}

$tiers = @(0, 1,  4)

$benchmarks = @(
    @{ Name = "monomorphic"; Class = "fuse.benchmarks.MonomorphicBenchmark" },
    @{ Name = "megamorphic"; Class = "fuse.benchmarks.MegamorphicBenchmark" },
    @{ Name = "parallel_megamorphic"; Class = "fuse.benchmarks.ParallelMegamorphicBenchmark" }
)

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "Avvio Suite Completa Tiered Compilation (4 Tier x 3 Benchmark = 12 Lanci)" -ForegroundColor Cyan
Write-Host "Parametri: 3 Fork, Warmup 5x2s (10s), Measurement 5x2s (10s), 8GB Heap, 4 Cores" -ForegroundColor Cyan
Write-Host "Cartella Risultati: $resultsDir" -ForegroundColor Cyan
Write-Host "Orario inizio: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

$totalRuns = $tiers.Count * $benchmarks.Count
$currentRun = 0
$overallStartTime = Get-Date

foreach ($tier in $tiers) {
    Write-Host "`n=================================================================" -ForegroundColor Yellow
    Write-Host ">>> AVVIO TIER $tier (-XX:TieredStopAtLevel=$tier)" -ForegroundColor Yellow
    Write-Host "=================================================================" -ForegroundColor Yellow

    foreach ($bench in $benchmarks) {
        $currentRun++
        $bName = $bench.Name
        $bClass = $bench.Class
        
        $summaryFile = "$resultsDir\tier${tier}_${bName}_summary.txt"
        $logFile = "$resultsDir\tier${tier}_${bName}.log"
        
        Write-Host "`n[$currentRun / $totalRuns] [$(Get-Date -Format 'HH:mm:ss')] Esecuzione: $bName (Tier $tier)" -ForegroundColor Green
        Write-Host "  -> Log completo: $logFile"
        Write-Host "  -> Tabella sommario: $summaryFile"
        
        $runStart = Get-Date
        
        $sbtArg = "benchmarks/Jmh/run -i 5 -wi 5 -f 3 -r 2s -w 2s -jvmArgsAppend -Xms8g -jvmArgsAppend -Xmx8g -jvmArgsAppend -XX:ActiveProcessorCount=4 -jvmArgsAppend -XX:+TieredCompilation -jvmArgsAppend -XX:TieredStopAtLevel=$tier -rf text -rff $summaryFile $bClass"
        
        # Invocazione diretta sbt con reindirizzamento completo a logFile
        Push-Location $projectRoot
        try {
            sbt $sbtArg *> $logFile
            $exitCode = $LASTEXITCODE
        } finally {
            Pop-Location
        }
        
        $runDuration = ((Get-Date) - $runStart).TotalSeconds
        if ($exitCode -eq 0) {
            Write-Host "  [OK] Concluso in $([math]::Round($runDuration, 1))s (ExitCode 0)" -ForegroundColor Green
        } else {
            Write-Host "  [ATTENZIONE] Uscita con ExitCode $exitCode" -ForegroundColor Red
        }
    }
}

$totalElapsed = ((Get-Date) - $overallStartTime).TotalMinutes
Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "TUTTI I 12 LANCI COMPLETATI in $([math]::Round($totalElapsed, 1)) minuti!" -ForegroundColor Cyan
Write-Host "Aggregazione risultati in corso..." -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# Aggregazione finale in un unico file markdown e json
$consolidatedMd = "$resultsDir\consolidated_results.md"
$report = @()
$report += "# Risultati Consolidati Benchmark HotSpot Tiered Compilation (12 Lanci)"
$report += ""
$report += "- **Data esecuzione:** $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
$report += "- **Durata totale:** $([math]::Round($totalElapsed, 1)) minuti"
$report += "- **Parametri:** 3 Forks, Warmup 5x2s (10s), Measurement 5x2s (10s), Heap: 8GB (-Xms8g -Xmx8g), CPU: 4 Cores (-XX:ActiveProcessorCount=4)"
$report += ""

foreach ($tier in $tiers) {
    $report += "## Tier $tier (-XX:TieredStopAtLevel=$tier)"
    $report += ""
    foreach ($bench in $benchmarks) {
        $bName = $bench.Name
        $summaryFile = "$resultsDir\tier${tier}_${bName}_summary.txt"
        $report += "### Benchmark: $bName (Tier $tier)"
        $report += ""
        if (Test-Path $summaryFile) {
            $content = Get-Content $summaryFile -Raw
            $report += '```'
            $report += $content.Trim()
            $report += '```'
        } else {
            $report += "_File di sommario non trovato._"
        }
        $report += ""
    }
}

$report | Set-Content -Path $consolidatedMd -Encoding UTF8
Write-Host "File consolidato salvato in: $consolidatedMd" -ForegroundColor Green
