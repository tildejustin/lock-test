package dev.tildejustin.example.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.sun.nio.file.ExtendedOpenOption;
import net.minecraft.world.level.storage.LevelStorage;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.HashMap;

@Mixin(LevelStorage.class)
public abstract class LevelStorageMixin {
    @Unique
    private static final HashMap<String, FileChannel> cache = new HashMap<>();

    @SuppressWarnings("InvalidInjectorMethodSignature")
    @WrapOperation(method = {"method_29015", "readDataPackSettings", "method_29582"}, at = @At(value = "NEW", target = "(Ljava/io/File;)Ljava/io/FileInputStream;"))
    private static @Coerce InputStream secureStream(File file, Operation<FileInputStream> original) throws IOException {
        FileChannel channel;
        if ((channel = cache.get(file.toPath().toString())) != null) {
            ByteBuffer byteBuffer = ByteBuffer.allocate((int) Files.size(file.toPath()));
            channel.position(0);
            channel.read(byteBuffer);
            return new ByteArrayInputStream(byteBuffer.array());
        }
        return original.call(file);
    }

    @Mixin(LevelStorage.Session.class)
    public abstract static class SessionMixin {
        @Unique
        private final OpenOption[] options = new OpenOption[]{
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                ExtendedOpenOption.NOSHARE_READ,
                ExtendedOpenOption.NOSHARE_WRITE,
                ExtendedOpenOption.NOSHARE_DELETE
        };

        @WrapOperation(method = "backupLevelDataFile(Lnet/minecraft/util/registry/RegistryTracker;Lnet/minecraft/world/SaveProperties;Lnet/minecraft/nbt/CompoundTag;)V", at = @At(value = "INVOKE", target = "Ljava/io/File;createTempFile(Ljava/lang/String;Ljava/lang/String;Ljava/io/File;)Ljava/io/File;"))
        private File noTempFile(String prefix, String suffix, File dir, Operation<File> original) {
            return null;
        }

        @SuppressWarnings("InvalidInjectorMethodSignature")
        @WrapOperation(method = "backupLevelDataFile(Lnet/minecraft/util/registry/RegistryTracker;Lnet/minecraft/world/SaveProperties;Lnet/minecraft/nbt/CompoundTag;)V", at = @At(value = "NEW", target = "java/io/FileOutputStream"))
        private @Coerce OutputStream writeNBTInMemory(File file, Operation<FileOutputStream> original, @Share("output") LocalRef<ByteArrayOutputStream> outputStream) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            outputStream.set(byteArrayOutputStream);
            return byteArrayOutputStream;
        }

        @WrapOperation(method = "backupLevelDataFile(Lnet/minecraft/util/registry/RegistryTracker;Lnet/minecraft/world/SaveProperties;Lnet/minecraft/nbt/CompoundTag;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;backupAndReplace(Ljava/io/File;Ljava/io/File;Ljava/io/File;)V"))
        private void secureReplace(File dat, File temp, File bak, Operation<Void> original, @Share("output") LocalRef<ByteArrayOutputStream> outputStream) throws IOException {
            refreshHandles(dat, bak);

            FileChannel levelDataFile = cache.get(dat.toPath().toString());
            FileChannel levelDataBackupFile = cache.get(bak.toPath().toString());


            if (Files.exists(dat.toPath()) && Files.size(dat.toPath()) > 0) {
                levelDataBackupFile.truncate(0);
                levelDataBackupFile.position(0);
                levelDataFile.position(0);
                ByteBuffer buffer = ByteBuffer.allocate((int) Files.size(dat.toPath()));
                levelDataFile.read(buffer);
                levelDataBackupFile.write(buffer);
            }

            // riskier than the "write to temp, rename file" strategy that vanilla uses
            // may want to consider reimplementing that, although hard to do while keeping handles valid
            levelDataFile.truncate(0);
            levelDataFile.position(0);
            levelDataFile.write(ByteBuffer.wrap(outputStream.get().toByteArray()));
        }

        // always makes a new file if it doesn't exist, where vanilla makes them at specific points
        // probably doesn't matter too much...
        @Unique
        private void refreshHandles(@Nullable File dat, @Nullable File bak) {
            if (dat != null) {
                FileChannel currDat = cache.get(dat.toPath().toString());
                if (currDat == null || Files.notExists(dat.toPath())) {
                    if (Files.notExists(dat.toPath()) && currDat.isOpen()) {
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
                        cache.put(dat.toPath().toString(), currDat);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            if (bak != null) {
                FileChannel currBak = cache.get(bak.toPath().toString());
                if (currBak == null || Files.notExists(bak.toPath())) {
                    if (Files.notExists(bak.toPath()) && currBak.isOpen()) {
                        try {
                            currBak.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    try {
                        currBak = FileChannel.open(bak.toPath(), options);
                        currBak.lock();
                        cache.put(bak.toPath().toString(), currBak);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
}
