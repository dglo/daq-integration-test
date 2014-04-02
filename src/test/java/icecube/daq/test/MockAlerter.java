package icecube.daq.test;

import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.Alerter;
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
    public void close()
    {
        closed = true;
    }

    /**
     * Get the service name
     *
     * @return service name
     */
    public String getService()
    {
        throw new Error("Unimplemented");
    }

    /**
     * If <tt>true</tt>, alerts will be sent to one or more recipients.
     *
     * @return <tt>true</tt> if this alerter will send messages
     */
    public boolean isActive()
    {
        return !closed;
    }

    /**
     * Send a message to IceCube Live.
     *
     * @param varname variable name
     * @param priority priority level
     * @param dateTime date and time for message
     * @param values map of names to values
     */
    public void send(String varname, Alerter.Priority priority,
                     Calendar dateTime, Map<String, Object> vars)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    /**
     * Send a message to IceCube Live.
     *
     * @param varname variable name
     * @param priority priority level
     * @param values map of names to values
     */
    public void send(String varname, Alerter.Priority priority,
                     Map<String, Object> vars)
        throws AlertException
    {
        System.out.format("Sent %s prio %s\n", varname, priority);
    }

    /**
     * Send an alert.
     *
     * @param priority priority level
     * @param condition I3Live condition
     * @param vars map of variable names to values
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    public void sendAlert(Alerter.Priority priority, String condition,
                          Map<String, Object> vars)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    /**
     * Send an alert.
     *
     * @param priority priority level
     * @param condition I3Live condition
     * @param notify list of email addresses which receive notification
     * @param vars map of variable names to values
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    public void sendAlert(Alerter.Priority priority, String condition,
                          String notify, Map<String, Object> vars)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    /**
     * Send an alert.
     *
     * @param dateTime date and time for message
     * @param priority priority level
     * @param condition I3Live condition
     * @param notify list of email addresses which receive notification
     * @param vars map of variable names to values
     *
     * @throws AlertException if there is a problem with one of the parameters
     */
    public void sendAlert(Calendar dateTime, Alerter.Priority priority,
                          String condition, String notify,
                          Map<String, Object> vars)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    /**
     * Send a Java object (as a JSON string) to a 0MQ server.
     *
     * @param obj object to send
     */
    public void sendObject(Object obj)
        throws AlertException
    {
        throw new Error("Unimplemented");
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
