package io.nem.client.node;

import io.nem.client.DefaultNemClientFactory;
import io.nem.client.blockchain.response.block.BlockHeight;
import io.nem.client.node.request.ApplicationMetaData;
import io.nem.client.node.request.BootNodeRequest;
import io.nem.client.node.request.PrivateIdentity;
import io.nem.client.node.response.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeClientTest {

    private final String ip = "153.122.112.137";
    private final NodeClient nodeClient = new DefaultNemClientFactory().createNodeClient("http://" + ip + ":7890");

    private final String privateKey = "0476fd96242ac5ef6cb1b268887254c1a3089759556beb1ce660c0cb2c42bb27";

    @Test
    void getNodeInfo() {
        Node info = nodeClient.info();
        assertEquals(ip, info.endpoint.host);

        ExtendedNodeInfo extendedNodeInfo = nodeClient.extendedInfo();
        assertEquals(info, extendedNodeInfo.node);
    }

    @Test
    void getPeersList() {
        PeersList peersList = nodeClient.peersList();
        assertNotNull(peersList);
    }

    @Test
    void getActivePeers() {
        PeersList peersList = nodeClient.peersList();
        NodeCollection active = nodeClient.active();
        assertEquals(peersList.active, active.data);
    }

    @Test
    void getActiveBroadcastPeers() {
        NodeCollection nodeCollection = nodeClient.activeBroadcasts();
        assertNotNull(nodeCollection);
    }

    @Test
    void getMaxChainHeight() {
        BlockHeight blockHeight = nodeClient.maxChainHeight();
        assertTrue(blockHeight.height > 0);
    }

    @Test
    void getNodeExperiences() {
        NodeExperiencesResponse experiences = nodeClient.experiences();
        assertTrue(experiences.data.size() > 0);
    }

    @Test
    @Disabled("only for local NIS nodes")
    void bootNode() {
        BootNodeRequest bootNodeRequest = new BootNodeRequest(new ApplicationMetaData("application"), new Endpoint("http", "127.0.0.1", 7890), new PrivateIdentity("test", privateKey));
        nodeClient.bootNode(bootNodeRequest);
    }
}