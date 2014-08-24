package io.bitsquare;

import io.bitsquare.msg.SeedNodeAddress;
import java.io.IOException;
import java.util.List;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.BaseFutureListener;
import net.tomp2p.nat.PeerBuilderNAT;
import net.tomp2p.nat.PeerNAT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.peers.PeerMapChangeListener;
import net.tomp2p.peers.PeerStatatistic;
import net.tomp2p.relay.FutureRelay;
import net.tomp2p.relay.RelayRPC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Well known node which is reachable for all peers for bootstrapping.
 * There will be several SeedNodes running on several servers.
 * <p>
 * TODO: Alternative bootstrap methods will follow later (save locally list of known nodes reported form other peers,...)
 */
public class SeedNode extends Thread
{
    private static final Logger log = LoggerFactory.getLogger(SeedNode.class);
    private static final List<SeedNodeAddress.StaticSeedNodeAddresses> staticSedNodeAddresses = SeedNodeAddress.StaticSeedNodeAddresses.getAllSeedNodeAddresses();

    /**
     * @param args If no args passed we use localhost, otherwise the param is used as index for selecting an address from seedNodeAddresses
     */
    public static void main(String[] args)
    {
        int index = 0;
        if (args.length > 0)
        {
            // use host index passes as param
            int param = Integer.valueOf(args[0]);
            if (param < staticSedNodeAddresses.size())
                index = param;
        }

        SeedNode seedNode = new SeedNode(new SeedNodeAddress(staticSedNodeAddresses.get(index)));
        seedNode.setDaemon(true);
        seedNode.start();

        try
        {
            // keep main thread up
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e)
        {
            log.error(e.toString());
        }
    }


    private final SeedNodeAddress seedNodeAddress;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SeedNode(SeedNodeAddress seedNodeAddress)
    {
        this.seedNodeAddress = seedNodeAddress;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void run()
    {
        Peer peer = startupPeer();

        for (; ; )
        {
            try
            {
                // ping(peer);

                Thread.sleep(300);
            } catch (InterruptedException e)
            {
                log.error(e.toString());
            }
        }
    }

    public Peer startupPeer()
    {
        Peer peer = null;
        try
        {
            peer = new PeerBuilder(Number160.createHash(seedNodeAddress.getId())).ports(seedNodeAddress.getPort()).start();

            // Need to add all features the clients will use (otherwise msg type is UNKNOWN_ID)
            new PeerBuilderDHT(peer).start();
            PeerNAT nodeBehindNat = new PeerBuilderNAT(peer).start();
            new RelayRPC(peer);
            //new PeerBuilderTracker(peer);
            nodeBehindNat.startSetupRelay(new FutureRelay());

            log.debug("Peer started. " + peer.peerAddress());

            peer.peerBean().peerMap().addPeerMapChangeListener(new PeerMapChangeListener()
            {
                @Override
                public void peerInserted(PeerAddress peerAddress, boolean verified)
                {
                    log.debug("Peer inserted: peerAddress=" + peerAddress + ", verified=" + verified);
                }

                @Override
                public void peerRemoved(PeerAddress peerAddress, PeerStatatistic peerStatistics)
                {
                    log.debug("Peer removed: peerAddress=" + peerAddress + ", peerStatistics=" + peerStatistics);
                }

                @Override
                public void peerUpdated(PeerAddress peerAddress, PeerStatatistic peerStatistics)
                {
                    log.debug("Peer updated: peerAddress=" + peerAddress + ", peerStatistics=" + peerStatistics);
                }
            });
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        return peer;
    }

    private void ping(Peer peer)
    {
        if (peer != null)
            return;

        try
        {
            // Optional pinging
            for (PeerAddress peerAddress : peer.peerBean().peerMap().all())
            {
                BaseFuture future = peer.ping().peerAddress(peerAddress).tcpPing().start();
                future.addListener(new BaseFutureListener<BaseFuture>()
                {
                    @Override
                    public void operationComplete(BaseFuture future) throws Exception
                    {
                        if (future.isSuccess())
                        {
                            log.debug("peer online (TCP):" + peerAddress);
                        }
                        else
                        {
                            log.debug("offline " + peerAddress);
                        }
                    }

                    @Override
                    public void exceptionCaught(Throwable t) throws Exception
                    {
                        log.error("exceptionCaught " + t);
                    }
                });

                future = peer.ping().peerAddress(peerAddress).start();
                future.addListener(new BaseFutureListener<BaseFuture>()
                {
                    @Override
                    public void operationComplete(BaseFuture future) throws Exception
                    {
                        if (future.isSuccess())
                        {
                            log.debug("peer online (UDP):" + peerAddress);
                        }
                        else
                        {
                            log.debug("offline " + peerAddress);
                        }
                    }

                    @Override
                    public void exceptionCaught(Throwable t) throws Exception
                    {
                        log.error("exceptionCaught " + t);
                    }
                });
                Thread.sleep(1500);
            }
        } catch (Exception e)
        {
            log.error("Exception: " + e);
        }
    }

}