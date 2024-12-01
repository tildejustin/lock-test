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
    private static final HashMap<String, FileChannel> files = new HashMap<>();

    static {
        // TODO: test noshare on linux
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

    public static boolean hasFile(File file) {
        return files.containsKey(file.toPath().toString());
    }

    public static boolean hasFile(Path path) {
        return files.containsKey(path.toString());
    }

    public static FileChannel remove(Path path) throws IOException {
        return files.remove(path.toString());
    }

    // gets the cache for the current file, making a new one if it doesn't exist
    public static FileChannel refresh(@NotNull File file) {
        @Nullable FileChannel channel = files.get(file.toPath().toString());

        if (channel != null && channel.isOpen() && Files.exists(file.toPath())) {
            return channel;
        }

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
            return channel;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
