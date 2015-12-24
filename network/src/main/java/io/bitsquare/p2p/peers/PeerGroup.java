package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.network.*;
import io.bitsquare.p2p.peers.messages.auth.AuthenticationRejection;
import io.bitsquare.p2p.peers.messages.auth.AuthenticationRequest;
import io.bitsquare.p2p.storage.messages.DataBroadcastMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

public class PeerGroup implements MessageListener, ConnectionListener {
    private static final Logger log = LoggerFactory.getLogger(PeerGroup.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static int MAX_CONNECTIONS_LOW_PRIORITY;
    private static int MAX_CONNECTIONS_NORMAL_PRIORITY;
    private static int MAX_CONNECTIONS_HIGH_PRIORITY;

    public static void setMaxConnectionsLowPriority(int maxConnectionsLowPriority) {
        MAX_CONNECTIONS_LOW_PRIORITY = maxConnectionsLowPriority;
        MAX_CONNECTIONS_NORMAL_PRIORITY = MAX_CONNECTIONS_LOW_PRIORITY + 4;
        MAX_CONNECTIONS_HIGH_PRIORITY = MAX_CONNECTIONS_NORMAL_PRIORITY + 4;
    }

    static {
        setMaxConnectionsLowPriority(18);
    }

    private static final int MAX_REPORTED_PEERS = 1000;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final MaintenanceManager maintenanceManager;
    private final PeerExchangeManager peerExchangeManager;

    private final CopyOnWriteArraySet<AuthenticationListener> authenticationListeners = new CopyOnWriteArraySet<>();
    private final Map<Address, Peer> authenticatedPeers = new HashMap<>();
    private final Set<ReportedPeer> reportedPeers = new HashSet<>();
    private final List<Address> remainingSeedNodes = new ArrayList<>();
    private final Map<Address, AuthenticationHandshake> authenticationHandshakes = new HashMap<>();
    private Optional<Set<Address>> seedNodeAddressesOptional = Optional.empty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PeerGroup(NetworkNode networkNode) {
        Log.traceCall();

        this.networkNode = networkNode;

        networkNode.addMessageListener(this);
        networkNode.addConnectionListener(this);

        maintenanceManager = new MaintenanceManager(networkNode,
                () -> getAuthenticatedPeers(),
                address -> removePeer(address));
        peerExchangeManager = new PeerExchangeManager(networkNode,
                () -> getAuthenticatedAndReportedPeers(),
                () -> getAuthenticatedPeers(),
                address -> removePeer(address),
                (newReportedPeers, connection) -> addToReportedPeers(newReportedPeers, connection));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
    }

    @Override
    public void onDisconnect(Reason reason, Connection connection) {
        log.debug("onDisconnect connection=" + connection + " / reason=" + reason);
        connection.getPeerAddress().ifPresent(peerAddress -> removePeer(peerAddress));
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof AuthenticationRequest)
            processAuthenticationRequest((AuthenticationRequest) message, connection);
        else if (message instanceof AuthenticationRejection)
            processAuthenticationRejection((AuthenticationRejection) message);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void broadcast(DataBroadcastMessage message, @Nullable Address sender) {
        Log.traceCall("Sender " + sender + ". Message " + message.toString());
        if (authenticatedPeers.values().size() > 0) {
            log.info("Broadcast message to {} peers. Message: {}", authenticatedPeers.values().size(), message);
            authenticatedPeers.values().stream()
                    .filter(e -> !e.address.equals(sender))
                    .forEach(peer -> UserThread.runAfterRandomDelay(() -> {
                                // as we use a delay we need to check again if our peer is still in the authenticated list
                                if (authenticatedPeers.containsValue(peer)) {
                                    final Address address = peer.address;
                                    log.trace("Broadcast message from " + getMyAddress() + " to " + address + ".");
                                    SettableFuture<Connection> future = networkNode.sendMessage(address, message);
                                    Futures.addCallback(future, new FutureCallback<Connection>() {
                                        @Override
                                        public void onSuccess(Connection connection) {
                                            log.trace("Broadcast from " + getMyAddress() + " to " + address + " succeeded.");
                                        }

                                        @Override
                                        public void onFailure(@NotNull Throwable throwable) {
                                            log.info("Broadcast failed. " + throwable.getMessage());
                                            UserThread.execute(() -> removePeer(address));
                                        }
                                    });
                                } else {
                                    log.debug("Peer is not in our authenticated list anymore. " +
                                            "That can happen as we use a delay in the loop for the broadcast. " +
                                            "Peer.address={}", peer.address);
                                }
                            },
                            10, 100, TimeUnit.MILLISECONDS));
        } else {
            log.info("Message not broadcasted because we have no authenticated peers yet. " +
                    "message = {}", message);
        }
    }

    public void shutDown() {
        Log.traceCall();
        maintenanceManager.shutDown();
        peerExchangeManager.shutDown();

        networkNode.removeMessageListener(this);
        networkNode.removeConnectionListener(this);
    }

    public void addAuthenticationListener(AuthenticationListener listener) {
        authenticationListeners.add(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Process incoming authentication messages
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void processAuthenticationRequest(AuthenticationRequest message, final Connection connection) {
        Log.traceCall(message.toString());
        Address peerAddress = message.senderAddress;

        // We set the address to the connection, otherwise we will not find the connection when sending
        // a reject message and we would create a new outbound connection instead using the inbound.
        connection.setPeerAddress(message.senderAddress);

        if (!authenticatedPeers.containsKey(peerAddress)) {
            AuthenticationHandshake authenticationHandshake;
            if (!authenticationHandshakes.containsKey(peerAddress)) {
                log.info("We got an incoming AuthenticationRequest for the peerAddress {}. " +
                        "We create an AuthenticationHandshake.", peerAddress);
                log.trace("message={}", message);
                log.trace("connection={}", connection);
                // We protect that connection from getting closed by maintenance cleanup...
                connection.setConnectionPriority(ConnectionPriority.AUTH_REQUEST);
                authenticationHandshake = new AuthenticationHandshake(networkNode,
                        getMyAddress(),
                        peerAddress,
                        () -> getAuthenticatedAndReportedPeers(),
                        (newReportedPeers, connection1) -> addToReportedPeers(newReportedPeers, connection1)
                );
                authenticationHandshakes.put(peerAddress, authenticationHandshake);
                doRespondToAuthenticationRequest(message, connection, peerAddress, authenticationHandshake);
            } else {
                log.info("We got an incoming AuthenticationRequest but we have started ourselves already " +
                        "an authentication handshake for that peerAddress ({})", peerAddress);
                log.debug("We avoid such race conditions by rejecting the request if the hashCode of our address ({}) is " +
                                "smaller then the hashCode of the peers address ({}).", getMyAddress().hashCode(),
                        message.senderAddress.hashCode());

                authenticationHandshake = authenticationHandshakes.get(peerAddress);

                if (getMyAddress().hashCode() < peerAddress.hashCode()) {
                    log.info("We reject the authentication request and keep our own request alive.");
                    rejectAuthenticationRequest(peerAddress);
                } else {
                    log.info("We accept the authentication request but cancel our own request.");
                    cancelOwnAuthenticationRequest(peerAddress);

                    doRespondToAuthenticationRequest(message, connection, peerAddress, authenticationHandshake);
                }
            }
        } else {
            log.info("We got an incoming AuthenticationRequest but we are already authenticated to that peer " +
                    "with peerAddress {}.\n" +
                    "That might happen in some race conditions. We reject the request.", peerAddress);
            rejectAuthenticationRequest(peerAddress);
        }
    }

    private void processAuthenticationRejection(AuthenticationRejection message) {
        Log.traceCall(message.toString());
        Address peerAddress = message.senderAddress;

        cancelOwnAuthenticationRequest(peerAddress);
    }

    private void doRespondToAuthenticationRequest(AuthenticationRequest message, Connection connection,
                                                  Address peerAddress, AuthenticationHandshake authenticationHandshake) {
        Log.traceCall(message.toString());
        SettableFuture<Connection> future = authenticationHandshake.respondToAuthenticationRequest(message, connection);
        Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        log.info("We got the peer ({}) who requested authentication authenticated.", peerAddress);
                        addAuthenticatedPeer(connection, peerAddress);
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        log.info("Authentication with peer who requested authentication failed.\n" +
                                "That can happen if the peer went offline. " + throwable.getMessage());
                        removePeer(peerAddress);
                    }
                }
        );
    }

