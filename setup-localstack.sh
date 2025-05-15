#!/usr/bin/env bash
set -e

# Desabilita o pager do AWS CLI
export AWS_PAGER=""

# Garante que LS tenha valor padrão (caso não esteja definido)
: "${LS:=http://localhost:4566}"

# (opcional) carrega .env
[ -f .env ] && source .env

# Se .env tiver sobrescrito LS com vazio, volta para o padrão
if [[ -z "$LS" ]]; then
  LS="http://localhost:4566"
fi

# Configurações
LAMBDA_NAME="ProdutoHandler"
JAR="target/produto-crud-lambda-1.0-SNAPSHOT.jar"
HANDLER="com.exemplo.handler.ProdutoHandler::handleRequest"
ROLE="arn:aws:iam::000000000000:role/lambda-role"
TABLE="Produtos"
LS="http://localhost:4566"
API_NAME="ProdutoAPI"
STAGE="dev"
S3_BUCKET="localstack"

echo "🗄️  Criando tabela DynamoDB (LocalStack)…"
if ! aws --endpoint-url=$LS dynamodb list-tables --query "TableNames" --output text | grep -q "^$TABLE$"; then
  aws --endpoint-url=$LS dynamodb create-table \
      --table-name $TABLE \
      --attribute-definitions AttributeName=id,AttributeType=S \
      --key-schema AttributeName=id,KeyType=HASH \
      --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5
  aws --endpoint-url=$LS dynamodb wait table-exists --table-name $TABLE
  echo "✅ Tabela $TABLE criada."
else
  echo "✅ Tabela $TABLE já existe."
fi

echo
echo "🌩️  Criando bucket S3 “$S3_BUCKET” (para API GW)…"
if ! aws --endpoint-url=$LS s3api head-bucket --bucket $S3_BUCKET 2>/dev/null; then
  aws --endpoint-url=$LS s3api create-bucket --bucket $S3_BUCKET
  echo "✅ Bucket $S3_BUCKET criado."
else
  echo "✅ Bucket $S3_BUCKET já existe."
fi

echo
echo "🔄  Deploy da Lambda…"
aws --endpoint-url=$LS lambda delete-function --function-name $LAMBDA_NAME 2>/dev/null || true
aws --endpoint-url=$LS lambda create-function \
    --function-name $LAMBDA_NAME \
    --runtime java17 \
    --handler "$HANDLER" \
    --zip-file fileb://$JAR \
    --role $ROLE \
    --timeout 30 \
    --environment "Variables={DYNAMODB_ENDPOINT=http://host.docker.internal:4566}"
echo "✅ Lambda $LAMBDA_NAME criada."

echo
echo "⌛ Aguardando Lambda ativa…"
aws --endpoint-url=$LS lambda wait function-active-v2 --function-name $LAMBDA_NAME

echo
echo "🔐 Concedendo permissão ao API Gateway invocar a Lambda…"
API_REST_ID=$(aws --endpoint-url=$LS apigateway create-rest-api --name $API_NAME --query 'id' --output text)
aws --endpoint-url=$LS lambda add-permission \
    --function-name $LAMBDA_NAME \
    --statement-id apigw-invoke \
    --action lambda:InvokeFunction \
    --principal apigateway.amazonaws.com \
    --source-arn "arn:aws:execute-api:us-east-1:000000000000:$API_REST_ID/*/*/produtos/*" \
    >/dev/null
echo "✅ Permissão concedida."

echo
echo "🌐  Criando & deployando API Gateway…"
# (já gerou API_REST_ID acima)
ROOT_ID=$(aws --endpoint-url=$LS apigateway get-resources --rest-api-id $API_REST_ID --query 'items[0].id' --output text)

# recurso /produtos
PROD_ID=$(aws --endpoint-url=$LS apigateway create-resource \
    --rest-api-id $API_REST_ID --parent-id $ROOT_ID \
    --path-part produtos --query 'id' --output text)

# métodos GET e POST
for M in GET POST; do
  aws --endpoint-url=$LS apigateway put-method \
      --rest-api-id $API_REST_ID --resource-id $PROD_ID \
      --http-method $M --authorization-type NONE
  aws --endpoint-url=$LS apigateway put-integration \
      --rest-api-id $API_REST_ID --resource-id $PROD_ID \
      --http-method $M --type AWS_PROXY \
      --integration-http-method POST \
      --uri "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:000000000000:function:$LAMBDA_NAME/invocations"
done

# deploy final
aws --endpoint-url=$LS apigateway create-deployment \
    --rest-api-id $API_REST_ID --stage-name $STAGE
ENDPOINT="$LS/restapis/$API_REST_ID/$STAGE/_user_request_/produtos"
echo "✅ API disponível em: $ENDPOINT"

echo
echo "🧪  Teste direto na Lambda (GET lista)…"
aws --endpoint-url=$LS lambda invoke \
    --function-name $LAMBDA_NAME \
    --cli-binary-format raw-in-base64-out \
    --payload '{"httpMethod":"GET","path":"/produtos"}' \
    response.json >/dev/null
echo "Resposta Lambda (raw JSON):"
cat response.json

echo
echo "🧪  Teste via API Gateway (GET /produtos)…"
RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X GET $ENDPOINT)
echo "$RESPONSE" | sed -n '1p'   # body
echo "$RESPONSE" | sed -n '2p'   # status

echo
echo "🎉  Deploy e testes concluídos!"
