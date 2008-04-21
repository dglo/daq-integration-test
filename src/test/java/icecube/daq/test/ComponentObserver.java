package icecube.daq.test;

import icecube.daq.common.DAQCmdInterface;
import icecube.daq.io.DAQComponentObserver;
import icecube.daq.io.ErrorState;
import icecube.daq.io.NormalState;

public class ComponentObserver
    implements DAQComponentObserver
{
    private boolean sinkStopNotificationCalled;
    private boolean sinkErrorNotificationCalled;

    boolean gotError()
    {
        return sinkErrorNotificationCalled;
    }

    boolean gotStop()
    {
        return sinkStopNotificationCalled;
    }

    public synchronized void update(Object object, String notificationID)
    {
        if (object instanceof NormalState){
            NormalState state = (NormalState)object;
            if (state == NormalState.STOPPED){
                if (notificationID.equals(DAQCmdInterface.SINK)){
                    sinkStopNotificationCalled = true;
                } else {
                    throw new Error("Unexpected notification update");
                }
            }
        } else if (object instanceof ErrorState){
            ErrorState state = (ErrorState)object;
            if (state == ErrorState.UNKNOWN_ERROR){
                if (notificationID.equals(DAQCmdInterface.SINK)){
                    sinkErrorNotificationCalled = true;
                } else {
                    throw new Error("Unexpected notification update");
                }
            }
        }
    }
}
