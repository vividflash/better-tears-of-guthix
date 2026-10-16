/*
 * Copyright (c) 2026, vividflash
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.vividflash.efficienttearsofguthix;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;

/**
 * The skill the tears' XP goes to if played now, the date of the last run
 * this plugin saw, and the login reminder once the next run is open.
 */
@Singleton
public class SkillReward
{
    /**
     * The game's tie-break order: the skill with the least XP gets the tears,
     * on a tie the first eligible one in this list.
     */
    static final Skill[] ORDER = {
        Skill.ATTACK, Skill.STRENGTH, Skill.RANGED, Skill.MAGIC, Skill.DEFENCE, Skill.HITPOINTS,
        Skill.PRAYER, Skill.AGILITY, Skill.HERBLORE, Skill.THIEVING, Skill.CRAFTING, Skill.RUNECRAFT,
        Skill.MINING, Skill.SMITHING, Skill.FISHING, Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING,
        Skill.FLETCHING, Skill.SLAYER, Skill.FARMING, Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING
    };

    /**
     * Quest progress values at which the game unlocks Runecraft, Herblore and
     * Sailing: the quests' end states, as checked by the XP lamp script.
     */
    private static final int RUNE_MYSTERIES_DONE = 6;
    private static final int DRUIDIC_RITUAL_DONE = 4;
    private static final int PANDEMONIUM_DONE = 50;

    /** The run opens at 00:00 UTC on this many days after the day of the last run. */
    static final int DAYS_BETWEEN_RUNS = 7;

    private static final String CONFIG_GROUP = "efficienttearsofguthix";
    /** Epoch millis of the last run's end, on the RuneScape profile. */
    private static final String LAST_RUN_KEY = "lastRun";
    /** The game's line at the end of a run. */
    private static final String RUN_END_MESSAGE = "Tears collected: ";
    @Inject
    private Client client;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ChatMessageManager chatMessageManager;

    @Inject
    private EfficientTearsOfGuthixConfig config;

    @Inject
    private StreamAdvisor advisor;

    private volatile Skill skill;
    /** The login screen was seen, so the next login is not a world hop. */
    private volatile boolean fromLoginScreen;
    /** Logged in from the login screen, the reminder is still to be checked. */
    private volatile boolean loginPending;
    /** Juna came into the scene: pick the skill on the next tick with stats loaded. */
    private volatile boolean junaPending;
    /** The XP skill setting, cached for the spawn handler. */
    private volatile boolean showSkill;
    /** The XP skill and its refresh setting both on, cached for the stat handler. */
    private volatile boolean refreshSkill;

    void start()
    {
        skill = null;
        // enabled on the login screen: the next login is not a world hop
        fromLoginScreen = client.getGameState() == GameState.LOGIN_SCREEN;
        loginPending = false;
        readConfig();
        junaPending = showSkill;
    }

    /** A setting changed: pick again, so turning the XP skill on at Juna shows it. */
    void configChanged()
    {
        readConfig();
        junaPending = showSkill;
    }

    private void readConfig()
    {
        showSkill = config.showSkill();
        refreshSkill = showSkill && config.refreshSkill();
    }

