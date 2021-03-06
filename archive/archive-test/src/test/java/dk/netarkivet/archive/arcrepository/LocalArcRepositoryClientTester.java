/*
 * #%L
 * Netarchivesuite - archive - test
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
package dk.netarkivet.archive.arcrepository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import dk.netarkivet.common.CommonSettings;
import dk.netarkivet.common.distribute.arcrepository.ArcRepositoryClient;
import dk.netarkivet.common.distribute.arcrepository.BatchStatus;
import dk.netarkivet.common.distribute.arcrepository.BitarchiveRecord;
import dk.netarkivet.common.distribute.arcrepository.LocalArcRepositoryClient;
import dk.netarkivet.common.distribute.arcrepository.Replica;
import dk.netarkivet.common.exceptions.IOFailure;
import dk.netarkivet.common.exceptions.IllegalState;
import dk.netarkivet.common.utils.FileUtils;
import dk.netarkivet.common.utils.KeyValuePair;
import dk.netarkivet.common.utils.Settings;
import dk.netarkivet.common.utils.batch.ChecksumJob;
import dk.netarkivet.common.utils.batch.FileListJob;
import dk.netarkivet.testutils.preconfigured.MoveTestFiles;
import dk.netarkivet.testutils.preconfigured.ReloadSettings;
import dk.netarkivet.testutils.preconfigured.UseTestRemoteFile;

/**
 * Unit-tests for the class LocalArcRepositoryClient.
 */
public class LocalArcRepositoryClientTester {
    MoveTestFiles mtf = new MoveTestFiles(TestInfo.DISTRIBUTE_ARCREPOSITORY_ORIGINALS_DIR,
    		TestInfo.DISTRIBUTE_ARCREPOSITORY_WORKING_DIR);
    ReloadSettings rs = new ReloadSettings();
    UseTestRemoteFile utrf = new UseTestRemoteFile();

    @Before
    public void setUp() {
        rs.setUp();
        utrf.setUp();

        Settings.set(CommonSettings.DIR_COMMONTEMPDIR,
        		TestInfo.DISTRIBUTE_ARCREPOSITORY_WORKING_DIR.getAbsolutePath());
        mtf.setUp();
    }

    @After
    public void tearDown() {
        mtf.tearDown();
        utrf.tearDown();
        rs.tearDown();
    }

    @Test
    public void testStore() throws Exception {
        File dir1 = new File(TestInfo.DISTRIBUTE_ARCREPOSITORY_WORKING_DIR, "dir1");
        File dir2 = new File(TestInfo.DISTRIBUTE_ARCREPOSITORY_WORKING_DIR, "dir2");
        Settings.set("settings.common.arcrepositoryClient.fileDir", dir1.getAbsolutePath(), dir2.getAbsolutePath());
        ArcRepositoryClient arcrep = new LocalArcRepositoryClient();

        FileUtils.copyFile(TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE,
        		TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY);
        arcrep.store(TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY);
        assertEquals("Should have stored file in one dir only", 1, dir1.list().length + dir2.list().length);
        assertTrue("Dir1 should contain file", new File(dir1,
        		TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY.getName()).exists());
        // Try to store the same file again. This should provoke an IllegalState
        // exception
        try {
            FileUtils.copyFile(TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE,
            		TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY);
            arcrep.store(TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY);
        } catch (IllegalState e) {
            // Expected
        }
        assertEquals("Should not have stored file twice", 1, dir1.list().length + dir2.list().length);

        assertTrue("Dir1 should contain file", new File(dir1,
        		TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY.getName()).exists());
        FileUtils.removeRecursively(dir1);
        FileUtils.copyFile(TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE,
        		TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY);
        arcrep.store(TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY);
        assertEquals("Should have stored file in other dir", 1, dir2.list().length);
        assertTrue("Dir1 should contain file", new File(dir2,
        		TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY.getName()).exists());
    }

    @Test
    public void testGetFileMethod() throws IOException {
        File dir1 = new File(TestInfo.DISTRIBUTE_ARCREPOSITORY_WORKING_DIR, "dir1");
        File dir2 = new File(TestInfo.DISTRIBUTE_ARCREPOSITORY_WORKING_DIR, "dir2");
        Settings.set("settings.common.arcrepositoryClient.fileDir", dir1.getAbsolutePath(), dir2.getAbsolutePath());
        ArcRepositoryClient arcrep = new LocalArcRepositoryClient();
        FileUtils.copyFile(TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE,
        		TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY);
        arcrep.store(TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY);
        assertFalse("Should have removed sample file original",
        		TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY.exists());
        arcrep.getFile(TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY.getName(),
                Replica.getReplicaFromId(Settings.get(CommonSettings.USE_REPLICA_ID)),
                TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY);
        assertTrue("Should have fetched sample file",
        		TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY.exists());
        assertEquals("Should have same contents as original",
                FileUtils.readFile(TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE),
                FileUtils.readFile(TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY));
        try {
            arcrep.getFile("No Such File", Replica.getReplicaFromId(Settings.get(CommonSettings.USE_REPLICA_ID)),
            		TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY);
            fail("Should have died on missing file");
        } catch (IOFailure e) {
            // expected
        }
    }

