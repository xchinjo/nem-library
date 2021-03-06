package com.github.rosklyar.client.transaction;

import com.github.rosklyar.client.account.domain.Hash;
import com.github.rosklyar.client.account.domain.Message;
import com.github.rosklyar.client.mosaic.domain.Levy;
import com.github.rosklyar.client.mosaic.domain.MosaicProperty;
import com.github.rosklyar.client.node.NodeClient;
import com.github.rosklyar.client.transaction.domain.NemAnnounceResult;
import com.github.rosklyar.client.transaction.domain.ProvisionNamespaceTransaction;
import com.github.rosklyar.client.transaction.domain.RequestAnnounce;
import com.github.rosklyar.client.transaction.domain.Transaction;
import com.github.rosklyar.client.transaction.domain.importance.Action;
import com.github.rosklyar.client.transaction.domain.importance.ImportanceTransferTransaction;
import com.github.rosklyar.client.transaction.domain.mosaic.*;
import com.github.rosklyar.client.transaction.domain.multisig.Modification;
import com.github.rosklyar.client.transaction.domain.multisig.MultisigTransaction;
import com.github.rosklyar.client.transaction.domain.multisig.RelativeChange;
import com.github.rosklyar.client.transaction.encode.DefaultSigner;
import com.github.rosklyar.client.transaction.encode.HexConverter;
import com.github.rosklyar.client.transaction.encode.Signer;
import com.github.rosklyar.client.transaction.encode.TransactionEncoder;
import com.github.rosklyar.client.transaction.fee.FeeCalculator;
import com.github.rosklyar.client.transaction.version.Network;
import com.github.rosklyar.client.transaction.version.VersionProvider;

import java.util.List;

import static com.github.rosklyar.client.transaction.TransactionType.*;
import static com.github.rosklyar.client.transaction.domain.multisig.ModificationType.ADD_COSIGNATORY;
import static com.github.rosklyar.client.transaction.domain.multisig.ModificationType.REMOVE_COSIGNATORY;
import static com.google.common.collect.Lists.newArrayList;
import static java.math.BigInteger.TEN;
import static java.util.stream.Collectors.toList;

public class SecureTransactionClient implements TransactionClient {

    private final Network network;
    private final FeignTransactionClient feignTransactionClient;
    private final TransactionEncoder transactionEncoder;
    private final HexConverter hexConverter;
    private final VersionProvider versionProvider;
    private final FeeCalculator feeCalculator;
    private final NodeClient nodeClient;

    public SecureTransactionClient(Network network,
                                   FeignTransactionClient feignTransactionClient,
                                   TransactionEncoder transactionEncoder,
                                   HexConverter hexConverter,
                                   VersionProvider versionProvider,
                                   FeeCalculator feeCalculator,
                                   NodeClient nodeClient) {
        this.network = network;
        this.feignTransactionClient = feignTransactionClient;
        this.transactionEncoder = transactionEncoder;
        this.hexConverter = hexConverter;
        this.versionProvider = versionProvider;
        this.feeCalculator = feeCalculator;
        this.nodeClient = nodeClient;
    }

    @Override
    public NemAnnounceResult transferNem(String privateKey, String toAddress, long microXemAmount, String message, int timeToLiveInSeconds) {

        Signer signer = new DefaultSigner(privateKey);
        String publicKey = signer.publicKey();

        int currentTime = nodeClient.extendedInfo().nisInfo.currentTime;

        Transaction transaction = transferNemTransaction(publicKey, toAddress, microXemAmount, message, currentTime, timeToLiveInSeconds);

        byte[] data = transactionEncoder.data(transaction);
        return feignTransactionClient.prepare(new RequestAnnounce(hexConverter.getString(data), signer.sign(data)));
    }