    private void cancelOwnAuthenticationRequest(Address peerAddress) {
        Log.traceCall();
        if (authenticationHandshakes.containsKey(peerAddress)) {
            authenticationHandshakes.get(peerAddress).cancel();
            authenticationHandshakes.remove(peerAddress);
        }
    }

    private void rejectAuthenticationRequest(Address peerAddress) {
        Log.traceCall();
        networkNode.sendMessage(peerAddress, new AuthenticationRejection(getMyAddress()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Authentication to seed node
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void authenticateToSeedNode(Address peerAddress, Set<Address> seedNodeAddresses) {
        Log.traceCall();
        seedNodeAddressesOptional = Optional.of(seedNodeAddresses);
        remainingSeedNodes.addAll(seedNodeAddresses);
        remainingSeedNodes.remove(peerAddress);
        authenticateToFirstSeedNode(peerAddress);
    }

    private void authenticateToFirstSeedNode(Address peerAddress) {
        Log.traceCall();
        if (!maxConnectionsForAuthReached()) {

            log.info("We try to authenticate to seed node {}.", peerAddress);
            authenticate(peerAddress, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(Connection connection) {
                    log.info("We got our first seed node authenticated. " +
                            "We try if there are reported peers available to authenticate.");

                    addAuthenticatedPeer(connection, peerAddress);
                    authenticateToRemainingReportedPeer();
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    log.info("Authentication to " + peerAddress + " failed." +
                            "\nThat is expected if seed nodes are offline." +
                            "\nException:" + throwable.getMessage());

                    removePeer(peerAddress);

                    if (remainingSeedNodesAvailable()) {
                        log.info("We try another random seed node for first authentication attempt.");
                        authenticateToFirstSeedNode(getAndRemoveRandomAddress(remainingSeedNodes));
                    } else {
                        log.info("There are no seed nodes available for authentication. " +
                                "We try if there are reported peers available to authenticate.");
                        authenticateToRemainingReportedPeer();
                    }
                }
            });
        } else {
            log.info("We have already enough connections.");
        }
    }

    private void authenticateToRemainingSeedNode() {
        Log.traceCall();
        if (!maxConnectionsForAuthReached()) {
            if (remainingSeedNodesAvailable()) {
                Address peerAddress = getAndRemoveRandomAddress(remainingSeedNodes);
                log.info("We try to authenticate to seed node {}.", peerAddress);
                authenticate(peerAddress, new FutureCallback<Connection>() {
                            @Override
                            public void onSuccess(Connection connection) {
                                log.info("We got a seed node authenticated. " +
                                        "We try if there are more seed nodes available to authenticate.");

                                addAuthenticatedPeer(connection, peerAddress);
                                authenticateToRemainingSeedNode();
                            }

                            @Override
                            public void onFailure(@NotNull Throwable throwable) {
                                log.info("Authentication to " + peerAddress + " failed." +
                                        "\nThat is expected if the seed node is offline." +
                                        "\nException:" + throwable.getMessage());

                                removePeer(peerAddress);

                                log.info("We try another random seed node for authentication.");
                                authenticateToRemainingSeedNode();
                            }
                        }

                );
            } else if (reportedPeersAvailable()) {
                authenticateToRemainingReportedPeer();
            } else {
                log.info("We don't have seed nodes or reported peers available. We will try again after a random pause.");
                UserThread.runAfterRandomDelay(() -> authenticateToRemainingReportedPeer(),
                        10, 20, TimeUnit.SECONDS);
            }
        } else {
            log.info("We have already enough connections.");
        }
    }

    private Address getAndRemoveRandomAddress(List<Address> list) {
        checkArgument(!list.isEmpty(), "List must not be empty");
        return list.remove(new Random().nextInt(list.size()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Authentication to reported peers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void authenticateToRemainingReportedPeer() {
        Log.traceCall();
        if (!maxConnectionsForAuthReached()) {
            if (reportedPeersAvailable()) {
                if (getAndRemoveNotAuthenticatingReportedPeer().isPresent()) {
                    Address peerAddress = getAndRemoveNotAuthenticatingReportedPeer().get().address;
                    removeFromReportedPeers(peerAddress);
                    log.info("We try to authenticate to peer {}.", peerAddress);
                    authenticate(peerAddress, new FutureCallback<Connection>() {
                        @Override
                        public void onSuccess(Connection connection) {
                            log.info("We got a peer authenticated. " +
                                    "We try if there are more reported peers available to authenticate.");

                            addAuthenticatedPeer(connection, peerAddress);
                            authenticateToRemainingReportedPeer();
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            log.info("Authentication to " + peerAddress + " failed." +
                                    "\nThat is expected if the peer is offline." +
                                    "\nException:" + throwable.getMessage());

                            removePeer(peerAddress);

                            log.info("We try another random seed node for authentication.");
                            authenticateToRemainingReportedPeer();
                        }
                    });
                } else {
                    log.info("We don't have a reported peers available (maybe one is authenticating already). We will try again after a random pause.");
                    UserThread.runAfterRandomDelay(() -> authenticateToRemainingReportedPeer(),
                            10, 20, TimeUnit.SECONDS);
                }
            } else if (remainingSeedNodesAvailable()) {
                authenticateToRemainingSeedNode();
            } else {
                log.info("We don't have seed nodes or reported peers available. We will try again after a random pause.");
                UserThread.runAfterRandomDelay(() -> authenticateToRemainingReportedPeer(),
                        30, 40, TimeUnit.SECONDS);
            }
        } else {
            log.info("We have already enough connections.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Authentication to peer used for direct messaging
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Priority is set when we receive a decrypted mail message as those are used for direct messages
    public void authenticateToDirectMessagePeer(Address peerAddress,
                                                @Nullable Runnable completeHandler,
                                                @Nullable Runnable faultHandler) {
        Log.traceCall(peerAddress.getFullAddress());

        if (authenticatedPeers.containsKey(peerAddress)) {
            log.warn("We have that peer already authenticated. That should never happen. peerAddress={}", peerAddress);
            if (completeHandler != null)
                completeHandler.run();
        } else if (authenticationHandshakes.containsKey(peerAddress)) {
            log.info("We are in the process to authenticate to that peer. peerAddress={}", peerAddress);
            Optional<SettableFuture<Connection>> resultFutureOptional = authenticationHandshakes.get(peerAddress).getResultFutureOptional();
            if (resultFutureOptional.isPresent()) {
                Futures.addCallback(resultFutureOptional.get(), new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        if (completeHandler != null)
                            completeHandler.run();
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        if (faultHandler != null)
                            faultHandler.run();
                    }
                });
            } else {
                log.warn("We are in the process to authenticate to that peer but the future object is not set. " +
                        "That should not happen. peerAddress={}", peerAddress);
                if (faultHandler != null)
                    faultHandler.run();
            }
        } else {
            log.info("We try to authenticate to peer {} for sending a private message.", peerAddress);
            authenticate(peerAddress, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(Connection connection) {
                    log.info("We got a new peer for sending a private message authenticated.");

                    addAuthenticatedPeer(connection, peerAddress);
                    if (completeHandler != null)
                        completeHandler.run();
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    log.error("Authentication to " + peerAddress + " for sending a private message failed." +
                            "\nSeems that the peer is offline." +
                            "\nException:" + throwable.getMessage());
                    removePeer(peerAddress);
                    if (faultHandler != null)
                        faultHandler.run();
                }
            });
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Authentication private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void authenticate(Address peerAddress, FutureCallback<Connection> futureCallback) {
        Log.traceCall(peerAddress.getFullAddress());
        if (!authenticationHandshakes.containsKey(peerAddress)) {
            log.info("We create an AuthenticationHandshake to authenticate to peer {}.", peerAddress);
            AuthenticationHandshake authenticationHandshake = new AuthenticationHandshake(networkNode,
                    getMyAddress(),
                    peerAddress,
                    () -> getAuthenticatedAndReportedPeers(),
                    (newReportedPeers, connection) -> addToReportedPeers(newReportedPeers, connection)
            );
            authenticationHandshakes.put(peerAddress, authenticationHandshake);
            SettableFuture<Connection> authenticationFuture = authenticationHandshake.requestAuthentication();
            Futures.addCallback(authenticationFuture, futureCallback);
        } else {
            log.warn("An authentication handshake is already created for that peerAddress ({}). That should not happen", peerAddress);
        }
    }

    private void addAuthenticatedPeer(Connection connection, Address peerAddress) {
        Log.traceCall(peerAddress.getFullAddress());

        connection.setPeerAddress(peerAddress);
        connection.setAuthenticated();

        if (authenticationHandshakes.containsKey(peerAddress))
            authenticationHandshakes.remove(peerAddress);

        log.info("\n\n############################################################\n" +
                "We are authenticated to:" +
                "\nconnection=" + connection.getUid()
                + "\nmyAddress=" + getMyAddress()
                + "\npeerAddress= " + peerAddress
                + "\n############################################################\n");

        authenticatedPeers.put(peerAddress, new Peer(connection, peerAddress));

        removeFromReportedPeers(peerAddress);

        if (!checkIfConnectedPeersExceeds())
            printAuthenticatedPeers();

        authenticationListeners.stream().forEach(e -> e.onPeerAuthenticated(peerAddress, connection));
    }

    void removePeer(@Nullable Address peerAddress) {
        Log.traceCall("peerAddress=" + peerAddress);
        if (peerAddress != null) {
            if (authenticationHandshakes.containsKey(peerAddress))
                authenticationHandshakes.remove(peerAddress);

            removeFromReportedPeers(peerAddress);

            Peer disconnectedPeer = authenticatedPeers.remove(peerAddress);
            if (disconnectedPeer != null)
                printAuthenticatedPeers();
        }
    }

    private void removeFromReportedPeers(Address peerAddress) {
        reportedPeers.remove(new ReportedPeer(peerAddress));
    }

    private boolean maxConnectionsForAuthReached() {
        return authenticatedPeers.size() >= MAX_CONNECTIONS_LOW_PRIORITY;
    }

    private boolean remainingSeedNodesAvailable() {
        return !remainingSeedNodes.isEmpty();
    }

    private boolean reportedPeersAvailable() {
        return !reportedPeers.isEmpty();
    }

    private boolean checkIfConnectedPeersExceeds() {
        Log.traceCall();
        int size = authenticatedPeers.size();
        if (size > PeerGroup.MAX_CONNECTIONS_LOW_PRIORITY) {
            Set<Connection> allConnections = networkNode.getAllConnections();
            int allConnectionsSize = allConnections.size();
            log.info("We have {} connections open. Lets remove the passive connections" +
                    " which have not been active recently.", allConnectionsSize);
            if (size != allConnectionsSize) {
                log.warn("authenticatedPeers.size()!=allConnections.size(). There is some inconsistency.");
                log.debug("authenticatedPeers={}", authenticatedPeers);
                log.debug("networkNode.getAllConnections()={}", networkNode.getAllConnections());
            }
            List<Connection> authenticatedConnections = allConnections.stream()
                    .filter(e -> e.isAuthenticated())
                    .filter(e -> e.getConnectionPriority() == ConnectionPriority.PASSIVE)
                    .collect(Collectors.toList());

            if (authenticatedConnections.size() == 0) {
                log.debug("There are no passive connections for closing. We check if we are exceeding " +
                        "MAX_CONNECTIONS_NORMAL ({}) ", PeerGroup.MAX_CONNECTIONS_NORMAL_PRIORITY);
                if (size > PeerGroup.MAX_CONNECTIONS_NORMAL_PRIORITY) {
                    authenticatedConnections = allConnections.stream()
                            .filter(e -> e.isAuthenticated())
                            .filter(e -> e.getConnectionPriority() == ConnectionPriority.PASSIVE || e.getConnectionPriority() == ConnectionPriority.ACTIVE)
                            .collect(Collectors.toList());

                    if (authenticatedConnections.size() == 0) {
                        log.debug("There are no passive or active connections for closing. We check if we are exceeding " +
                                "MAX_CONNECTIONS_HIGH ({}) ", PeerGroup.MAX_CONNECTIONS_HIGH_PRIORITY);
                        if (size > PeerGroup.MAX_CONNECTIONS_HIGH_PRIORITY) {
                            authenticatedConnections = allConnections.stream()
                                    .filter(e -> e.isAuthenticated())
                                    .filter(e -> e.getConnectionPriority() != ConnectionPriority.AUTH_REQUEST)
                                    .collect(Collectors.toList());
                        }
                    }
                }
            }

            if (authenticatedConnections.size() > 0) {
                authenticatedConnections.sort((o1, o2) -> o1.getLastActivityDate().compareTo(o2.getLastActivityDate()));
                log.info("Number of connections exceeding MAX_CONNECTIONS. Current size=" + authenticatedConnections.size());
                Connection connection = authenticatedConnections.remove(0);
                log.info("We are going to shut down the oldest connection with last activity date="
                        + connection.getLastActivityDate() + " / connection=" + connection);
                connection.shutDown(() -> UserThread.runAfterRandomDelay(() -> checkIfConnectedPeersExceeds(), 10, 50, TimeUnit.MILLISECONDS));
                return true;
            } else {
                log.debug("authenticatedConnections.size() == 0. That might happen in rare cases. (checkIfConnectedPeersExceeds)");
                return false;
            }
        } else {
            log.trace("We only have {} connections open and don't need to close any.", size);
            return false;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Set<ReportedPeer> getAuthenticatedAndReportedPeers() {
        Set<ReportedPeer> all = new HashSet<>(reportedPeers);
        Set<ReportedPeer> authenticated = authenticatedPeers.values().stream()
                .filter(e -> e.address != null)
                .filter(e -> !seedNodeAddressesOptional.isPresent() || !seedNodeAddressesOptional.get().contains(e.address))
                .map(e -> new ReportedPeer(e.address, new Date()))
                .collect(Collectors.toSet());
        all.addAll(authenticated);
        return all;
    }

    public Map<Address, Peer> getAuthenticatedPeers() {
        return authenticatedPeers;
    }

    public boolean isInAuthenticationProcess(Address address) {
        return authenticationHandshakes.containsKey(address);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Reported peers
    ///////////////////////////////////////////////////////////////////////////////////////////

    void addToReportedPeers(HashSet<ReportedPeer> reportedPeersToAdd, Connection connection) {
        Log.traceCall("reportedPeersToAdd = " + reportedPeersToAdd);
        // we disconnect misbehaving nodes trying to send too many peers
        // reported peers include the authenticated peers which is normally max. 8 but we give some headroom 
        // for safety
        if (reportedPeersToAdd.size() > (MAX_REPORTED_PEERS + MAX_CONNECTIONS_LOW_PRIORITY * 3)) {
            connection.shutDown();
        } else {
            // In case we have one of the peers already we adjust the lastActivityDate by adjusting the date to the mid 
            // of the lastActivityDate of our already stored peer and the reported one
            Map<Address, ReportedPeer> reportedPeersMap = reportedPeers.stream()
                    .collect(Collectors.toMap(e -> e.address, Function.identity()));
            Set<ReportedPeer> adjustedReportedPeers = new HashSet<>();
            reportedPeersToAdd.stream()
                    .filter(e -> !e.address.equals(getMyAddress()))
                    .filter(e -> !seedNodeAddressesOptional.isPresent() || !seedNodeAddressesOptional.get().contains(e.address))
                    .filter(e -> !authenticatedPeers.containsKey(e.address))
                    .forEach(e -> {
                        if (reportedPeersMap.containsKey(e.address)) {
                            long adjustedTime = (e.lastActivityDate.getTime() +
                                    reportedPeersMap.get(e.address).lastActivityDate.getTime()) / 2;
                            adjustedReportedPeers.add(new ReportedPeer(e.address,
                                    new Date(adjustedTime)));
                        } else {
                            adjustedReportedPeers.add(e);
                        }
                    });

            reportedPeers.addAll(adjustedReportedPeers);
            purgeReportedPeersIfExceeds();
        }

        printReportedPeers();
    }

    private void purgeReportedPeersIfExceeds() {
        Log.traceCall();
        int size = reportedPeers.size();
        if (size > MAX_REPORTED_PEERS) {
            log.trace("We have more then {} reported peers. size={}. " +
                    "We remove random peers from the reported peers list.", MAX_REPORTED_PEERS, size);
            int diff = size - MAX_REPORTED_PEERS;
            List<ReportedPeer> list = new ArrayList<>(reportedPeers);
            // we dont use sorting by lastActivityDate to avoid attack vectors and keep it more random
            //log.debug("Peers before sort " + list);
            //list.sort((a, b) -> a.lastActivityDate.compareTo(b.lastActivityDate));
            //log.debug("Peers after sort  " + list);
            for (int i = 0; i < diff; i++) {
                ReportedPeer toRemove = getAndRemoveRandomReportedPeer(list);
                reportedPeers.remove(toRemove);
            }
        } else {
            log.trace("No need to purge reported peers. We don't have more then {} reported peers yet.", MAX_REPORTED_PEERS);
        }
    }

    Address getMyAddress() {
        return networkNode.getAddress();
    }

    private ReportedPeer getAndRemoveRandomReportedPeer(List<ReportedPeer> list) {
        checkArgument(!list.isEmpty(), "List must not be empty");
        return list.remove(new Random().nextInt(list.size()));
    }

    private Optional<ReportedPeer> getAndRemoveNotAuthenticatingReportedPeer() {
        Optional<ReportedPeer> reportedPeer = Optional.empty();
        List<ReportedPeer> list = new ArrayList<>(reportedPeers);
        if (!list.isEmpty()) {
            do {
                reportedPeer = Optional.of(getAndRemoveRandomReportedPeer(list));
            }
            while (!list.isEmpty() && authenticationHandshakes.containsKey(reportedPeer.get().address));
        }
        return reportedPeer;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void printAllPeers() {
        printAuthenticatedPeers();
        printReportedPeers();
    }

    private void printAuthenticatedPeers() {
        StringBuilder result = new StringBuilder("\n\n------------------------------------------------------------\n" +
                "Authenticated peers for node " + getMyAddress() + ":");
        authenticatedPeers.values().stream().forEach(e -> result.append("\n").append(e.address));
        result.append("\n------------------------------------------------------------\n");
        log.info(result.toString());
    }

    private void printReportedPeers() {
        StringBuilder result = new StringBuilder("\n\n------------------------------------------------------------\n" +
                "Reported peers for node " + getMyAddress() + ":");
        reportedPeers.stream().forEach(e -> result.append("\n").append(e));
        result.append("\n------------------------------------------------------------\n");
        log.info(result.toString());
    }
}