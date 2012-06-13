package dk.netarkivet.harvester.harvesting;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.utils.Settings;
import dk.netarkivet.harvester.HarvesterSettings;

public abstract class MetadataFileWriter {

	protected static final int MDF_ARC = 1;

	protected static final int MDF_WARC = 2;

    protected static int metadataFormat = 0;

	protected static synchronized void getMetadataFormat() {
        String metadataFormatSetting = Settings.get(HarvesterSettings.METADATA_FORMAT);
        if ("arc".equalsIgnoreCase(metadataFormatSetting)) {
        	metadataFormat = MDF_ARC;
        } else if ("warc".equalsIgnoreCase(metadataFormatSetting)) {
            metadataFormat = MDF_WARC;
        } else {
        	throw new ArgumentNotValid("Configuration of '" + HarvesterSettings.METADATA_FORMAT + "' is invalid!");
        }
	}

	/**
     * Generates a name for an ARC file containing "preharvest" metadata
     * regarding a given job (e.g. excluded alises).
     *
     * @param jobID the number of the harvester job
     * @return The file name to use for the preharvest metadata, as a String.
     * @throws ArgumentNotValid If jobId is negative
     */
    public static String getPreharvestMetadataARCFileName(long jobID)
        throws ArgumentNotValid {
        ArgumentNotValid.checkNotNegative(jobID, "jobID");
        if (metadataFormat == 0) {
        	getMetadataFormat();
        }
        switch (metadataFormat) {
        case MDF_ARC:
            return jobID + "-preharvest-metadata-" + 1 + ".arc";
        case MDF_WARC:
            return jobID + "-preharvest-metadata-" + 1 + ".warc";
        default:
        	throw new ArgumentNotValid("Configuration of '" + HarvesterSettings.METADATA_FORMAT + "' is invalid!");
        }
    }

    /**
     * Generates a name for an ARC file containing metadata regarding
     * a given job.
     *
     * @param jobID The number of the job that generated the ARC file.
     * @return A "flat" file name (i.e. no path) containing the jobID parameter
     * and ending on "-metadata-N.arc", where N is the serial number of the
     * metadata files for this job, e.g. "42-metadata-1.arc".  Currently,
     * only one file is ever made.
     * @throws ArgumentNotValid if any parameter was null.
     */
    public static String getMetadataARCFileName(String jobID)
        throws ArgumentNotValid {
        ArgumentNotValid.checkNotNull(jobID, "jobID");
        if (metadataFormat == 0) {
        	getMetadataFormat();
        }
        switch (metadataFormat) {
        case MDF_ARC:
            return jobID + "-metadata-" + 1 + ".arc";
        case MDF_WARC:
            return jobID + "-metadata-" + 1 + ".warc";
        default:
        	throw new ArgumentNotValid("Configuration of '" + HarvesterSettings.METADATA_FORMAT + "' is invalid!");
        }
    }

    public static MetadataFileWriter createWriter(File metadataFile) {
        if (metadataFormat == 0) {
        	getMetadataFormat();
        }
        switch (metadataFormat) {
        case MDF_ARC:
        	return MetadataFileWriterArc.createWriter(metadataFile);
        case MDF_WARC:
        	return MetadataFileWriterWarc.createWriter(metadataFile);
        default:
        	throw new ArgumentNotValid("Configuration of '" + HarvesterSettings.METADATA_FORMAT + "' is invalid!");
        }
    }

	public abstract void close() throws IOException;

	public abstract File getFile();

	public abstract void insertMetadataFile(File metadataFile);

    public abstract void writeFileTo(File file, String uri, String mime);

    /** Writes a File to an ARCWriter, if available,
     * otherwise logs the failure to the class-logger.
     * @param writer the given ARCWriter
     * @param fileToArchive the File to archive
     * @param URL the URL with which it is stored in the arcfile
     * @param mimetype The mimetype of the File-contents
     * @return true, if file exists, and is written to the arcfile.
     */
    public abstract boolean writeTo(File fileToArchive, String URL, String mimetype);

    /* Copied from the ARCWriter. */
    public abstract void write(String uri, String contentType, String hostIP,
            long fetchBeginTimeStamp, long recordLength, InputStream in)
            									throws java.io.IOException;

}
