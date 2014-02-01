package io.nadron.handlers.netty;

import flex.messaging.io.amf.ASObject;
import io.nadron.app.GameRoom;
import io.nadron.app.Player;
import io.nadron.app.PlayerSession;
import io.nadron.app.Session;
import io.nadron.communication.NettyTCPMessageSender;
import io.nadron.event.Events;
import io.nadron.event.impl.ReconnetEvent;
import io.nadron.server.netty.AbstractNettyServer;
import io.nadron.service.LookupService;
import io.nadron.service.SessionRegistryService;
import io.nadron.service.UniqueIDGeneratorService;
import io.nadron.service.impl.ReconnectSessionRegistry;
import io.nadron.util.Credentials;
import io.nadron.util.NadronConfig;
import io.nadron.util.NettyUtils;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;


@Sharable
public class AMF3LoginHandler extends SimpleChannelInboundHandler<ASObject> {
    private static final Logger LOG = LoggerFactory
            .getLogger(AMF3LoginHandler.class);
    /**
     * Used for book keeping purpose. It will count all open channels. Currently
     * closed channels will not lead to a decrement.
     */
    private static final AtomicInteger CHANNEL_COUNTER = new AtomicInteger(0);
    protected LookupService lookupService;
    protected SessionRegistryService<SocketAddress> udpSessionRegistry;
    protected ReconnectSessionRegistry reconnectRegistry;
    protected UniqueIDGeneratorService idGeneratorService;

