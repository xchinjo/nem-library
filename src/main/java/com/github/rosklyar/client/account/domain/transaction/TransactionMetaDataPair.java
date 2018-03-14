package com.github.rosklyar.client.account.domain.transaction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.rosklyar.client.transaction.domain.Transaction;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class TransactionMetaDataPair {

    public final TransactionMetaData meta;
    public final Transaction transaction;

    @JsonCreator
    public TransactionMetaDataPair(@JsonProperty("meta") TransactionMetaData meta,
                                   @JsonProperty("transaction") Transaction transaction) {
        this.meta = meta;
        this.transaction = transaction;
    }
}