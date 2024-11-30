package dev.tildejustin.example.mixin;

import com.sun.nio.file.ExtendedOpenOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.registry.RegistryTracker;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;

@Mixin(LevelStorage.Session.class)
public abstract class LevelStorage$SessionMixin {
    @Shadow
    @Final
    private Path directory;

    @Unique
    private SeekableByteChannel leakedHandle;

    @Inject(
            method = "backupLevelDataFile(Lnet/minecraft/util/registry/RegistryTracker;Lnet/minecraft/world/SaveProperties;Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At(value = "RETURN")
    )
    private void lockLevelDat(RegistryTracker registryTracker, SaveProperties saveProperties, CompoundTag compoundTag, CallbackInfo ci) throws IOException {
        Path file = this.directory.resolve("level.dat");
        if (leakedHandle == null) {
            leakedHandle = Files.newByteChannel(
                    file,
                    StandardOpenOption.WRITE,
                    ExtendedOpenOption.NOSHARE_READ,
                    ExtendedOpenOption.NOSHARE_WRITE,
                    ExtendedOpenOption.NOSHARE_DELETE
            );
        }
    }
}
