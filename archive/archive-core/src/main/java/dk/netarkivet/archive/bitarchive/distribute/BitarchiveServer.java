/*
 * #%L
 * Netarchivesuite - archive
 * %%
 * Copyright (C) 2005 - 2018 The Royal Danish Library, 
 *             the National Library of France and the Austrian National Library.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package dk.netarkivet.archive.bitarchive.distribute;

import java.io.File;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.netarkivet.archive.ArchiveSettings;
import dk.netarkivet.archive.bitarchive.Bitarchive;
import dk.netarkivet.archive.bitarchive.BitarchiveAdmin;
import dk.netarkivet.archive.distribute.ArchiveMessageHandler;
import dk.netarkivet.common.CommonSettings;
import dk.netarkivet.common.distribute.ChannelID;
import dk.netarkivet.common.distribute.Channels;
import dk.netarkivet.common.distribute.JMSConnection;
import dk.netarkivet.common.distribute.JMSConnectionFactory;
import dk.netarkivet.common.distribute.NullRemoteFile;
import dk.netarkivet.common.distribute.arcrepository.BatchStatus;
import dk.netarkivet.common.distribute.arcrepository.BitarchiveRecord;
import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.PermissionDenied;
import dk.netarkivet.common.exceptions.UnknownID;
import dk.netarkivet.common.utils.ChecksumCalculator;
import dk.netarkivet.common.utils.CleanupIF;
import dk.netarkivet.common.utils.FileUtils;
import dk.netarkivet.common.utils.LoggingOutputStream;
import dk.netarkivet.common.utils.NotificationType;
import dk.netarkivet.common.utils.NotificationsFactory;
import dk.netarkivet.common.utils.Settings;
import dk.netarkivet.common.utils.SystemUtils;

/**
 * Bitarchive container responsible for processing the different classes of message which can be received by a
 * bitarchive and returning appropriate data.
 */
public class BitarchiveServer extends ArchiveMessageHandler implements CleanupIF {

    /** The bitarchive serviced by this server. */
    private Bitarchive ba;

    /** The admin data for the bit archive. */
    private BitarchiveAdmin baa;

    /** The unique instance of this class. */
    private static BitarchiveServer instance;

    /** the jms connection. */
    protected JMSConnection con;

    /** The logger used by this class. */
    private static final Logger log = LoggerFactory.getLogger(BitarchiveServer.class);

    /** the thread which sends heartbeat messages from this bitarchive to its BitarchiveMonitorServer. */
    private HeartBeatSender heartBeatSender;

    /** the unique id of this application. */
    private String bitarchiveAppId;

    /** Channel to listen on for get/batch/correct. */
    private ChannelID allBa;
    /** Topic to listen on for store. */
    private ChannelID anyBa;
    /** Channel to send BatchEnded messages to when replying. */
    private ChannelID baMon;

    /** Map between running batchjob processes and their message id. */
    public Map<String, Thread> batchProcesses;

    /**
     * Returns the unique instance of this class The server creates an instance of the bitarchive it provides access to
     * and starts to listen to JMS messages on the incomming jms queue
     * <p>
     * Also, heartbeats are sent out at regular intervals to the Bitarchive Monitor, to tell that this bitarchive is
     * alive.
     *
     * @return the instance
     * @throws UnknownID - if there was no heartbeat frequency defined in settings
     * @throws ArgumentNotValid - if the heartbeat frequency in settings is invalid or either argument is null
     */
    public static synchronized BitarchiveServer getInstance() throws ArgumentNotValid, UnknownID {
        if (instance == null) {
            instance = new BitarchiveServer();
        }
        return instance;
    }

