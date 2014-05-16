/* File:        $Id$
 * Revision:    $Revision$
 * Author:      $Author$
 *
 * The Netarchive Suite - Software to harvest and preserve websites
 * Copyright 2004-2012 The Royal Danish Library, the Danish State and
 * University Library, the National Library of France and the Austrian
 * National Library.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package dk.netarkivet.common.exceptions;

/**
 * An exception to throw when an unfinished function is called.
 *
 */

@SuppressWarnings({ "serial"})
public class NotImplementedException extends NetarkivetException {
    /**
     * Constructs new NotImplementedException with the specified detail message.
     * @param message The detail message
     */
    public NotImplementedException(String message) {
        super(message);
    }

    /**
     * Constructs new NotImplementedException with the
     * specified detail message and cause.
     * @param message The detail message
     * @param cause The cause
     */
    public NotImplementedException(String message, Throwable cause) {
        super(message, cause);
    }
}
