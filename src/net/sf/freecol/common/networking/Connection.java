/**
 *  Copyright (C) 2002-2014   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.common.networking;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.io.FreeColXMLReader;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;


/**
 * A network connection.
 * Responsible for both sending and receiving network messages.
 *
 * @see #send(Element)
 * @see #sendAndWait(Element)
 * @see #ask(Element)
 */
public class Connection {

    private static final Logger logger = Logger.getLogger(Connection.class.getName());

    public static final String DISCONNECT_TAG = "disconnect";
    public static final String NETWORK_REPLY_ID_TAG = "networkReplyId";
    public static final String QUESTION_TAG = "question";
    public static final String REPLY_TAG = "reply";

    private static final int TIMEOUT = 5000; // 5s

    private InputStream in;

    private Socket socket;

    private OutputStream out;

    private Transformer xmlTransformer;

    private ReceivingThread thread;

    private MessageHandler messageHandler;

    private String name;

    protected static final boolean dump
        = FreeColDebugger.isInDebugMode(FreeColDebugger.DebugMode.COMMS);


    /**
     * Trivial constructor.
     *
     * @param name The name of the connection.
     */
    protected Connection(String name) {
        this.in = null;
        this.socket = null;
        this.out = null;
        this.xmlTransformer = null;
        this.thread = null;
        this.messageHandler = null;
        this.name = name;
    }

    /**
     * Sets up a new socket with specified host and port and uses
     * {@link #Connection(Socket, MessageHandler, String)}.
     *
     * @param host The host to connect to.
     * @param port The port to connect to.
     * @param messageHandler The MessageHandler to call for each message
     *     received.
     * @exception IOException
     */
    public Connection(String host, int port, MessageHandler messageHandler,
                      String name) throws IOException {
        this(createSocket(host, port), messageHandler, name);
    }