    @Override
    public NemAnnounceResult transferMosaics(String privateKey, String toAddress, List<MosaicTransfer> mosaics, int times, String message, int timeToLiveInSeconds) {

        Signer signer = new DefaultSigner(privateKey);
        String publicKey = signer.publicKey();

        int currentTime = nodeClient.extendedInfo().nisInfo.currentTime;

        Transaction transaction = mosaicsTransferTransaction(publicKey, toAddress, mosaics, times, message, currentTime, timeToLiveInSeconds);

        byte[] data = transactionEncoder.data(transaction);

        return feignTransactionClient.prepare(new RequestAnnounce(hexConverter.getString(data), signer.sign(data)));
    }

    @Override
    public NemAnnounceResult createMultisigAccount(String privateKey, List<String> cosignatories, int minCosignatories, int timeToLiveInSeconds) {

        Signer signer = new DefaultSigner(privateKey);

        int currentTime = nodeClient.extendedInfo().nisInfo.currentTime;

        List<Modification> modifications = cosignatories.stream().map(publicKey -> new Modification(1, publicKey)).collect(toList());

        Transaction transaction = aggregateModificationTransaction(signer.publicKey(), modifications, minCosignatories, currentTime, timeToLiveInSeconds);

        byte[] data = transactionEncoder.data(transaction);

        return feignTransactionClient.prepare(new RequestAnnounce(hexConverter.getString(data), signer.sign(data)));
    }

    @Override
    public NemAnnounceResult addCosignatoriesToMultisigAccount(String privateKey, List<String> cosignatories, int relativeChange, String multisigPublicKey, int timeToLiveInSeconds) {
        Signer signer = new DefaultSigner(privateKey);
        int currentTime = nodeClient.extendedInfo().nisInfo.currentTime;
        List<Modification> modifications = cosignatories.stream().map(cosignatory -> new Modification(ADD_COSIGNATORY.type, cosignatory)).collect(toList());
        return modifyMultisigAccountTransaction(signer, modifications, relativeChange, multisigPublicKey, currentTime, timeToLiveInSeconds);
    }

    @Override
    public NemAnnounceResult removeCosignatoriesFromMultisigAccount(String privateKey, List<String> cosignatories, int relativeChange, String multisigPublicKey, int timeToLiveInSeconds) {
        Signer signer = new DefaultSigner(privateKey);
        int currentTime = nodeClient.extendedInfo().nisInfo.currentTime;
        List<Modification> modifications = cosignatories.stream().map(cosignatory -> new Modification(REMOVE_COSIGNATORY.type, cosignatory)).collect(toList());
        return modifyMultisigAccountTransaction(signer, modifications, relativeChange, multisigPublicKey, currentTime, timeToLiveInSeconds);
    }

    @Override
    public NemAnnounceResult multisigTransferNem(String privateKey, String toAddress, long microXemAmount, String message, String multisigPublicKey, int timeToLiveInSeconds) {
        Signer signer = new DefaultSigner(privateKey);
        int currentTime = nodeClient.extendedInfo().nisInfo.currentTime;

        Transaction transferTransaction = transferNemTransaction(multisigPublicKey, toAddress, microXemAmount, message, currentTime, timeToLiveInSeconds);
        MultisigTransaction<Transaction> transaction = MultisigTransaction.<Transaction>builder()
                .type(MULTISIG_TRANSACTION.type)
                .version(versionProvider.version(network, MULTISIG_TRANSACTION))
                .timeStamp(currentTime)
                .signer(signer.publicKey())
                .fee(feeCalculator.multisigTransactionFee())
                .deadline(currentTime + timeToLiveInSeconds)
                .otherTrans(transferTransaction)
                .build();

        byte[] data = transactionEncoder.dataMultisigTransfer(transaction);

        return feignTransactionClient.prepare(new RequestAnnounce(hexConverter.getString(data), signer.sign(data)));
    }