    @Override
    public void channelRead0(ChannelHandlerContext ctx, ASObject event) throws Exception {

        final Channel channel = ctx.channel();
        LOG.trace("-------Login data received------");
        LOG.trace(event.toString());

        AMF3LoginData loginData = new AMF3LoginData(event);
        int type = loginData.getType();

        if (Events.LOG_IN == type) {
            LOG.debug("Login attempt from {}", channel.remoteAddress());
            Player player = lookupPlayer(loginData);
            handleLogin(player, ctx, loginData.getGameRoom());
        } else if (Events.RECONNECT == type) {
            LOG.debug("Reconnect attempt from {}", channel.remoteAddress());
            String reconnectKey = loginData.getReconnectKey();
            PlayerSession playerSession = lookupSession(reconnectKey);
            handleReconnect(playerSession, ctx);
        } else {
            LOG.error("Invalid event {} sent from remote address {}. "
                    + "Going to close channel {}",
                    new Object[]{event.getType(), channel.remoteAddress(), channel});
            closeChannelWithLoginFailure(channel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        Channel channel = ctx.channel();
        LOG.error("Exception {} occurred during log in process, going to close channel {}",
                cause, channel);
        channel.close();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        AbstractNettyServer.ALL_CHANNELS.add(ctx.channel());
        LOG.debug("Added Channel with id: {} as the {}th open channel", ctx
                .channel(), CHANNEL_COUNTER.incrementAndGet());
    }

    public Player lookupPlayer(final AMF3LoginData loginData) {
        //build a credential up from the login data
        Credentials credentials = new Credentials() {
            @Override
            public String getUsername() {
                return loginData.getUsername();
            }

            @Override
            public String getPassword() {
                return loginData.getPassword();
            }
        };
        //and build the player instance
        Player player = lookupService.playerLookup(credentials);
        if (null == player) {
            LOG.error("Invalid credentials provided by user: {}", credentials);
        }
        return player;
    }

    public PlayerSession lookupSession(final String reconnectKey) {
        PlayerSession playerSession = (PlayerSession) reconnectRegistry.getSession(reconnectKey);
        if (null != playerSession) {
            synchronized (playerSession) {
                // if its an already active session then do not allow a
                // reconnect. So the only state in which a client is allowed to
                // reconnect is if it is "NOT_CONNECTED"
                if (playerSession.getStatus() == Session.Status.NOT_CONNECTED) {
                    playerSession.setStatus(Session.Status.CONNECTING);
                } else {
                    playerSession = null;
                }
            }
        }
        return playerSession;
    }

    public void handleLogin(Player player, ChannelHandlerContext ctx, String refKey) {
        if (null != player)
            handleGameRoomJoin(player, ctx, refKey);
        else
            closeChannelWithLoginFailure(ctx.channel());
    }

    protected void handleReconnect(PlayerSession playerSession, ChannelHandlerContext ctx) {
        if (null != playerSession) {
            GameRoom gameRoom = playerSession.getGameRoom();
            gameRoom.disconnectSession(playerSession);
            if (null != playerSession.getTcpSender())
                playerSession.getTcpSender().close();

            if (null != playerSession.getUdpSender())
                playerSession.getUdpSender().close();

            handleReJoin(playerSession, gameRoom, ctx.channel());
        } else {
            // Write future and close channel
            closeChannelWithLoginFailure(ctx.channel());
        }
    }

    /**
     * Helper method which will close the channel after writing
     * {@link io.nadron.event.Events#LOG_IN_FAILURE} to remote connection.
     *
     * @param channel The tcp connection to remote machine that will be closed.
     */
    private void closeChannelWithLoginFailure(Channel channel) {
        ChannelFuture future = channel.write(NettyUtils
                .createBufferForOpcode(Events.LOG_IN_FAILURE));
        future.addListener(ChannelFutureListener.CLOSE);
    }

    public void handleGameRoomJoin(Player player, ChannelHandlerContext ctx, String refKey) {
        Channel channel = ctx.channel();
        GameRoom gameRoom = lookupService.gameRoomLookup(refKey);
        if (null != gameRoom) {
            PlayerSession playerSession = gameRoom.createPlayerSession(player);
            String reconnectKey = (String) idGeneratorService.generateFor(playerSession.getClass());
            playerSession.setAttribute(NadronConfig.RECONNECT_KEY, reconnectKey);
            playerSession.setAttribute(NadronConfig.RECONNECT_REGISTRY, reconnectRegistry);
            connectToGameRoom(gameRoom, playerSession, channel);
        } else {
            // Write failure and close channel.
            ChannelFuture future = channel.write(NettyUtils.createBufferForOpcode(Events.GAME_ROOM_JOIN_FAILURE));
            future.addListener(ChannelFutureListener.CLOSE);
            LOG.error("Invalid ref key provided by client: {}. Channel {} will be closed", refKey, channel);
        }
    }

    protected void handleReJoin(PlayerSession playerSession, GameRoom gameRoom, Channel channel) {
        LOG.trace("Going to clear pipeline");
        // Clear the existing pipeline
        NettyUtils.clearPipeline(channel.pipeline());
        // Set the tcp channel on the session.
        NettyTCPMessageSender sender = new NettyTCPMessageSender(channel);
        playerSession.setTcpSender(sender);
        // Connect the pipeline to the game room.
        gameRoom.connectSession(playerSession);
        playerSession.setWriteable(true);// TODO remove if unnecessary. It should be done in start event
        // Send the re-connect event so that it will in turn send the START event.
        playerSession.onEvent(new ReconnetEvent(sender));
    }

    public void connectToGameRoom(final GameRoom gameRoom, final PlayerSession playerSession, Channel channel) {
        LOG.trace("Going to clear pipeline");
        // Clear the existing pipeline
        NettyUtils.clearPipeline(channel.pipeline());
        // Set the tcp channel on the session.
        NettyTCPMessageSender tcpSender = new NettyTCPMessageSender(channel);
        playerSession.setTcpSender(tcpSender);
        // Connect the pipeline to the game room.
        gameRoom.connectSession(playerSession);
        gameRoom.onLogin(playerSession);
    }

    public LookupService getLookupService() {
        return lookupService;
    }

    public void setLookupService(LookupService lookupService) {
        this.lookupService = lookupService;
    }

    public UniqueIDGeneratorService getIdGeneratorService() {
        return idGeneratorService;
    }

    public void setIdGeneratorService(UniqueIDGeneratorService idGeneratorService) {
        this.idGeneratorService = idGeneratorService;
    }

    public SessionRegistryService<SocketAddress> getUdpSessionRegistry() {
        return udpSessionRegistry;
    }

    public void setUdpSessionRegistry(
            SessionRegistryService<SocketAddress> udpSessionRegistry) {
        this.udpSessionRegistry = udpSessionRegistry;
    }

    public ReconnectSessionRegistry getReconnectRegistry() {
        return reconnectRegistry;
    }

    public void setReconnectRegistry(ReconnectSessionRegistry reconnectRegistry) {
        this.reconnectRegistry = reconnectRegistry;
    }

}
