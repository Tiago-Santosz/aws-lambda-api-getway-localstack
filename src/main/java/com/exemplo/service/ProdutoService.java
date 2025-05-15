package com.exemplo.service;

import com.exemplo.model.Produto;
import com.exemplo.repository.ProdutoRepository;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.util.List;

public class ProdutoService {
    private final ProdutoRepository repository;

    public ProdutoService(ProdutoRepository produtoRepository) {
        String endpoint = System.getenv("DYNAMODB_ENDPOINT");
        if (endpoint == null || endpoint.isEmpty()) {
            endpoint = "http://localhost:4566"; // fallback para testes locais
        }

        DynamoDbClient client = DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test","test")))
                .build();

        DynamoDbEnhancedClient enhanced = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(client)
                .build();

        this.repository = new ProdutoRepository(enhanced);
    }


    public void criar(Produto produto) {
        repository.salvar(produto);
    }

    public Produto buscar(String id) {
        return repository.buscar(id);
    }

    public List<Produto> listar() {
        return repository.listar();
    }

    public void deletar(String id) {
        repository.deletar(id);
    }
}