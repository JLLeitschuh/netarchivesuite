/* File:       $Id: PostgreSQLSpecifics.java 2804 2013-11-01 16:06:07Z svc $
 * Revision:   $Revision: 2804 $
 * Author:     $Author: svc $
 * Date:       $Date: 2013-11-01 17:06:07 +0100 (Fri, 01 Nov 2013) $
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

package dk.netarkivet.harvester.dao.spec;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import dk.netarkivet.common.exceptions.IOFailure;
import dk.netarkivet.harvester.datamodel.Constants;

/**
 * PostgreSQL-specific implementation of DB methods.
 * Intended for PostgreSQL 8.3 and above.
 *
 *  PostgreSQL does not support the CLOB datatype but instead provides a "text"
 *  data type.
 *  See http://www.postgresql.org/docs/current/static/datatype-character.html.
 *
 */
public class PostgreSQLSpecifics extends DBSpecifics {

    /** The log. */
    private Log log = LogFactory.getLog(PostgreSQLSpecifics.class);
    
    /**
     * Get an instance of the PostgreSQL specifics class.
     *
     * @return Instance of the PostgreSQL specifics class.
     */
    public static DBSpecifics getInstance() {
        return new PostgreSQLSpecifics();
    }
    
    /**
     * Get a temporary table for short-time use. The table should be disposed of
     * with dropTemporaryTable. The table has two columns domain_name
     * varchar(Constants.MAX_NAME_SIZE) config_name
     * varchar(Constants.MAX_NAME_SIZE)
     * @return The name of the created table
     */
    public String getJobConfigsTmpTable() {
    	getDao().executeUpdate(
    			"CREATE TEMPORARY TABLE jobconfignames ("
    			+ "domain_name varchar(" + Constants.MAX_NAME_SIZE + ")"
    			+ ", config_name varchar(" + Constants.MAX_NAME_SIZE + ")"
    			+ ") ON COMMIT DROP",
    			false);
        return "jobconfignames";
    }

    /**
     * Dispose of a temporary table created with getTemporaryTable. This can be
     * expected to be called from within a finally clause, so it mustn't throw
     * exceptions.
     * @param tableName The name of the temporary table
     */
    public void dropJobConfigsTmpTable(final String tableName) {
    	// Commit triggers the drop, see getJobConfigsTmpTable
    	getDao().executeUpdate("SELECT 1", true);
    }

    /**
     * Get the name of the JDBC driver class that handles interfacing to this
     * server.
     *
     * @return The name of a JDBC driver class
     */
    public String getDriverClassName() {
        return "org.postgresql.Driver";
    }

    @Override
    public String getOrderByLimitAndOffsetSubClause(long limit, long offset) {
        return "LIMIT " + limit + " OFFSET " + offset;
    }

    @Override
    public boolean supportsClob() {
        return false;
    }

    /**
     * Migrates the 'jobs' table from version 3 to version 4 consisting of a
     * change of the field forcemaxbytes from int to bigint and setting its
     * default to -1. Furthermore the default value for field num_configs is set
     * to 0.
     *
     * @throws IOFailure
     *             in case of problems in interacting with the database
     */
    protected synchronized void migrateJobsv3tov4() {
        String[] sqlStatements = {
                "ALTER TABLE jobs DROP COLUMN forcemaxbytes",
                "ALTER TABLE jobs ADD COLUMN forcemaxbytes bigint not null default -1",
                "ALTER TABLE jobs DROP COLUMN num_configs",
                "ALTER TABLE jobs ADD COLUMN num_configs int not null default 0" };
        getDao().updateTable("jobs", 4, sqlStatements);
    }

    /**
     * Migrates the 'jobs' table from version 4 to version 5 consisting of
     * adding new fields 'resubmitted_as_job' and 'submittedDate'.
     *
     * @throws IOFailure
     *             in case of problems in interacting with the database
     */
    protected synchronized void migrateJobsv4tov5() {
        String[] sqlStatements = {
                "ALTER TABLE jobs ADD COLUMN submitteddate datetime "
                        + "AFTER enddate",
                "ALTER TABLE jobs ADD COLUMN resubmitted_as_job bigint" };
        getDao().updateTable("jobs", 5, sqlStatements);
    }

