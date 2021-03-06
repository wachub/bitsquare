package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.messages.peers.GetPeersRequest;
import io.bitsquare.p2p.peers.messages.peers.GetPeersResponse;
import io.bitsquare.p2p.peers.messages.peers.PeerExchangeMessage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PeerExchangeManager implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(PeerExchangeManager.class);

    private final NetworkNode networkNode;
    private final Supplier<Set<ReportedPeer>> authenticatedAndReportedPeersSupplier;
    private final Supplier<Map<Address, Peer>> authenticatedPeersSupplier;
    private final Consumer<Address> removePeerConsumer;
    private final BiConsumer<HashSet<ReportedPeer>, Connection> addReportedPeersConsumer;

    private Timer getPeersTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PeerExchangeManager(NetworkNode networkNode,
                               Supplier<Set<ReportedPeer>> authenticatedAndReportedPeersSupplier,
                               Supplier<Map<Address, Peer>> authenticatedPeersSupplier,
                               Consumer<Address> removePeerConsumer,
                               BiConsumer<HashSet<ReportedPeer>, Connection> addReportedPeersConsumer) {
        this.networkNode = networkNode;
        this.authenticatedAndReportedPeersSupplier = authenticatedAndReportedPeersSupplier;
        this.authenticatedPeersSupplier = authenticatedPeersSupplier;
        this.removePeerConsumer = removePeerConsumer;
        this.addReportedPeersConsumer = addReportedPeersConsumer;

        networkNode.addMessageListener(this);
        startGetPeersTimer();
    }

    public void shutDown() {
        Log.traceCall();
        if (getPeersTimer != null)
            getPeersTimer.cancel();

        networkNode.removeMessageListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof PeerExchangeMessage) {
            Log.traceCall(message.toString());
            if (message instanceof GetPeersRequest) {
                GetPeersRequest getPeersRequestMessage = (GetPeersRequest) message;
                HashSet<ReportedPeer> reportedPeers = getPeersRequestMessage.reportedPeers;
                log.trace("Received peers: " + reportedPeers);

                SettableFuture<Connection> future = networkNode.sendMessage(connection,
                        new GetPeersResponse(new HashSet<>(authenticatedAndReportedPeersSupplier.get())));
                Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        log.trace("GetPeersResponse sent successfully");
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        log.info("GetPeersResponse sending failed " + throwable.getMessage());
                        removePeerConsumer.accept(getPeersRequestMessage.senderAddress);
                    }
                });
                addReportedPeersConsumer.accept(reportedPeers, connection);
            } else if (message instanceof GetPeersResponse) {
                GetPeersResponse getPeersResponse = (GetPeersResponse) message;
                HashSet<ReportedPeer> reportedPeers = getPeersResponse.reportedPeers;
                log.trace("Received peers: " + reportedPeers);
                addReportedPeersConsumer.accept(reportedPeers, connection);
            }
        }
    }

    private void startGetPeersTimer() {
        Log.traceCall();
        if (getPeersTimer != null)
            getPeersTimer.cancel();

        getPeersTimer = UserThread.runAfterRandomDelay(() -> {
            trySendGetPeersRequest();
            startGetPeersTimer();
        }, 2, 4, TimeUnit.MINUTES);
    }

    private void trySendGetPeersRequest() {
        Set<Peer> connectedPeersList = new HashSet<>(authenticatedPeersSupplier.get().values());
        if (!connectedPeersList.isEmpty()) {
            Log.traceCall();
            connectedPeersList.stream()
                    .forEach(e -> UserThread.runAfterRandomDelay(() -> {
                        SettableFuture<Connection> future = networkNode.sendMessage(e.connection,
                                new GetPeersRequest(networkNode.getAddress(), new HashSet<>(authenticatedAndReportedPeersSupplier.get())));
                        Futures.addCallback(future, new FutureCallback<Connection>() {
                            @Override
                            public void onSuccess(Connection connection) {
                                log.trace("sendGetPeersRequest sent successfully");
                            }

                            @Override
                            public void onFailure(@NotNull Throwable throwable) {
                                log.info("sendGetPeersRequest sending failed " + throwable.getMessage());
                                removePeerConsumer.accept(e.address);
                            }
                        });
                    }, 3, 5, TimeUnit.SECONDS));
        }
    }
}
