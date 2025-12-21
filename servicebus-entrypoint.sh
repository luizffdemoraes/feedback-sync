#!/bin/bash
set -e

echo "=========================================="
echo "Azure Service Bus Emulator - Inicialização"
echo "=========================================="

# Função para aguardar o SQL Server estar disponível
wait_for_sql() {
    local host=$1
    local port=$2
    local max_attempts=60
    local attempt=0
    
    echo "Aguardando SQL Server em ${host}:${port}..."
    
    while [ $attempt -lt $max_attempts ]; do
        if timeout 5 bash -c "echo > /dev/tcp/${host}/${port}" 2>/dev/null; then
            echo "✓ SQL Server está disponível!"
            return 0
        fi
        attempt=$((attempt + 1))
        echo "  Tentativa ${attempt}/${max_attempts}..."
        sleep 2
    done
    
    echo "✗ Timeout aguardando SQL Server"
    return 1
}

# Resolver IP do SQL Server usando getent ou ping
resolve_sql_ip() {
    local sql_host=$1
    
    # Tentar resolver usando getent (melhor opção)
    if command -v getent &> /dev/null; then
        local ip=$(getent hosts ${sql_host} | awk '{ print $1 }' | head -n 1)
        if [ -n "$ip" ]; then
            echo "$ip"
            return 0
        fi
    fi
    
    # Fallback: usar o próprio nome do host (Docker network resolve)
    echo "${sql_host}"
    return 0
}

# Configurações
SQL_HOST="${SQL_SERVER_HOST:-sqlserver}"
SQL_PORT="${SQL_SERVER_PORT:-1433}"
SQL_USER="${SQL_SERVER_USER:-sa}"
SQL_PASSWORD="${SQL_SERVER_PASSWORD:-YourStrong@Passw0rd}"
SQL_DATABASE="${SQL_SERVER_DATABASE:-ServiceBusEmulator}"

echo "Configurações:"
echo "  SQL Host: ${SQL_HOST}"
echo "  SQL Port: ${SQL_PORT}"
echo "  SQL Database: ${SQL_DATABASE}"

# Resolver IP do SQL Server
echo "Resolvendo IP do SQL Server..."
SQL_IP=$(resolve_sql_ip ${SQL_HOST})
echo "  IP resolvido: ${SQL_IP}"

# Aguardar SQL Server estar disponível
if ! wait_for_sql ${SQL_IP} ${SQL_PORT}; then
    echo "ERRO: Não foi possível conectar ao SQL Server"
    exit 1
fi

# Construir connection string com IP
CONNECTION_STRING="Server=${SQL_IP},${SQL_PORT};Database=${SQL_DATABASE};User Id=${SQL_USER};Password=${SQL_PASSWORD};TrustServerCertificate=True;Encrypt=False;Connection Timeout=30;"

echo ""
echo "Connection String configurada:"
echo "  Server=${SQL_IP},${SQL_PORT}"
echo ""

# Exportar variáveis de ambiente para o Service Bus Emulator
export SERVICEBUS_EMULATOR__STORAGE__MODE=SqlServer
export SERVICEBUS_EMULATOR__STORAGE__SQLSERVER__CONNECTIONSTRING="${CONNECTION_STRING}"

echo "=========================================="
echo "Iniciando Azure Service Bus Emulator..."
echo "=========================================="

# A imagem base do Service Bus Emulator usa dotnet para executar o emulador
# Vamos procurar o executável e executá-lo
# O caminho padrão é /app/ServiceBus.Emulator.dll baseado na documentação

if command -v dotnet &> /dev/null; then
    # Procurar o DLL do emulador nos locais comuns
    if [ -f "/app/ServiceBus.Emulator.dll" ]; then
        echo "Executando: dotnet /app/ServiceBus.Emulator.dll"
        exec dotnet /app/ServiceBus.Emulator.dll
    elif [ -f "/ServiceBus.Emulator.dll" ]; then
        echo "Executando: dotnet /ServiceBus.Emulator.dll"
        exec dotnet /ServiceBus.Emulator.dll
    else
        echo "AVISO: DLL do emulador não encontrado nos caminhos padrão"
        echo "Tentando encontrar o executável..."
        # Procurar recursivamente
        EMULATOR_DLL=$(find / -name "ServiceBus.Emulator.dll" 2>/dev/null | head -n 1)
        if [ -n "$EMULATOR_DLL" ]; then
            echo "Encontrado em: $EMULATOR_DLL"
            exec dotnet "$EMULATOR_DLL"
        else
            echo "ERRO: Não foi possível encontrar o ServiceBus.Emulator.dll"
            echo "Tentando executar o entrypoint padrão da imagem..."
            # Tentar executar o que a imagem original executaria
            exec /bin/sh
        fi
    fi
else
    echo "ERRO: dotnet não encontrado no PATH"
    exit 1
fi