    /**
     * The skill the tears' XP goes to if played now, as of the last login, the
     * last time Juna came into the scene, or with the refresh setting on the
     * last XP gain in the cave. Null before the first pick.
     */
    Skill skill()
    {
        return skill;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        try
        {
            GameState state = event.getGameState();
            if (state == GameState.LOGGED_IN)
            {
                loginPending = fromLoginScreen && config.loginReminder();
                fromLoginScreen = false;
            }
            else if (state == GameState.LOGIN_SCREEN)
            {
                skill = null;
                fromLoginScreen = true;
                loginPending = false;
                junaPending = false;
            }
        }
        catch (Exception e)
        {
            advisor.report("skillGameState", e);
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        if (showSkill && event.getGameObject().getId() == ObjectID.TOG_JUNA)
        {
            junaPending = true;
        }
    }

    /** With the refresh setting on, an XP gain in the cave picks the skill again. */
    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        if (refreshSkill && advisor.inArea())
        {
            junaPending = true;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        try
        {
            if (event.getType() == ChatMessageType.GAMEMESSAGE && event.getMessage().contains(RUN_END_MESSAGE))
            {
                configManager.setRSProfileConfiguration(CONFIG_GROUP, LAST_RUN_KEY, System.currentTimeMillis());
            }
        }
        catch (Exception e)
        {
            advisor.report("skillChat", e);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        try
        {
            // outside the cave a pending Juna pick waits for the first tick inside
            boolean juna = junaPending && advisor.inArea();
            if ((!juna && !loginPending) || client.getOverallExperience() <= 0)
            {
                return;
            }

            // on login the skills are only checked when the reminder is due
            boolean remind = loginPending && config.loginReminder() && ready();
            if ((juna && config.showSkill()) || remind)
            {
                Skill picked = pick(client.getSkillExperiences(), eligible());
                if (picked != skill)
                {
                    skill = picked;
                    advisor.infoChanged();
                }
            }
            if (juna)
            {
                junaPending = false;
            }
            loginPending = false;
            if (remind)
            {
                remind(skill);
            }
        }
        catch (Exception e)
        {
            advisor.report("skillTick", e);
        }
    }

    /** Per entry of {@link #ORDER}, whether the game counts that skill. */
    private boolean[] eligible()
    {
        boolean[] eligible = new boolean[ORDER.length];
        for (int i = 0; i < ORDER.length; i++)
        {
            switch (ORDER[i])
            {
                case RUNECRAFT:
                    eligible[i] = client.getVarpValue(VarPlayerID.RUNEMYSTERIES) >= RUNE_MYSTERIES_DONE;
                    break;
                case HERBLORE:
                    eligible[i] = client.getVarpValue(VarPlayerID.DRUIDQUEST) >= DRUIDIC_RITUAL_DONE;
                    break;
                case SAILING:
                    eligible[i] = client.getVarbitValue(VarbitID.SAILING_INTRO) >= PANDEMONIUM_DONE;
                    break;
                case CONSTRUCTION:
                    // Construction XP can only be gained in an owned house
                    eligible[i] = client.getVarbitValue(VarbitID.POH_HOUSE_LOCATION) != 0
                        || client.getSkillExperience(Skill.CONSTRUCTION) > 0;
                    break;
                default:
                    eligible[i] = true;
            }
        }
        return eligible;
    }

    /**
     * The eligible skill with the least XP, the first in {@link #ORDER} on a tie.
     *
     * @param experience indexed by {@link Skill#ordinal()}
     * @param eligible indexed like {@link #ORDER}
     */
    static Skill pick(int[] experience, boolean[] eligible)
    {
        Skill best = null;
        for (int i = 0; i < ORDER.length; i++)
        {
            if (eligible[i] && (best == null || experience[ORDER[i].ordinal()] < experience[best.ordinal()]))
            {
                best = ORDER[i];
            }
        }
        return best;
    }

    /**
     * A run was recorded and the next one is open. False with no run recorded.
     * Not checking for the quest point or 100,000 XP requirement because it is unnecessary.
     */
    private boolean ready()
    {
        Long lastRun = configManager.getRSProfileConfiguration(CONFIG_GROUP, LAST_RUN_KEY, Long.class);
        return lastRun != null && ready(lastRun, System.currentTimeMillis());
    }

    static boolean ready(long lastRun, long now)
    {
        LocalDate opens = Instant.ofEpochMilli(lastRun).atZone(ZoneOffset.UTC).toLocalDate()
            .plusDays(DAYS_BETWEEN_RUNS);
        return now >= opens.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private void remind(Skill skill)
    {
        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.CONSOLE)
            .runeLiteFormattedMessage(new ChatMessageBuilder()
                .append("Tears of Guthix is ready. The XP goes to " + skill.getName() + ".")
                .build())
            .build());
    }
}
