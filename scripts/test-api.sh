#!/bin/bash

# Script para testar a API local
# Uso: ./scripts/test-api.sh

set -e

API_URL="${API_URL:-http://localhost:7071/api/avaliacao}"

echo "🧪 Testando API de Feedback..."
echo "URL: $API_URL"
echo ""

# Teste 1: Feedback normal
echo "📝 Teste 1: Criando feedback normal (nota 7)..."
RESPONSE1=$(curl -s -X POST "$API_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Produto muito bom, recomendo!",
    "nota": 7,
    "urgencia": "LOW"
  }')

echo "Resposta: $RESPONSE1"
echo ""

# Teste 2: Feedback crítico (deve disparar notificação)
echo "🚨 Teste 2: Criando feedback crítico (nota 2)..."
RESPONSE2=$(curl -s -X POST "$API_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Produto com defeito grave, precisa de atenção urgente!",
    "nota": 2,
    "urgencia": "HIGH"
  }')

echo "Resposta: $RESPONSE2"
echo ""

# Teste 3: Validação - nota inválida
echo "❌ Teste 3: Tentando criar feedback com nota inválida (15)..."
RESPONSE3=$(curl -s -X POST "$API_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "descricao": "Teste de validação",
    "nota": 15
  }')

echo "Resposta: $RESPONSE3"
echo ""

# Teste 4: Validação - campo obrigatório faltando
echo "❌ Teste 4: Tentando criar feedback sem descrição..."
RESPONSE4=$(curl -s -X POST "$API_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "nota": 5
  }')

echo "Resposta: $RESPONSE4"
echo ""

echo "✅ Testes concluídos!"
echo ""
echo "💡 Verifique os logs da aplicação para ver as notificações e persistências."

