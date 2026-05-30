package com.hywbridge;

import com.hywbridge.command.HYWBridgeCommands;
import com.hywbridge.network.OwnerSyncPacket;
import com.hywbridge.util.FactionSyncHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("hywbridge")
public class HYWBridge {
    public static final String MOD_ID = "hywbridge";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static final String PROTOCOL_VERSION = "1";
    public static SimpleChannel NETWORK;

    public HYWBridge() {
        FMLJavaModLoadingContext.get().getModEventBus()
                .addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(FactionSyncHandler.class);
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("HYW Bridge initialized!");
    }

    private void setup(final FMLCommonSetupEvent event) {
        NETWORK = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(MOD_ID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );
        NETWORK.registerMessage(0, OwnerSyncPacket.class,
                OwnerSyncPacket::encode,
                OwnerSyncPacket::decode,
                OwnerSyncPacket::handle);
        LOGGER.info("HYW Bridge setup complete!");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        HYWBridgeCommands.register(event.getDispatcher());
    }
}