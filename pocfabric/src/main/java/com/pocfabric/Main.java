package com.pocfabric;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.exception.TransactionException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

import java.io.*;
import java.security.PrivateKey;
import java.security.Security;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Main {
private final static String BASE_DIR = "C:\\Users\\Public\\Documents\\pocfabric\\basic-network\\";

    private final static String CRYPTO_CONFIG_DIR = BASE_DIR + "crypto-config\\";

    private final static String DOCKER_HOST = "localhost";
    
    private final static ChaincodeID CHAINCODE_ID = ChaincodeID.newBuilder()
            .setName("poc_cc_go")
            .setPath("poc_cc")
            .setVersion("1")
            .build();

    public static void main(String[] args) {

        try {
            if ("init".equals(args[0])) {
                initChaincode();
            } else if ("transfer".equals(args[0])) {
                String user = args[1];
                String quantity = args[2];

                transfer(user, quantity);
            } else if ("balance".equals(args[0])) {
                String user = args[1];

                balance(user);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static void balance(String user) throws IOException, CryptoException, InvalidArgumentException, TransactionException, ProposalException {
        HFClient client = setupUser1Client();

        Channel channel = setupChannel(client);

        TransactionProposalRequest queryRequest = client.newTransactionProposalRequest();

        queryRequest.setChaincodeID(CHAINCODE_ID);
        queryRequest.setFcn("invoke");
        queryRequest.setArgs(new String[] {"query", user});

        Collection<ProposalResponse> queryResponse = channel.sendTransactionProposal(queryRequest, channel.getPeers());

        out(user + " balance: " + new String(queryResponse.iterator().next().getChaincodeActionResponsePayload()));
    }

    private static void transfer(String user, String quantity) throws IOException, CryptoException, InvalidArgumentException, TransactionException, ProposalException {
        HFClient client = setupUser1Client();
        TransactionProposalRequest transactionProposalRequest = client.newTransactionProposalRequest();
        transactionProposalRequest.setChaincodeID(CHAINCODE_ID);
        transactionProposalRequest.setFcn("invoke");
        transactionProposalRequest.setArgs(new String[] {"move", "user1", user, quantity});

        Channel channel = setupChannel(client);
        Collection<ProposalResponse> transactionPropResp = channel.sendTransactionProposal(transactionProposalRequest, channel.getPeers());

        channel.sendTransaction(transactionPropResp);
    }

    private static Channel setupChannel(HFClient client) throws InvalidArgumentException, TransactionException {
        Orderer orderer = client.newOrderer("orderer.example.com", "grpc://"+DOCKER_HOST+":7050");

        Peer peer = client.newPeer("peer0.testorg.example.com", "grpc://"+DOCKER_HOST+":7051");

        Channel channel = client.newChannel("pocchannel");

        channel.addOrderer(orderer);
        channel.addPeer(peer);
        channel.initialize();
        return channel;
    }

    private static void initChaincode() throws IOException, CryptoException, InvalidArgumentException, ProposalException, Exception {
        HFClient adminClient = setupAdminClient();

        InstallProposalRequest installProposalRequest = adminClient.newInstallProposalRequest();
        installProposalRequest.setChaincodeID(CHAINCODE_ID);
        installProposalRequest.setChaincodeSourceLocation(new File(BASE_DIR + "chaincode"));
        installProposalRequest.setChaincodeVersion(CHAINCODE_ID.getVersion());

        Collection<ProposalResponse> installProposalResponse = adminClient.sendInstallProposal(installProposalRequest, Collections.singletonList(adminClient.newPeer("peer0.testorg.example.com", "grpc://"+DOCKER_HOST+":7051")));


        InstantiateProposalRequest instantiateProposalRequest = adminClient.newInstantiationProposalRequest();
        instantiateProposalRequest.setProposalWaitTime(40000);
        instantiateProposalRequest.setChaincodeID(CHAINCODE_ID);
        instantiateProposalRequest.setFcn("init");
        instantiateProposalRequest.setArgs(new String[] {"user1", "100", "user2", "100"});
        Map<String, byte[]> tm = new HashMap<>();
        instantiateProposalRequest.setTransientMap(tm);


        ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
        chaincodeEndorsementPolicy.fromYamlFile(new File(BASE_DIR + "chaincodeendorsementpolicy.yaml"));
        instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);

        Channel channel = setupChannel(adminClient);
        Collection<ProposalResponse> instantiationProposalResponse = channel.sendInstantiationProposal(instantiateProposalRequest, channel.getPeers());

        Orderer orderer = adminClient.newOrderer("orderer.example.com", "grpc://"+DOCKER_HOST+":7050");
        channel.addOrderer(orderer);
        channel.sendTransaction(instantiationProposalResponse, Collections.singletonList(orderer));

    }

    private static HFClient setupUser1Client() throws IOException, CryptoException, InvalidArgumentException {
        HFClient client = HFClient.createNewInstance();

        final String certificate = new String(IOUtils.toByteArray(new FileInputStream(new File(CRYPTO_CONFIG_DIR +
                "peerOrganizations\\testorg.example.com\\users\\User1@testorg.example.com\\msp\\signcerts\\User1@testorg.example.com-cert.pem"))), "UTF-8");

        final PrivateKey privateKey = getPrivateKeyFromBytes(IOUtils.toByteArray(new FileInputStream(new File(CRYPTO_CONFIG_DIR +
                "peerOrganizations\\testorg.example.com\\users\\User1@testorg.example.com\\msp\\keystore\\9bea5f491cec00790427d9b63d25d7cd8562ae4c6c4afda1f76446012c6da751_sk"))));

        Enrollment user1Enrollment = new Enrollment() {
            @Override
            public PrivateKey getKey() {
                return privateKey;
            }

            @Override
            public String getCert() {
                return certificate;
            }
        };

        User user1 = new User() {
            @Override
            public String getName() {
                return "User1";
            }

            @Override
            public Set<String> getRoles() {
                return null;
            }

            @Override
            public String getAccount() {
                return null;
            }

            @Override
            public String getAffiliation() {
                return null;
            }

            @Override
            public Enrollment getEnrollment() {
                return user1Enrollment;
            }

            @Override
            public String getMspId() {
                return "TestOrgMSP";
            }
        };

        CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();
        client.setCryptoSuite(cryptoSuite);
        client.setUserContext(user1);
        return client;
    }


    private static HFClient setupAdminClient() throws IOException, CryptoException, InvalidArgumentException {
        HFClient client = HFClient.createNewInstance();

        final String certificate = new String(IOUtils.toByteArray(new FileInputStream(new File(CRYPTO_CONFIG_DIR +
                "peerOrganizations\\testorg.example.com\\users\\Admin@testorg.example.com\\msp\\signcerts\\Admin@testorg.example.com-cert.pem"))), "UTF-8");

        final PrivateKey privateKey = getPrivateKeyFromBytes(IOUtils.toByteArray(new FileInputStream(new File(CRYPTO_CONFIG_DIR +
                "peerOrganizations\\testorg.example.com\\users\\Admin@testorg.example.com\\msp\\keystore\\6509f02d9f52d1920957b64921d0d64cdd87cc5a89c1783aae1d206a30fcb5d1_sk"))));

        Enrollment user1Enrollment = new Enrollment() {
            @Override
            public PrivateKey getKey() {
                return privateKey;
            }

            @Override
            public String getCert() {
                return certificate;
            }
        };

        User user1 = new User() {
            @Override
            public String getName() {
                return "Admin";
            }

            @Override
            public Set<String> getRoles() {
                return null;
            }

            @Override
            public String getAccount() {
                return null;
            }

            @Override
            public String getAffiliation() {
                return null;
            }

            @Override
            public Enrollment getEnrollment() {
                return user1Enrollment;
            }

            @Override
            public String getMspId() {
                return "TestOrgMSP";
            }
        };

        CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();
        client.setCryptoSuite(cryptoSuite);
        client.setUserContext(user1);
        return client;
    }

    static void out(String str) {
        System.out.println(str);
    }

    static PrivateKey getPrivateKeyFromBytes(byte[] data) throws IOException {
        Security.addProvider(new BouncyCastleProvider());

        final Reader pemReader = new StringReader(new String(data));

        final PrivateKeyInfo pemPair;
        try (PEMParser pemParser = new PEMParser(pemReader)) {
            pemPair = (PrivateKeyInfo) pemParser.readObject();
        }

        PrivateKey privateKey = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getPrivateKey(pemPair);

        return privateKey;
    }

}
