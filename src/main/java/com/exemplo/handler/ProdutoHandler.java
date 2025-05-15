package com.exemplo.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.exemplo.model.Produto;
import com.exemplo.repository.ProdutoRepository;
import com.exemplo.service.ProdutoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class ProdutoHandler implements RequestHandler<Map<String,Object>, Map<String,Object>> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ProdutoService service;

    public ProdutoHandler() {
        // instância DynamoDB localstack
        DynamoDbClient ddb = DynamoDbClient.builder()
                .endpointOverride(java.net.URI.create("http://localhost:4566"))
                .region(Region.US_EAST_1)
                .build();
        DynamoDbEnhancedClient enhanced = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(ddb)
                .build();
        this.service = new ProdutoService(new ProdutoRepository(enhanced));
    }

    @Override
    public Map<String,Object> handleRequest(Map<String,Object> event, Context ctx) {
        try {
            String method = (String) event.get("httpMethod");
            String body   = (String) event.get("body");
            @SuppressWarnings("unchecked")
            Map<String,String> pathParams = (Map<String,String>) event.get("pathParameters");

            Map<String,Object> resp = new HashMap<>();
            resp.put("headers", Map.of("Content-Type","application/json"));

            switch(method) {
                case "GET":
                    if (pathParams!=null && pathParams.containsKey("id")) {
                        Produto p = service.buscar(pathParams.get("id"));
                        if (p!=null) {
                            resp.put("statusCode",200);
                            resp.put("body", mapper.writeValueAsString(p));
                        } else {
                            resp.put("statusCode",404);
                            resp.put("body","{\"message\":\"Produto não encontrado\"}");
                        }
                    } else {
                        List<Produto> all = service.listar();
                        resp.put("statusCode",200);
                        resp.put("body", mapper.writeValueAsString(all));
                    }
                    break;
                case "POST":
                    Produto novo = mapper.readValue(body,Produto.class);
                    service.criar(novo);
                    resp.put("statusCode",201);
                    resp.put("body", mapper.writeValueAsString(novo));
                    break;
                case "DELETE":
                    if (pathParams!=null && pathParams.containsKey("id")) {
                        service.deletar(pathParams.get("id"));
                        resp.put("statusCode",204);
                        resp.put("body","");
                    } else {
                        resp.put("statusCode",400);
                        resp.put("body","{\"message\":\"ID necessário\"}");
                    }
                    break;
                default:
                    resp.put("statusCode",400);
                    resp.put("body","{\"message\":\"Método não suportado\"}");
            }

            return resp;
        } catch(Exception e) {
            return Map.of(
                    "statusCode",500,
                    "body", "{\"message\":\"" + e.getMessage().replace("\"","'") + "\"}"
            );
        }
    }
}