    /**
     * Migrates the 'configurations' table from version 3 to version 4. This
     * consists of altering the default value of field 'maxbytes' to -1.
     */
    protected synchronized void migrateConfigurationsv3ov4() {
        // Update configurations table to version 4
        String[] sqlStatements
            = {"ALTER TABLE configurations ALTER maxbytes SET DEFAULT -1" };
        getDao().updateTable("configurations", 4, sqlStatements);
    }

    /**
     * Migrates the 'fullharvests' table from version 2 to version 3. This
     * consists of altering the default value of field 'maxbytes' to -1
     */
    protected synchronized void migrateFullharvestsv2tov3() {
        // Update fullharvests table to version 3
        String[] sqlStatements
            = { "ALTER TABLE fullharvests ALTER maxbytes SET DEFAULT -1" };
        getDao().updateTable("fullharvests", 3, sqlStatements);
    }

    @Override
    protected void createGlobalCrawlerTrapExpressions() {
        log.warn("Please use the provided SQL scripts to update the DB schema");
        getDao().updateTable("global_crawler_trap_expressions", 1);
    }

    @Override
    protected void createGlobalCrawlerTrapLists() {
        log.warn("Please use the provided SQL scripts to update the DB schema");
        getDao().updateTable("global_crawler_trap_lists", 1);
    }

    @Override
    public void createFrontierReportMonitorTable() {
        log.warn("Please use the provided SQL scripts to update the DB schema");
        getDao().updateTable("frontierreportmonitor", 1);
    }

    @Override
    public void createRunningJobsHistoryTable() {
        log.warn("Please use the provided SQL scripts to update the DB schema");
        getDao().updateTable("runningjobshistory", 1);
    }

    @Override
    public void createRunningJobsMonitorTable() {
        log.warn("Please use the provided SQL scripts to update the DB schema");
        getDao().updateTable("runningjobsmonitor", 1);
    }

    // Below DB changes introduced with development release 3.15
    // with changes to tables 'runningjobshistory', 'runningjobsmonitor',
    // 'configurations', 'fullharvests', and 'jobs'.

    /**
     * Migrates the 'runningjobshistory' table from version 1 to version 2. This
     * consists of adding the new column 'retiredQueuesCount'.
     */
    @Override
    protected void migrateRunningJobsHistoryTableV1ToV2() {
        String[] sqlStatements = {
                "ALTER TABLE runningjobshistory "
                + "ADD COLUMN retiredQueuesCount bigint not null"
        };
        getDao().updateTable("runningjobshistory", 2, sqlStatements);
    }

    /**
     * Migrates the 'runningjobsmonitor' table from version 1 to version 2. This
     * consists of adding the new column 'retiredQueuesCount'.
     */
    @Override
    protected void migrateRunningJobsMonitorTableV1ToV2() {
        String[] sqlStatements = {
                "ALTER TABLE runningjobsmonitor "
                + "ADD COLUMN retiredQueuesCount bigint not null"
        };
        getDao().updateTable("runningjobsmonitor", 2, sqlStatements);
    }

    @Override
    protected void migrateDomainsv2tov3() {
        String[] sqlStatements = {
                "ALTER TABLE domains "
                + "ALTER COLUMN crawlertraps type text"
        };
        getDao().updateTable("domains", 3, sqlStatements);
    }

    @Override
    protected void migrateConfigurationsv4tov5() {
        // Update configurations table to version 5
        String[] sqlStatements
            = {"ALTER TABLE configurations ALTER COLUMN maxobjects TYPE bigint" };
        getDao().updateTable("configurations", 5, sqlStatements);
    }

    @Override
    protected void migrateFullharvestsv3tov4() {
        // Update fullharvests table to version 4
        String[] sqlStatements
            = {"ALTER TABLE fullharvests ADD COLUMN maxjobrunningtime bigint NOT NULL DEFAULT 0"};
        getDao().updateTable("fullharvests", 4, sqlStatements);
    }

    @Override
    protected void migrateJobsv5tov6() {
        String[] sqlStatements
        = {"ALTER TABLE jobs ADD COLUMN forcemaxrunningtime bigint NOT NULL DEFAULT 0"};
        getDao().updateTable("jobs", 6, sqlStatements);
    }

    @Override
    protected void migrateFullharvestsv4tov5() {
        // Update fullharvests table to version 5
        String[] sqlStatements
            = {"ALTER TABLE fullharvests ADD COLUMN isindexready bool NOT NULL DEFAULT false"};
        getDao().updateTable("fullharvests", 5, sqlStatements);
        
    }
    
