package dev.tildejustin.lock;

import com.sun.nio.file.ExtendedOpenOption;
import net.minecraft.util.Util;
import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.HashMap;

public class Cache {
    private static final OpenOption[] options;

    // TODO: drop locks after seedqueue limit
    public static HashMap<String, FileChannel> files = new HashMap<>();

    static {
        if (Util.getOperatingSystem() == Util.OperatingSystem.WINDOWS) {
            options = new OpenOption[]{
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE,
                    ExtendedOpenOption.NOSHARE_READ,
                    ExtendedOpenOption.NOSHARE_WRITE,
                    ExtendedOpenOption.NOSHARE_DELETE
            };
        } else {
            options = new OpenOption[]{
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE,
            };
        }
    }

    // always makes a new file if it doesn't exist, where vanilla makes them at specific points
    // probably doesn't matter too much...
    public static void refreshBoth(@Nullable File dat, @Nullable File bak) {
        if (dat != null) {
            FileChannel currDat = Cache.files.get(dat.toPath().toString());
            refresh(dat, currDat);
        }

        if (bak != null) {
            FileChannel currBak = Cache.files.get(bak.toPath().toString());
            refresh(bak, currBak);
        }
    }

    public static void refresh(@NotNull File file, @Nullable FileChannel channel) {
        if (Files.notExists(file.toPath())) {
            if (channel != null && channel.isOpen()) {
                try {
                    channel.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            try {
                channel = FileChannel.open(file.toPath(), options);
                channel.lock();
                Cache.files.put(file.toPath().toString(), channel);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
