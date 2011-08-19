package icecube.daq.test;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public final class MinimalServer
{
    private Selector sel;
    private ServerSocketChannel ssChan;
    private int port;

    public MinimalServer()
        throws IOException
    {
        ssChan = ServerSocketChannel.open();
        ssChan.configureBlocking(false);
        ssChan.socket().setReuseAddress(true);

        ssChan.socket().bind(null);

        sel = Selector.open();
        ssChan.register(sel, SelectionKey.OP_ACCEPT);

        port = ssChan.socket().getLocalPort();
    }

    public SocketChannel acceptChannel()
        throws IOException
    {
        SocketChannel chan = null;

        while (chan == null) {
            int numSel = sel.select(500);
            if (numSel == 0) {
                continue;
            }

            Iterator iter = sel.selectedKeys().iterator();
            while (iter.hasNext()) {
                SelectionKey selKey = (SelectionKey) iter.next();
                iter.remove();

                if (!selKey.isAcceptable()) {
                    selKey.cancel();
                    continue;
                }

                ServerSocketChannel ssChan =
                    (ServerSocketChannel) selKey.channel();
                if (chan != null) {
                    System.err.println("Got multiple socket connections");
                    continue;
                }

                try {
                    chan = ssChan.accept();
                } catch (IOException ioe) {
                    System.err.println("Couldn't accept client socket");
                    ioe.printStackTrace();
                    chan = null;
                }
            }
        }

        return chan;
    }

    public void close()
        throws IOException
    {
        IOException delayedEx = null;

        try {
            ssChan.close();
        } catch (IOException ioe) {
            if (delayedEx == null) delayedEx = ioe;
        }

        try {
            sel.close();
        } catch (IOException ioe) {
            if (delayedEx == null) delayedEx = ioe;
        }

        if (delayedEx != null) {
            throw delayedEx;
        }
    }

    public int getPort()
    {
        return port;
    }
}