    @Override
    public NemAnnounceResult multisigTransferMosaics(String privateKey, String toAddress, List<MosaicTransfer> mosaics, int times, String message, String multisigPublicKey, int timeToLiveInSeconds) {

        Signer signer = new DefaultSigner(privateKey);
        int currentTime = nodeClient.extendedInfo().nisInfo.currentTime;

        Transaction transferTransaction = mosaicsTransferTransaction(multisigPublicKey, toAddress, mosaics, times, message, currentTime, timeToLiveInSeconds);
        MultisigTransaction<Transaction> transaction = MultisigTransaction.<Transaction>builder()
                .type(MULTISIG_TRANSACTION.type)
                .version(versionProvider.version(network, MULTISIG_TRANSACTION))
                .timeStamp(currentTime)
                .signer(signer.publicKey())
                .fee(feeCalculator.multisigTransactionFee())
                .deadline(currentTime + timeToLiveInSeconds)
                .otherTrans(transferTransaction)
                .build();

        byte[] data = transactionEncoder.dataMultisigTransfer(transaction);

        return feignTransactionClient.prepare(new RequestAnnounce(hexConverter.getString(data), signer.sign(data)));
    }

    @Override
    public NemAnnounceResult multisigCreateNamespace(String privateKey, String parentNamespace, String namespace, String multisigPublicKey, int timeToLiveInSeconds) {
        Signer signer = new DefaultSigner(privateKey);
        int currentTime = nodeClient.extendedInfo().nisInfo.currentTime;

        ProvisionNamespaceTransaction provisionNamespaceTransaction = provisionNamespaceTransaction(multisigPublicKey, parentNamespace, namespace, currentTime, timeToLiveInSeconds);

        MultisigTransaction<ProvisionNamespaceTransaction> transaction = MultisigTransaction.<ProvisionNamespaceTransaction>builder()
                .type(MULTISIG_TRANSACTION.type)
                .version(versionProvider.version(network, MULTISIG_TRANSACTION))
                .timeStamp(currentTime)
                .signer(signer.publicKey())
                .fee(feeCalculator.multisigTransactionFee())
                .deadline(currentTime + timeToLiveInSeconds)
                .otherTrans(provisionNamespaceTransaction)
                .build();

        byte[] data = transactionEncoder.dataMultisigProvisionNamespace(transaction);

        return feignTransactionClient.prepare(new RequestAnnounce(hexConverter.getString(data), signer.sign(data)));
    }

    @Override
    public NemAnnounceResult multisigCreateMosaic(String privateKey, MosaicId mosaicId, String mosaicDescription, MosaicProperties mosaicProperties, Levy levy, String multisigPublicKey, int timeToLiveInSeconds) {
        Signer signer = new DefaultSigner(privateKey);
        int currentTime = nodeClient.extendedInfo().nisInfo.currentTime;

        MosaicDefinitionCreationTransaction mosaicDefinitionCreationTransaction = mosaicDefinitionCreationTransaction(mosaicId, mosaicDescription, mosaicProperties, levy, multisigPublicKey, currentTime, timeToLiveInSeconds);

        MultisigTransaction<MosaicDefinitionCreationTransaction> transaction = MultisigTransaction.<MosaicDefinitionCreationTransaction>builder()
                .type(MULTISIG_TRANSACTION.type)
                .version(versionProvider.version(network, MULTISIG_TRANSACTION))
                .timeStamp(currentTime)
                .signer(signer.publicKey())
                .fee(feeCalculator.multisigTransactionFee())
                .deadline(currentTime + timeToLiveInSeconds)
                .otherTrans(mosaicDefinitionCreationTransaction)
                .build();

        byte[] data = transactionEncoder.dataMultisigMosaicCreation(transaction);

        return feignTransactionClient.prepare(new RequestAnnounce(hexConverter.getString(data), signer.sign(data)));
    }

    @Override
    public NemAnnounceResult multisigChangeMosaicSupply(String privateKey, MosaicId mosaicId, SupplyType supplyType, long amount, String multisigPublicKey, int timeToLiveInSeconds) {
        Signer signer = new DefaultSigner(privateKey);
        int currentTime = nodeClient.extendedInfo().nisInfo.currentTime;

        MosaicSupplyChangeTransaction supplyChangeTransaction = mosaicSupplyChangeTransaction(mosaicId, supplyType, amount, multisigPublicKey, currentTime, timeToLiveInSeconds);

        MultisigTransaction<MosaicSupplyChangeTransaction> transaction = MultisigTransaction.<MosaicSupplyChangeTransaction>builder()
                .type(MULTISIG_TRANSACTION.type)
                .version(versionProvider.version(network, MULTISIG_TRANSACTION))
                .timeStamp(currentTime)
                .signer(signer.publicKey())
                .fee(feeCalculator.multisigTransactionFee())
                .deadline(currentTime + timeToLiveInSeconds)
                .otherTrans(supplyChangeTransaction)
                .build();

        byte[] data = transactionEncoder.dataMultisigMosaicSupplyChange(transaction);

        return feignTransactionClient.prepare(new RequestAnnounce(hexConverter.getString(data), signer.sign(data)));
    }

