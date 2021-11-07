package com.github.turtton.exampleuitest.mixin;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.datafixers.DataFixer;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ServerResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListenerFactory;
import net.minecraft.test.TestServer;
import net.minecraft.util.UserCache;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.GameMode;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.level.storage.LevelStorage;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.util.Objects;

@Mixin(TestServer.class)
public abstract class MixinTestServer extends MinecraftServer {
  @Shadow @Final private static Logger LOGGER;

  public MixinTestServer(
      Thread serverThread,
      DynamicRegistryManager.Impl registryManager,
      LevelStorage.Session session,
      SaveProperties saveProperties,
      ResourcePackManager dataPackManager,
      Proxy proxy,
      DataFixer dataFixer,
      ServerResourceManager serverResourceManager,
      @Nullable MinecraftSessionService sessionService,
      @Nullable GameProfileRepository gameProfileRepo,
      @Nullable UserCache userCache,
      WorldGenerationProgressListenerFactory worldGenerationProgressListenerFactory) {
    super(
        serverThread,
        registryManager,
        session,
        saveProperties,
        dataPackManager,
        proxy,
        dataFixer,
        serverResourceManager,
        sessionService,
        gameProfileRepo,
        userCache,
        worldGenerationProgressListenerFactory);
  }

  @Inject(method = "setupServer", at = @At("HEAD"), cancellable = true)
  private void enableLocalPlayerConnecting(CallbackInfoReturnable<Boolean> cir) throws IOException {
    var server = (TestServer) (Object) this;
    server.setOnlineMode(false);
    server.setServerIp("127.0.0.1");
    server.setPvpEnabled(true);
    server.setFlightEnabled(true);
    //        server.setResourcePack();
    //        server.setMotd();
    server.setPlayerIdleTimeout(0);
    var gamemode = GameMode.DEFAULT;
    server.getSaveProperties().setGameMode(gamemode);
    LOGGER.info("Default game type: {}", gamemode);

    var inetAddress = InetAddress.getByName(server.getServerIp());
    server.setServerPort(25565);
    server.generateKeyPair();
    LOGGER.info(
        "Starting Minecraft server on {}:{}",
        server.getServerIp().isEmpty() ? "*" : server.getServerIp(),
        server.getServerPort());

    try {
      Objects.requireNonNull(server.getNetworkIo()).bind(inetAddress, server.getServerPort());
    } catch (Exception exception) {
      LOGGER.warn("**** FAILED TO BIND TO PORT!");
      LOGGER.warn("The exception was: {}", exception.toString());
      LOGGER.warn("Perhaps a server is already running on that port?");
      cir.setReturnValue(false);
      cir.cancel();
    }
  }

  // Disable compression
  @Override
  public int getNetworkCompressionThreshold() {
    return -1;
  }
}
