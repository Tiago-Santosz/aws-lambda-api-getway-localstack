package com.exemplo.model;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@Data
@DynamoDbBean
public class Produto {
    private String id;
    private String nome;
    private double preco;

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }
}
