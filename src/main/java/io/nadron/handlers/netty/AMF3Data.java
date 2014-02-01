package io.nadron.handlers.netty;

import flex.messaging.io.amf.ASObject;

/**
 * Created with IntelliJ IDEA.
 *
 * @author Robert Carcasses Quevedo
 *         Date: 7/11/13
 *         Time: 1:05 PM
 */
public class AMF3Data
{

    private final ASObject data;

    public AMF3Data(ASObject data)
    {
        this.data = data;
    }

    /**
     * The commands are defined in game specific files.
     * They are a mimic of the Events of Nadron server, but
     * in the level of the AMF3 protocol. The intention is to
     * simplify as much as possible the development in both sides
     * of the game, server and client.
     * @return  An integer which represents a command. There should be a
     * map of commands in both client and server.
     */
    public int getCommand()
    {
        return (Integer) data.get("type");
    }

    /**
     * Every command has a payload associated, the game logic
     * should take care of what to do with this in every command
     * specific case.
     * @return  The body of the command (the arguments).
     */
    public ASObject getPayload()
    {
        return (ASObject) data.get("payload");
    }
}