    @Override
    public NemAnnounceResult multisigImportanceTransfer(String privateKey, Action action, String remoteAccountPublicKey, String multisigPublicKey, int timeToLiveInSeconds) {

        Signer signer = new DefaultSigner(privateKey);
        int currentTime = nodeClient.extendedInfo().nisInfo.currentTime;
        String publicKey = signer.publicKey();

        ImportanceTransferTransaction importanceTransferTransaction = importanceTransferTransaction(action, remoteAccountPublicKey, timeToLiveInSeconds, currentTime, publicKey);

        MultisigTransaction<ImportanceTransferTransaction> transaction = MultisigTransaction.<ImportanceTransferTransaction>builder()
                .type(MULTISIG_TRANSACTION.type)
                .version(versionProvider.version(network, MULTISIG_TRANSACTION))
                .timeStamp(currentTime)
                .signer(signer.publicKey())
                .fee(feeCalculator.multisigTransactionFee())
                .deadline(currentTime + timeToLiveInSeconds)
                .otherTrans(importanceTransferTransaction)
                .build();

        byte[] data = transactionEncoder.dataMultisigImportanceTransfer(transaction);

        return feignTransactionClient.prepare(new RequestAnnounce(hexConverter.getString(data), signer.sign(data)));
    }

    @Override
    public NemAnnounceResult cosignTransaction(String privateKey, String transactionHash, String multisigAddress, int timeToLiveInSeconds) {
        Signer signer = new DefaultSigner(privateKey);
        int currentTime = nodeClient.extendedInfo().nisInfo.currentTime;

        Transaction transaction = Transaction.builder()
                .type(MULTISIG_SIGNATURE.type)
                .version(versionProvider.version(network, MULTISIG_SIGNATURE))
                .timeStamp(currentTime)
                .signer(signer.publicKey())
                .fee(feeCalculator.cosigningFee())
                .deadline(currentTime + timeToLiveInSeconds)
                .otherAccount(multisigAddress)
                .otherHash(new Hash(transactionHash))
                .build();

        byte[] data = transactionEncoder.data(transaction);

        return feignTransactionClient.prepare(new RequestAnnounce(hexConverter.getString(data), signer.sign(data)));
    }

    @Override
    public NemAnnounceResult createNamespace(String privateKey, String parentNamespace, String namespace, int timeToLiveInSeconds) {
        Signer signer = new DefaultSigner(privateKey);
        int currentTime = nodeClient.extendedInfo().nisInfo.currentTime;

        ProvisionNamespaceTransaction transaction = provisionNamespaceTransaction(signer.publicKey(), parentNamespace, namespace, currentTime, timeToLiveInSeconds);

        byte[] data = transactionEncoder.data(transaction);

        return feignTransactionClient.prepare(new RequestAnnounce(hexConverter.getString(data), signer.sign(data)));
    }

    @Override
    public NemAnnounceResult importanceTransfer(String privateKey, Action action, String remoteAccountPublicKey, int timeToLiveInSeconds) {
        Signer signer = new DefaultSigner(privateKey);
        int currentTime = nodeClient.extendedInfo().nisInfo.currentTime;
        String publicKey = signer.publicKey();

        ImportanceTransferTransaction transaction = importanceTransferTransaction(action, remoteAccountPublicKey, timeToLiveInSeconds, currentTime, publicKey);

        byte[] data = transactionEncoder.data(transaction);

        return feignTransactionClient.prepare(new RequestAnnounce(hexConverter.getString(data), signer.sign(data)));
    }

