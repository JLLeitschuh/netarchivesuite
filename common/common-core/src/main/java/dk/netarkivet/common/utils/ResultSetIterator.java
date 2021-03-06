/*
 * #%L
 * Netarchivesuite - common
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

package dk.netarkivet.common.utils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.NoSuchElementException;

import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.IOFailure;

/**
 * Similar to a FilterIterator, but takes a java.sql.ResultSet (which is neither Iterable, Iterator nor Enumeration).
 *
 * @param <T> The type returned by the ResultSetIterator
 */
public abstract class ResultSetIterator<T> implements Iterator<T> {

    /** The current Statement that this Result originates from. */
    private final Statement stm;

    /** The current ResultSet that this Iterator operates upon. */
    private final ResultSet res;

    /** Temporary storage to hold the object that the Iterator returns. */
    private T objectCache;

    /** Tells us whether the resultset is closed yet. */
    private boolean isClosed = false;

    /**
     * Constructor for this class.
     *
     * @param res a ResultSet for this Iterator to operate on.
     */
    public ResultSetIterator(Statement stm, ResultSet res) {
        ArgumentNotValid.checkNotNull(stm, "Statement");
        ArgumentNotValid.checkNotNull(res, "ResultSet");
        this.stm = stm;
        this.res = res;
    }

    /**
     * Returns <tt>true</tt> if the iteration has more elements. (In other words, returns <tt>true</tt> if <tt>next</tt>
     * would return an element rather than throwing an exception.)
     *
     * @return <tt>true</tt> if the iterator has more elements.
     */
    public boolean hasNext() {
        if (objectCache == null) {
            try {
                if (!isClosed && res.next()) {
                    objectCache = filter(res);
                } else {
                    isClosed = true;
                    res.close();
                    stm.close();
                }
            } catch (SQLException e) {
                throw new IOFailure("SQL error getting next element from " + res + "\n"
                        + ExceptionUtils.getSQLExceptionCause(e), e);
            }
        }
        return objectCache != null;
    }

    /**
     * Returns the object corresponding to the given object, or null if that object is to be skipped.
     *
     * @param result An object in the source iterator domain
     * @return An object in this iterators domain, or null
     */
    public abstract T filter(ResultSet result);

    /**
     * Returns the next element in the iteration. Calling this method repeatedly until the {@link #hasNext()} method
     * returns false will return each element in the underlying collection exactly once.
     *
     * @return the next element in the iteration.
     * @throws NoSuchElementException iteration has no more elements.
     */
    public T next() {
        if (objectCache != null) {
            T obj = objectCache;
            objectCache = null;
            return obj;
        }
        throw new NoSuchElementException();
    }

    /**
     * Removes from the underlying collection the last element returned by the iterator (optional operation). This
     * method can be called only once per call to <tt>next</tt>. The behavior of an iterator is unspecified if the
     * underlying collection is modified while the iteration is in progress in any way other than by calling this
     * method.
     *
     * @throws UnsupportedOperationException if the <tt>remove</tt> operation is not supported by this Iterator.
     * @throws IllegalStateException if the <tt>next</tt> method has not yet been called, or the <tt>remove</tt> method
     * has already been called after the last call to the <tt>next</tt> method.
     */
    public void remove() {
        throw new UnsupportedOperationException("This class does not support remove()");
    }

}