    @Override
    protected void createExtendedFieldTypeTable() {
        String[] statements = new String[3];
        statements[0] = "" + "CREATE TABLE extendedfieldtype " + "  ( "
                + "     extendedfieldtype_id BIGINT NOT NULL PRIMARY KEY, "
                + "     name             VARCHAR(50) NOT NULL " + "  )";

        statements[1] =
            "INSERT INTO extendedfieldtype ( extendedfieldtype_id, name )"
            + "VALUES ( 1, 'domains')";
        statements[2] = 
            "INSERT INTO extendedfieldtype ( extendedfieldtype_id, name )"
            + " VALUES ( 2, 'harvestdefinitions')";

        getDao().updateTable("extendedfieldtype", 1, statements);
    }
    
    @Override
    protected void createExtendedFieldTable() {
        String createStatement = "" + "CREATE TABLE extendedfield " + "  ( "
                + "     extendedfield_id BIGINT NOT NULL PRIMARY KEY, "
                + "     extendedfieldtype_id BIGINT NOT NULL, "
                + "     name             VARCHAR(50) NOT NULL, "
                + "     format           VARCHAR(50) NOT NULL, "
                + "     defaultvalue     VARCHAR(50) NOT NULL, "
                + "     options          VARCHAR(50) NOT NULL, "
                + "     datatype         INT NOT NULL, "
                + "     mandatory        INT NOT NULL, "
                + "     sequencenr       INT " + "  )";

        getDao().updateTable("extendedfield", 1, createStatement);
    }

    @Override
    protected void createExtendedFieldValueTable() {
        String createStatement = "" + "CREATE TABLE extendedfieldvalue "
                + "  ( "
                + "     extendedfieldvalue_id BIGINT NOT NULL PRIMARY KEY, "
                + "     extendedfield_id      BIGINT NOT NULL, "
                + "     instance_id           BIGINT NOT NULL, "
                + "     content               VARCHAR(100) NOT NULL " + "  )";

        getDao().updateTable("extendedfieldvalue", 1,
                createStatement);
    }

    @Override
    protected synchronized void migrateJobsv6tov7() {
        String[] sqlStatements = {
                "ALTER TABLE jobs ADD COLUMN continuationof BIGINT DEFAULT NULL"
        };
        getDao().updateTable("jobs", 7, sqlStatements);
    }
    
    @Override
    protected void migrateJobsv7tov8() {
        String[] sqlStatements = {
                "ALTER TABLE jobs ADD COLUMN creationdate TIMESTAMP DEFAULT NULL"
        };
        getDao().updateTable("jobs", 8, sqlStatements);
    }

    @Override
    protected void migrateJobsv8tov9() {
        String[] sqlStatements = {
                "ALTER TABLE jobs ADD COLUMN harvestname_prefix VARCHAR(100) DEFAULT NULL"
        };
        getDao().updateTable("jobs", 9, sqlStatements);   
    }
    
    @Override
    protected void migrateHarvestdefinitionsv2tov3() {
        String[] sqlStatements = {
                "ALTER TABLE harvestdefinitions ADD COLUMN audience VARCHAR(100) DEFAULT NULL"
        };
        getDao().updateTable("harvestdefinitions", 3, sqlStatements);
    }
    
    @Override
    protected void migrateHarvestdefinitionsv3tov4() {
        String[] sqlStatements = {
                "ALTER TABLE harvestdefinitions ADD COLUMN channel_id BIGINT DEFAULT NULL"
        };
        getDao().updateTable("harvestdefinitions", 4, sqlStatements);
    }
    
    @Override
    protected void migrateJobsv9tov10() {
        String[] sqlStatements = {
                "ALTER TABLE jobs ADD COLUMN channel VARCHAR(300) DEFAULT NULL",
                "ALTER TABLE jobs ADD COLUMN snapshot BOOL",
        };
        getDao().updateTable("jobs", 10, sqlStatements);   
    }

    @Override
    protected void createHarvestChannelTable() {
        String createStatement = "CREATE TABLE harvestchannel ("
                + "id BIGINT NOT NULL PRIMARY KEY, "
                + "name VARCHAR(300) NOT NULL UNIQUE,"
                + "snapshot BOOL NOT NULL,"
                + "isdefault BOOL NOT NULL,"
                + "comments VARCHAR(30000)"
                + ")";
        getDao().updateTable("harvestchannel", 1, createStatement);
    }    
}