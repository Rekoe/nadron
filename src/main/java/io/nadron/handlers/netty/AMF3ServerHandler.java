package io.nadron.handlers.netty;

import flex.messaging.io.amf.ASObject;
import io.nadron.app.PlayerSession;
import io.nadron.event.Event;
import io.nadron.event.Events;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created with IntelliJ IDEA.
 *
 * @author Robert Carcasses Quevedo
 *         Date: 7/11/13
 *         Time: 12:10 PM
 */
public class AMF3ServerHandler extends SimpleChannelInboundHandler<ASObject> {
    private static final Logger LOG = LoggerFactory.getLogger(AMF3ServerHandler.class);
    /**
     * The player session associated with this stateful business handler.
     */
    private final PlayerSession playerSession;

    public AMF3ServerHandler(PlayerSession playerSession) {
        super();
        this.playerSession = playerSession;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ASObject asObject) throws Exception {
        /**
         * Here we perform the actions that are related to the
         * player only, like upgrading skills or buying stuff.
         * If we need to interact with other players we call
         * onRoomRequest and pass the ASObject as data
         */
        AMF3Data data = new AMF3Data(asObject);
        Event event = Events.event(data, Events.ANY);
        playerSession.onEvent(event);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        LOG.error("Exception during network communication: {}.", cause);
        Event event = Events.event(cause, Events.EXCEPTION);
        playerSession.onEvent(event);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx)
            throws Exception {
        LOG.debug("Netty Channel {} is closed.", ctx.channel());
        if (!playerSession.isShuttingDown()) {
            // Should not send close to session, since reconnection/other
            // business logic might be in place.
            Event event = Events.event(null, Events.DISCONNECT);
            playerSession.onEvent(event);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            LOG.warn(
                    "Channel {} has been idle, exception event will be raised now: ",
                    ctx.channel());
            // TODO check if setting payload as non-throwable cause issue?
            Event event = Events.event(evt, Events.EXCEPTION);
            playerSession.onEvent(event);
        }
    }

    public PlayerSession getPlayerSession() {
        return playerSession;
    }

}