    /**
     * The server creates an instance of the bitarchive it provides access to and starts to listen to JMS messages on
     * the incoming jms queue
     * <p>
     * Also, heartbeats are sent out at regular intervals to the Bitarchive Monitor, to tell that this bitarchive is
     * alive.
     *
     * @throws UnknownID - if there was no heartbeat frequency or temp dir defined in settings or if the bitarchiveid
     * cannot be created.
     * @throws PermissionDenied - if the temporary directory or the file directory cannot be written
     */
    protected BitarchiveServer() throws UnknownID, PermissionDenied {
        System.setOut(new PrintStream(new LoggingOutputStream(LoggingOutputStream.LoggingLevel.INFO, log, "StdOut: ")));
        System.setErr(new PrintStream(new LoggingOutputStream(LoggingOutputStream.LoggingLevel.WARN, log, "StdErr: ")));
        boolean listening = false; // are we listening to queue ANY_BA
        File serverdir = FileUtils.getTempDir();
        if (!serverdir.exists()) {
            serverdir.mkdirs();
        }
        if (!serverdir.canWrite()) {
            throw new PermissionDenied("Not allowed to write to temp directory '" + serverdir + "'");
        }
        log.info("Storing temporary files at '{}'", serverdir.getPath());

        bitarchiveAppId = createBitarchiveAppId();

        allBa = Channels.getAllBa();
        anyBa = Channels.getAnyBa();
        baMon = Channels.getTheBamon();
        ba = Bitarchive.getInstance();
        con = JMSConnectionFactory.getInstance();
        con.setListener(allBa, this);
        baa = BitarchiveAdmin.getInstance();
        if (baa.hasEnoughSpace() && !baa.isReadonlyMode()) {
            con.setListener(anyBa, this);
            listening = true;
        } else {
        	if (baa.isReadonlyMode()) {
        		log.info("Bitarchiveserver set to readonly mode -- not listening to {}", anyBa.getName());
        	} else {
        		log.warn("Not enough space to guarantee store -- not listening to {}", anyBa.getName());
        	}
        }

        // create map for batchjobs
        batchProcesses = Collections.synchronizedMap(new HashMap<String, Thread>());

        // Create and start the heartbeat sender
        Timer timer = new Timer(true);
        heartBeatSender = new HeartBeatSender(baMon, this);
        long frequency = Settings.getLong(ArchiveSettings.BITARCHIVE_HEARTBEAT_FREQUENCY);
        timer.scheduleAtFixedRate(heartBeatSender, 0, frequency);
        log.info("Heartbeat frequency: '{}'", frequency);
        // Next logentry depends on whether we are listening to ANY_BA or not
        String logmsg = "Created bitarchive server listening on: " + allBa.getName();
        if (listening) {
            logmsg += " and " + anyBa.getName();
        }

        log.info(logmsg);

        log.info("Broadcasting heartbeats on: {}", baMon.getName());
    }

    /**
     * Ends the heartbeat sender before next loop and removes the server as listener on allBa and anyBa. Closes the
     * bitarchive. Calls cleanup.
     */
    public synchronized void close() {
        log.info("BitarchiveServer {} closing down", getBitarchiveAppId());
        cleanup();
        if (con != null) {
            con.removeListener(allBa, this);
            con.removeListener(anyBa, this);
            con = null;
        }
        log.info("BitarchiveServer {} closed down", getBitarchiveAppId());
    }

    /**
     * Ends the heartbeat sender before next loop.
     */
    public void cleanup() {
        if (ba != null) {
            ba.close();
            ba = null;
        }
        if (baa != null) {
            baa.close();
            baa = null;
        }
        if (heartBeatSender != null) {
            heartBeatSender.cancel();
            heartBeatSender = null;
        }
        instance = null;
    }

    /**
     * Process a get request and send the result back to the client. If the arcfile is not found on this bitarchive
     * machine, nothing happens.
     *
     * @param msg a container for upload request
     * @throws ArgumentNotValid If the message is null.
     */
    @Override
    public void visit(GetMessage msg) throws ArgumentNotValid {
        ArgumentNotValid.checkNotNull(msg, "GetMessage msg");
        BitarchiveRecord bar;
        log.trace("Processing getMessage({}:{}).", msg.getArcFile(), msg.getIndex());
        try {
            bar = ba.get(msg.getArcFile(), msg.getIndex());
        } catch (Throwable t) {
            log.warn("Error while processing get message '{}'", msg, t);
            msg.setNotOk(t);
            con.reply(msg);
            return;
        }
        if (bar != null) {
            msg.setRecord(bar);
            log.debug("Sending reply: {}", msg.toString());
            con.reply(msg);
        } else {
            log.trace("Record({}:{}). not found on this BitarchiveServer", msg.getArcFile(), msg.getIndex());
        }
    }

