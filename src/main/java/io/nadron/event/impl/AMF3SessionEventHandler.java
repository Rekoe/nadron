package io.nadron.event.impl;

import io.nadron.app.Session;
import io.nadron.event.Event;
import io.nadron.event.Events;
import io.nadron.event.SessionEventHandler;

public class AMF3SessionEventHandler implements SessionEventHandler {
    private final Session session;

    public AMF3SessionEventHandler(Session session) {
        this.session = session;
    }

    @Override
    public Session getSession() {
        return session;
    }

    @Override
    public void setSession(Session session) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Session is a final variable and cannot be reset.");
    }

    @Override
    public void onEvent(Event event) {
        /*if (null != session)
        {
            PlayerSession pSession = (PlayerSession) session;
            NetworkEvent networkEvent = new DefaultNetworkEvent(event);
            if (pSession.isUDPEnabled())
            {
                networkEvent.setDeliveryGuaranty(FAST);
            }
            pSession.getGameRoom().sendBroadcast(networkEvent);
        }*/
    }

    @Override
    public int getEventType() {
        return Events.ANY;
    }
}