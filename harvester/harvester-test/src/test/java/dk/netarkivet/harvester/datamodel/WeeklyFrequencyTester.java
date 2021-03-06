/*
 * #%L
 * Netarchivesuite - harvester - test
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
package dk.netarkivet.harvester.datamodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.junit.Test;

import dk.netarkivet.common.exceptions.ArgumentNotValid;

/**
 * Tests a weekly frequency.
 */
public class WeeklyFrequencyTester {

    /**
     * Test value is weekly.
     */
    @Test
    public void testTimeunitIsWeekly() {
        WeeklyFrequency freq = new WeeklyFrequency(20);
        assertEquals("Timeunit must be weekly.", freq.ordinal(), TimeUnit.WEEKLY.ordinal());
        assertEquals("Check TimeUnit Weekly", TimeUnit.WEEKLY, TimeUnit.fromOrdinal(TimeUnit.WEEKLY.ordinal()));
    }

    /**
     * Given a frequency that can start any time, check that first event is immediate.
     *
     * @throws Exception
     */
    @Test
    public void testGetFirstEvent1() throws Exception {
        WeeklyFrequency freq = new WeeklyFrequency(4); // Every four days, anytime
        Calendar cal = GregorianCalendar.getInstance();
        cal.set(2005, 1, 28, 22, 42);// Feb. 28th.
        Date d1 = cal.getTime();
        Date d2 = freq.getFirstEvent(d1);
        assertEquals("First event should happen at once.", d1, d2);
    }

    /**
     * Given a frequency that can start Wed at 4:22, check that first event is first possible Wed 4:22
     *
     * @throws Exception
     */
    @Test
    public void testGetFirstEvent2() throws Exception {
        WeeklyFrequency freq = new WeeklyFrequency(4, Calendar.WEDNESDAY, 4, 22); // Every four weeks, on the day, hour
        // and minute
        Calendar cal = GregorianCalendar.getInstance();
        cal.set(2005, Calendar.FEBRUARY, 28, 22, 42);// Feb. 28th. is a monday
        Date d1 = cal.getTime();
        cal.add(Calendar.MINUTE, ((60 + 22) - 42));
        cal.add(Calendar.HOUR, ((24 + 4) - (22 + 1))); // now tuesday
        cal.add(Calendar.DAY_OF_WEEK, 1); // wednesday
        Date d3 = cal.getTime();
        Date d2 = freq.getFirstEvent(d1);
        assertEquals("First event should happen Wednesday on the 22nd minute of the 4th hour.", d3, d2);
    }

    /**
     * Given a frequency that can start any time, check that next event is after correct period.
     *
     * @throws Exception
     */
    @Test
    public void testGetNextEvent1() throws Exception {
        WeeklyFrequency freq = new WeeklyFrequency(4); // Every four days, anytime
        Calendar cal = GregorianCalendar.getInstance();
        cal.set(2005, 1, 28, 22, 42);// Feb. 28th.
        Date d1 = cal.getTime();
        cal.add(Calendar.DATE, 4 * 7);
        Date d3 = cal.getTime();
        Date d4 = freq.getNextEvent(d1);
        assertEquals("Second event should happen four weeks later", d3, d4);
    }

    /**
     * Given a frequency that can start Mon at 5:23, check that first event is first possible Mon 5:23.
     *
     * @throws Exception
     */
    @Test
    public void testGetNextEvent2() throws Exception {
        WeeklyFrequency freq = new WeeklyFrequency(4, Calendar.MONDAY, 5, 23); // Every four days, on the day, hour and
        // minute
        Calendar cal = GregorianCalendar.getInstance();
        cal.set(2005, Calendar.FEBRUARY, 28, 22, 42); // 28/2 2005 is a Monday
        Date d1 = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, 4 * 7);
        cal.add(Calendar.MINUTE, ((60 + 23) - 42));
        cal.add(Calendar.HOUR, ((24 + 5) - (22 + 1))); // Now it's Tuesday
        cal.add(Calendar.DATE, 6); // Make it next Monday
        Date d3 = cal.getTime();
        Date d4 = freq.getNextEvent(d1);
        assertEquals("Second event should happen four weeks later, on Monday at the 23rd minute of the 5th hour", d3,
                d4);
    }

    /**
     * Given a frequency that can start Mon at 5:23, check that first event is first possible Mon 5:23, given a time
     * that IS actually Mon 5:23.
     *
     * @throws Exception
     */
    @Test
    public void testGetNextEvent3() throws Exception {
        WeeklyFrequency freq = new WeeklyFrequency(4, Calendar.MONDAY, 5, 23); // Every four days, on the day, hour and
        // minute
        Calendar cal = new GregorianCalendar(2005, Calendar.FEBRUARY, 28, 5, 23); // 28/2 2005 is a Monday
        Date d1 = cal.getTime();
        Calendar cal2 = new GregorianCalendar(2005, Calendar.MARCH, 28, 5, 23);
        Date d3 = cal2.getTime();
        Date d4 = freq.getNextEvent(d1);
        assertEquals("Second event should happen four weeks later, on Monday at the 23rd minute of the 5th hour", d3,
                d4);
    }

    /**
     * Test validity of arguments.
     */
    @Test
    public void testValidityOfArguments() throws Exception {
        try {
            new WeeklyFrequency(-1);
            fail("should throw exception on negative number of units");
        } catch (ArgumentNotValid e) {
            // Expected
        }

        try {
            new WeeklyFrequency(0);
            fail("should throw exception on zero number of units");
        } catch (ArgumentNotValid e) {
            // Expected
        }

        try {
            new WeeklyFrequency(-1, 5, 5, 23);
            fail("should throw exception on negative number of units");
        } catch (ArgumentNotValid e) {
            // Expected
        }

        try {
            new WeeklyFrequency(0, 5, 5, 23);
            fail("should throw exception on zero number of units");
        } catch (ArgumentNotValid e) {
            // Expected
        }

        try {
            new WeeklyFrequency(1, Calendar.SUNDAY - 1, 5, 23);
            fail("should throw exception on illegal date");
        } catch (ArgumentNotValid e) {
            // Expected
        }

        try {
            new WeeklyFrequency(1, Calendar.SATURDAY + 1, 5, 23);
            fail("should throw exception on illegal date");
        } catch (ArgumentNotValid e) {
            // Expected
        }

        try {
            new WeeklyFrequency(1, 21, 24, 23);
            fail("should throw exception on illegal time");
        } catch (ArgumentNotValid e) {
            // Expected
        }

        try {
            new WeeklyFrequency(1, 32, -1, 23);
            fail("should throw exception on illegal time");
        } catch (ArgumentNotValid e) {
            // Expected
        }

        try {
            new WeeklyFrequency(1, 32, 0, -1);
            fail("should throw exception on illegal time");
        } catch (ArgumentNotValid e) {
            // Expected
        }

        try {
            new WeeklyFrequency(1, 32, 0, 60);
            fail("should throw exception on illegal time");
        } catch (ArgumentNotValid e) {
            // Expected
        }

        WeeklyFrequency freq = new WeeklyFrequency(4, Calendar.MONDAY, 5, 23);
        try {
            freq.getFirstEvent(null);
            fail("should throw exception");
        } catch (ArgumentNotValid e) {
            // Expected
        }
        try {
            freq.getNextEvent(null);
            fail("should throw exception");
        } catch (ArgumentNotValid e) {
            // Expected
        }
    }
}
