package io.github.yoglappland.spectralization;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.slf4j.Logger;

@Mod(Spectralization.MODID)
public class Spectralization {
    public static final String MODID = "spectralization";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Spectralization() {
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("Spectralization initialized");
    }

    @SubscribeEvent
    public void onBlockDrops(BlockDropsEvent event) {
        if (!event.getState().is(Blocks.GLOWSTONE)) {
            return;
        }

        BlockPos pos = event.getPos();
        ItemStack spectralResidue = new ItemStack(Items.AMETHYST_SHARD);
        ItemEntity residueDrop = new ItemEntity(
                event.getLevel(),
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                spectralResidue
        );

        event.getDrops().add(residueDrop);
    }
}
