/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2022 games647 and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package es.karmadev.locklogin.spigot.protocol.protocol.premium.handler;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketEvent;
import es.karmadev.locklogin.api.CurrentPlugin;
import es.karmadev.locklogin.api.network.client.ConnectionType;
import es.karmadev.locklogin.api.network.client.offline.LocalNetworkClient;
import es.karmadev.locklogin.api.plugin.file.Messages;
import es.karmadev.locklogin.api.user.premium.PremiumDataStore;
import es.karmadev.locklogin.spigot.protocol.protocol.premium.LoginSession;
import es.karmadev.locklogin.spigot.protocol.protocol.premium.ProtocolListener;
import es.karmadev.locklogin.spigot.protocol.protocol.premium.StartClient;
import es.karmadev.locklogin.spigot.protocol.protocol.premium.mojang.client.ClientKey;
import ml.karmaconfigs.api.common.minecraft.api.MineAPI;
import ml.karmaconfigs.api.common.minecraft.api.response.OKARequest;
import ml.karmaconfigs.api.common.string.StringUtils;
import ml.karmaconfigs.api.common.utils.uuid.UUIDType;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.security.PublicKey;
import java.util.Random;
import java.util.UUID;

/**
 * Part of the code of this class is from:
 * <a href="https://github.com/games647/FastLogin/blob/main/bukkit/src/main/java/com/github/games647/fastlogin/bukkit/listener/protocollib/NameCheckTask.java">FastLogin</a>
 */
public final class LoginHandler implements Runnable {

    private final static Messages messages = CurrentPlugin.getPlugin().messages();

    private final PacketEvent packet;
    private final ClientKey client;
    private final PublicKey server;
    private final Random random;
    private final Player player;
    private final String username;

    public LoginHandler(final Random random, final Player player, final PacketEvent event, final String name, final ClientKey key, final PublicKey pub) {
        this.random = random;
        this.player = player;
        this.packet = event;
        this.username = name;
        this.client = key;
        this.server = pub;
    }


    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        StartClient start = new StartClient(player, random, client, server);

        try {
            InetSocketAddress address = player.getAddress();
            if (address != null) {
                PremiumDataStore premium = CurrentPlugin.getPlugin().premiumStore();

                UUID offline_id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
                UUID online_uid = premium.onlineId(username);
                if (online_uid == null) {
                    /*OKAResponse response = UUIDUtil.fetchOKA(username);
                    online_uid = response.getId(UUIDType.ONLINE);

                    premium.saveId(username, online_uid);*/
                    OKARequest request = MineAPI.fetchAndWait(username);
                    online_uid = request.getUUID(UUIDType.ONLINE);
                    if (online_uid != null) {
                        premium.saveId(username, online_uid);
                    }
                }

                LocalNetworkClient offline = CurrentPlugin.getPlugin().network().getOfflinePlayer(offline_id);

                LoginSession session;
                boolean cracked = true;
                if (!offline_id.equals(online_uid)) {
                    if (offline.connection().equals(ConnectionType.ONLINE) && start.toggleOnline()) {
                        byte[] verify = start.token();
                        ClientKey key = start.key();
                        session = new LoginSession(username, verify, key);
                        cracked = false;
                    } else {
                        session = new LoginSession(username, null, null);
                    }
                } else {
                    session = new LoginSession(username, null, null);
                }

                ProtocolListener.sessions.put(address, session);
                if (!cracked) {
                    synchronized (packet.getAsyncMarker().getProcessingLock()) {
                        packet.setCancelled(true);
                    }
                }
            } else {
                player.kickPlayer(StringUtils.toColor(messages.premiumFailSession()));
            }
        } finally {
            ProtocolLibrary.getProtocolManager().getAsynchronousManager().signalPacketTransmission(packet);
        }
    }
}
