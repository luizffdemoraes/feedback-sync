#!/bin/bash
set -e

# Verificar se estamos em ambiente Docker
if [ -z "$DOCKER_CONTAINER" ]; then
    export DOCKER_CONTAINER=1
fi

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

# Após configurar a connection string, precisamos executar o Service Bus Emulator
# Como não temos a imagem oficial disponível aqui, vamos usar uma abordagem alternativa:
# Usar docker run com a imagem oficial, ou instalar o emulador manualmente

# Por enquanto, vamos usar a imagem oficial via docker-in-docker ou
# instalar o emulador manualmente. A solução mais prática é usar a imagem oficial
# diretamente no docker-compose sem customização, e resolver o problema de DNS
# de outra forma.

echo "AVISO: Este Dockerfile customizado requer o Service Bus Emulator instalado."
echo "Recomendação: Use a imagem oficial diretamente no docker-compose.yml"
echo "e configure a connection string usando o IP do SQL Server via variável de ambiente."

# Se chegou até aqui, vamos tentar executar o que estiver disponível
# Por padrão, vamos apenas aguardar e mostrar a configuração
echo ""
echo "Connection String configurada:"
echo "  $CONNECTION_STRING"
echo ""
echo "Para usar a imagem oficial do Service Bus Emulator,"
echo "configure a variável SERVICEBUS_EMULATOR__STORAGE__SQLSERVER__CONNECTIONSTRING"
echo "no docker-compose.yml com o IP resolvido."

# Manter o container rodando para debug
tail -f /dev/null

