package dev.tildejustin.example.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.sun.nio.file.ExtendedOpenOption;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;

@Mixin(LevelStorage.Session.class)
public abstract class LevelStorage$SessionMixin {
    @Unique
    private SeekableByteChannel levelDataFile;

    @Unique
    private SeekableByteChannel levelDataBackupFile;

    @Unique
    private final OpenOption[] options = new OpenOption[]{StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE, ExtendedOpenOption.NOSHARE_READ, ExtendedOpenOption.NOSHARE_WRITE};

    @WrapOperation(method = "backupLevelDataFile(Lnet/minecraft/util/registry/RegistryTracker;Lnet/minecraft/world/SaveProperties;Lnet/minecraft/nbt/CompoundTag;)V", at = @At(value = "INVOKE", target = "Ljava/io/File;createTempFile(Ljava/lang/String;Ljava/lang/String;Ljava/io/File;)Ljava/io/File;"))
    private File noTempFile(String prefix, String suffix, File dir, Operation<File> original) {
        return null;
    }

    @SuppressWarnings("InvalidInjectorMethodSignature")
    @WrapOperation(method = "backupLevelDataFile(Lnet/minecraft/util/registry/RegistryTracker;Lnet/minecraft/world/SaveProperties;Lnet/minecraft/nbt/CompoundTag;)V", at = @At(value = "NEW", target = "java/io/FileOutputStream"))
    private @Coerce OutputStream replaceOutputStream(File file, Operation<FileOutputStream> original, @Share("output") LocalRef<ByteArrayOutputStream> outputStream) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        outputStream.set(byteArrayOutputStream);
        return byteArrayOutputStream;
    }

    @WrapOperation(method = "backupLevelDataFile(Lnet/minecraft/util/registry/RegistryTracker;Lnet/minecraft/world/SaveProperties;Lnet/minecraft/nbt/CompoundTag;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;backupAndReplace(Ljava/io/File;Ljava/io/File;Ljava/io/File;)V"))
    private void secureReplace(File dat, File temp, File bak, Operation<Void> original, @Share("output") LocalRef<ByteArrayOutputStream> outputStream) throws IOException {
        if (levelDataFile == null || Files.notExists(dat.toPath())) {
            if (levelDataFile != null) {
                levelDataFile.close();
            }
            levelDataFile = Files.newByteChannel(dat.toPath(), options);
        }

        if (levelDataBackupFile == null) {
            levelDataBackupFile = Files.newByteChannel(bak.toPath(), options);
        }

        if (Files.exists(dat.toPath())) {
            levelDataBackupFile.truncate(0);
            levelDataBackupFile.position(0);
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
}
