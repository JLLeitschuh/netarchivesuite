/*
 * #%L
 * Netarchivesuite - common - test
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
package dk.netarkivet.common.utils.warc;

import java.io.IOException;

import org.archive.io.ArchiveRecordHeader;
import org.archive.format.warc.WARCConstants;
import org.archive.io.warc.WARCRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.netarkivet.common.Constants;
import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.IOFailure;

/**
 * Various utilities on WARC-records. We have borrowed code from wayback.
 *
 * @see org.archive.wayback.resourcestore.indexer.NetarchiveSuiteWARCRecordToSearchResultAdapter.java
 */
public class WARCUtilsInTest {

    /** Logging output place. */
    //protected static final Log log = LogFactory.getLog(WARCUtilsInTest.class);
    protected static final Logger log = LoggerFactory.getLogger(WARCUtilsInTest.class);
    
    /**
     * Read the contents (payload) of an WARC record into a byte array.
     *
     * @param record An WARC record to read from. After reading, the WARC Record will no longer have its own data
     * available for reading.
     * @return A byte array containing the payload of the WARC record. Note that the size of the payload is calculated
     * by subtracting the contentBegin value from the length of the record (both values included in the record header).
     * @throws IOFailure If there is an error reading the data, or if the record is longer than Integer.MAX_VALUE (since
     * we can't make bigger arrays).
     */
    public static byte[] readWARCRecord(WARCRecord record) throws IOFailure {
        ArgumentNotValid.checkNotNull(record, "WARCRecord record");
        if (record.getHeader().getLength() > Integer.MAX_VALUE) {
            throw new IOFailure("WARC Record too long to fit in array: " + record.getHeader().getLength() + " > "
                    + Integer.MAX_VALUE);
        }
        // Calculate the length of the payload.
        // the size of the payload is calculated by subtracting
        // the contentBegin value from the length of the record.

        ArchiveRecordHeader header = record.getHeader();
        long length = header.getLength();

        int payloadLength = (int) (length - header.getContentBegin());

        // read from stream
        byte[] tmpbuffer = new byte[payloadLength];
        byte[] buffer = new byte[Constants.IO_BUFFER_SIZE];
        int bytesRead;
        int totalBytes = 0;
        try {
            for (; (totalBytes < payloadLength) && ((bytesRead = record.read(buffer)) != -1); totalBytes += bytesRead) {
                System.arraycopy(buffer, 0, tmpbuffer, totalBytes, bytesRead);
            }
        } catch (IOException e) {
            throw new IOFailure("Failure when reading the WARC-record", e);
        }

        // Check if the number of bytes read (= totalbytes) matches the
        // size of the buffer.
        if (tmpbuffer.length != totalBytes) {
            // make sure we only return an array with bytes we actualy read
            byte[] truncateBuffer = new byte[totalBytes];
            System.arraycopy(tmpbuffer, 0, truncateBuffer, 0, totalBytes);
            log.debug("Storing " + totalBytes + " bytes. Expected to store: " + tmpbuffer.length);
            return truncateBuffer;
        } else {
            return tmpbuffer;
        }

    }

    /**
     * Find out what type of WARC-record this is.
     *
     * @param record a given WARCRecord
     * @return the type of WARCRecord as a String.
     */
    public static String getRecordType(WARCRecord record) {
        ArchiveRecordHeader header = record.getHeader();
        return (String) header.getHeaderValue(WARCConstants.HEADER_KEY_TYPE);
    }

}
