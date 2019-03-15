package dk.netarkivet.common.distribute.hadoop;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.io.IOUtils;

import dk.netarkivet.common.distribute.RemoteFile;
import dk.netarkivet.common.exceptions.IOFailure;

public class HadoopRemoteFile implements RemoteFile, Closeable {

    private final Path outputDir;
    private final FileSystem srcFS;

    public HadoopRemoteFile(Path outputDir, FileSystem srcFS) {
        this.outputDir = outputDir;
        this.srcFS = srcFS;
    }

    @Override public void copyTo(File destFile) {
        try (InputStream inputStream = getInputStream()) {
            try (FileOutputStream outputStream = new FileOutputStream(destFile)) {
                org.apache.commons.io.IOUtils.copyLarge(inputStream, outputStream);
            }
        } catch (IOException e) {
            throw new IOFailure("message", e);
        }

    }

    @Override public void appendTo(OutputStream out) {
        try {
            RemoteIterator<LocatedFileStatus> files = srcFS
                    .listFiles(outputDir, true);
            while (files.hasNext()) {
                LocatedFileStatus next = files.next();
                if (next.isFile()) {
                    try (InputStream inpustream = srcFS.open(next.getPath())) {
                        IOUtils.copyBytes(inpustream, out, 4096, false);
                    }
                }
            }
        } catch (IOException e) {
            throw new IOFailure("message", e);
        }
    }

    @Override public InputStream getInputStream() {

        try {

            RemoteIterator<LocatedFileStatus> files = srcFS
                    .listFiles(outputDir, true);

            ArrayList<LocatedFileStatus> filesAsList = new ArrayList<>();
            while (files.hasNext()) {
                LocatedFileStatus next = files.next();
                if (next.isFile()) {
                    filesAsList.add(next);
                }
            }
            Iterator<FSDataInputStream> inputstreamIterator = filesAsList.stream()
                    .map(file -> {
                        try {
                            return srcFS.open(file.getPath());
                        } catch (IOException e) {
                            throw new IOFailure("message", e);
                        }
                    }).iterator();

            return new SequenceInputStream(inputstreamIterator);
        } catch (IOException e) {
            throw new IOFailure("message", e);
        }
    }

    @Override public String getName() {
        return outputDir.getName();
    }

    @Override public String getChecksum() {
        return null;
    }

    @Override public void cleanup() {
        try {
            close();
        } catch (IOException e) {
            throw new IOFailure("message", e);
        }
    }

    @Override public void close() throws IOException {
        srcFS.delete(outputDir, true);
    }

    @Override public long getSize() {
        try {
            if (!srcFS.exists(outputDir)){
                return 0;
            }
            return Arrays.stream(srcFS.listStatus(outputDir)).mapToLong(FileStatus::getLen).sum();
        } catch (IOException e) {
            throw new IOFailure("message", e);
        }

    }

    @Override public String toString() {
        return "HadoopRemoteFile{" +
                "outputDir=" + outputDir + ", " +
                "name=" + getName() + ", " +
                "size=" + getSize() +
                '}';
    }
}