    /**
     * Process a upload request and send the result back to the client. This may be a very time consuming process and is
     * a blocking call.
     *
     * @param msg a container for upload request
     * @throws ArgumentNotValid If the message is null.
     */
    @Override
    public void visit(UploadMessage msg) throws ArgumentNotValid {
        ArgumentNotValid.checkNotNull(msg, "UploadMessage msg");
        // TODO Implement a thread-safe solution on resource level rather than
        // message processor level.
        try {
            try {
                synchronized (this) {
                    // Important when two identical files are uploaded
                    // simultanously.
                    ba.upload(msg.getRemoteFile(), msg.getArcfileName());
                }
            } catch (Throwable t) {
                log.warn("Error while processing upload message '{}'", msg, t);
                msg.setNotOk(t);
            } finally { // Stop listening if disk is now full
                if (!baa.hasEnoughSpace()) {
                    log.warn("Cannot guarantee enough space, no longer listening to {} for uploads", anyBa.getName());
                    con.removeListener(anyBa, this);
                }
            }
        } catch (Throwable t) {
            // This block will be executed if the above finally block throws an
            // exception. Therefore the message is not set to notOk here
            log.warn("Error while removing listener after upload message '{}'", msg, t);
        } finally {
            log.info("Sending reply: {}", msg.toString());
            con.reply(msg);
        }
    }

    /**
     * Removes an arcfile from the bitarchive and returns the removed file as an remotefile.
     * <p>
     * Answers OK if the file is actually removed. Answers notOk if the file exists with wrong checksum or wrong
     * credentials Doesn't answer if the file doesn't exist.
     * <p>
     * This method always generates a warning when deleting a file.
     * <p>
     * Before the file is removed it is verified that - the file exists in the bitarchive - the file has the correct
     * checksum - the supplied credentials are correct
     *
     * @param msg a container for remove request
     * @throws ArgumentNotValid If the RemoveAndGetFileMessage is null.
     */
    @Override
    public void visit(RemoveAndGetFileMessage msg) throws ArgumentNotValid {
        ArgumentNotValid.checkNotNull(msg, "RemoveAndGetFileMessage msg");
        String mesg = "Request to move file '" + msg.getFileName() + "' with checksum '" + msg.getCheckSum()
                + "' to attic";
        log.info(mesg);
        NotificationsFactory.getInstance().notify(mesg, NotificationType.INFO);

        File foundFile = ba.getFile(msg.getFileName());
        // Only send an reply if the file was found
        if (foundFile == null) {
            log.warn("Remove: '{}' not found", msg.getFileName());
            return;
        }

        try {

            log.debug("File located - now checking the credentials");
            // Check credentials
            String credentialsReceived = msg.getCredentials();
            ArgumentNotValid.checkNotNullOrEmpty(credentialsReceived, "credentialsReceived");
            if (!credentialsReceived.equals(Settings.get(ArchiveSettings.ENVIRONMENT_THIS_CREDENTIALS))) {
                String message = "Attempt to remove '" + foundFile + "' with wrong credentials!";
                log.warn(message);
                msg.setNotOk(message);
                return;
            }

            log.debug("Credentials accepted, now checking the checksum");

            String checksum = ChecksumCalculator.calculateMd5(foundFile);

            if (!checksum.equals(msg.getCheckSum())) {
                final String message = "Attempt to remove '" + foundFile + " failed due to checksum mismatch: "
                        + msg.getCheckSum() + " != " + checksum;
                log.warn(message);
                msg.setNotOk(message);
                return;
            }

            log.debug("Checksums matched - preparing to move and return file");
            File moveTo = baa.getAtticPath(foundFile);
            if (!foundFile.renameTo(moveTo)) {
                final String message = "Failed to move the file:" + foundFile + "to attic";
                log.warn(message);
                msg.setNotOk(message);
                return;
            }
            msg.setFile(moveTo);

            log.warn("Removed file '{}' with checksum '{}'", msg.getFileName(), msg.getCheckSum());
        } catch (Exception e) {
            final String message = "Error while processing message '" + msg + "'";
            log.warn(message, e);
            msg.setNotOk(e);
        } finally {
            con.reply(msg);
        }
    }

    /**
     * Process a batch job and send the result back to the client.
     *
     * @param msg a container for batch jobs
     * @throws ArgumentNotValid If the BatchMessage is null.
     */
    @Override
    public void visit(final BatchMessage msg) throws ArgumentNotValid {
        ArgumentNotValid.checkNotNull(msg, "BatchMessage msg");
        Thread batchThread = new Thread("Batch-" + msg.getID()) {
            @Override
            public void run() {
                try {
                    // TODO Possibly tell batch something that will let
                    // it create more comprehensible file names.
                    // Run the batch job on all files on this machine
                    BatchStatus batchStatus = ba.batch(bitarchiveAppId, msg.getJob());

                    // Create the message which will contain the reply
                    BatchEndedMessage resultMessage = new BatchEndedMessage(baMon, msg.getID(), batchStatus);

                    // Update informational fields in reply message
                    if (batchStatus.getFilesFailed().size() > 0) {
                        resultMessage
                                .setNotOk("Batch job failed on " + batchStatus.getFilesFailed().size() + " files.");
                    }

                    // Send the reply
                    con.send(resultMessage);
                    log.debug("Submitted result message for batch job: {}", msg.getID());
                } catch (Throwable t) {
                    log.warn("Batch processing failed for message '{}'", msg, t);
                    BatchEndedMessage failMessage = new BatchEndedMessage(baMon, bitarchiveAppId, msg.getID(),
                            new NullRemoteFile());
                    failMessage.setNotOk(t);

                    con.send(failMessage);
                    log.debug("Submitted failure message for batch job: {}", msg.getID());
                } finally {
                    // remove from map
                    batchProcesses.remove(msg.getBatchID());
                }
            }
        };
        batchProcesses.put(msg.getBatchID(), batchThread);
        batchThread.start();
    }

