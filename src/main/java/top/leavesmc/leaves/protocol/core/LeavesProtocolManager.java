package top.leavesmc.leaves.protocol.core;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.lang.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import org.bukkit.event.player.PlayerKickEvent;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class LeavesProtocolManager {

    private static final Map<LeavesProtocol, Map<ProtocolHandler.PayloadReceiver, Constructor<? extends CustomPacketPayload>>> KNOWN_TYPES = new HashMap<>();
    private static final Map<LeavesProtocol, Map<ProtocolHandler.PayloadReceiver, Method>> KNOW_RECEIVERS = new HashMap<>();

    private static final List<Method> TICKERS = new ArrayList<>();
    private static final List<Method> PLAYER_JOIN = new ArrayList<>();
    private static final List<Method> PLAYER_LEAVE = new ArrayList<>();
    private static final List<Method> RELOAD_SERVER = new ArrayList<>();
    private static final Map<LeavesProtocol, Map<ProtocolHandler.MinecraftRegister, Method>> MINECRAFT_REGISTER = new HashMap<>();

    public static void init() {
        for (Class<?> clazz : getClasses("top.leavesmc.leaves.protocol")) {
            final LeavesProtocol protocol = clazz.getAnnotation(LeavesProtocol.class);
            if (protocol != null) {
                Set<Method> methods;
                try {
                    Method[] publicMethods = clazz.getMethods();
                    Method[] privateMethods = clazz.getDeclaredMethods();
                    methods = new HashSet<>(publicMethods.length + privateMethods.length, 1.0f);
                    Collections.addAll(methods, publicMethods);
                    Collections.addAll(methods, privateMethods);
                } catch (NoClassDefFoundError e) {
                    e.printStackTrace();
                    return;
                }

                Map<ProtocolHandler.PayloadReceiver, Constructor<? extends CustomPacketPayload>> map = KNOWN_TYPES.getOrDefault(protocol, new HashMap<>());
                for (final Method method : methods) {
                    if (method.isBridge() || method.isSynthetic() || !Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }

                    method.setAccessible(true);

                    final ProtocolHandler.Init init = method.getAnnotation(ProtocolHandler.Init.class);
                    if (init != null) {
                        try {
                            method.invoke(null);
                        } catch (InvocationTargetException | IllegalAccessException e) {
                            e.printStackTrace();
                        }
                        continue;
                    }

                    final ProtocolHandler.PayloadReceiver receiver = method.getAnnotation(ProtocolHandler.PayloadReceiver.class);
                    if (receiver != null) {
                        try {
                            map.put(receiver, receiver.payload().getConstructor(ResourceLocation.class, FriendlyByteBuf.class));
                        } catch (NoSuchMethodException e) {
                            e.printStackTrace();
                            continue;
                        }

                        if (!KNOW_RECEIVERS.containsKey(protocol)) {
                            KNOW_RECEIVERS.put(protocol, new HashMap<>());
                        }

                        KNOW_RECEIVERS.get(protocol).put(receiver, method);
                        continue;
                    }

                    final ProtocolHandler.Ticker ticker = method.getAnnotation(ProtocolHandler.Ticker.class);
                    if (ticker != null) {
                        TICKERS.add(method);
                        continue;
                    }

                    final ProtocolHandler.PlayerJoin playerJoin = method.getAnnotation(ProtocolHandler.PlayerJoin.class);
                    if (playerJoin != null) {
                        PLAYER_JOIN.add(method);
                        continue;
                    }

                    final ProtocolHandler.PlayerLeave playerLeave = method.getAnnotation(ProtocolHandler.PlayerLeave.class);
                    if (playerLeave != null) {
                        PLAYER_LEAVE.add(method);
                        continue;
                    }

                    final ProtocolHandler.ReloadServer reloadServer = method.getAnnotation(ProtocolHandler.ReloadServer.class);
                    if (reloadServer != null) {
                        RELOAD_SERVER.add(method);
                        continue;
                    }

                    final ProtocolHandler.MinecraftRegister minecraftRegister = method.getAnnotation(ProtocolHandler.MinecraftRegister.class);
                    if (minecraftRegister != null) {
                        if (!MINECRAFT_REGISTER.containsKey(protocol)) {
                            MINECRAFT_REGISTER.put(protocol, new HashMap<>());
                        }

                        MINECRAFT_REGISTER.get(protocol).put(minecraftRegister, method);
                    }
                }
                KNOWN_TYPES.put(protocol, map);
            }
        }
    }

    public static CustomPacketPayload getPayload(ResourceLocation id, FriendlyByteBuf buf) {
        for (LeavesProtocol protocol : KNOWN_TYPES.keySet()) {
            if (!ArrayUtils.contains(protocol.namespace(), id.getNamespace())) {
                continue;
            }

            Map<ProtocolHandler.PayloadReceiver, Constructor<? extends CustomPacketPayload>> map = KNOWN_TYPES.get(protocol);
            for (ProtocolHandler.PayloadReceiver receiver : map.keySet()) {
                if (receiver.ignoreId() || ArrayUtils.contains(receiver.payloadId(), id.getPath())) {
                    try {
                        return map.get(receiver).newInstance(id, buf);
                    } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
                        e.printStackTrace();
                        buf.readBytes(buf.readableBytes());
                        return new ErrorPayload(id, protocol.namespace(), receiver.payloadId());
                    }
                }
            }
        }
        return null;
    }

    public static void handlePayload(ServerPlayer player, CustomPacketPayload payload) {
        if (payload instanceof ServerboundCustomPayloadPacket.UnknownPayload) {
            return;
        }

        if (payload instanceof ErrorPayload errorPayload) {
            player.connection.disconnect("Payload " + Arrays.toString(errorPayload.packetID) + " from " + Arrays.toString(errorPayload.protocolID) + " error", PlayerKickEvent.Cause.INVALID_PAYLOAD);
            return;
        }

        for (LeavesProtocol protocol : KNOW_RECEIVERS.keySet()) {
            if (!ArrayUtils.contains(protocol.namespace(), payload.id().getNamespace())) {
                continue;
            }

            Map<ProtocolHandler.PayloadReceiver, Method> map = KNOW_RECEIVERS.get(protocol);
            for (ProtocolHandler.PayloadReceiver receiver : map.keySet()) {
                if (payload.getClass() == receiver.payload()) {
                    if (receiver.ignoreId() || ArrayUtils.contains(receiver.payloadId(), payload.id().getPath())) {
                        try {
                            map.get(receiver).invoke(null, player, payload);
                        } catch (InvocationTargetException | IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public static void handleTick() {
        if (!TICKERS.isEmpty()) {
            try {
                for (Method method : TICKERS) {
                    method.invoke(null);
                }
            } catch (InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public static void handlePlayerJoin(ServerPlayer player) {
        if (!PLAYER_JOIN.isEmpty()) {
            try {
                for (Method method : PLAYER_JOIN) {
                    method.invoke(null, player);
                }
            } catch (InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public static void handlePlayerLeave(ServerPlayer player) {
        if (!PLAYER_LEAVE.isEmpty()) {
            try {
                for (Method method : PLAYER_LEAVE) {
                    method.invoke(null, player);
                }
            } catch (InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public static void handleServerReload() {
        if (!RELOAD_SERVER.isEmpty()) {
            try {
                for (Method method : RELOAD_SERVER) {
                    method.invoke(null);
                }
            } catch (InvocationTargetException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public static void handleMinecraftRegister(String channelId, ServerPlayer player) {
        for (LeavesProtocol protocol : MINECRAFT_REGISTER.keySet()) {
            String[] channel = channelId.split(":");
            if (!ArrayUtils.contains(protocol.namespace(), channel[0])) {
                continue;
            }

            Map<ProtocolHandler.MinecraftRegister, Method> map = MINECRAFT_REGISTER.get(protocol);
            for (ProtocolHandler.MinecraftRegister register : map.keySet()) {
                if (register.ignoreId() || register.channelId().equals(channel[1]) ||
                    ArrayUtils.contains(register.channelIds(), channel[1])) {
                    try {
                        map.get(register).invoke(null, player);
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static Set<Class<?>> getClasses(String pack) {
        Set<Class<?>> classes = new LinkedHashSet<Class<?>>();
        String packageDirName = pack.replace('.', '/');
        Enumeration<URL> dirs;
        try {
            dirs = Thread.currentThread().getContextClassLoader().getResources(packageDirName);
            while (dirs.hasMoreElements()) {
                URL url = dirs.nextElement();
                String protocol = url.getProtocol();
                if ("file".equals(protocol)) {
                    String filePath = URLDecoder.decode(url.getFile(), StandardCharsets.UTF_8);
                    findClassesInPackageByFile(pack, filePath, classes);
                } else if ("jar".equals(protocol)) {
                    JarFile jar;
                    try {
                        jar = ((JarURLConnection) url.openConnection()).getJarFile();
                        Enumeration<JarEntry> entries = jar.entries();
                        findClassesInPackageByJar(pack, entries, packageDirName, classes);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return classes;
    }

    private static void findClassesInPackageByFile(String packageName, String packagePath, Set<Class<?>> classes) {
        File dir = new File(packagePath);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] dirfiles = dir.listFiles((file) -> file.isDirectory() || file.getName().endsWith(".class"));
        if (dirfiles != null) {
            for (File file : dirfiles) {
                if (file.isDirectory()) {
                    findClassesInPackageByFile(packageName + "." + file.getName(), file.getAbsolutePath(), classes);
                } else {
                    String className = file.getName().substring(0, file.getName().length() - 6);
                    try {
                        classes.add(Class.forName(packageName + '.' + className));
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static void findClassesInPackageByJar(String packageName, Enumeration<JarEntry> entries, String packageDirName, Set<Class<?>> classes) {
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.charAt(0) == '/') {
                name = name.substring(1);
            }
            if (name.startsWith(packageDirName)) {
                int idx = name.lastIndexOf('/');
                if (idx != -1) {
                    packageName = name.substring(0, idx).replace('/', '.');
                }
                if (name.endsWith(".class") && !entry.isDirectory()) {
                    String className = name.substring(packageName.length() + 1, name.length() - 6);
                    try {
                        classes.add(Class.forName(packageName + '.' + className));
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public record ErrorPayload(ResourceLocation id, String[] protocolID,
                               String[] packetID) implements CustomPacketPayload {
        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
        }
    }

    public record EmptyPayload(ResourceLocation id) implements CustomPacketPayload {

        public EmptyPayload(ResourceLocation location, FriendlyByteBuf buf) {
            this(location);
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
        }
    }

    public record LeavesPayload(FriendlyByteBuf data, ResourceLocation id) implements CustomPacketPayload {

        public LeavesPayload(ResourceLocation location, FriendlyByteBuf buf) {
            this(new FriendlyByteBuf(buf.readBytes(buf.readableBytes())), location);
        }

        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeBytes(data);
        }
    }
}
