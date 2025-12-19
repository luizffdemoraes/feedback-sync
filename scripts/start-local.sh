#!/bin/bash

# Script para iniciar o ambiente local com Docker Compose
# Uso: ./scripts/start-local.sh

set -e

echo "🚀 Iniciando ambiente local com Docker Compose..."

# Verifica se o Docker está rodando
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker não está rodando. Por favor, inicie o Docker Desktop."
    exit 1
fi

# Verifica se o docker-compose está instalado
if ! command -v docker-compose &> /dev/null; then
    echo "❌ docker-compose não está instalado."
    exit 1
fi

# Para containers existentes (se houver)
echo "🛑 Parando containers existentes..."
docker-compose down

# Inicia os serviços
echo "📦 Iniciando serviços Azure (Cosmos DB, Azurite, Service Bus)..."
docker-compose up -d

# Aguarda os serviços ficarem prontos
echo "⏳ Aguardando serviços ficarem prontos..."
sleep 10

# Verifica saúde dos serviços
echo "🏥 Verificando saúde dos serviços..."

# Cosmos DB
if docker exec cosmos-emulator curl -k -f https://localhost:8081/_explorer/emulator.pem > /dev/null 2>&1; then
    echo "✅ Cosmos DB Emulator está rodando"
else
    echo "⚠️  Cosmos DB Emulator ainda não está pronto (aguarde alguns segundos)"
fi

# Azurite
if curl -f http://localhost:10000/devstoreaccount1 > /dev/null 2>&1; then
    echo "✅ Azurite está rodando"
else
    echo "⚠️  Azurite ainda não está pronto (aguarde alguns segundos)"
fi

# Service Bus
if curl -f http://localhost:8080/health > /dev/null 2>&1; then
    echo "✅ Service Bus Emulator está rodando"
else
    echo "⚠️  Service Bus Emulator ainda não está pronto (aguarde alguns segundos)"
fi

echo ""
echo "✅ Ambiente local iniciado!"
echo ""
echo "📋 Serviços disponíveis:"
echo "   - Cosmos DB: https://localhost:8081"
echo "   - Azurite Blob: http://localhost:10000"
echo "   - Service Bus: http://localhost:8080 (Management API)"
echo ""
echo "🔍 Para ver os logs: docker-compose logs -f"
echo "🛑 Para parar: docker-compose down"
echo ""
echo "💡 Próximo passo: Execute a aplicação com:"
echo "   ./mvnw quarkus:dev -Dquarkus.profile=local"

