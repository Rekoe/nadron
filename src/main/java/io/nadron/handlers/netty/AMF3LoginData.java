package io.nadron.handlers.netty;

import flex.messaging.io.amf.ASObject;

import java.io.Serializable;

/**
 * Created with IntelliJ IDEA.
 * User: robert
 * Date: 7/9/13
 * Time: 10:41 PM
 */
public class AMF3LoginData implements Serializable
{
    private static final long serialVersionUID = 8188757584720622117L;

    private String username = "_empty_";
    private String password = "_empty_";
    private String gameRoom = "_empty_";
    private String fbToken = "_empty_";
    private String reconnectKey = "_empty_";
    private int type;

    public AMF3LoginData(ASObject data)
    {
        username = (String) data.get("username");
        password = (String) data.get("password");
        fbToken = (String) data.get("fbToken");
        gameRoom = (String) data.get("gameRoom");
        reconnectKey = (String) data.get("reconnectKey");
        type = (Integer) data.get("type");
    }

    @Override
    public String toString()
    {
        return "username: " + username + ", password: " + password + " gameRoom: " + gameRoom + ", fbToken:" + fbToken;
    }

    public String getFbToken()
    {
        return fbToken;
    }

    public void setFbToken(String fbToken)
    {
        this.fbToken = fbToken;
    }

    public String getGameRoom()
    {
        return gameRoom;
    }

    public void setGameRoom(String gameRoom)
    {
        this.gameRoom = gameRoom;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public int getType()
    {
        return type;
    }

    public void setType(int type)
    {
        this.type = type;
    }

    public String getReconnectKey()
    {
        return reconnectKey;
    }

    public void setReconnectKey(String reconnectKey)
    {
        this.reconnectKey = reconnectKey;
    }
}
