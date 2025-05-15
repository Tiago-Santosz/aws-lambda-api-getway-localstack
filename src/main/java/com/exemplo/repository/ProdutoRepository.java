package com.exemplo.repository;

import com.exemplo.model.Produto;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.util.List;
import java.util.stream.Collectors;


public class ProdutoRepository {
    private final DynamoDbTable<Produto> table;

    public ProdutoRepository(DynamoDbEnhancedClient client) {
        this.table = client.table("Produtos", TableSchema.fromBean(Produto.class));
    }

    public void salvar(Produto produto) {
        table.putItem(produto);
    }

    public Produto buscar(String id) {
        return table.getItem(r -> r.key(k -> k.partitionValue(id)));
    }

    public List<Produto> listar() {
        return table.scan(ScanEnhancedRequest.builder().build())
                .items()
                .stream()
                .collect(Collectors.toList());
    }

    public void deletar(String id) {
        table.deleteItem(r -> r.key(k -> k.partitionValue(id)));
    }
}