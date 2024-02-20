package net.zencraft.velocity.chathistory;

import com.github.retrooper.packetevents.PacketEvents;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;

import net.kyori.adventure.text.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(
        id = "zenchathistory",
        name = "ZenChatHistory",
        version = "1.0.1-SNAPSHOT",
        dependencies = {
            @Dependency(id = "packetevents")
        }
)
public class ChatHistory {

    private final ConcurrentHashMap<Player, Deque<ComponentHolder>> messagesCache = new ConcurrentHashMap<>();
    public ConcurrentHashMap<Player,  Deque<ComponentHolder>> getMessagesCache() {
        return messagesCache;
    }

    private final ConcurrentHashMap<Player, Deque<Component>> unknownServerCache = new ConcurrentHashMap<>();
    public ConcurrentHashMap<Player,  Deque<Component>> getUnknownServerCache() {
        return unknownServerCache;
    }

    private static ChatHistory instance;
    public static ChatHistory getInstance() {
        return instance;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        instance = this;

        PacketEvents.getAPI().getSettings().reEncodeByDefault(false)
                .checkForUpdates(true)
                .bStats(true);
        PacketEvents.getAPI().load();

        PacketEvents.getAPI().getEventManager().registerListener(new SystemChatPacketListener());
        PacketEvents.getAPI().init();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event){
        messagesCache.remove(event.getPlayer());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event){
        Player player = event.getPlayer();
        String serverName = event.getServer().getServerInfo().getName();
        Deque<Component> unknownComponentJsonList = getUnknownServerCache().getOrDefault(event.getPlayer(), new ArrayDeque<>());
        Deque<ComponentHolder> componentJsonList = getMessagesCache().getOrDefault(event.getPlayer(), new ArrayDeque<>());
        unknownComponentJsonList.removeIf(component -> {
            componentJsonList.addLast(new ComponentHolder(component, serverName));
            return true;
        });
        while (componentJsonList.size() > 50) {
            componentJsonList.removeFirst();
        }
        if(componentJsonList != null){
            for(ComponentHolder componentHolder : componentJsonList){
                if (!componentHolder.attemptSendToServer(serverName)){
                    player.sendMessage(componentHolder.getComponent());
                }
            }
        }
    }

    public void addMessage(Player player, Component component) {
        Deque<ComponentHolder> messages = messagesCache.getOrDefault(player, new ArrayDeque<>());
        String serverName = "";
        ServerConnection server = player.getCurrentServer().orElse(null);
        if (server != null){
            serverName = server.getServerInfo().getName();
            messages.addLast(new ComponentHolder(component, serverName));
    
            while (messages.size() > 50) {
                messages.removeFirst();
            }
    
            messagesCache.put(player, messages);
        } else {
            Deque<Component> unknownMessages = unknownServerCache.getOrDefault(player, new ArrayDeque<>());
            unknownMessages.addLast(component);
            unknownServerCache.put(player, unknownMessages);
        }
    }

    private class ComponentHolder {
        private final Component component;
        private final ArrayList<String> sentServers;

        public ComponentHolder(Component component, String originServer){
            this.component = component;
            this.sentServers = new ArrayList<>(1);
            this.sentServers.add(originServer);
        }

        public Component getComponent(){
            return this.component;
        }

        /**
         * Checks if this message has already been sent to the given server.
         * 
         * If the message has not been sent, the server will be added to the list for later.
         * 
         * @param serverName server to check against
         * @return true if this message has already been sent
         */
        public boolean attemptSendToServer(String serverName){
            boolean alreadySent = this.sentServers.contains(serverName);
            System.out.println("Check " + serverName + " against " + sentServers.toString() + " for " + this.component.toString());
            if (!alreadySent){
                this.sentServers.add(serverName);
            }
            return alreadySent;
        }
    }

}
