package dev.tildejustin.lock.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.tildejustin.lock.Cache;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

@Mixin(LevelStorage.class)
public abstract class LevelStorageMixin {
    @SuppressWarnings("InvalidInjectorMethodSignature")
    @WrapOperation(method = {"method_29015", "readDataPackSettings", "method_29582"}, at = @At(value = "NEW", target = "(Ljava/io/File;)Ljava/io/FileInputStream;"))
    private static @Coerce InputStream secureStream(File file, Operation<FileInputStream> original) throws IOException {
        FileChannel channel;
        if ((channel = Cache.files.get(file.toPath().toString())) != null) {
            Cache.refresh(file, channel);
            ByteBuffer byteBuffer = ByteBuffer.allocate((int) Files.size(file.toPath()));
            channel.position(0);
            channel.read(byteBuffer);
            return new ByteArrayInputStream(byteBuffer.array());
        }
        return original.call(file);
    }

    @Mixin(LevelStorage.Session.class)
    public abstract static class SessionMixin {
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
            Cache.refreshBoth(dat, bak);
            FileChannel levelDataFile = Cache.files.get(dat.toPath().toString());
            FileChannel levelDataBackupFile = Cache.files.get(bak.toPath().toString());

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

        @Mixin(targets = "net/minecraft/world/level/storage/LevelStorage$Session$1")
        public abstract static class _1Mixin {
            @Inject(method = "visitFile(Ljava/nio/file/Path;Ljava/nio/file/attribute/BasicFileAttributes;)Ljava/nio/file/FileVisitResult;", at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;delete(Ljava/nio/file/Path;)V"))
            private void unlockBeforeDelete(Path path, BasicFileAttributes basicFileAttributes, CallbackInfoReturnable<FileVisitResult> cir) throws IOException {
                FileChannel curr = Cache.files.get(path.toString());
                if (curr != null) {
                    Cache.files.remove(path.toString()).close();
                }
            }
        }
    }
}