    @Test
    public void testBatch() {
        File dir1 = new File(TestInfo.DISTRIBUTE_ARCREPOSITORY_WORKING_DIR, "dir1");
        File dir2 = new File(TestInfo.DISTRIBUTE_ARCREPOSITORY_WORKING_DIR, "dir2");
        Settings.set("settings.common.arcrepositoryClient.fileDir", dir1.getAbsolutePath(), dir2.getAbsolutePath());
        ArcRepositoryClient arcrep = new LocalArcRepositoryClient();
        FileUtils.getTempDir().mkdir();
        assertTrue("tmpDir is wrong", FileUtils.getTempDir().isDirectory());
        BatchStatus status = arcrep.batch(new FileListJob(), "BA");
        assertEquals("Should have no files processed at outset", 0, status.getNoOfFilesProcessed());
        FileUtils.copyFile(TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE,
        		TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY);
        arcrep.store(TestInfo.DISTRIBUTE_ARCREPOSITORY_SAMPLE_FILE_COPY);
        status = arcrep.batch(new FileListJob(), "BA");
        assertEquals("Should have one file processed at end", 1, status.getNoOfFilesProcessed());
    }

    @Test
    public void testStoreAndGet() {

        File basedir = new File(TestInfo.DISTRIBUTE_ARCREPOSITORY_WORKING_DIR,
                "localArcRepository");
        Settings.set("settings.common.arcrepositoryClient.fileDir", basedir.getAbsolutePath());

        String testArcName = "2-2-20060731110420-00000-sb-test-har-001.statsbiblioteket.dk.arc";

        ArcRepositoryClient arcrep = new LocalArcRepositoryClient();

        File srcArcFile = new File(TestInfo.DISTRIBUTE_ARCREPOSITORY_ORIGINALS_DIR, "bitarchive1"
                + File.separator + "filedir" + File.separator + testArcName);

        File uploadFile = new File(TestInfo.DISTRIBUTE_ARCREPOSITORY_WORKING_DIR, testArcName);
        FileUtils.copyFile(srcArcFile, uploadFile);

        arcrep.store(uploadFile);

        BitarchiveRecord bar = arcrep.get(testArcName, 0);
        assertNotNull(bar);
    }

    @Test
    public void testNewFunctions() throws IOException {
        Settings.set("settings.common.arcrepositoryClient.fileDir", TestInfo.DISTRIBUTE_ARCREPOSITORY_WORKING_DIR.getAbsolutePath()
                + "/bitarchive2/filedir");
        Settings.set("settings.archive.bitarchive.thisCredentials", "credentials");
        ArcRepositoryClient arcrep = new LocalArcRepositoryClient();

        File res = arcrep.getAllFilenames("ONE");
        String content = FileUtils.readFile(res);
        assertTrue("Should contain test1.arc", content.contains("test1.arc"));
        assertTrue("Should contain test2.arc", content.contains("test2.arc"));
        assertTrue("Should contain test3.arc", content.contains("test3.arc"));

        res = arcrep.getAllChecksums("ONE");

        for (String line : FileUtils.readListFromFile(res)) {
            KeyValuePair<String, String> checksumLine = ChecksumJob.parseLine(line);
            assertEquals("Unexpected checksum for file '" + checksumLine.getKey() + "'", checksumLine.getValue(),
                    arcrep.getChecksum("ONE", checksumLine.getKey()));
        }

        String csTest1 = arcrep.getChecksum("ONE", "test1.arc");

        // test correct.
        File test1 = new File(FileUtils.getTempDir(), "test1.arc");
        FileUtils.copyFile(res, test1);
        File badTest1 = arcrep.correct("ONE", csTest1, test1, "credentials");

        assertNotSame("The checksum of test1.arc should have changed.", csTest1, arcrep.getChecksum("ONE", "test1.arc"));
        assertEquals("test1.arc should have old checksum.", csTest1,
                dk.netarkivet.common.utils.ChecksumCalculator.calculateMd5(badTest1));
    }
}
