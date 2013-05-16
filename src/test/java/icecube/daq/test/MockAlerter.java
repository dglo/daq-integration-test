package icecube.daq.test;

import icecube.daq.juggler.alert.AlertException;
import icecube.daq.juggler.alert.Alerter;
import java.util.Calendar;
import java.util.Map;

public class MockAlerter
    implements Alerter
{
    public MockAlerter()
    {
    }

    public void close()
    {
        // do nothing
    }

    public String getService()
    {
        throw new Error("Unimplemented");
    }

    public boolean isActive()
    {
        throw new Error("Unimplemented");
    }

    public void send(String s0, Alerter.Priority x1, Calendar x2, Map x3)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    public void send(String s0, Alerter.Priority x1, Map x2)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    public void sendAlert(Alerter.Priority x0, String s1, Map x2)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    public void sendAlert(Alerter.Priority x0, String s1, String s2, Map x3)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    public void sendAlert(Calendar x0, Alerter.Priority x1, String s2, String s3, Map x4)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }

    public void setAddress(String s0, int i1)
        throws AlertException
    {
        throw new Error("Unimplemented");
    }
}
