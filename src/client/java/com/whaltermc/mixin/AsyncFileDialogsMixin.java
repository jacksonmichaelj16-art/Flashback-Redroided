package com.whaltermc.mixin;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.io.File;
import java.util.concurrent.CompletableFuture;

@Mixin(
        targets = "com.moulberry.flashback.exporting.AsyncFileDialogs",
        remap = false
)
public class AsyncFileDialogsMixin {

    private static File flashbackRedroided$getExportDir() {
        File dir = new File(
                Minecraft.getInstance().gameDirectory,
                "flashback/exports"
        );

        if (!dir.exists() && !dir.mkdirs()) {
            Flashback.LOGGER.warn(
                    "Failed to create Flashback export directory: {}",
                    dir.getAbsolutePath()
            );
        }

        return dir;
    }

    private static String flashbackRedroided$addExtension(
            String name,
            String... filters
    ) {
        if (name == null || name.isEmpty()) {
            name = "export";
        }

        if (filters != null
                && filters.length == 1
                && filters[0] != null
                && !filters[0].isEmpty()
                && !name.contains(".")) {

            name += "." + filters[0];
        }

        return name;
    }

    @Overwrite
    public static CompletableFuture<String> saveFileDialog(
            String defaultPath,
            String defaultName,
            String filterDescription,
            String... filters
    ) {
        File exportDir =
                flashbackRedroided$getExportDir();

        String fileName =
                flashbackRedroided$addExtension(
                        defaultName,
                        filters
                );

        File output =
                new File(exportDir, fileName);

        Flashback.LOGGER.info(
                "Flashback Redroided: Android save path: {}",
                output.getAbsolutePath()
        );

        return CompletableFuture.completedFuture(
                output.getAbsolutePath()
        );
    }

    @Overwrite
    public static CompletableFuture<String> openFolderDialog(
            String defaultPath
    ) {
        File exportDir =
                flashbackRedroided$getExportDir();

        Flashback.LOGGER.info(
                "Flashback Redroided: Android folder path: {}",
                exportDir.getAbsolutePath()
        );

        return CompletableFuture.completedFuture(
                exportDir.getAbsolutePath()
        );
    }
}