    private ImportanceTransferTransaction importanceTransferTransaction(Action action, String remoteAccountPublicKey, int timeToLiveInSeconds, int currentTime, String publicKey) {
        return ImportanceTransferTransaction.builder()
                .type(IMPORTANCE_TRANSFER_TRANSACTION.type)
                .version(versionProvider.version(network, IMPORTANCE_TRANSFER_TRANSACTION))
                .timeStamp(currentTime)
                .signer(publicKey)
                .fee(feeCalculator.importanceTransferFee())
                .deadline(currentTime + timeToLiveInSeconds)
                .action(action)
                .remoteAccount(remoteAccountPublicKey)
                .build();
    }

    @Override
    public NemAnnounceResult createMosaic(String privateKey, MosaicId mosaicId, String mosaicDescription, MosaicProperties mosaicProperties, Levy levy, int timeToLiveInSeconds) {
        Signer signer = new DefaultSigner(privateKey);
        int currentTime = nodeClient.extendedInfo().nisInfo.currentTime;
        String publicKey = signer.publicKey();

        MosaicDefinitionCreationTransaction mosaicDefinitionCreationTransaction = mosaicDefinitionCreationTransaction(mosaicId, mosaicDescription, mosaicProperties, levy, publicKey, currentTime, timeToLiveInSeconds);

        byte[] data = transactionEncoder.data(mosaicDefinitionCreationTransaction);

        return feignTransactionClient.prepare(new RequestAnnounce(hexConverter.getString(data), signer.sign(data)));
    }

    @Override
    public NemAnnounceResult changeMosaicSupply(String privateKey, MosaicId mosaicId, SupplyType supplyType, long amount, int timeToLiveInSeconds) {
        Signer signer = new DefaultSigner(privateKey);
        int currentTime = nodeClient.extendedInfo().nisInfo.currentTime;
        String publicKey = signer.publicKey();

        MosaicSupplyChangeTransaction transaction = mosaicSupplyChangeTransaction(mosaicId, supplyType, amount, publicKey, currentTime, timeToLiveInSeconds);

        byte[] data = transactionEncoder.data(transaction);

        return feignTransactionClient.prepare(new RequestAnnounce(hexConverter.getString(data), signer.sign(data)));
    }

    private MosaicSupplyChangeTransaction mosaicSupplyChangeTransaction(MosaicId mosaicId, SupplyType supplyType, long amount, String publicKey, int currentTime, int timeToLiveInSeconds) {
        return MosaicSupplyChangeTransaction.builder()
                .type(MOSAIC_SUPPLY_CHANGE.type)
                .version(versionProvider.version(network, MOSAIC_SUPPLY_CHANGE))
                .timeStamp(currentTime)
                .signer(publicKey)
                .fee(feeCalculator.importanceTransferFee())
                .deadline(currentTime + timeToLiveInSeconds)
                .mosaicId(mosaicId)
                .supplyType(supplyType)
                .delta(amount)
                .build();
    }

    private Transaction transferNemTransaction(String publicKey, String toAddress, long microXemAmount, String message, int currentTime, int timeToLiveInSeconds) {
        return Transaction.builder()
                .type(TRANSFER_NEM.type)
                .version(versionProvider.version(network, TRANSFER_NEM))
                .timeStamp(currentTime)
                .signer(publicKey)
                .fee(feeCalculator.fee(microXemAmount, message))
                .deadline(currentTime + timeToLiveInSeconds)
                .recipient(toAddress)
                .amount(microXemAmount)
                .message(new Message(message, 1))
                .build();
    }

