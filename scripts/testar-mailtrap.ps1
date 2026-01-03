param(
    [string]$Token = "",
    [string]$InboxId = "",
    [string]$Email = ""
)

Write-Host "=== Teste Isolado de Envio de Email via Mailtrap ===" -ForegroundColor Cyan
Write-Host ""

# Obter variaveis de ambiente ou parametros
if ([string]::IsNullOrWhiteSpace($Token)) {
    $Token = $env:MAILTRAP_API_TOKEN
}
if ([string]::IsNullOrWhiteSpace($InboxId)) {
    $InboxId = $env:MAILTRAP_INBOX_ID
}
if ([string]::IsNullOrWhiteSpace($Email)) {
    $Email = $env:ADMIN_EMAIL
}

# Validar configuracoes
if ([string]::IsNullOrWhiteSpace($Token) -or [string]::IsNullOrWhiteSpace($InboxId) -or [string]::IsNullOrWhiteSpace($Email)) {
    Write-Host "ERRO: Configure as variaveis:" -ForegroundColor Red
    Write-Host '   $env:MAILTRAP_API_TOKEN = "seu-token"' -ForegroundColor White
    Write-Host '   $env:MAILTRAP_INBOX_ID = "4049775"' -ForegroundColor White
    Write-Host '   $env:ADMIN_EMAIL = "seu-email@gmail.com"' -ForegroundColor White
    exit 1
}

# Configurar variaveis de ambiente
$env:MAILTRAP_API_TOKEN = $Token
$env:MAILTRAP_INBOX_ID = $InboxId
$env:ADMIN_EMAIL = $Email

Write-Host "OK: Configuracoes validadas" -ForegroundColor Green
Write-Host ""

# Encontrar diretorio do projeto
$currentDir = (Get-Location).Path

# Verificar se pom.xml existe no diretorio atual
if (Test-Path (Join-Path $currentDir "pom.xml")) {
    $projectRoot = $currentDir
} else {
    # Tentar encontrar o diretorio do script
    $scriptPath = $MyInvocation.MyCommand.Path
    if ($scriptPath) {
        $scriptDir = Split-Path -Parent $scriptPath
        $projectRoot = Split-Path -Parent $scriptDir
        if (-not (Test-Path (Join-Path $projectRoot "pom.xml"))) {
            Write-Host "ERRO: Execute do diretorio raiz do projeto" -ForegroundColor Red
            exit 1
        }
        Set-Location $projectRoot
    } else {
        Write-Host "ERRO: Execute do diretorio raiz do projeto" -ForegroundColor Red
        exit 1
    }
}

Write-Host "Diretorio do projeto: $projectRoot" -ForegroundColor Gray

# Compilar (sem clean para evitar problemas com arquivos em uso)
Write-Host "Compilando projeto..." -ForegroundColor Yellow
$compileOutput = & mvn compile test-compile 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERRO: Falha ao compilar" -ForegroundColor Red
    Write-Host $compileOutput
    exit 1
}
Write-Host "OK: Compilado" -ForegroundColor Green
Write-Host ""

# Construir classpath
Write-Host "Construindo classpath..." -ForegroundColor Gray
$cpFile = Join-Path $env:TEMP "mailtrap-cp-$(Get-Random).txt"
$cpOutput = & mvn dependency:build-classpath "-DincludeScope=test" "-Dmdep.outputFile=$cpFile" 2>&1

if (-not (Test-Path $cpFile)) {
    Write-Host "ERRO: Falha ao construir classpath" -ForegroundColor Red
    Write-Host $cpOutput
    exit 1
}

$cp = (Get-Content $cpFile -Raw).Trim()
$mainClasses = Join-Path $projectRoot "target\classes"
$testClasses = Join-Path $projectRoot "target\test-classes"
$fullCp = "$mainClasses;$testClasses;$cp"

# Executar teste
Write-Host "Executando teste..." -ForegroundColor Yellow
& java -cp $fullCp br.com.fiap.postech.feedback.MailtrapEmailTest

$exitCode = $LASTEXITCODE

# Limpar
Remove-Item $cpFile -ErrorAction SilentlyContinue

# Resultado
if ($exitCode -eq 0) {
    Write-Host ""
    Write-Host "SUCESSO! Verifique sua inbox no Mailtrap." -ForegroundColor Green
    exit 0
} else {
    Write-Host ""
    Write-Host "ERRO: Teste falhou (codigo: $exitCode)" -ForegroundColor Red
    exit 1
}