    /**
     * Creates a new <code>Connection</code> with the specified
     * <code>Socket</code> and {@link MessageHandler}.
     *
     * @param socket The socket to the client.
     * @param messageHandler The MessageHandler to call for each message
     *     received.
     * @exception IOException
     */
    public Connection(Socket socket, MessageHandler messageHandler,
                      String name) throws IOException {
        this(name);

        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        Transformer myTransformer = null;
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            myTransformer = factory.newTransformer();
            myTransformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION,
                                            "yes");
        } catch (TransformerException e) {
            logger.log(Level.WARNING, "Failed to install transformer!", e);
        }
        this.xmlTransformer = myTransformer;
        this.thread = new ReceivingThread(this, in, name);
        this.messageHandler = messageHandler;
        this.name = name;

        thread.start();
    }

    /**
     * Creates a socket to communication with a given host, port pair.
     *
     * @param host The host to connect to.
     * @param port The port to connect to.
     * @exception IOException on failure to create/connect the socket.
     * @return A new socket.
     */
    private static Socket createSocket(String host, int port)
        throws IOException {
        Socket socket = new Socket();
        SocketAddress addr = new InetSocketAddress(host, port);
        socket.connect(addr, TIMEOUT);
        return socket;
    }

    /**
     * Gets the socket.
     *
     * @return The <code>Socket</code> used while communicating with
     *     the other peer.
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * Sets the MessageHandler for this Connection.
     *
     * @param mh The new MessageHandler for this Connection.
     */
    public void setMessageHandler(MessageHandler mh) {
        messageHandler = mh;
    }

    /**
     * Gets the MessageHandler for this Connection.
     *
     * @return The MessageHandler for this Connection.
     */
    public MessageHandler getMessageHandler() {
        return messageHandler;
    }

    /**
     * Gets the connection name.
     *
     * @return The connection name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sends a "disconnect"-message and closes this connection.
     *
     * Failure is expected if the other end has closed already.
     */
    public void close() {
        if (this.out != null) {
            try {
                sendDumping(DOMMessage.createMessage(DISCONNECT_TAG));
            } catch (IOException ioe) {
                logger.fine("Error disconnecting " + this.name
                    + ": " + ioe.getMessage());
            } finally {
                reallyClose();
            }
        }
    }

    /**
     * Really closes this connection.
     */
    public void reallyClose() {
        if (thread != null) thread.askToStop();

        if (this.out != null) {
            try {
                this.out.close();
            } catch (IOException ioe) {
                logger.log(Level.WARNING, "Error closing output", ioe);
            } finally {
                this.out = null;
            }
        }
        if (this.in != null) {
            try {
                this.in.close();
            } catch (IOException ioe) {
                logger.log(Level.WARNING, "Error closing input", ioe);
            } finally {
                this.in = null;
            }
        }
        if (this.socket != null) {
            try {
                this.socket.close();
            } catch (IOException ioe) {
                logger.log(Level.WARNING, "Error closing socket", ioe);
            } finally {
                this.socket = null;
            }
        }

        logger.fine("Connection really closed for " + this.name);
    }

    /**
     * Fundamental routine to send a message over this Connection.
     *
     * @param element The <code>Element</code> (root element in a
     *     DOM-parsed XML tree) that holds all the information
     * @param logOK Log the send if true.
     * @exception IOException If an error occur while sending the message.
     */
    private void send(Element element, boolean logOK) throws IOException {
        if (logOK) logger.fine("Send: " + element.getTagName());
        synchronized (this.out) {
            try {
                xmlTransformer.transform(new DOMSource(element),
                                         new StreamResult(this.out));
                this.out.write('\n');
                this.out.flush();
                this.out.notifyAll(); // Just in case others are waiting
            } catch (TransformerException te) {
                logger.log(Level.WARNING, "Failed to transform", te);
            }
        }
    }

    /**
     * Sends the given message over this Connection.
     *
     * @param element The element (root element in a DOM-parsed XML tree) that
     *            holds all the information
     * @exception IOException If an error occur while sending the message.
     * @see #sendAndWait(Element)
     * @see #ask(Element)
     */
    public void send(Element element) throws IOException {
        send(element, logger.isLoggable(Level.FINE));
    }

    /**
     * Dumping wrapper for send().
     *
     * @param element The element (root element in a DOM-parsed XML tree) that
     *            holds all the information
     * @exception IOException If an error occur while sending the message.
     * @see #sendAndWait(Element)
     * @see #ask(Element)
     */
    public void sendDumping(Element element) throws IOException {
        if (dump) {
            String x = getName() + "-send";
            try {
                System.err.println("<" + x + ">"
                    + DOMMessage.elementToString(element) + "</" + x + ">\n");
            } catch (Exception e) {}
        }
        send(element);
    }

    /**
     * Sends the given message over this <code>Connection</code> and waits for
     * confirmation of reception before returning.
     *
     * @param element The element (root element in a DOM-parsed XML tree) that
     *            holds all the information
     * @exception IOException If an error occur while sending the message.
     * @see #send(Element)
     * @see #ask(Element)
     */
    public void sendAndWait(Element element) throws IOException {
        askDumping(element);
    }

    /**
     * Sends a message to the other peer and returns the reply.
     *
     * @param element The question for the other peer.
     * @return The reply from the other peer.
     * @exception IOException if an error occur while sending the message.
     * @see #send(Element)
     * @see #sendAndWait(Element)
     */
    public Element ask(Element element) throws IOException {
        int networkReplyId = thread.getNextNetworkReplyId();
        String tag = element.getTagName();

        if (Thread.currentThread() == thread) {
            throw new IOException("wait(ReceivingThread) for: " + tag);
        }

        Element question = element.getOwnerDocument()
            .createElement(QUESTION_TAG);
        question.setAttribute(NETWORK_REPLY_ID_TAG,
                              Integer.toString(networkReplyId));
        question.appendChild(element);

        NetworkReplyObject nro = thread.waitForNetworkReply(networkReplyId);
        send(question, false);
        DOMMessage response = (DOMMessage)nro.getResponse();
        Element reply = (response == null) ? null
            : response.getDocument().getDocumentElement();
        Element child = (reply == null) ? null
            : (Element)reply.getFirstChild();

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Ask(" + networkReplyId + "): " + tag + ", reply: "
                + ((child == null) ? "null" : child.getTagName()));
        }
        return child;
    }

    /**
     * Dumping wrapper for ask().
     *
     * Dumps to System.err with a faked-XML prefix so the whole line can
     * be fed to an XML-pretty printer if required.
     *
     * @param request The <code>Element</code> to send.
     * @return The reply <code>Element</code>.
     * @exception IOException if ask() fails.
     */
    public Element askDumping(Element request) throws IOException {
        Element reply = ask(request);
        if (dump) {
            final String name = getName();
            try {
                System.err.println("<" + name + "-request>"
                    + DOMMessage.elementToString(request)
                    + "</" + name + "-request>\n"
                    + "<" + name + "-reply>"
                    + DOMMessage.elementToString(reply)
                    + "</" + name + "-reply>\n");
            } catch (Exception x) {
                logger.log(Level.WARNING, "Dump log fail for " + name, x);
            }
        }
        return reply;
    }

    /**
     * Handles a message using the registered <code>MessageHandler</code>.
     *
     * @param in The stream containing the message.
     * @exception IOException if the streaming fails.
     */
    public void handleAndSendReply(final BufferedInputStream in) 
        throws IOException {

        in.mark(200); // Peek at the reply identifier and tag.

        // Extract the reply id and check if this is a question.
        final String networkReplyId;
        final boolean question;
        FreeColXMLReader xr = null;
        try {
            xr = new FreeColXMLReader(in);
            xr.nextTag();
            question = QUESTION_TAG.equals(xr.getLocalName());
            networkReplyId = xr.getAttribute(NETWORK_REPLY_ID_TAG,
                                             (String)null);

        } catch (XMLStreamException xse) {
            logger.log(Level.WARNING, "XML stream failure", xse);
            return;
        } 

        // Reset and build a message.
        final DOMMessage msg;
        in.reset();
        try {
            msg = new DOMMessage(in);
        } catch (SAXException e) {
            logger.log(Level.WARNING, "Unable to read message.", e);
            return;
        } finally {
            if (xr != null) xr.close();
        }

        // Process the message in its own thread.
        final Connection conn = this;
        Thread t = new Thread(msg.getType()) {
                @Override
                public void run() {
                    Element element = msg.getDocument().getDocumentElement();
                    Element reply;
                    try {
                        if (question) {
                            reply = (Element)element.getFirstChild();
                            reply = conn.handle(reply);
                            if (reply == null) {
                                reply = DOMMessage.createMessage(REPLY_TAG,
                                    NETWORK_REPLY_ID_TAG, networkReplyId);
                            } else {
                                Element header = reply.getOwnerDocument()
                                    .createElement(REPLY_TAG);
                                header.setAttribute(NETWORK_REPLY_ID_TAG,
                                                    networkReplyId);
                                header.appendChild(reply);
                                reply = header;
                            }
                        } else {
                            reply = conn.handle(element);
                        }
                        if (reply != null) conn.sendDumping(reply);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Handler failed: "
                            + element, e);
                    }
                }
            };
        t.setName(name + "-MessageHandler-" + t.getName());
        t.start();
    }

    /**
     * Handle a request.
     *
     * @param request The request <code>Element</code> to handle.
     * @return The reply from the message handler.
     * @exception FreeColException if there is trouble with the response.
     */
    public Element handle(Element request) throws FreeColException {
        return messageHandler.handle(this, request);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "[Connection " + name + " (" + socket + ")]";
    }
}