    private MosaicDefinitionCreationTransaction mosaicDefinitionCreationTransaction(MosaicId mosaicId, String mosaicDescription, MosaicProperties mosaicProperties, Levy levy, String publicKey, int currentTime, int timeToLiveInSeconds) {
        MosaicDefinition mosaicDefinition = MosaicDefinition.builder()
                .creator(publicKey)
                .id(mosaicId)
                .description(mosaicDescription)
                .levy(levy)
                .properties(newArrayList(
                        new MosaicProperty("divisibility", String.valueOf(mosaicProperties.divisibility)),
                        new MosaicProperty("initialSupply", String.valueOf(mosaicProperties.initialSupply)),
                        new MosaicProperty("supplyMutable", String.valueOf(mosaicProperties.supplyMutable)),
                        new MosaicProperty("transferable", String.valueOf(mosaicProperties.transferable))
                ))
                .build();

        return MosaicDefinitionCreationTransaction.builder()
                .type(MOSAIC_DEFINITION_CREATION.type)
                .version(versionProvider.version(network, MOSAIC_DEFINITION_CREATION))
                .timeStamp(currentTime)
                .signer(publicKey)
                .fee(feeCalculator.mosaicCreationFee())
                .deadline(currentTime + timeToLiveInSeconds)
                .creationFee(feeCalculator.mosaicRentalFee())
                .creationFeeSink(network.creationFeeSink)
                .mosaicDefinition(mosaicDefinition)
                .build();
    }

    private ProvisionNamespaceTransaction provisionNamespaceTransaction(String publicKey, String parentNamespace, String namespace, int currentTime, int timeToLiveInSeconds) {
        return ProvisionNamespaceTransaction.builder()
                .type(PROVISION_NAMESPACE.type)
                .version(versionProvider.version(network, PROVISION_NAMESPACE))
                .timeStamp(currentTime)
                .signer(publicKey)
                .fee(feeCalculator.namespaceProvisionFee())
                .deadline(currentTime + timeToLiveInSeconds)
                .rentalFeeSink(network.rentalFeeSink)
                .rentalFee(feeCalculator.rentalFee(parentNamespace, namespace))
                .parent(parentNamespace)
                .newPart(namespace)
                .build();
    }

    private Transaction mosaicsTransferTransaction(String publicKey, String toAddress, List<MosaicTransfer> mosaics, int times, String message, int currentTime, int timeToLiveInSeconds) {
        return Transaction.builder()
                .type(TRANSFER_MOSAICS.type)
                .version(versionProvider.version(network, TRANSFER_MOSAICS))
                .timeStamp(currentTime)
                .signer(publicKey)
                .fee(feeCalculator.fee(mosaics, times, message))
                .deadline(currentTime + timeToLiveInSeconds)
                .recipient(toAddress)
                .amount(times * TEN.pow(6).longValue())
                .message(new Message(message, 1))
                .mosaics(mosaics)
                .build();
    }

    private Transaction aggregateModificationTransaction(String publicKey, List<Modification> modifications, int minCosignatoriesRelativeChange, int currentTime, int timeToLiveInSeconds) {
        return Transaction.builder()
                .type(MULTISIG_AGGREGATE_MODIFICATION.type)
                .version(versionProvider.version(network, MULTISIG_AGGREGATE_MODIFICATION))
                .timeStamp(currentTime)
                .signer(publicKey)
                .fee(feeCalculator.multisigAccountCreationFee())
                .deadline(currentTime + timeToLiveInSeconds)
                .modifications(modifications)
                .minCosignatories(new RelativeChange(minCosignatoriesRelativeChange))
                .build();
    }

    private NemAnnounceResult modifyMultisigAccountTransaction(Signer signer, List<Modification> modifications, int relativeChange, String multisigPublicKey, int currentTime, int timeToLiveInSeconds) {
        Transaction modificationTransaction = aggregateModificationTransaction(multisigPublicKey, modifications, relativeChange, currentTime, timeToLiveInSeconds);

        Transaction transaction = Transaction.builder()
                .type(MULTISIG_TRANSACTION.type)
                .version(versionProvider.version(network, MULTISIG_TRANSACTION))
                .timeStamp(currentTime)
                .signer(signer.publicKey())
                .fee(feeCalculator.multisigTransactionFee())
                .deadline(currentTime + timeToLiveInSeconds)
                .otherTrans(modificationTransaction)
                .build();

        byte[] data = transactionEncoder.data(transaction);

        return feignTransactionClient.prepare(new RequestAnnounce(hexConverter.getString(data), signer.sign(data)));
    }
}
