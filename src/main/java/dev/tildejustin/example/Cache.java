package dev.tildejustin.example;

import com.sun.nio.file.ExtendedOpenOption;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.HashMap;

public class Cache {
    public static HashMap<String, FileChannel> files = new HashMap<>();

    private static final OpenOption[] options = new OpenOption[]{
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            ExtendedOpenOption.NOSHARE_READ,
            ExtendedOpenOption.NOSHARE_WRITE,
            ExtendedOpenOption.NOSHARE_DELETE
    };

    // always makes a new file if it doesn't exist, where vanilla makes them at specific points
    // probably doesn't matter too much...
    public static void refreshHandles(@Nullable File dat, @Nullable File bak) {
        if (dat != null) {
            FileChannel currDat = Cache.files.get(dat.toPath().toString());
            if (currDat == null || Files.notExists(dat.toPath())) {
                if (currDat != null /* therefore Files.notExists(dat.toPath()) */ && currDat.isOpen()) {
                    try {
                        currDat.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                try {
                    System.out.println("new handle for " + dat.toPath());
                    currDat = FileChannel.open(dat.toPath(), options);
                    currDat.lock();
                    Cache.files.put(dat.toPath().toString(), currDat);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                System.out.println("keep old handle for " + dat.toPath());
            }
        }

        if (bak != null) {
            FileChannel currBak = Cache.files.get(bak.toPath().toString());
            if (currBak == null || Files.notExists(bak.toPath())) {
                if (currBak != null && currBak.isOpen()) {
                    try {
                        currBak.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                try {
                    currBak = FileChannel.open(bak.toPath(), options);
                    currBak.lock();
                    Cache.files.put(bak.toPath().toString(), currBak);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