    public void visit(BatchTerminationMessage msg) throws ArgumentNotValid {
        ArgumentNotValid.checkNotNull(msg, "BatchTerminationMessage msg");
        log.info("Received BatchTerminationMessage: {}", msg);

        try {
            Thread t = batchProcesses.get(msg.getTerminateID());

            // check whether the batchjob is still running.
            if (t == null) {
                log.info("The batchjob with ID '{}' cannot be found, and must have terminated by it self.",
                        msg.getTerminateID());
                return;
            }

            // try to interrupt.
            if (t.isAlive()) {
                t.interrupt();
            }

            // wait one second, before verifying whether it is dead.
            synchronized (this) {
                try {
                    this.wait(1000);
                } catch (InterruptedException e) {
                    log.trace("Unimportant InterruptedException caught.", e);
                }
            }

            // Verify that is dead, or log that it might have a problem.
            if (t.isAlive()) {
                log.error("The thread '{}' should have been terminated, but it is apparently still alive.", t);
            } else {
                log.info("The batchjob with ID '{}' has successfully been terminated!", msg.getTerminateID());
            }
        } catch (Throwable t) {
            // log problem and set to NotOK!
            log.error("An error occured while trying to terminate {}", msg.getTerminateID(), t);
        }
    }

    /**
     * Process a getFile request and send the result back to the client.
     *
     * @param msg a container for a getfile request
     * @throws ArgumentNotValid If the GetFileMessage is null.
     */
    @Override
    public void visit(GetFileMessage msg) throws ArgumentNotValid {
        ArgumentNotValid.checkNotNull(msg, "GetFileMessage msg");

        try {
            File foundFile = ba.getFile(msg.getArcfileName());
            // Only send an reply if the file was found
            if (foundFile != null) {
                // Be Warned!! The following call does not do what you think it
                // does. This actually creates the RemoteFile object, uploading
                // the file to the ftp server as it does so.
                msg.setFile(foundFile);
                log.info("Sending reply: {}", msg.toString());
                con.reply(msg);
            }
        } catch (Throwable t) {
            log.warn("Error while processing get file message '{}'", msg, t);
        }
    }

    /**
     * Returns a String that identifies this bit archive application (within the bit archive, i.e. either with id ONE or
     * TWO)
     *
     * @return String with IP address of this host and, if specified, the APPLICATION_INSTANCE_ID from settings
     */
    public String getBitarchiveAppId() {
        return bitarchiveAppId;
    }

    /**
     * Returns a String that identifies this bit archive application (within the bit archive, i.e. either with id ONE or
     * TWO). The string has the following form: hostaddress[_applicationinstanceid] fx. "10.0.0.1_appOne" or just
     * "10.0.0.1", if no applicationinstanceid has been chosen.
     *
     * @return String with IP address of this host and, if specified, the APPLICATION_INSTANCE_ID from settings
     * @throws UnknownID - if InetAddress.getLocalHost() failed
     */
    private String createBitarchiveAppId() throws UnknownID {
        String id;

        // Create an id with the IP address of this current host
        id = SystemUtils.getLocalIP();

        // Append an underscore and APPLICATION_INSTANCE_ID from settings
        // to the id, if specified in settings.
        // If no APPLICATION_INSTANCE_ID is found do nothing.
        try {
            String applicationInstanceId = Settings.get(CommonSettings.APPLICATION_INSTANCE_ID);
            if (!applicationInstanceId.isEmpty()) {
                id += "_" + applicationInstanceId;
            }
        } catch (UnknownID e) {
            // Ignore the fact, that there is no APPLICATION_INSTANCE_ID in
            // settings
            log.warn("No setting APPLICATION_INSTANCE_ID found in settings");
        }

        return id;
    }

}
