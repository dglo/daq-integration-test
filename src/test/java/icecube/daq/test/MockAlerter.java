package icecube.daq.test;

import com.google.gson.Gson;

import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.Alerter;
import icecube.daq.payload.IUTCTime;

import java.util.Calendar;
import java.util.Map;

public class MockAlerter
    implements Alerter
{
    private boolean closed;

    public MockAlerter()
    {
    }

    /**
     * Close any open files/sockets.
     */
    @Override
    public void close()
    {
        closed = true;
    }

    /**
     * Get the service name
     *
     * @return service name
     */
    @Override
    public String getService()
    {
        return DEFAULT_SERVICE;
    }

    /**
     * If <tt>true</tt>, alerts will be sent to one or more recipients.
     *
     * @return <tt>true</tt> if this alerter will send messages
     */
    @Override
    public boolean isActive()
    {
        return !closed;
    }

    /**
     * Send a Java object (as a JSON string) to a 0MQ server.
     *
     * @param obj object to send
     */
    @Override
    public void sendObject(Object obj)
        throws AlertException
    {
        Gson gson = new Gson();
        gson.toJson(obj);
    }

    /**
     * Set monitoring server host and port
     *
     * @param host - server host name
     * @param port - server port number
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    public void setAddress(String host, int port)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }
}
